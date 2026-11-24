package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.UiState
import com.example.data.model.MediaCropSpec
import com.example.data.model.PostComment
import com.example.data.model.PostFilter
import com.example.data.model.SocialMediaType
import com.example.data.model.SocialPost
import com.example.data.model.SocialStory
import com.example.data.model.UserFollowEdge
import com.example.data.repository.SocialRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SocialViewModel(
    private val repository: SocialRepository
) : ViewModel() {

    val currentUserId: String
        get() = repository.currentUserId

    private val _manualPostsFlow = MutableSharedFlow<List<SocialPost>>(extraBufferCapacity = 1)
    private val _manualStoriesFlow = MutableSharedFlow<List<SocialStory>>(extraBufferCapacity = 1)

    val postsState: StateFlow<UiState<List<SocialPost>>> =
        merge(repository.observePosts(), _manualPostsFlow)
            .map<List<SocialPost>, UiState<List<SocialPost>>> { UiState.Success(it) }
            .catch { err -> emit(UiState.Error(err.localizedMessage ?: "Failed to load feed")) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val stories: StateFlow<List<SocialStory>> =
        merge(repository.observeStories(), _manualStoriesFlow)
            .catch { emit(emptyList()) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = emptyList()
            )

    val savedPostIds: StateFlow<Set<String>> = repository.observeSavedPostIds(repository.currentUserId)
        .catch { emit(emptySet()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptySet()
        )

    val followEdges: StateFlow<List<UserFollowEdge>> = repository.observeFollows()
        .catch { emit(emptyList()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    private val _commentsByPost = MutableStateFlow<Map<String, List<PostComment>>>(emptyMap())
    val commentsByPost: StateFlow<Map<String, List<PostComment>>> = _commentsByPost.asStateFlow()
    private val commentObserverJobs = mutableMapOf<String, Job>()

    private val _isPublishing = MutableStateFlow(false)
    val isPublishing: StateFlow<Boolean> = _isPublishing.asStateFlow()

    private val _uploadProgress = MutableStateFlow<Float?>(null)
    val uploadProgress: StateFlow<Float?> = _uploadProgress.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private val _isRefreshingFeed = MutableStateFlow(false)
    val isRefreshingFeed: StateFlow<Boolean> = _isRefreshingFeed.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    fun refreshFeed() {
        if (_isRefreshingFeed.value) return
        viewModelScope.launch {
            _isRefreshingFeed.value = true
            try {
                coroutineScope {
                    val postsDeferred = async { repository.fetchLatestPosts() }
                    val storiesDeferred = async {
                        runCatching { repository.fetchLatestStories() }.getOrNull()
                    }
                    val latestPosts = postsDeferred.await()
                    val latestStories = storiesDeferred.await()
                    _manualPostsFlow.tryEmit(latestPosts)
                    if (latestStories != null) {
                        _manualStoriesFlow.tryEmit(latestStories)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showStatusBanner(e.localizedMessage ?: "Could not refresh feed from Firestore.")
            } finally {
                _isRefreshingFeed.value = false
            }
        }
    }

    fun observeCommentsForPost(postId: String) {
        if (commentObserverJobs[postId]?.isActive == true) return
        commentObserverJobs[postId] = viewModelScope.launch {
            repository.observeComments(postId)
                .catch { /* keep existing cached list if offline */ }
                .collect { comments ->
                    _commentsByPost.value = _commentsByPost.value + (postId to comments)
                }
        }
    }

    fun publishPostOrReel(
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
        onPublished: () -> Unit
    ) {
        if (_isPublishing.value) return
        viewModelScope.launch {
            _isPublishing.value = true
            _uploadProgress.value = 0.05f
            _actionError.value = null
            runCatching {
                repository.publishPostOrReel(
                    imageUri = imageUri,
                    mediaType = mediaType,
                    caption = caption,
                    location = location,
                    audioLabel = audioLabel,
                    aspectRatio = aspectRatio,
                    isVideo = isVideo,
                    cropSpec = cropSpec,
                    filter = filter,
                    taggedUsernames = taggedUsernames,
                    onProgress = { progress -> _uploadProgress.value = progress }
                )
            }.onSuccess {
                _isPublishing.value = false
                _uploadProgress.value = null
                showStatusBanner(
                    if (mediaType == SocialMediaType.REEL) "Reel shared to Esosa" else "Post shared to Esosa"
                )
                onPublished()
            }.onFailure { err ->
                _isPublishing.value = false
                _uploadProgress.value = null
                _actionError.value = err.localizedMessage ?: "Could not publish post."
            }
        }
    }

    fun publishStory(
        imageUri: Uri?,
        caption: String,
        isVideo: Boolean = false,
        cropSpec: MediaCropSpec = MediaCropSpec(targetAspectRatio = 0.75f),
        filter: PostFilter = PostFilter.NORMAL,
        onPublished: () -> Unit
    ) {
        if (_isPublishing.value) return
        viewModelScope.launch {
            _isPublishing.value = true
            _uploadProgress.value = if (imageUri != null) 0.05f else null
            _actionError.value = null
            runCatching {
                repository.publishStory(
                    imageUri = imageUri,
                    caption = caption,
                    isVideo = isVideo,
                    cropSpec = cropSpec,
                    filter = filter,
                    onProgress = { progress -> if (imageUri != null) _uploadProgress.value = progress }
                )
            }.onSuccess {
                _isPublishing.value = false
                _uploadProgress.value = null
                showStatusBanner("Added to Your Story")
                onPublished()
            }.onFailure { err ->
                _isPublishing.value = false
                _uploadProgress.value = null
                _actionError.value = err.localizedMessage ?: "Could not publish story."
            }
        }
    }

    fun toggleLike(post: SocialPost) {
        viewModelScope.launch {
            runCatching { repository.toggleLike(post) }
                .onFailure { _actionError.value = it.localizedMessage ?: "Failed to update like." }
        }
    }

    fun likeOnly(post: SocialPost) {
        if (post.isLikedBy(currentUserId)) return
        toggleLike(post)
    }

    fun addComment(post: SocialPost, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            runCatching { repository.addComment(post, text) }
                .onFailure { _actionError.value = it.localizedMessage ?: "Failed to post comment." }
        }
    }

    fun deleteComment(postId: String, commentId: String) {
        viewModelScope.launch {
            runCatching { repository.deleteComment(postId, commentId) }
                .onFailure { _actionError.value = it.localizedMessage ?: "Failed to delete comment." }
        }
    }

    fun toggleSave(postId: String) {
        val currentlySaved = savedPostIds.value.contains(postId)
        viewModelScope.launch {
            runCatching { repository.toggleSave(postId, currentlySaved) }
                .onSuccess {
                    showStatusBanner(if (currentlySaved) "Removed from Saved" else "Saved to your collection")
                }
                .onFailure { _actionError.value = it.localizedMessage ?: "Failed to update bookmark." }
        }
    }

    fun toggleFollow(targetUid: String, targetUsername: String) {
        if (targetUid.isBlank() || targetUid == currentUserId) return
        val currentlyFollowing = followEdges.value.any {
            it.followerId == currentUserId && it.targetUid == targetUid
        }
        viewModelScope.launch {
            runCatching {
                repository.toggleFollow(targetUid, targetUsername, currentlyFollowing)
            }.onFailure {
                _actionError.value = it.localizedMessage ?: "Failed to update follow status."
            }
        }
    }

    fun markStoryViewed(story: SocialStory) {
        viewModelScope.launch {
            runCatching { repository.markStoryViewed(story) }
        }
    }

    fun deletePost(post: SocialPost) {
        viewModelScope.launch {
            runCatching { repository.deletePost(post) }
                .onSuccess { showStatusBanner("Post deleted") }
                .onFailure { _actionError.value = it.localizedMessage ?: "Failed to delete post." }
        }
    }

    fun deleteStory(story: SocialStory) {
        viewModelScope.launch {
            runCatching { repository.deleteStory(story) }
                .onSuccess { showStatusBanner("Story deleted") }
                .onFailure { _actionError.value = it.localizedMessage ?: "Failed to delete story." }
        }
    }

    fun showStatusBanner(message: String) {
        _statusBanner.value = message
        viewModelScope.launch {
            delay(2600L)
            if (_statusBanner.value == message) {
                _statusBanner.value = null
            }
        }
    }

    fun dismissBanner() {
        _statusBanner.value = null
    }

    fun clearError() {
        _actionError.value = null
    }
}
