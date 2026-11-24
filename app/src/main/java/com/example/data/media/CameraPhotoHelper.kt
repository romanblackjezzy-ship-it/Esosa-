package com.example.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.model.MediaCropSpec
import com.example.data.model.PostFilter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
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
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        } ?: throw IllegalStateException("Could not open captured photo.")

        val srcWidth = boundsOptions.outWidth
        val srcHeight = boundsOptions.outHeight
        if (srcWidth <= 0 || srcHeight <= 0) {
            throw IllegalStateException("Captured image is empty or unreadable.")
        }

        var sampleSize = 1
        while (srcWidth / sampleSize > maxDimension * 2 || srcHeight / sampleSize > maxDimension * 2) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: throw IllegalStateException("Could not decode captured photo.")

        val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL

        val oriented = rotateIfNeeded(decoded, orientation)
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
            if (oriented !== decoded && !oriented.isRecycled) oriented.recycle()
            if (!decoded.isRecycled) decoded.recycle()
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
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        } ?: throw IllegalStateException("Could not open selected image.")

        val srcWidth = boundsOptions.outWidth
        val srcHeight = boundsOptions.outHeight
        if (srcWidth <= 0 || srcHeight <= 0) {
            throw IllegalStateException("Selected image is empty or unreadable.")
        }

        var sampleSize = 1
        while (srcWidth / sampleSize > 3200 || srcHeight / sampleSize > 3200) {
            sampleSize *= 2
        }

        val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        } ?: throw IllegalStateException("Could not decode selected image.")

        val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL

        val oriented = rotateIfNeeded(decoded, orientation)
        val cropRect = computeCropRect(oriented.width, oriented.height, cropSpec)
        val cropped = Bitmap.createBitmap(
            oriented,
            cropRect.left,
            cropRect.top,
            cropRect.width().coerceAtLeast(1),
            cropRect.height().coerceAtLeast(1)
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
            if (oriented !== decoded && !oriented.isRecycled) oriented.recycle()
            if (!decoded.isRecycled) decoded.recycle()
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
        val stream = context.contentResolver.openInputStream(videoUri)
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

    private fun rotateIfNeeded(bitmap: Bitmap, orientation: Int): Bitmap {
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
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
