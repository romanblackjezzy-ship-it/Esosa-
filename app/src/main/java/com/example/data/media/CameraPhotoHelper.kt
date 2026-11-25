package com.example.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.example.data.model.MediaCropSpec
import com.example.data.model.PostFilter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import kotlin.math.roundToInt

object CameraPhotoHelper {

    fun createTempPhotoTarget(context: Context): Pair<Uri, File> {
        val dir = File(context.cacheDir, "camera_photos").apply {
            if (!exists()) mkdirs()
        }
        val file = File(dir, "esosa_cam_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return uri to file
    }

    fun createTempVideoTarget(context: Context): Pair<Uri, File> {
        val dir = File(context.cacheDir, "camera_photos").apply {
            if (!exists()) mkdirs()
        }
        val file = File(dir, "esosa_vid_${System.currentTimeMillis()}.mp4")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return uri to file
    }

    fun isVideoUri(context: Context, uri: Uri): Boolean {
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()
        if (mime.startsWith("video/", ignoreCase = true)) return true
        val raw = uri.toString().lowercase()
        return raw.endsWith(".mp4") ||
            raw.endsWith(".mov") ||
            raw.endsWith(".webm") ||
            raw.endsWith(".3gp") ||
            raw.endsWith(".mkv")
    }

    fun saveUriToLocalChatPhotos(
        context: Context,
        uri: Uri,
        maxDimension: Int = 1600,
        quality: Int = 84
    ): File {
        val jpegBytes = compressUriToJpegBytes(
            context = context,
            uri = uri,
            maxDimension = maxDimension,
            quality = quality
        )
        return saveJpegBytesToLocalChatPhotos(context, jpegBytes)
    }

    fun saveJpegBytesToLocalChatPhotos(
        context: Context,
        jpegBytes: ByteArray
    ): File {
        val dir = File(context.filesDir, "chat_photos").apply {
            if (!exists()) mkdirs()
        }
        val destFile = File(dir, "photo_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg")
        FileOutputStream(destFile).use { out ->
            out.write(jpegBytes)
            out.flush()
        }
        return destFile
    }

    fun compressUriToJpegBytesWithSizeCap(
        context: Context,
        uri: Uri,
        maxBytes: Int = 9_500_000
    ): ByteArray {
        val attempts = listOf(
            1600 to 84,
            1280 to 72,
            1024 to 58
        )
        var lastBytes = ByteArray(0)
        for ((dimension, quality) in attempts) {
            val encoded = compressUriToJpegBytes(
                context = context,
                uri = uri,
                maxDimension = dimension,
                quality = quality
            )
            lastBytes = encoded
            if (encoded.size <= maxBytes) {
                return encoded
            }
        }
        return lastBytes
    }

    fun deleteLocalPhotoIfOwned(context: Context, storagePath: String?) {
        if (storagePath.isNullOrBlank()) return
        runCatching {
            val file = File(storagePath)
            val ownedDir = File(context.filesDir, "chat_photos").canonicalPath
            if (file.canonicalPath.startsWith(ownedDir) && file.exists()) {
                file.delete()
            }
        }
    }

    fun compressUriToJpegBytes(
        context: Context,
        uri: Uri,
        maxDimension: Int = 1600,
        quality: Int = 82
    ): ByteArray {
        val oriented = decodeOrientedBitmapFromUri(
            context = context,
            uri = uri,
            maxTargetDimension = maxDimension * 2,
            openErrorMessage = "Could not open captured photo.",
            emptyErrorMessage = "Captured image is empty or unreadable.",
            decodeErrorMessage = "Could not decode captured photo."
        )
        val scaled = scaleDownIfNeeded(oriented, maxDimension)

        return try {
            ByteArrayOutputStream().use { out ->
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
                    throw IllegalStateException("Could not encode JPEG photo.")
                }
                out.toByteArray()
            }
        } finally {
            if (scaled !== oriented && !scaled.isRecycled) scaled.recycle()
            if (!oriented.isRecycled) oriented.recycle()
        }
    }

