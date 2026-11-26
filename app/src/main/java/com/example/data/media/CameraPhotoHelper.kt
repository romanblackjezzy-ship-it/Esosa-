package com.example.data.media

import android.content.Context
import android.content.Intent
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
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.example.data.model.MediaCropSpec
import com.example.data.model.PostFilter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import kotlin.math.roundToInt

enum class SupportedImageFormat(
    val extension: String,
    val mimeType: String
) {
    JPEG(extension = "jpg", mimeType = "image/jpeg"),
    PNG(extension = "png", mimeType = "image/png"),
    WEBP(extension = "webp", mimeType = "image/webp");

    @Suppress("DEPRECATION")
    fun bitmapCompressFormat(): Bitmap.CompressFormat = when (this) {
        JPEG -> Bitmap.CompressFormat.JPEG
        PNG -> Bitmap.CompressFormat.PNG
        WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }
    }

    companion object {
        fun fromMimeOrExtension(value: String?): SupportedImageFormat? {
            val normalized = value?.trim()?.lowercase().orEmpty()
            return when {
                normalized == "image/png" || normalized == "png" || normalized.endsWith(".png") -> PNG
                normalized == "image/webp" || normalized == "webp" || normalized.endsWith(".webp") -> WEBP
                normalized == "image/jpeg" ||
                    normalized == "image/jpg" ||
                    normalized == "jpg" ||
                    normalized == "jpeg" ||
                    normalized.endsWith(".jpg") ||
                    normalized.endsWith(".jpeg") -> JPEG
                else -> null
            }
        }
    }
}

data class StagedSelection(
    val file: File,
    val fileUri: Uri,
    val isVideo: Boolean,
    val contentType: String,
    val fileExtension: String
)

