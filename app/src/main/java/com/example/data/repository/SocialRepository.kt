package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.core.LocalProfilePreferences
import com.example.data.media.CameraPhotoHelper
import com.example.data.media.ImageCompressionPreset
import com.example.data.model.MediaCropSpec
import com.example.data.model.PostComment
import com.example.data.model.PostFilter
import com.example.data.model.SocialMediaType
import com.example.data.model.SocialPost
import com.example.data.model.SocialStory
import com.example.data.model.StoryReply
import com.example.data.model.UserFollowEdge
import com.example.data.remote.FirebasePhotoStorageService
import com.example.data.remote.FirestoreSocialService
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

class SocialRepository(
    private val appContext: Context,
    private val socialService: FirestoreSocialService,
    private val photoStorageService: FirebasePhotoStorageService,
    private val profilePreferences: LocalProfilePreferences,
    private val auth: FirebaseAuth
) {
    val currentUserId: String
        get() = auth.currentUser?.uid ?: profilePreferences.getProfile().userId

    fun observePosts(
        limitCount: Long = FirestoreSocialService.DEFAULT_POSTS_PAGE_SIZE
    ): Flow<List<SocialPost>> = socialService.observePosts(limitCount)

    fun observeStories(): Flow<List<SocialStory>> = socialService.observeStories()

    fun observeStoryReplies(story: SocialStory): Flow<List<StoryReply>> =
        socialService.observeStoryReplies(story, currentUserId)

    fun observeComments(postId: String): Flow<List<PostComment>> = socialService.observeComments(postId)

    fun observeSavedPostIds(uid: String): Flow<Set<String>> = socialService.observeSavedPostIds(uid)

    fun observeFollows(): Flow<List<UserFollowEdge>> = socialService.observeFollows()

    suspend fun fetchLatestPosts(
        limitCount: Long = FirestoreSocialService.DEFAULT_POSTS_PAGE_SIZE
    ): List<SocialPost> = socialService.fetchLatestPosts(limitCount)

    suspend fun fetchPostsAfter(
        beforeCreatedAtMillis: Long,
        limitCount: Long = FirestoreSocialService.DEFAULT_POSTS_PAGE_SIZE
    ): List<SocialPost> = socialService.fetchPostsAfter(beforeCreatedAtMillis, limitCount)

    suspend fun fetchLatestStories(): List<SocialStory> = socialService.fetchLatestStories()

    suspend fun publishPostOrReel(
        imageUri: Uri,
        mediaType: SocialMediaType,
        caption: String,
        location: String,
        audioLabel: String,
        aspectRatio: Float,
        isVideo: Boolean = false,
        cropSpec: MediaCropSpec = MediaCropSpec(targetAspectRatio = aspectRatio),
        filter: PostFilter = PostFilter.NORMAL,
        taggedUsernames: List<String> = emptyList(),
        onProgress: (Float) -> Unit
    ) {
        val user = auth.currentUser ?: throw IllegalStateException("Please sign in with Google to post.")
        val uid = user.uid
        val profile = profilePreferences.getProfile()
        val username = resolveUsername(profile.username, profile.displayName, user.email, uid)
        val displayName = profile.displayName.ifBlank { user.displayName ?: username }
        val photoUrl = profile.photoUrl.ifBlank { user.photoUrl?.toString().orEmpty() }
        val postId = "post_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"

        val effectiveAspectRatio = cropSpec.targetAspectRatio.coerceIn(0.5f, 2.0f)
        val resolvedIsVideo = isVideo || withContext(Dispatchers.IO) {
            CameraPhotoHelper.isVideoUri(appContext, imageUri)
        }

        if (resolvedIsVideo) {
            onProgress(0.08f)
            val posterBytes = withContext(Dispatchers.IO) {
                CameraPhotoHelper.extractVideoThumbnailJpegBytes(
                    context = appContext,
                    videoUri = imageUri,
                    filter = filter
                )
            }
            val preparedPoster = withContext(Dispatchers.IO) {
                CameraPhotoHelper.writeBytesToUploadTempFile(appContext, posterBytes)
            }
            val uploadedPoster = try {
                onProgress(0.16f)
                photoStorageService.uploadSocialMediaFile(
                    uid = uid,
                    mediaId = postId,
                    prefix = "post_poster",
                    preparedFile = preparedPoster,
                    onProgress = { p -> onProgress(0.16f + p * 0.18f) }
                )
            } finally {
                preparedPoster.file.delete()
            }

            onProgress(0.35f)
            val preparedVideo = withContext(Dispatchers.IO) {
                CameraPhotoHelper.prepareUploadVideoFile(appContext, imageUri)
            }
            val uploadedVideo = try {
                photoStorageService.uploadSocialVideoFile(
                    uid = uid,
                    mediaId = postId,
                    preparedFile = preparedVideo,
                    onProgress = { p -> onProgress(0.35f + p * 0.58f) }
                )
            } finally {
                preparedVideo.file.delete()
            }

            socialService.createPost(
                postId = postId,
                authorId = uid,
                authorUsername = username,
                authorDisplayName = displayName,
                authorPhotoUrl = photoUrl,
                mediaType = mediaType,
                imageUrl = uploadedPoster.downloadUrl,
                videoUrl = uploadedVideo.downloadUrl,
                storagePath = uploadedPoster.storagePath,
                caption = caption.trim(),
                location = location.trim(),
                audioLabel = audioLabel.trim(),
                taggedUsernames = taggedUsernames,
                aspectRatio = effectiveAspectRatio
            )
        } else {
            onProgress(0.08f)
            val preparedImage = withContext(Dispatchers.IO) {
                CameraPhotoHelper.prepareUploadImageFile(
                    context = appContext,
                    uri = imageUri,
                    cropSpec = cropSpec,
                    filter = filter,
                    preset = ImageCompressionPreset.FEED_POST
                )
            }
            val uploaded = try {
                onProgress(0.15f)
                photoStorageService.uploadSocialMediaFile(
                    uid = uid,
                    mediaId = postId,
                    prefix = if (mediaType == SocialMediaType.REEL) "reel" else "post",
                    preparedFile = preparedImage,
                    onProgress = onProgress
                )
            } finally {
                preparedImage.file.delete()
            }

            socialService.createPost(
                postId = postId,
                authorId = uid,
                authorUsername = username,
                authorDisplayName = displayName,
                authorPhotoUrl = photoUrl,
                mediaType = mediaType,
                imageUrl = uploaded.downloadUrl,
                storagePath = uploaded.storagePath,
                caption = caption.trim(),
                location = location.trim(),
                audioLabel = audioLabel.trim(),
                taggedUsernames = taggedUsernames,
                aspectRatio = effectiveAspectRatio
            )
        }
        onProgress(1f)
    }

    suspend fun publishStory(
        imageUri: Uri?,
        caption: String,
        isVideo: Boolean = false,
        cropSpec: MediaCropSpec = MediaCropSpec(targetAspectRatio = 0.75f),
        filter: PostFilter = PostFilter.NORMAL,
        onProgress: (Float) -> Unit
    ) {
        val user = auth.currentUser ?: throw IllegalStateException("Please sign in with Google to share a story.")
        val uid = user.uid
        val profile = profilePreferences.getProfile()
        val username = resolveUsername(profile.username, profile.displayName, user.email, uid)
        val photoUrl = profile.photoUrl.ifBlank { user.photoUrl?.toString().orEmpty() }
        val storyId = "story_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"

        var imageUrl: String? = null
        var videoUrl: String? = null
        var storagePath: String? = null
        if (imageUri != null) {
            onProgress(0.08f)
            val resolvedVideo = isVideo || withContext(Dispatchers.IO) {
                CameraPhotoHelper.isVideoUri(appContext, imageUri)
            }
            if (resolvedVideo) {
                val posterBytes = withContext(Dispatchers.IO) {
                    CameraPhotoHelper.extractVideoThumbnailJpegBytes(
                        context = appContext,
                        videoUri = imageUri,
                        filter = filter
                    )
                }
                val preparedPoster = withContext(Dispatchers.IO) {
                    CameraPhotoHelper.writeBytesToUploadTempFile(appContext, posterBytes)
                }
                val uploadedPoster = try {
                    onProgress(0.16f)
                    photoStorageService.uploadSocialMediaFile(
                        uid = uid,
                        mediaId = storyId,
                        prefix = "story_poster",
                        preparedFile = preparedPoster,
                        onProgress = { p -> onProgress(0.16f + p * 0.18f) }
                    )
                } finally {
                    preparedPoster.file.delete()
                }
                imageUrl = uploadedPoster.downloadUrl
                storagePath = uploadedPoster.storagePath

                onProgress(0.35f)
                val preparedVideo = withContext(Dispatchers.IO) {
                    CameraPhotoHelper.prepareUploadVideoFile(appContext, imageUri)
                }
                val uploadedVideo = try {
                    photoStorageService.uploadSocialVideoFile(
                        uid = uid,
                        mediaId = storyId,
                        preparedFile = preparedVideo,
                        onProgress = { p -> onProgress(0.35f + p * 0.58f) }
                    )
                } finally {
                    preparedVideo.file.delete()
                }
                videoUrl = uploadedVideo.downloadUrl
            } else {
                val preparedImage = withContext(Dispatchers.IO) {
                    CameraPhotoHelper.prepareUploadImageFile(
                        context = appContext,
                        uri = imageUri,
                        cropSpec = cropSpec,
                        filter = filter,
                        preset = ImageCompressionPreset.STORY
                    )
                }
                val uploaded = try {
                    onProgress(0.15f)
                    photoStorageService.uploadSocialMediaFile(
                        uid = uid,
                        mediaId = storyId,
                        prefix = "story",
                        preparedFile = preparedImage,
                        onProgress = onProgress
                    )
                } finally {
                    preparedImage.file.delete()
                }
                imageUrl = uploaded.downloadUrl
                storagePath = uploaded.storagePath
            }
        }

        val cleanCaption = caption.trim()
        require(!imageUrl.isNullOrBlank() || !videoUrl.isNullOrBlank() || cleanCaption.isNotBlank()) {
            "Add a photo, video, or caption for your story."
        }
        socialService.createStory(
            storyId = storyId,
            authorId = uid,
            authorUsername = username,
            authorPhotoUrl = photoUrl,
            imageUrl = imageUrl,
            videoUrl = videoUrl,
            storagePath = storagePath,
            caption = cleanCaption
        )
        onProgress(1f)
    }

    suspend fun sendStoryReply(story: SocialStory, text: String) {
        val user = auth.currentUser ?: return
        val uid = user.uid
        val profile = profilePreferences.getProfile()
        val username = resolveUsername(profile.username, profile.displayName, user.email, uid)
        val photoUrl = profile.photoUrl.ifBlank { user.photoUrl?.toString().orEmpty() }
        val replyId = "sr_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        socialService.addStoryReply(
            story = story,
            replyId = replyId,
            authorId = uid,
            authorUsername = username,
            authorPhotoUrl = photoUrl,
            text = text
        )
    }

    suspend fun toggleLike(post: SocialPost) {
        val uid = auth.currentUser?.uid ?: return
        socialService.toggleLike(post, uid)
    }

    suspend fun addComment(post: SocialPost, text: String) {
        val user = auth.currentUser ?: return
        val uid = user.uid
        val profile = profilePreferences.getProfile()
        val username = resolveUsername(profile.username, profile.displayName, user.email, uid)
        val photoUrl = profile.photoUrl.ifBlank { user.photoUrl?.toString().orEmpty() }
        val commentId = "c_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        socialService.addComment(
            post = post,
            commentId = commentId,
            authorId = uid,
            authorUsername = username,
            authorPhotoUrl = photoUrl,
            text = text
        )
    }

    suspend fun deleteComment(postId: String, commentId: String) {
        socialService.deleteComment(postId, commentId)
    }

    suspend fun toggleSave(postId: String, currentlySaved: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        socialService.setSavedPost(uid = uid, postId = postId, save = !currentlySaved)
    }

    suspend fun toggleFollow(targetUid: String, targetUsername: String, currentlyFollowing: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        socialService.setFollow(
            followerId = uid,
            targetUid = targetUid,
            targetUsername = targetUsername,
            follow = !currentlyFollowing
        )
    }

    suspend fun markStoryViewed(story: SocialStory) {
        val uid = auth.currentUser?.uid ?: return
        socialService.markStoryViewed(story, uid)
    }

    suspend fun deletePost(post: SocialPost) {
        socialService.deletePost(post.id)
        runCatching {
            photoStorageService.deletePhotoIfOwned(post.storagePath)
        }
        if (post.videoUrl.isNotBlank() && post.authorId.isNotBlank()) {
            runCatching {
                photoStorageService.deletePhotoIfOwned(
                    FirebasePhotoStorageService.socialVideoStoragePath(post.authorId, post.id)
                )
            }
        }
    }

    suspend fun deleteStory(story: SocialStory) {
        socialService.deleteStory(story.id)
        runCatching {
            photoStorageService.deletePhotoIfOwned(story.storagePath)
        }
        if (story.videoUrl.isNotBlank() && story.authorId.isNotBlank()) {
            runCatching {
                photoStorageService.deletePhotoIfOwned(
                    FirebasePhotoStorageService.socialVideoStoragePath(story.authorId, story.id)
                )
            }
        }
    }

    private fun resolveUsername(
        preferredUsername: String,
        displayName: String,
        email: String?,
        uid: String
    ): String {
        val candidate = preferredUsername.trim()
            .ifBlank { displayName.trim().lowercase().replace(" ", "_") }
            .ifBlank { email?.substringBefore('@')?.trim()?.lowercase().orEmpty() }
            .ifBlank { "esosa_${uid.take(6)}" }
        return candidate.replace(Regex("[^a-zA-Z0-9._]"), "_").take(32).ifBlank { "esosa_user" }
    }
}