    fun computeCropRect(srcWidth: Int, srcHeight: Int, cropSpec: MediaCropSpec): Rect {
        val w = srcWidth.coerceAtLeast(1)
        val h = srcHeight.coerceAtLeast(1)
        val targetAspect = cropSpec.targetAspectRatio.coerceIn(0.45f, 2.2f)
        val srcAspect = w.toFloat() / h.toFloat()

        val baseCropW: Float
        val baseCropH: Float
        if (srcAspect > targetAspect) {
            baseCropH = h.toFloat()
            baseCropW = baseCropH * targetAspect
        } else {
            baseCropW = w.toFloat()
            baseCropH = baseCropW / targetAspect
        }

        val scale = cropSpec.scale.coerceIn(1f, 3f)
        val cropW = (baseCropW / scale).roundToInt().coerceIn(1, w)
        val cropH = (baseCropH / scale).roundToInt().coerceIn(1, h)

        val maxLeft = (w - cropW).coerceAtLeast(0)
        val maxTop = (h - cropH).coerceAtLeast(0)
        val centerLeft = maxLeft / 2f
        val centerTop = maxTop / 2f

        val shiftedLeft = (centerLeft - cropSpec.offsetXFraction.coerceIn(-1f, 1f) * centerLeft)
            .roundToInt()
            .coerceIn(0, maxLeft)
        val shiftedTop = (centerTop - cropSpec.offsetYFraction.coerceIn(-1f, 1f) * centerTop)
            .roundToInt()
            .coerceIn(0, maxTop)

        return Rect(shiftedLeft, shiftedTop, shiftedLeft + cropW, shiftedTop + cropH)
    }

    fun processImageToJpegBytesWithCropAndFilter(
        context: Context,
        uri: Uri,
        cropSpec: MediaCropSpec,
        filter: PostFilter,
        maxBytes: Int = 9_500_000
    ): ByteArray {
        val oriented = decodeOrientedBitmapFromUri(
            context = context,
            uri = uri,
            maxTargetDimension = 3200,
            openErrorMessage = "Could not open selected image.",
            emptyErrorMessage = "Selected image is empty or unreadable.",
            decodeErrorMessage = "Could not decode selected image."
        )
        val cropRect = computeCropRect(oriented.width, oriented.height, cropSpec)
        val cropped = Bitmap.createBitmap(
            oriented,
            cropRect.left,
            cropRect.top,
            cropRect.width().coerceIn(1, oriented.width - cropRect.left),
            cropRect.height().coerceIn(1, oriented.height - cropRect.top)
        )

        val filtered = if (filter == PostFilter.NORMAL) {
            cropped
        } else {
            val outBitmap = Bitmap.createBitmap(
                cropped.width,
                cropped.height,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(outBitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(ColorMatrix(filter.colorMatrixValues()))
            }
            canvas.drawBitmap(cropped, 0f, 0f, paint)
            outBitmap
        }

        return try {
            val attempts = listOf(1600 to 86, 1280 to 74, 1024 to 60)
            var resultBytes = ByteArray(0)
            for ((dim, quality) in attempts) {
                val scaled = scaleDownIfNeeded(filtered, dim)
                val bytes = try {
                    ByteArrayOutputStream().use { out ->
                        if (!scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
                            throw IllegalStateException("Could not encode processed JPEG.")
                        }
                        out.toByteArray()
                    }
                } finally {
                    if (scaled !== filtered && !scaled.isRecycled) scaled.recycle()
                }
                resultBytes = bytes
                if (bytes.size <= maxBytes) break
            }
            resultBytes
        } finally {
            if (filtered !== cropped && !filtered.isRecycled) filtered.recycle()
            if (cropped !== oriented && !cropped.isRecycled) cropped.recycle()
            if (!oriented.isRecycled) oriented.recycle()
        }
    }

    fun extractVideoThumbnailJpegBytes(
        context: Context,
        videoUri: Uri,
        filter: PostFilter = PostFilter.NORMAL,
        maxDimension: Int = 1280
    ): ByteArray {
        val retriever = MediaMetadataRetriever()
        val rawFrame = try {
            retriever.setDataSource(context, videoUri)
            retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
        } finally {
            runCatching { retriever.release() }
        } ?: throw IllegalStateException("Could not extract preview frame from selected video.")

        val scaled = scaleDownIfNeeded(rawFrame, maxDimension)
        val filtered = if (filter == PostFilter.NORMAL) {
            scaled
        } else {
            val outBitmap = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(outBitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(ColorMatrix(filter.colorMatrixValues()))
            }
            canvas.drawBitmap(scaled, 0f, 0f, paint)
            outBitmap
        }

        return try {
            ByteArrayOutputStream().use { out ->
                filtered.compress(Bitmap.CompressFormat.JPEG, 84, out)
                out.toByteArray()
            }
        } finally {
            if (filtered !== scaled && !filtered.isRecycled) filtered.recycle()
            if (scaled !== rawFrame && !scaled.isRecycled) scaled.recycle()
            if (!rawFrame.isRecycled) rawFrame.recycle()
        }
    }

    fun readVideoBytesWithSizeCap(
        context: Context,
        videoUri: Uri,
        maxBytes: Int = 24 * 1024 * 1024
    ): ByteArray {
        val stream = openInputStreamWithFallback(context, videoUri)
            ?: throw IllegalStateException("Could not open selected video.")
        return stream.use { input ->
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val read = input.read(chunk)
                if (read <= 0) break
                total += read
                if (total > maxBytes) {
                    throw IllegalArgumentException("Selected video exceeds the 24 MB upload limit. Trim or record a shorter clip.")
                }
                buffer.write(chunk, 0, read)
            }
            val bytes = buffer.toByteArray()
            if (bytes.isEmpty()) {
                throw IllegalStateException("Selected video file is empty.")
            }
            bytes
        }
    }

