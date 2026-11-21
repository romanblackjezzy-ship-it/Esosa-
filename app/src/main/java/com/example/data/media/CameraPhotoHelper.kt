package com.example.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

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
