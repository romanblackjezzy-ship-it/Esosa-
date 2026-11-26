package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.LocalProfilePreferences
import com.example.core.UiState
import com.example.data.model.MediaCropSpec
import com.example.data.model.PostComment
import com.example.data.model.PostFilter
import com.example.data.model.SocialMediaType
import com.example.data.model.SocialPost
import com.example.data.model.SocialStory
import com.example.data.model.StoryReply
import com.example.data.model.UserFollowEdge
import com.example.data.remote.FirestoreSocialService
import com.example.data.repository.SocialRepository
import com.example.notifications.EsosaNotificationManager
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SocialViewModel(
    private val repository: SocialRepository,
    private val notificationManager: EsosaNotificationManager? = null,
    private val profilePreferences: LocalProfilePreferences? = null
) : ViewModel() {

    val currentUserId: String
        get() = repository.currentUserId

    private val _manualPostsFlow = MutableSharedFlow<List<SocialPost>>(extraBufferCapacity = 1)
    private val _manualStoriesFlow = MutableSharedFlow<List<SocialStory>>(extraBufferCapacity = 1)
    private val _pagedOlderPosts = MutableStateFlow<List<SocialPost>>(emptyList())
    private val _deletedPostIds = MutableStateFlow<Set<String>>(emptySet())

    private val _isLoadingMorePosts = MutableStateFlow(false)
    val isLoadingMorePosts: StateFlow<Boolean> = _isLoadingMorePosts.asStateFlow()

    private val _hasMorePosts = MutableStateFlow(true)
    val hasMorePosts: StateFlow<Boolean> = _hasMorePosts.asStateFlow()

    private var hasInitializedLikeBaseline = false
    private val knownLikesByMyPost = mutableMapOf<String, Set<String>>()

    val postsState: StateFlow<UiState<List<SocialPost>>> =
        combine(
            merge(repository.observePosts(), _manualPostsFlow)
                .onEach { headPosts ->
                    if (_pagedOlderPosts.value.isEmpty() &&
                        headPosts.size < FirestoreSocialService.DEFAULT_POSTS_PAGE_SIZE.toInt()
                    ) {
                        _hasMorePosts.value = false
                    } else if (headPosts.size >= FirestoreSocialService.DEFAULT_POSTS_PAGE_SIZE.toInt()) {
                        _hasMorePosts.value = true
                    }
                },
            _pagedOlderPosts,
            _deletedPostIds
        ) { headPosts, olderPosts, deletedIds ->
            val combined = LinkedHashMap<String, SocialPost>(headPosts.size + olderPosts.size)
            headPosts.forEach { post ->
                if (post.id !in deletedIds) combined[post.id] = post
            }
            olderPosts.forEach { post ->
                if (post.id !in deletedIds && !combined.containsKey(post.id)) {
                    combined[post.id] = post
                }
            }
            combined.values.sortedByDescending { it.createdAtMillis }
        }
            .onEach { posts ->
                prefetchPreviewComments(posts)
                val uid = currentUserId
                val myPosts = posts.filter { it.authorId == uid }
                if (!hasInitializedLikeBaseline) {
                    hasInitializedLikeBaseline = true
                    myPosts.forEach { post ->
                        knownLikesByMyPost[post.id] = post.likedByUids.toSet()
                    }
                } else {
                    val notificationsEnabled = profilePreferences?.getProfile()?.notificationsEnabled ?: true
                    myPosts.forEach { post ->
                        val previous = knownLikesByMyPost[post.id] ?: emptySet()
                        val currentSet = post.likedByUids.toSet()
                        val newLikers = currentSet - previous - uid
                        knownLikesByMyPost[post.id] = currentSet
                        if (newLikers.isNotEmpty() && notificationsEnabled) {
                            val label = if (newLikers.size == 1) "Someone" else "${newLikers.size} people"
                            notificationManager?.showPostLikeNotification(
                                postId = post.id,
                                likerUsername = label,
                                postPreview = post.caption
                            )
                            showStatusBanner("$label liked your post")
                        }
                    }
                }
            }
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

    private val _repliesByStory = MutableStateFlow<Map<String, List<StoryReply>>>(emptyMap())
    val repliesByStory: StateFlow<Map<String, List<StoryReply>>> = _repliesByStory.asStateFlow()
    private val storyReplyObserverJobs = mutableMapOf<String, Job>()

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
                    _pagedOlderPosts.value = emptyList()
                    _deletedPostIds.value = emptySet()
                    _hasMorePosts.value = latestPosts.size >= FirestoreSocialService.DEFAULT_POSTS_PAGE_SIZE.toInt()
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

    fun loadMorePosts() {
        if (_isLoadingMorePosts.value || !_hasMorePosts.value) return
        val currentPosts = (postsState.value as? UiState.Success)?.data.orEmpty()
        val cursorMillis = currentPosts.minOfOrNull { it.createdAtMillis } ?: return
        if (cursorMillis <= 0L) {
            _hasMorePosts.value = false
            return
        }
        viewModelScope.launch {
            _isLoadingMorePosts.value = true
            try {
                val pageSize = FirestoreSocialService.DEFAULT_POSTS_PAGE_SIZE
                val fetched = repository.fetchPostsAfter(
                    beforeCreatedAtMillis = cursorMillis,
                    limitCount = pageSize
                )
                val existingIds = currentPosts.mapTo(HashSet()) { it.id }
                val newUnique = fetched.filter { it.id !in existingIds && it.id !in _deletedPostIds.value }
                if (newUnique.isNotEmpty()) {
                    _pagedOlderPosts.value = _pagedOlderPosts.value + newUnique
                }
                if (fetched.size < pageSize.toInt() || newUnique.isEmpty()) {
                    _hasMorePosts.value = false
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showStatusBanner(e.localizedMessage ?: "Could not load older posts.")
            } finally {
                _isLoadingMorePosts.value = false
            }
        }
    }

    private fun prefetchPreviewComments(posts: List<SocialPost>) {
        if (commentObserverJobs.size >= 24) return
        posts.asSequence()
            .filter { it.commentCount > 0 && commentObserverJobs[it.id]?.isActive != true }
            .take((24 - commentObserverJobs.size).coerceAtLeast(0))
            .forEach { post ->
                observeCommentsForPost(post.id)
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
        val uid = currentUserId
        if (uid.isNotBlank() && _pagedOlderPosts.value.any { it.id == post.id }) {
            val nextLikes = if (post.isLikedBy(uid)) {
                post.likedByUids.filterNot { it == uid }
            } else {
                (post.likedByUids + uid).distinct()
            }
            _pagedOlderPosts.value = _pagedOlderPosts.value.map {
                if (it.id == post.id) it.copy(likedByUids = nextLikes) else it
            }
        }
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
        observeCommentsForPost(post.id)
        if (_pagedOlderPosts.value.any { it.id == post.id }) {
            _pagedOlderPosts.value = _pagedOlderPosts.value.map {
                if (it.id == post.id) it.copy(commentCount = it.commentCount + 1) else it
            }
        }
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

    fun observeRepliesForStory(story: SocialStory) {
        if (story.id.isBlank() || storyReplyObserverJobs[story.id]?.isActive == true) return
        storyReplyObserverJobs[story.id] = viewModelScope.launch {
            repository.observeStoryReplies(story)
                .catch { /* keep cached replies */ }
                .collect { replies ->
                    _repliesByStory.value = _repliesByStory.value + (story.id to replies)
                }
        }
    }

    fun sendStoryReply(story: SocialStory, text: String, onSent: () -> Unit = {}) {
        val clean = text.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            runCatching { repository.sendStoryReply(story, clean) }
                .onSuccess {
                    showStatusBanner("Reply sent to @${story.authorUsername}")
                    onSent()
                }
                .onFailure {
                    _actionError.value = it.localizedMessage ?: "Failed to send story reply."
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
                .onSuccess {
                    _deletedPostIds.value = _deletedPostIds.value + post.id
                    _pagedOlderPosts.value = _pagedOlderPosts.value.filterNot { it.id == post.id }
                    showStatusBanner("Post deleted")
                }
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