data class PreparedUploadFile(
    val file: File,
    val fileUri: Uri,
    val contentType: String,
    val fileExtension: String,
    val sizeBytes: Long,
    val originalUri: Uri? = null
)

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

    fun detectImageFormat(
        context: Context,
        uri: Uri,
        candidateFile: File? = null
    ): SupportedImageFormat {
        val resolverMime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        SupportedImageFormat.fromMimeOrExtension(resolverMime)?.let { return it }

        SupportedImageFormat.fromMimeOrExtension(uri.lastPathSegment)?.let { return it }
        SupportedImageFormat.fromMimeOrExtension(uri.path)?.let { return it }

        val candidateName = candidateFile?.name?.takeUnless { it.endsWith(".tmp", ignoreCase = true) }
        SupportedImageFormat.fromMimeOrExtension(candidateName)?.let { return it }

        if (candidateFile != null && candidateFile.exists() && candidateFile.length() >= 12L) {
            detectImageFormatFromFileHeader(candidateFile)?.let { return it }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(candidateFile.absolutePath, bounds)
            SupportedImageFormat.fromMimeOrExtension(bounds.outMimeType)?.let { return it }
        }

        return SupportedImageFormat.JPEG
    }

    fun stageSelectedMediaForUpload(
        context: Context,
        uri: Uri
    ): StagedSelection {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        val isVideo = isVideoUri(context, uri)
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        val extFromMime = mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
        val extFromPath = uri.lastPathSegment
            ?.substringAfterLast('.', "")
            ?.lowercase()
            ?.takeIf { it.length in 2..5 && it.all(Char::isLetterOrDigit) }

        val initialExt = when {
            isVideo -> extFromMime ?: extFromPath ?: "mp4"
            else -> SupportedImageFormat.fromMimeOrExtension(mime)?.extension
                ?: SupportedImageFormat.fromMimeOrExtension(extFromPath)?.extension
                ?: "tmp"
        }

        val stagingDir = File(context.cacheDir, "staged_media").apply {
            if (!exists()) mkdirs()
        }
        val destFile = File(
            stagingDir,
            "staged_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.$initialExt"
        )
        val tempCopied = copyUriToTempFile(
            context = context,
            uri = uri,
            openErrorMessage = "Selected media URI is not accessible.",
            emptyErrorMessage = "Selected media file is empty."
        )
        try {
            val resolvedFormat = if (!isVideo) {
                detectImageFormat(context, uri, tempCopied)
            } else {
                null
            }
            val finalExt = resolvedFormat?.extension ?: initialExt
            val finalFile = if (finalExt != initialExt) {
                File(
                    stagingDir,
                    "staged_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.$finalExt"
                )
            } else {
                destFile
            }
            tempCopied.copyTo(finalFile, overwrite = true)
            if (!finalFile.exists() || !finalFile.canRead() || finalFile.length() <= 0L) {
                throw IllegalStateException("Could not stage selected media for upload.")
            }
            val contentType = when {
                isVideo -> mime?.takeIf { it.startsWith("video/", ignoreCase = true) } ?: "video/mp4"
                resolvedFormat != null -> resolvedFormat.mimeType
                else -> SupportedImageFormat.JPEG.mimeType
            }
            return StagedSelection(
                file = finalFile,
                fileUri = Uri.fromFile(finalFile),
                isVideo = isVideo,
                contentType = contentType,
                fileExtension = finalExt
            )
        } finally {
            tempCopied.delete()
        }
    }

    fun prepareUploadImageFile(
        context: Context,
        uri: Uri,
        cropSpec: MediaCropSpec? = null,
        filter: PostFilter = PostFilter.NORMAL,
        maxDimension: Int = 1600,
        maxBytes: Int = 9_500_000
    ): PreparedUploadFile {
        val stagedSource = copyUriToTempFile(
            context = context,
            uri = uri,
            openErrorMessage = "Could not open selected image.",
            emptyErrorMessage = "Selected image is empty or unreadable."
        )
        try {
            val format = detectImageFormat(context, uri, stagedSource)
            val isUneditedCrop = cropSpec == null || (
                cropSpec.scale <= 1.01f &&
                    kotlin.math.abs(cropSpec.offsetXFraction) < 0.01f &&
                    kotlin.math.abs(cropSpec.offsetYFraction) < 0.01f
                )

            if (isUneditedCrop && filter == PostFilter.NORMAL && stagedSource.length() in 1L..maxBytes.toLong()) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(stagedSource.absolutePath, bounds)
                val orientation = runCatching {
                    ExifInterface(stagedSource.absolutePath).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
                val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
                val aspectMatches = cropSpec == null || (
                    bounds.outWidth > 0 && bounds.outHeight > 0 &&
                        kotlin.math.abs((bounds.outWidth.toFloat() / bounds.outHeight.toFloat()) - cropSpec.targetAspectRatio) < 0.03f
                    )
                val normalOrientation = orientation == ExifInterface.ORIENTATION_NORMAL ||
                    orientation == ExifInterface.ORIENTATION_UNDEFINED
                if (maxSide in 1..maxDimension && normalOrientation && aspectMatches) {
                    val directOut = createUploadStagingFile(context, format.extension)
                    stagedSource.copyTo(directOut, overwrite = true)
                    return PreparedUploadFile(
                        file = directOut,
                        fileUri = Uri.fromFile(directOut),
                        contentType = format.mimeType,
                        fileExtension = format.extension,
                        sizeBytes = directOut.length(),
                        originalUri = uri
                    )
                }
            }

            val oriented = decodeOrientedBitmapFromFile(
                tempFile = stagedSource,
                maxTargetDimension = 3200,
                emptyErrorMessage = "Selected image is empty or unreadable.",
                decodeErrorMessage = "Could not decode selected image."
            )
            val cropped = if (cropSpec != null) {
                val cropRect = computeCropRect(oriented.width, oriented.height, cropSpec)
                Bitmap.createBitmap(
                    oriented,
                    cropRect.left,
                    cropRect.top,
                    cropRect.width().coerceIn(1, oriented.width - cropRect.left),
                    cropRect.height().coerceIn(1, oriented.height - cropRect.top)
                )
            } else {
                oriented
            }

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
                encodeBitmapToUploadFile(
                    context = context,
                    bitmap = filtered,
                    preferredFormat = format,
                    maxDimension = maxDimension,
                    maxBytes = maxBytes,
                    originalUri = uri
                )
            } finally {
                if (filtered !== cropped && !filtered.isRecycled) filtered.recycle()
                if (cropped !== oriented && !cropped.isRecycled) cropped.recycle()
                if (!oriented.isRecycled) oriented.recycle()
            }
        } finally {
            stagedSource.delete()
        }
    }

    fun prepareUploadVideoFile(
        context: Context,
        videoUri: Uri,
        maxBytes: Int = 24 * 1024 * 1024
    ): PreparedUploadFile {
        val stream = openInputStreamWithFallback(context, videoUri)
            ?: throw IllegalStateException("Could not open selected video.")
        val mime = runCatching { context.contentResolver.getType(videoUri) }
            .getOrNull()
            ?.takeIf { it.startsWith("video/", ignoreCase = true) }
            ?: "video/mp4"
        val outFile = createUploadStagingFile(context, "mp4")
        try {
            stream.use { input ->
                FileOutputStream(outFile).use { output ->
                    val chunk = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(chunk)
                        if (read <= 0) break
                        total += read
                        if (total > maxBytes) {
                            throw IllegalArgumentException("Selected video exceeds the 24 MB upload limit. Trim or record a shorter clip.")
                        }
                        output.write(chunk, 0, read)
                    }
                    output.flush()
                }
            }
            if (outFile.length() <= 0L) {
                throw IllegalStateException("Selected video file is empty.")
            }
            return PreparedUploadFile(
                file = outFile,
                fileUri = Uri.fromFile(outFile),
                contentType = mime,
                fileExtension = "mp4",
                sizeBytes = outFile.length(),
                originalUri = videoUri
            )
        } catch (t: Throwable) {
            outFile.delete()
            throw t
        }
    }

    fun writeBytesToUploadTempFile(
        context: Context,
        bytes: ByteArray,
        extension: String = "jpg",
        contentType: String = "image/jpeg"
    ): PreparedUploadFile {
        require(bytes.isNotEmpty()) { "Media bytes cannot be empty." }
        val cleanExt = extension.trim().lowercase().ifBlank { "jpg" }
        val outFile = createUploadStagingFile(context, cleanExt)
        FileOutputStream(outFile).use { out ->
            out.write(bytes)
            out.flush()
        }
        return PreparedUploadFile(
            file = outFile,
            fileUri = Uri.fromFile(outFile),
            contentType = contentType,
            fileExtension = cleanExt,
            sizeBytes = outFile.length(),
            originalUri = Uri.fromFile(outFile)
        )
    }

    private fun createUploadStagingFile(context: Context, extension: String): File {
        val dir = File(context.cacheDir, "upload_staging").apply {
            if (!exists()) mkdirs()
        }
        return File(
            dir,
            "upload_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.$extension"
        )
    }

    private fun encodeBitmapToUploadFile(
        context: Context,
        bitmap: Bitmap,
        preferredFormat: SupportedImageFormat,
        maxDimension: Int,
        maxBytes: Int,
        originalUri: Uri
    ): PreparedUploadFile {
        val attempts = listOf(
            maxDimension to 86,
            minOf(maxDimension, 1280) to 74,
            minOf(maxDimension, 1024) to 60
        )

        val formatSequence = if (preferredFormat == SupportedImageFormat.PNG) {
            // Try PNG across dimension caps first, falling back to WEBP if lossless PNG exceeds size cap
            listOf(SupportedImageFormat.PNG, SupportedImageFormat.WEBP, SupportedImageFormat.JPEG)
        } else {
            listOf(preferredFormat)
        }

        for (targetFormat in formatSequence) {
            for ((dim, quality) in attempts) {
                val scaled = scaleDownIfNeeded(bitmap, dim)
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
                if (bytes.isNotEmpty() && bytes.size <= maxBytes) {
                    val outFile = createUploadStagingFile(context, targetFormat.extension)
                    FileOutputStream(outFile).use { output ->
                        output.write(bytes)
                        output.flush()
                    }
                    return PreparedUploadFile(
                        file = outFile,
                        fileUri = Uri.fromFile(outFile),
                        contentType = targetFormat.mimeType,
                        fileExtension = targetFormat.extension,
                        sizeBytes = outFile.length(),
                        originalUri = originalUri
                    )
                }
            }
        }

        // Last resort JPEG attempt if image was exceptionally dense
        val fallbackScaled = scaleDownIfNeeded(bitmap, 960)
        val fallbackBytes = try {
            ByteArrayOutputStream().use { out ->
                if (!fallbackScaled.compress(Bitmap.CompressFormat.JPEG, 55, out)) {
                    throw IllegalStateException("Could not encode image for upload.")
                }
                out.toByteArray()
            }
        } finally {
            if (fallbackScaled !== bitmap && !fallbackScaled.isRecycled) fallbackScaled.recycle()
        }
        val outFile = createUploadStagingFile(context, SupportedImageFormat.JPEG.extension)
        FileOutputStream(outFile).use { output ->
            output.write(fallbackBytes)
            output.flush()
        }
        return PreparedUploadFile(
            file = outFile,
            fileUri = Uri.fromFile(outFile),
            contentType = SupportedImageFormat.JPEG.mimeType,
            fileExtension = SupportedImageFormat.JPEG.extension,
            sizeBytes = outFile.length(),
            originalUri = originalUri
        )
    }

    private fun detectImageFormatFromFileHeader(file: File): SupportedImageFormat? {
        val header = ByteArray(12)
        val read = runCatching {
            file.inputStream().use { it.read(header) }
        }.getOrDefault(0)
        if (read >= 8 &&
            header[0] == 0x89.toByte() &&
            header[1] == 0x50.toByte() &&
            header[2] == 0x4E.toByte() &&
            header[3] == 0x47.toByte()
        ) {
            return SupportedImageFormat.PNG
        }
        if (read >= 12 &&
            header[0] == 'R'.code.toByte() &&
            header[1] == 'I'.code.toByte() &&
            header[2] == 'F'.code.toByte() &&
            header[3] == 'F'.code.toByte() &&
            header[8] == 'W'.code.toByte() &&
            header[9] == 'E'.code.toByte() &&
            header[10] == 'B'.code.toByte() &&
            header[11] == 'P'.code.toByte()
        ) {
            return SupportedImageFormat.WEBP
        }
        if (read >= 3 &&
            header[0] == 0xFF.toByte() &&
            header[1] == 0xD8.toByte() &&
            header[2] == 0xFF.toByte()
        ) {
            return SupportedImageFormat.JPEG
        }
        return null
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
        return try {
            decodeOrientedBitmapFromFile(
                tempFile = tempFile,
                maxTargetDimension = maxTargetDimension,
                emptyErrorMessage = emptyErrorMessage,
                decodeErrorMessage = decodeErrorMessage
            )
        } finally {
            tempFile.delete()
        }
    }

    private fun decodeOrientedBitmapFromFile(
        tempFile: File,
        maxTargetDimension: Int,
        emptyErrorMessage: String,
        decodeErrorMessage: String
    ): Bitmap {
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
