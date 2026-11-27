package com.example.data.remote

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class FirestoreMediaBlobService(
    private val appContext: Context,
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth? = null
) {
    suspend fun uploadFile(
        ownerUid: String,
        fileUri: Uri,
        contentType: String,
        fileExtension: String,
        onProgress: (Float) -> Unit = {}
    ): UploadedPhoto {
        val cleanUid = ownerUid.trim().takeIf { it.isNotBlank() }
            ?: auth?.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IOException("Sign in with Google before uploading media.")

        val cleanExt = normalizeExtension(fileExtension, contentType)
        val cleanMime = contentType.trim().ifBlank { mimeTypeForExtension(cleanExt) ?: "application/octet-stream" }
            .take(80)

        onProgress(0.10f)
        val rawBytes = withContext(Dispatchers.IO) {
            readUriBytesBounded(appContext, fileUri, MAX_MEDIA_BYTES)
        }
        val encodedChunks = withContext(Dispatchers.Default) {
            splitIntoBase64Chunks(rawBytes)
        }

        val mediaId = generateMediaId()
        val metaRef = db.collection(MEDIA_BLOBS_COLLECTION).document(mediaId)
        val metaPath = "$MEDIA_BLOBS_COLLECTION/$mediaId"
        val metaPayload = hashMapOf<String, Any>(
            "ownerId" to cleanUid,
            "contentType" to cleanMime,
            "fileExtension" to cleanExt,
            "totalBytes" to rawBytes.size.toLong(),
            "totalChunks" to encodedChunks.size,
            "createdAt" to FieldValue.serverTimestamp()
        )

        onProgress(0.20f)
        try {
            metaRef.set(metaPayload).await()

            val totalChunks = encodedChunks.size
            var uploadedCount = 0
            encodedChunks.withIndex().chunked(UPLOAD_CONCURRENCY).forEach { batch ->
                coroutineScope {
                    batch.map { (index, chunkData) ->
                        async {
                            val chunkPayload = hashMapOf<String, Any>(
                                "ownerId" to cleanUid,
                                "index" to index,
                                "data" to chunkData
                            )
                            metaRef.collection(CHUNKS_SUBCOLLECTION)
                                .document(index.toString())
                                .set(chunkPayload)
                                .await()
                        }
                    }.awaitAll()
                }
                uploadedCount += batch.size
                val fraction = uploadedCount.toFloat() / totalChunks.toFloat().coerceAtLeast(1f)
                onProgress(0.20f + fraction * 0.72f)
            }

            withContext(Dispatchers.IO) {
                writeBytesToCacheFile(appContext, mediaId, cleanExt, rawBytes)
            }
            onProgress(0.95f)
            Log.i(
                TAG,
                "Uploaded media blob to Firestore: mediaId=$mediaId, bytes=${rawBytes.size}, chunks=$totalChunks, mime=$cleanMime"
            )
            return UploadedPhoto(
                downloadUrl = buildMediaUri(mediaId),
                storagePath = storagePathFor(mediaId),
                bucket = FIRESTORE_BUCKET_LABEL
            )
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                runCatching { deleteMediaInternal(db, mediaId, auth) }
            }
            throw e
        } catch (e: Exception) {
            withContext(NonCancellable) {
                runCatching { deleteMediaInternal(db, mediaId, auth) }
            }
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.CREATE, metaPath, auth)
            throw if (e is IOException) e else IOException(
                e.localizedMessage ?: "Failed to upload media to Firestore.",
                e
            )
        }
    }

    suspend fun deleteMediaIfOwned(storagePathOrUri: String?): DeletedMediaBlobInfo? {
        val mediaId = extractMediaId(storagePathOrUri) ?: return null
        return deleteMediaInternal(db, mediaId, auth)
    }

    companion object {
        private const val TAG = "FirestoreMediaBlob"
        const val URI_SCHEME = "esosa-media"
        const val URI_HOST = "media"
        const val URI_PREFIX = "esosa-media://media/"
        const val MEDIA_BLOBS_COLLECTION = "media_blobs"
        const val CHUNKS_SUBCOLLECTION = "chunks"
        const val FIRESTORE_BUCKET_LABEL = "firestore"
        const val CACHE_DIR_NAME = "esosa_media_cache"
        const val RAW_CHUNK_BYTES = 450_000
        const val MAX_MEDIA_BYTES = 10L * 1024L * 1024L
        const val MAX_CHUNKS = 25
        private const val UPLOAD_CONCURRENCY = 4
        private val VALID_ID_REGEX = Regex("^[a-zA-Z0-9_\\-]+$")

        private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val inFlightDownloads = ConcurrentHashMap<String, Deferred<File?>>()

        fun isFirestoreMediaUri(url: String?): Boolean {
            val clean = url?.trim() ?: return false
            return clean.startsWith(URI_PREFIX, ignoreCase = true)
        }

        fun isMediaBlobStoragePath(path: String?): Boolean {
            val clean = path?.trim() ?: return false
            return clean.startsWith("$MEDIA_BLOBS_COLLECTION/")
        }

        fun isCloudMediaUrl(url: String?): Boolean {
            val clean = url?.trim() ?: return false
            return clean.startsWith("https://") || isFirestoreMediaUri(clean)
        }

        fun buildMediaUri(mediaId: String): String = "$URI_PREFIX$mediaId"

        fun storagePathFor(mediaId: String): String = "$MEDIA_BLOBS_COLLECTION/$mediaId"

        fun extractMediaId(uriOrPath: String?): String? {
            val clean = uriOrPath?.trim()?.takeIf { it.isNotBlank() } ?: return null
            val candidate = when {
                clean.startsWith(URI_PREFIX, ignoreCase = true) -> {
                    clean.substring(URI_PREFIX.length)
                        .substringBefore('?')
                        .substringBefore('#')
                        .trim('/')
                }
                clean.startsWith("$MEDIA_BLOBS_COLLECTION/") -> {
                    clean.substring("$MEDIA_BLOBS_COLLECTION/".length)
                        .trim()
                }
                else -> {
                    val parsed = runCatching { Uri.parse(clean) }.getOrNull()
                    if (parsed != null &&
                        parsed.scheme.equals(URI_SCHEME, ignoreCase = true) &&
                        parsed.host.equals(URI_HOST, ignoreCase = true)
                    ) {
                        parsed.path?.trim('/') ?: ""
                    } else if (!clean.contains('/')) {
                        clean
                    } else {
                        ""
                    }
                }
            }
            return candidate.takeIf { it.length in 1..128 && VALID_ID_REGEX.matches(it) }
        }

        fun extractStoragePathFromUri(uri: String?): String? =
            extractMediaId(uri)?.let(::storagePathFor)

        fun generateMediaId(prefix: String = "mb"): String {
            val cleanPrefix = prefix.replace(Regex("[^a-zA-Z0-9_]"), "").ifBlank { "mb" }.take(12)
            val suffix = UUID.randomUUID().toString().replace("-", "").take(10)
            return "${cleanPrefix}_${System.currentTimeMillis()}_$suffix"
        }

        fun normalizeExtension(fileExtension: String?, contentType: String?): String {
            val ext = fileExtension?.trim()?.lowercase()?.removePrefix(".")
            if (!ext.isNullOrBlank() && ext.length in 1..12 && VALID_ID_REGEX.matches(ext)) {
                return ext
            }
            val mime = contentType?.trim()?.lowercase().orEmpty()
            return when {
                mime.contains("png") -> "png"
                mime.contains("webp") -> "webp"
                mime.contains("mp4") && mime.startsWith("video/") -> "mp4"
                mime.contains("mp4") || mime.contains("m4a") -> "m4a"
                mime.contains("webm") -> "webm"
                else -> "jpg"
            }
        }

        fun mimeTypeForExtension(extension: String?): String? {
            return when (extension?.trim()?.lowercase()?.removePrefix(".")) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "webp" -> "image/webp"
                "mp4" -> "video/mp4"
                "m4a" -> "audio/mp4"
                "webm" -> "video/webm"
                else -> null
            }
        }

        fun splitIntoBase64Chunks(
            bytes: ByteArray,
            chunkSize: Int = RAW_CHUNK_BYTES
        ): List<String> {
            require(bytes.isNotEmpty()) { "Selected media file is empty." }
            require(chunkSize > 0) { "Chunk size must be positive." }
            if (bytes.size.toLong() > MAX_MEDIA_BYTES) {
                throw IOException(
                    "Selected media exceeds the 10 MB cloud limit without Firebase Storage. Please choose a smaller photo or shorter clip."
                )
            }
            val chunks = ArrayList<String>((bytes.size + chunkSize - 1) / chunkSize)
            var offset = 0
            while (offset < bytes.size) {
                val len = minOf(chunkSize, bytes.size - offset)
                chunks.add(Base64.encodeToString(bytes, offset, len, Base64.NO_WRAP))
                offset += len
            }
            if (chunks.size > MAX_CHUNKS) {
                throw IOException("Selected media exceeds the 10 MB cloud chunk limit.")
            }
            return chunks
        }

        fun decodeBase64Chunks(encodedChunks: List<String>): ByteArray {
            val out = ByteArrayOutputStream()
            for (chunk in encodedChunks) {
                if (chunk.isNotEmpty()) {
                    out.write(Base64.decode(chunk, Base64.DEFAULT))
                }
            }
            return out.toByteArray()
        }

        fun findCachedMediaFile(context: Context, mediaId: String): File? {
            val cacheDir = File(context.applicationContext.cacheDir, CACHE_DIR_NAME)
            if (!cacheDir.exists()) return null
            return cacheDir.listFiles()?.firstOrNull { file ->
                file.isFile &&
                    file.length() > 0L &&
                    !file.name.endsWith(".tmp") &&
                    (file.name == mediaId || file.name.startsWith("$mediaId."))
            }
        }

        suspend fun resolveToCachedFile(
            context: Context,
            mediaUriOrId: String,
            dbProvider: (() -> FirebaseFirestore)? = null
        ): File? {
            val appCtx = context.applicationContext
            val mediaId = extractMediaId(mediaUriOrId) ?: return null
            findCachedMediaFile(appCtx, mediaId)?.let { return it }

            val deferred = inFlightDownloads.computeIfAbsent(mediaId) {
                downloadScope.async {
                    try {
                        findCachedMediaFile(appCtx, mediaId)?.let { return@async it }
                        val firestore = dbProvider?.invoke()
                            ?: FirebaseFirestore.getInstance(appCtx.getString(R.string.firestore_database_id))
                        val metaDoc = firestore.collection(MEDIA_BLOBS_COLLECTION)
                            .document(mediaId)
                            .get()
                            .await()
                        if (!metaDoc.exists()) {
                            return@async null
                        }
                        val ext = normalizeExtension(
                            fileExtension = metaDoc.getString("fileExtension"),
                            contentType = metaDoc.getString("contentType")
                        )
                        val expectedChunks = (metaDoc.get("totalChunks") as? Number)?.toInt() ?: 0
                        val chunkSnap = firestore.collection(MEDIA_BLOBS_COLLECTION)
                            .document(mediaId)
                            .collection(CHUNKS_SUBCOLLECTION)
                            .get()
                            .await()

                        val indexedChunks = chunkSnap.documents.mapNotNull { doc ->
                            val idx = (doc.get("index") as? Number)?.toInt()
                                ?: doc.id.toIntOrNull()
                                ?: return@mapNotNull null
                            val data = doc.getString("data") ?: return@mapNotNull null
                            idx to data
                        }.sortedBy { it.first }

                        if (indexedChunks.isEmpty()) {
                            return@async null
                        }
                        if (expectedChunks > 0 && indexedChunks.size < expectedChunks) {
                            Log.w(
                                TAG,
                                "Incomplete chunk download for mediaId=$mediaId: got ${indexedChunks.size}/$expectedChunks"
                            )
                            return@async null
                        }

                        val bytes = decodeBase64Chunks(indexedChunks.map { it.second })
                        if (bytes.isEmpty()) {
                            return@async null
                        }
                        writeBytesToCacheFile(appCtx, mediaId, ext, bytes)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to resolve Firestore media blob $mediaId: ${e.message}")
                        null
                    }
                }
            }

            return try {
                deferred.await()
            } finally {
                inFlightDownloads.remove(mediaId, deferred)
            }
        }

        private fun writeBytesToCacheFile(
            appContext: Context,
            mediaId: String,
            extension: String,
            bytes: ByteArray
        ): File {
            val cacheDir = File(appContext.cacheDir, CACHE_DIR_NAME).apply {
                if (!exists()) mkdirs()
            }
            val cleanExt = normalizeExtension(extension, null)
            val target = File(cacheDir, "$mediaId.$cleanExt")
            val tempFile = File(cacheDir, "${mediaId}_${UUID.randomUUID().toString().take(6)}.tmp")
            FileOutputStream(tempFile).use { out ->
                out.write(bytes)
                out.flush()
            }
            if (!tempFile.renameTo(target)) {
                tempFile.copyTo(target, overwrite = true)
                tempFile.delete()
            }
            return target
        }

        private fun readUriBytesBounded(
            context: Context,
            fileUri: Uri,
            maxBytes: Long
        ): ByteArray {
            if (fileUri.scheme.equals("file", ignoreCase = true)) {
                val path = fileUri.path
                if (!path.isNullOrBlank()) {
                    val file = File(path)
                    if (!file.exists() || !file.canRead()) {
                        throw IOException("Selected media file is no longer accessible before upload: $path")
                    }
                    val len = file.length()
                    if (len <= 0L) {
                        throw IllegalArgumentException("Selected media file is empty.")
                    }
                    if (len > maxBytes) {
                        throw IOException(
                            "Selected media exceeds the ${maxBytes / (1024L * 1024L)} MB cloud limit without Firebase Storage."
                        )
                    }
                    return file.readBytes()
                }
            }

            val stream = context.contentResolver.openInputStream(fileUri)
                ?: throw IOException("Unable to read selected media URI: $fileUri")
            return stream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > maxBytes) {
                        throw IOException(
                            "Selected media exceeds the ${maxBytes / (1024L * 1024L)} MB cloud limit without Firebase Storage."
                        )
                    }
                    out.write(buffer, 0, read)
                }
                val result = out.toByteArray()
                if (result.isEmpty()) {
                    throw IllegalArgumentException("Selected media file is empty.")
                }
                result
            }
        }

        private suspend fun deleteMediaInternal(
            db: FirebaseFirestore,
            mediaId: String,
            auth: FirebaseAuth?
        ): DeletedMediaBlobInfo? {
            val metaRef = db.collection(MEDIA_BLOBS_COLLECTION).document(mediaId)
            val path = "$MEDIA_BLOBS_COLLECTION/$mediaId"
            try {
                val metaSnap = runCatching { metaRef.get().await() }.getOrNull()
                val deletedBytes = (metaSnap?.get("totalBytes") as? Number)?.toLong()?.coerceAtLeast(0L) ?: 0L
                val deletedContentType = metaSnap?.getString("contentType").orEmpty()
                val chunks = metaRef.collection(CHUNKS_SUBCOLLECTION).get().await()
                for (doc in chunks.documents) {
                    runCatching { doc.reference.delete().await() }
                }
                metaRef.delete().await()
                return if (deletedBytes > 0L) {
                    DeletedMediaBlobInfo(totalBytes = deletedBytes, contentType = deletedContentType)
                } else {
                    null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: FirebaseFirestoreException) {
                if (e.code != FirebaseFirestoreException.Code.NOT_FOUND) {
                    FirestoreErrorHandler.handleFirestoreError(e, OperationType.DELETE, path, auth)
                    throw e
                }
                return null
            }
        }
    }
}

data class DeletedMediaBlobInfo(
    val totalBytes: Long,
    val contentType: String
)