    private fun openInputStreamWithFallback(context: Context, uri: Uri): InputStream? {
        runCatching { context.contentResolver.openInputStream(uri) }
            .getOrNull()
            ?.let { return it }

        runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.createInputStream()
        }.getOrNull()?.let { return it }

        if (uri.scheme.equals("file", ignoreCase = true)) {
            val path = uri.path
            if (!path.isNullOrBlank()) {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    return runCatching { file.inputStream() }.getOrNull()
                }
            }
        }
        return null
    }

    private fun copyUriToTempFile(
        context: Context,
        uri: Uri,
        openErrorMessage: String,
        emptyErrorMessage: String
    ): File {
        val stagingDir = File(context.cacheDir, "media_staging").apply {
            if (!exists()) mkdirs()
        }
        val tempFile = File.createTempFile("esosa_media_", ".tmp", stagingDir)
        try {
            val candidates: List<() -> InputStream?> = listOf(
                { context.contentResolver.openInputStream(uri) },
                { context.contentResolver.openAssetFileDescriptor(uri, "r")?.createInputStream() },
                {
                    if (uri.scheme.equals("file", ignoreCase = true)) {
                        val path = uri.path
                        if (!path.isNullOrBlank()) {
                            File(path).takeIf { it.exists() && it.canRead() }?.inputStream()
                        } else null
                    } else null
                }
            )

            var anyOpenedAndRead = false
            var lastError: Throwable? = null
            for (openCandidate in candidates) {
                val attempt = runCatching {
                    val stream = openCandidate() ?: return@runCatching false
                    stream.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                            output.flush()
                        }
                    }
                    true
                }
                if (attempt.getOrNull() == true) {
                    anyOpenedAndRead = true
                    break
                }
                attempt.exceptionOrNull()?.let { lastError = it }
            }

            if (!anyOpenedAndRead) {
                throw IllegalStateException(openErrorMessage, lastError)
            }
            if (tempFile.length() <= 0L) {
                throw IllegalStateException(emptyErrorMessage)
            }
            return tempFile
        } catch (t: Throwable) {
            tempFile.delete()
            throw t
        }
    }

    private fun decodeOrientedBitmapFromUri(
        context: Context,
        uri: Uri,
        maxTargetDimension: Int,
        openErrorMessage: String,
        emptyErrorMessage: String,
        decodeErrorMessage: String
    ): Bitmap {
        val tempFile = copyUriToTempFile(
            context = context,
            uri = uri,
            openErrorMessage = openErrorMessage,
            emptyErrorMessage = emptyErrorMessage
        )
        try {
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(tempFile.absolutePath, boundsOptions)

            val srcWidth = boundsOptions.outWidth
            val srcHeight = boundsOptions.outHeight
            if (srcWidth > 0 && srcHeight > 0) {
                var sampleSize = 1
                while (srcWidth / sampleSize > maxTargetDimension || srcHeight / sampleSize > maxTargetDimension) {
                    sampleSize *= 2
                }
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                }
                val decoded = BitmapFactory.decodeFile(tempFile.absolutePath, decodeOptions)
                if (decoded != null) {
                    val orientation = runCatching {
                        ExifInterface(tempFile.absolutePath).getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL
                        )
                    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
                    val oriented = rotateIfNeeded(decoded, orientation)
                    if (oriented !== decoded && !decoded.isRecycled) {
                        decoded.recycle()
                    }
                    return oriented
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val decodedWithImageDecoder = runCatching {
                    val source = ImageDecoder.createSource(tempFile)
                    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        val w = info.size.width
                        val h = info.size.height
                        val maxSide = maxOf(w, h)
                        if (maxSide > maxTargetDimension && maxSide > 0) {
                            val ratio = maxTargetDimension.toFloat() / maxSide.toFloat()
                            decoder.setTargetSize(
                                (w * ratio).roundToInt().coerceAtLeast(1),
                                (h * ratio).roundToInt().coerceAtLeast(1)
                            )
                        }
                    }
                }.getOrNull()
                if (decodedWithImageDecoder != null) {
                    return decodedWithImageDecoder
                }
            }

            if (srcWidth <= 0 || srcHeight <= 0) {
                throw IllegalStateException(emptyErrorMessage)
            }
            throw IllegalStateException(decodeErrorMessage)
        } finally {
            tempFile.delete()
        }
    }

    private fun rotateIfNeeded(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= maxDimension) return bitmap
        val ratio = maxDimension.toFloat() / maxSide.toFloat()
        val targetW = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val targetH = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }
}
