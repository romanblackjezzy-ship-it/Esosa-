package com.example.data.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

enum class ImageCompressionPreset(
    val label: String,
    val maxDimension: Int,
    val minDimension: Int,
    val targetMaxBytes: Int,
    val hardMaxBytes: Int,
    val qualitySteps: List<Int>
) {
    FEED_POST(
        label = "feed_post",
        maxDimension = 1440,
        minDimension = 960,
        targetMaxBytes = 850_000,
        hardMaxBytes = 9_500_000,
        qualitySteps = listOf(82, 74, 64, 56)
    ),
    STORY(
        label = "story",
        maxDimension = 1280,
        minDimension = 864,
        targetMaxBytes = 700_000,
        hardMaxBytes = 9_500_000,
        qualitySteps = listOf(80, 72, 62, 54)
    ),
    CHAT_PHOTO(
        label = "chat_photo",
        maxDimension = 1280,
        minDimension = 864,
        targetMaxBytes = 700_000,
        hardMaxBytes = 9_500_000,
        qualitySteps = listOf(80, 72, 62, 54)
    ),
    PROFILE_AVATAR(
        label = "profile_avatar",
        maxDimension = 512,
        minDimension = 320,
        targetMaxBytes = 180_000,
        hardMaxBytes = 2_000_000,
        qualitySteps = listOf(82, 72, 62)
    )
}

data class CompressionMetrics(
    val preset: ImageCompressionPreset,
    val originalBytes: Long,
    val compressedBytes: Long,
    val width: Int,
    val height: Int,
    val format: SupportedImageFormat,
    val wasReencoded: Boolean
) {
    val savedBytes: Long
        get() = (originalBytes - compressedBytes).coerceAtLeast(0L)

    val savingsPercent: Int
        get() {
            if (originalBytes <= 0L || compressedBytes >= originalBytes) return 0
            return (((originalBytes - compressedBytes).toDouble() / originalBytes.toDouble()) * 100.0)
                .roundToInt()
                .coerceIn(0, 99)
        }
}

object ImageCompressionUtil {

    private const val TAG = "ImageCompressionUtil"

    fun canKeepCompactUneditedFile(
        originalBytes: Long,
        width: Int,
        height: Int,
        normalOrientation: Boolean,
        aspectMatches: Boolean,
        preset: ImageCompressionPreset,
        maxBytesCap: Int = preset.targetMaxBytes
    ): Boolean {
        val effectiveBudget = minOf(preset.targetMaxBytes, maxBytesCap)
        val maxSide = maxOf(width, height)
        return originalBytes in 1L..effectiveBudget.toLong() &&
            maxSide in 1..preset.maxDimension &&
            normalOrientation &&
            aspectMatches
    }

    fun compressBitmapForUpload(
        context: Context,
        bitmap: Bitmap,
        preferredFormat: SupportedImageFormat,
        preferredExtension: String? = null,
        preset: ImageCompressionPreset = ImageCompressionPreset.FEED_POST,
        originalBytes: Long = 0L,
        maxDimensionOverride: Int? = null,
        maxBytesOverride: Int? = null,
        originalUri: Uri,
        stagingFileCreator: (Context, String) -> File
    ): PreparedUploadFile {
        val maxDim = (maxDimensionOverride ?: preset.maxDimension).coerceAtLeast(128)
        val hardCap = (maxBytesOverride ?: preset.hardMaxBytes).coerceAtLeast(64_000)
        val targetCap = minOf(preset.targetMaxBytes, hardCap)

        val dimSteps = listOf(
            maxDim,
            minOf(maxDim, 1080),
            minOf(maxDim, preset.minDimension)
        ).distinct()

        val attemptPlan = buildList {
            preset.qualitySteps.forEachIndexed { idx, quality ->
                val dim = if (idx < dimSteps.size) dimSteps[idx] else dimSteps.last()
                add(dim to quality)
            }
            add(dimSteps.last() to preset.qualitySteps.last())
        }.distinct()

        val formatSequence = when (preferredFormat) {
            SupportedImageFormat.PNG -> listOf(
                SupportedImageFormat.PNG,
                SupportedImageFormat.WEBP,
                SupportedImageFormat.JPEG
            )
            SupportedImageFormat.WEBP -> listOf(
                SupportedImageFormat.WEBP,
                SupportedImageFormat.JPEG
            )
            SupportedImageFormat.JPEG -> listOf(SupportedImageFormat.JPEG)
        }

        var bestCandidateBytes: ByteArray? = null
        var bestCandidateFormat: SupportedImageFormat = SupportedImageFormat.JPEG
        var bestWidth = bitmap.width
        var bestHeight = bitmap.height

        for (targetFormat in formatSequence) {
            val stepsForFormat = if (targetFormat == SupportedImageFormat.PNG) {
                dimSteps.map { it to 100 }
            } else {
                attemptPlan
            }

            for ((dim, quality) in stepsForFormat) {
                val scaled = scaleDownIfNeeded(bitmap, dim)
                val encodedWidth = scaled.width
                val encodedHeight = scaled.height
                val bytes = try {
                    ByteArrayOutputStream().use { out ->
                        if (!scaled.compress(targetFormat.bitmapCompressFormat(), quality, out)) {
                            throw IllegalStateException("Could not encode ${targetFormat.name} image.")
                        }
                        out.toByteArray()
                    }
                } finally {
                    if (scaled !== bitmap && !scaled.isRecycled) scaled.recycle()
                }

                if (bytes.isNotEmpty() && bytes.size <= hardCap) {
                    if (bestCandidateBytes == null || bytes.size < bestCandidateBytes.size) {
                        bestCandidateBytes = bytes
                        bestCandidateFormat = targetFormat
                        bestWidth = encodedWidth
                        bestHeight = encodedHeight
                    }
                }

                if (bytes.isNotEmpty() && bytes.size <= targetCap) {
                    return writeCompressedFile(
                        context = context,
                        bytes = bytes,
                        format = targetFormat,
                        preferredExtension = preferredExtension,
                        preset = preset,
                        originalBytes = originalBytes,
                        width = encodedWidth,
                        height = encodedHeight,
                        originalUri = originalUri,
                        stagingFileCreator = stagingFileCreator
                    )
                }
            }
        }

        val chosenBytes = bestCandidateBytes ?: run {
            val fallbackScaled = scaleDownIfNeeded(bitmap, preset.minDimension.coerceAtMost(maxDim))
            bestWidth = fallbackScaled.width
            bestHeight = fallbackScaled.height
            try {
                ByteArrayOutputStream().use { out ->
                    if (!fallbackScaled.compress(Bitmap.CompressFormat.JPEG, 52, out)) {
                        throw IllegalStateException("Could not compress image for upload.")
                    }
                    out.toByteArray()
                }
            } finally {
                if (fallbackScaled !== bitmap && !fallbackScaled.isRecycled) fallbackScaled.recycle()
            }
        }

        val chosenFormat = if (bestCandidateBytes != null) bestCandidateFormat else SupportedImageFormat.JPEG
        return writeCompressedFile(
            context = context,
            bytes = chosenBytes,
            format = chosenFormat,
            preferredExtension = preferredExtension,
            preset = preset,
            originalBytes = originalBytes,
            width = bestWidth,
            height = bestHeight,
            originalUri = originalUri,
            stagingFileCreator = stagingFileCreator
        )
    }

