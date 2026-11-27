package com.example.data.media

import android.content.Context
import android.net.Uri
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.disk.DiskCache
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.imageLoader
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.Options
import com.example.data.remote.FirebasePhotoStorageService
import com.example.data.remote.FirestoreMediaBlobService
import okio.Path.Companion.toOkioPath
import java.io.File
import java.io.IOException

object EsosaImageCache {

    const val DISK_CACHE_DIR_NAME = "esosa_image_cache"
    const val DISK_CACHE_MAX_BYTES = 150L * 1024L * 1024L
    const val MEMORY_CACHE_MAX_PERCENT = 0.25
    const val MAX_PREFETCH_IMAGES_PER_ROOM = 12

    const val VARIANT_THUMBNAIL = "thumb"
    const val VARIANT_FULLSCREEN = "full"
    const val VARIANT_AVATAR = "avatar"

    fun createImageLoader(context: Context): ImageLoader {
        val appContext = context.applicationContext
        val cacheDir = File(appContext.cacheDir, DISK_CACHE_DIR_NAME)
        return ImageLoader.Builder(appContext)
            .components {
                add(FirestoreMediaFetcherFactory())
            }
            .memoryCache {
                MemoryCache.Builder(appContext)
                    .maxSizePercent(MEMORY_CACHE_MAX_PERCENT)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir)
                    .maxSizeBytes(DISK_CACHE_MAX_BYTES)
                    .build()
            }
            .respectCacheHeaders(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build()
    }

    fun stableCacheKey(imageUrl: String?, storagePath: String? = null): String? {
        val remoteStorageKey = storagePath
            ?.trim()
            ?.takeIf {
                FirebasePhotoStorageService.isRemoteRoomPhotoPath(it) ||
                    it.startsWith("users/") ||
                    FirestoreMediaBlobService.isMediaBlobStoragePath(it)
            }
        if (!remoteStorageKey.isNullOrBlank()) {
            return "esosa:$remoteStorageKey"
        }
        return imageUrl?.trim()?.takeIf { it.isNotBlank() }
    }

    fun memoryCacheKeyFor(
        imageUrl: String?,
        storagePath: String? = null,
        variant: String = VARIANT_THUMBNAIL
    ): String? {
        val base = stableCacheKey(imageUrl, storagePath) ?: return null
        return "$base#$variant"
    }

    fun resolveImageData(imageUrl: String?, storagePath: String? = null): Any? {
        val cleanUrl = imageUrl?.trim().orEmpty()
        val cleanPath = storagePath?.trim().orEmpty()
        if (cleanPath.isNotBlank() &&
            !FirebasePhotoStorageService.isRemoteRoomPhotoPath(cleanPath) &&
            !cleanPath.startsWith("users/") &&
            !FirestoreMediaBlobService.isMediaBlobStoragePath(cleanPath)
        ) {
            val localFile = File(cleanPath)
            if (localFile.exists() && localFile.length() > 0L && (cleanUrl.isBlank() || cleanUrl.startsWith("file://"))) {
                return localFile
            }
        }
        return cleanUrl.ifBlank { null }
    }

    fun buildChatImageRequest(
        context: Context,
        imageUrl: String?,
        storagePath: String? = null,
        variant: String = VARIANT_THUMBNAIL,
        placeholderMemoryCacheKey: String? = null,
        retryAttempt: Int = 0,
        offlineOnly: Boolean = false
    ): ImageRequest {
        val data = resolveImageData(imageUrl, storagePath)
        val diskKey = stableCacheKey(imageUrl, storagePath)
        val memKey = memoryCacheKeyFor(imageUrl, storagePath, variant)
        return ImageRequest.Builder(context)
            .data(data)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(if (offlineOnly) CachePolicy.DISABLED else CachePolicy.ENABLED)
            .apply {
                if (diskKey != null) diskCacheKey(diskKey)
                if (memKey != null) memoryCacheKey(memKey)
                if (!placeholderMemoryCacheKey.isNullOrBlank()) {
                    placeholderMemoryCacheKey(placeholderMemoryCacheKey)
                }
                if (retryAttempt > 0) {
                    setParameter("retry_attempt", retryAttempt, memoryCacheKey = null)
                }
            }
            .crossfade(true)
            .build()
    }

    fun prefetchChatImages(
        context: Context,
        items: List<Pair<String?, String?>>,
        maxItems: Int = MAX_PREFETCH_IMAGES_PER_ROOM
    ) {
        if (items.isEmpty() || maxItems <= 0) return
        val appContext = context.applicationContext
        val loader = appContext.imageLoader
        val seenKeys = HashSet<String>()
        var enqueued = 0
        for ((imageUrl, storagePath) in items) {
            if (enqueued >= maxItems) break
            val cleanUrl = imageUrl?.trim().orEmpty()
            if (cleanUrl.isBlank()) continue
            val key = stableCacheKey(cleanUrl, storagePath) ?: continue
            if (!seenKeys.add(key)) continue
            val request = buildChatImageRequest(
                context = appContext,
                imageUrl = cleanUrl,
                storagePath = storagePath,
                variant = VARIANT_THUMBNAIL
            )
            loader.enqueue(request)
            enqueued++
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    fun diskCacheSizeBytes(context: Context): Long {
        return context.applicationContext.imageLoader.diskCache?.size ?: 0L
    }

    @OptIn(ExperimentalCoilApi::class)
    fun clearCache(context: Context) {
        val loader = context.applicationContext.imageLoader
        loader.memoryCache?.clear()
        loader.diskCache?.clear()
    }

    private class FirestoreMediaFetcherFactory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (!data.scheme.equals(FirestoreMediaBlobService.URI_SCHEME, ignoreCase = true)) {
                return null
            }
            return FirestoreMediaFetcher(data, options)
        }
    }

    private class FirestoreMediaFetcher(
        private val uri: Uri,
        private val options: Options
    ) : Fetcher {
        override suspend fun fetch(): FetchResult {
            val cachedFile = FirestoreMediaBlobService.resolveToCachedFile(
                context = options.context,
                mediaUriOrId = uri.toString()
            ) ?: throw IOException("Unable to load cloud media: $uri")
            return SourceResult(
                source = ImageSource(
                    file = cachedFile.toOkioPath(),
                    diskCacheKey = uri.toString()
                ),
                mimeType = FirestoreMediaBlobService.mimeTypeForExtension(cachedFile.extension),
                dataSource = DataSource.DISK
            )
        }
    }
}