    fun compressBitmapToJpegBytes(
        bitmap: Bitmap,
        preset: ImageCompressionPreset = ImageCompressionPreset.CHAT_PHOTO,
        maxBytesOverride: Int? = null
    ): ByteArray {
        val hardCap = (maxBytesOverride ?: preset.hardMaxBytes).coerceAtLeast(32_000)
        val targetCap = minOf(preset.targetMaxBytes, hardCap)
        val dimSteps = listOf(
            preset.maxDimension,
            minOf(preset.maxDimension, 1080),
            preset.minDimension
        ).distinct()

        var smallestBytes = ByteArray(0)
        for ((index, quality) in preset.qualitySteps.withIndex()) {
            val dim = dimSteps.getOrElse(index) { dimSteps.last() }
            val scaled = scaleDownIfNeeded(bitmap, dim)
            val bytes = try {
                ByteArrayOutputStream().use { out ->
                    if (!scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
                        throw IllegalStateException("Could not encode JPEG photo.")
                    }
                    out.toByteArray()
                }
            } finally {
                if (scaled !== bitmap && !scaled.isRecycled) scaled.recycle()
            }
            if (smallestBytes.isEmpty() || bytes.size < smallestBytes.size) {
                smallestBytes = bytes
            }
            if (bytes.size <= targetCap) {
                return bytes
            }
        }
        return smallestBytes
    }

    fun scaleDownIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= maxDimension) return bitmap
        val ratio = maxDimension.toFloat() / maxSide.toFloat()
        val targetW = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val targetH = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    private fun resolveOutputExtension(
        format: SupportedImageFormat,
        preferredExtension: String?
    ): String {
        return if (format == SupportedImageFormat.JPEG && preferredExtension.equals("jpeg", ignoreCase = true)) {
            "jpeg"
        } else {
            format.extension
        }
    }

    private fun writeCompressedFile(
        context: Context,
        bytes: ByteArray,
        format: SupportedImageFormat,
        preferredExtension: String?,
        preset: ImageCompressionPreset,
        originalBytes: Long,
        width: Int,
        height: Int,
        originalUri: Uri,
        stagingFileCreator: (Context, String) -> File
    ): PreparedUploadFile {
        val ext = resolveOutputExtension(format, preferredExtension)
        val outFile = stagingFileCreator(context, ext)
        FileOutputStream(outFile).use { output ->
            output.write(bytes)
            output.flush()
        }
        val finalSize = outFile.length()
        val metrics = CompressionMetrics(
            preset = preset,
            originalBytes = if (originalBytes > 0L) originalBytes else finalSize,
            compressedBytes = finalSize,
            width = width,
            height = height,
            format = format,
            wasReencoded = true
        )
        runCatching {
            Log.i(
                TAG,
                "Compressed image [preset=${preset.label}, format=${format.mimeType}, " +
                    "dim=${width}x$height, original=${metrics.originalBytes}B, " +
                    "compressed=${metrics.compressedBytes}B, saved=${metrics.savingsPercent}%]"
            )
        }
        return PreparedUploadFile(
            file = outFile,
            fileUri = Uri.fromFile(outFile),
            contentType = format.mimeType,
            fileExtension = ext,
            sizeBytes = finalSize,
            originalUri = originalUri,
            compressionMetrics = metrics
        )
    }
}
