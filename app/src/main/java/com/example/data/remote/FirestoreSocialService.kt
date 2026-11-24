package com.example.data.remote

import com.example.data.model.PostComment
import com.example.data.model.SocialMediaType
import com.example.data.model.SocialPost
import com.example.data.model.SocialStory
import com.example.data.model.StoryReply
import com.example.data.model.UserFollowEdge
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreSocialService(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val estimate = DocumentSnapshot.ServerTimestampBehavior.ESTIMATE

    fun observePosts(limitCount: Long = 100L): Flow<List<SocialPost>> = callbackFlow {
        val reg = db.collection(POSTS_COLLECTION)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limitCount)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    FirestoreErrorHandler.handleFirestoreError(err, OperationType.LIST, POSTS_COLLECTION, auth)
                    close(err)
                    return@addSnapshotListener
                }
                val list = snap?.documents.orEmpty().mapNotNull(::parsePost)
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    fun observeStories(limitCount: Long = 50L): Flow<List<SocialStory>> = callbackFlow {
        val reg = db.collection(STORIES_COLLECTION)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limitCount)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    FirestoreErrorHandler.handleFirestoreError(err, OperationType.LIST, STORIES_COLLECTION, auth)
                    close(err)
                    return@addSnapshotListener
                }
                val list = snap?.documents.orEmpty().mapNotNull(::parseStory)
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun fetchLatestPosts(limitCount: Long = 100L): List<SocialPost> {
        return try {
            val snap = db.collection(POSTS_COLLECTION)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limitCount)
                .get(Source.SERVER)
                .await()
            snap.documents.mapNotNull(::parsePost)
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.LIST, POSTS_COLLECTION, auth)
            throw e
        }
    }

    suspend fun fetchLatestStories(limitCount: Long = 50L): List<SocialStory> {
        return try {
            val snap = db.collection(STORIES_COLLECTION)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limitCount)
                .get(Source.SERVER)
                .await()
            snap.documents.mapNotNull(::parseStory)
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.LIST, STORIES_COLLECTION, auth)
            throw e
        }
    }

    fun observeComments(postId: String): Flow<List<PostComment>> = callbackFlow {
        val path = "$POSTS_COLLECTION/$postId/$COMMENTS_SUBCOLLECTION"
        val reg = db.collection(POSTS_COLLECTION)
            .document(postId)
            .collection(COMMENTS_SUBCOLLECTION)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limit(100L)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    FirestoreErrorHandler.handleFirestoreError(err, OperationType.LIST, path, auth)
                    close(err)
                    return@addSnapshotListener
                }
                val list = snap?.documents.orEmpty().mapNotNull(::parseComment)
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    fun observeSavedPostIds(uid: String): Flow<Set<String>> = callbackFlow {
        val path = "$USERS_COLLECTION/$uid/$SAVED_POSTS_SUBCOLLECTION"
        val reg = db.collection(USERS_COLLECTION)
            .document(uid)
            .collection(SAVED_POSTS_SUBCOLLECTION)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    FirestoreErrorHandler.handleFirestoreError(err, OperationType.LIST, path, auth)
                    close(err)
                    return@addSnapshotListener
                }
                val ids = snap?.documents.orEmpty().mapNotNull { it.getString("postId") ?: it.id }.toSet()
                trySend(ids)
            }
        awaitClose { reg.remove() }
    }

    fun observeFollows(): Flow<List<UserFollowEdge>> = callbackFlow {
        val reg = db.collection(FOLLOWS_COLLECTION)
            .limit(500L)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    FirestoreErrorHandler.handleFirestoreError(err, OperationType.LIST, FOLLOWS_COLLECTION, auth)
                    close(err)
                    return@addSnapshotListener
                }
                val edges = snap?.documents.orEmpty().mapNotNull(::parseFollowEdge)
                trySend(edges)
            }
        awaitClose { reg.remove() }
    }

    suspend fun createPost(
        postId: String,
        authorId: String,
        authorUsername: String,
        authorDisplayName: String,
        authorPhotoUrl: String,
        mediaType: SocialMediaType,
        imageUrl: String,
        videoUrl: String = "",
        storagePath: String?,
        caption: String,
        location: String,
        audioLabel: String,
        taggedUsernames: List<String> = emptyList(),
        aspectRatio: Float
    ) {
        val payload = hashMapOf<String, Any>(
            "authorId" to authorId,
            "authorUsername" to authorUsername.take(40).ifBlank { "esosa_user" },
            "authorDisplayName" to authorDisplayName.take(80).ifBlank { "Esosa Creator" },
            "mediaType" to mediaType.id,
            "imageUrl" to imageUrl,
            "caption" to caption.take(2200),
            "aspectRatio" to aspectRatio.coerceIn(0.5f, 2.0f),
            "likedByUids" to emptyList<String>(),
            "commentCount" to 0,
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (videoUrl.isNotBlank()) {
            payload["videoUrl"] = videoUrl.take(2000)
        }
        if (authorPhotoUrl.isNotBlank()) {
            payload["authorPhotoUrl"] = authorPhotoUrl.take(2000)
        }
        if (!storagePath.isNullOrBlank()) {
            payload["storagePath"] = storagePath.take(500)
        }
        if (location.isNotBlank()) {
            payload["location"] = location.trim().take(120)
        }
        if (audioLabel.isNotBlank()) {
            payload["audioLabel"] = audioLabel.trim().take(120)
        }
        val cleanTags = taggedUsernames
            .map { it.trim().removePrefix("@").take(32) }
            .filter { it.isNotBlank() }
            .distinct()
            .take(20)
        if (cleanTags.isNotEmpty()) {
            payload["taggedUsernames"] = cleanTags
        }
        val path = "$POSTS_COLLECTION/$postId"
        try {
            db.collection(POSTS_COLLECTION).document(postId).set(payload).await()
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.CREATE, path, auth)
            throw e
        }
    }

    suspend fun createStory(
        storyId: String,
        authorId: String,
        authorUsername: String,
        authorPhotoUrl: String,
        imageUrl: String?,
        videoUrl: String? = null,
        storagePath: String?,
        caption: String
    ) {
        val payload = hashMapOf<String, Any>(
            "authorId" to authorId,
            "authorUsername" to authorUsername.take(40).ifBlank { "esosa_user" },
            "caption" to caption.take(300),
            "viewedByUids" to emptyList<String>(),
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (authorPhotoUrl.isNotBlank()) {
            payload["authorPhotoUrl"] = authorPhotoUrl.take(2000)
        }
        if (!imageUrl.isNullOrBlank()) {
            payload["imageUrl"] = imageUrl.take(2000)
        }
        if (!videoUrl.isNullOrBlank()) {
            payload["videoUrl"] = videoUrl.take(2000)
        }
        if (!storagePath.isNullOrBlank()) {
            payload["storagePath"] = storagePath.take(500)
        }
        val path = "$STORIES_COLLECTION/$storyId"
        try {
            db.collection(STORIES_COLLECTION).document(storyId).set(payload).await()
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.CREATE, path, auth)
            throw e
        }
    }

    fun observeStoryReplies(story: SocialStory, currentUid: String): Flow<List<StoryReply>> = callbackFlow {
        if (story.id.isBlank() || currentUid.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val path = "$STORIES_COLLECTION/${story.id}/$REPLIES_SUBCOLLECTION"
        val filterField = if (story.authorId == currentUid) "storyAuthorId" else "authorId"
        val reg = db.collection(STORIES_COLLECTION)
            .document(story.id)
            .collection(REPLIES_SUBCOLLECTION)
            .whereEqualTo(filterField, currentUid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    FirestoreErrorHandler.handleFirestoreError(err, OperationType.LIST, path, auth)
                    close(err)
                    return@addSnapshotListener
                }
                val list = snap?.documents.orEmpty()
                    .mapNotNull(::parseStoryReply)
                    .sortedBy { it.createdAtMillis }
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun addStoryReply(
        story: SocialStory,
        replyId: String,
        authorId: String,
        authorUsername: String,
        authorPhotoUrl: String,
        text: String
    ) {
        val cleanText = text.trim().take(500)
        if (cleanText.isBlank() || story.id.isBlank() || story.authorId.isBlank()) return
        val path = "$STORIES_COLLECTION/${story.id}/$REPLIES_SUBCOLLECTION/$replyId"
        val payload = hashMapOf<String, Any>(
            "storyId" to story.id,
            "storyAuthorId" to story.authorId,
            "authorId" to authorId,
            "authorUsername" to authorUsername.take(40).ifBlank { "esosa_user" },
            "text" to cleanText,
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (authorPhotoUrl.isNotBlank()) {
            payload["authorPhotoUrl"] = authorPhotoUrl.take(2000)
        }
        try {
            db.collection(STORIES_COLLECTION).document(story.id)
                .collection(REPLIES_SUBCOLLECTION).document(replyId)
                .set(payload)
                .await()
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.CREATE, path, auth)
            throw e
        }
    }

    suspend fun toggleLike(post: SocialPost, currentUid: String) {
        val currentLikes = post.likedByUids
        val updatedLikes = if (currentLikes.contains(currentUid)) {
            currentLikes.filterNot { it == currentUid }
        } else {
            (currentLikes + currentUid).distinct()
        }
        val path = "$POSTS_COLLECTION/${post.id}"
        try {
            db.collection(POSTS_COLLECTION).document(post.id)
                .update("likedByUids", updatedLikes)
                .await()
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.UPDATE, path, auth)
            throw e
        }
    }

    suspend fun addComment(
        post: SocialPost,
        commentId: String,
        authorId: String,
        authorUsername: String,
        authorPhotoUrl: String,
        text: String
    ) {
        val cleanText = text.trim().take(1000)
        if (cleanText.isBlank()) return
        val commentPath = "$POSTS_COLLECTION/${post.id}/$COMMENTS_SUBCOLLECTION/$commentId"
        val postPath = "$POSTS_COLLECTION/${post.id}"
        val payload = hashMapOf<String, Any>(
            "postId" to post.id,
            "authorId" to authorId,
            "authorUsername" to authorUsername.take(40).ifBlank { "esosa_user" },
            "text" to cleanText,
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (authorPhotoUrl.isNotBlank()) {
            payload["authorPhotoUrl"] = authorPhotoUrl.take(2000)
        }
        try {
            db.collection(POSTS_COLLECTION).document(post.id)
                .collection(COMMENTS_SUBCOLLECTION).document(commentId)
                .set(payload)
                .await()
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.CREATE, commentPath, auth)
            throw e
        }
        try {
            db.collection(POSTS_COLLECTION).document(post.id)
                .update("commentCount", post.commentCount + 1)
                .await()
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.UPDATE, postPath, auth)
            throw e
        }
    }

    suspend fun deleteComment(postId: String, commentId: String) {
        val path = "$POSTS_COLLECTION/$postId/$COMMENTS_SUBCOLLECTION/$commentId"
        try {
            db.collection(POSTS_COLLECTION).document(postId)
                .collection(COMMENTS_SUBCOLLECTION).document(commentId)
                .delete()
                .await()
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.DELETE, path, auth)
            throw e
        }
    }

    suspend fun setSavedPost(uid: String, postId: String, save: Boolean) {
        val ref = db.collection(USERS_COLLECTION).document(uid)
            .collection(SAVED_POSTS_SUBCOLLECTION).document(postId)
        val path = "$USERS_COLLECTION/$uid/$SAVED_POSTS_SUBCOLLECTION/$postId"
        try {
            if (save) {
                ref.set(
                    mapOf(
                        "userId" to uid,
                        "postId" to postId,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                ref.delete().await()
            }
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(
                e,
                if (save) OperationType.CREATE else OperationType.DELETE,
                path,
                auth
            )
            throw e
        }
    }

    suspend fun setFollow(
        followerId: String,
        targetUid: String,
        targetUsername: String,
        follow: Boolean
    ) {
        if (followerId.isBlank() || targetUid.isBlank() || followerId == targetUid) return
        val followId = "${followerId}_$targetUid"
        val ref = db.collection(FOLLOWS_COLLECTION).document(followId)
        val path = "$FOLLOWS_COLLECTION/$followId"
        try {
            if (follow) {
                ref.set(
                    mapOf(
                        "followerId" to followerId,
                        "targetUid" to targetUid,
                        "targetUsername" to targetUsername.take(40).ifBlank { "esosa_creator" },
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                ref.delete().await()
            }
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(
                e,
                if (follow) OperationType.CREATE else OperationType.DELETE,
                path,
                auth
            )
            throw e
        }
    }

    suspend fun markStoryViewed(story: SocialStory, currentUid: String) {
        if (currentUid.isBlank() || story.authorId == currentUid || story.viewedByUids.contains(currentUid)) {
            return
        }
        val updated = (story.viewedByUids + currentUid).distinct()
        val path = "$STORIES_COLLECTION/${story.id}"
        try {
            db.collection(STORIES_COLLECTION).document(story.id)
                .update("viewedByUids", updated)
                .await()
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.UPDATE, path, auth)
        }
    }

    suspend fun deletePost(postId: String) {
        val path = "$POSTS_COLLECTION/$postId"
        try {
            db.collection(POSTS_COLLECTION).document(postId).delete().await()
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.DELETE, path, auth)
            throw e
        }
    }

    suspend fun deleteStory(storyId: String) {
        val path = "$STORIES_COLLECTION/$storyId"
        try {
            db.collection(STORIES_COLLECTION).document(storyId).delete().await()
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.DELETE, path, auth)
            throw e
        }
    }

    private fun parsePost(doc: DocumentSnapshot): SocialPost? {
        if (!doc.exists()) return null
        val authorId = doc.getString("authorId") ?: return null
        val videoUrl = doc.getString("videoUrl").orEmpty()
        val imageUrl = doc.getString("imageUrl").orEmpty()
        if (imageUrl.isBlank() && videoUrl.isBlank()) return null
        return SocialPost(
            id = doc.id,
            authorId = authorId,
            authorUsername = doc.getString("authorUsername").orEmpty().ifBlank { "esosa_user" },
            authorDisplayName = doc.getString("authorDisplayName").orEmpty().ifBlank { "Esosa Creator" },
            authorPhotoUrl = doc.getString("authorPhotoUrl").orEmpty(),
            mediaType = SocialMediaType.fromId(doc.getString("mediaType")),
            imageUrl = imageUrl.ifBlank { videoUrl },
            videoUrl = videoUrl,
            storagePath = doc.getString("storagePath"),
            caption = doc.getString("caption").orEmpty(),
            location = doc.getString("location").orEmpty(),
            audioLabel = doc.getString("audioLabel").orEmpty(),
            taggedUsernames = parseStringList(doc.get("taggedUsernames")),
            aspectRatio = (doc.get("aspectRatio") as? Number)?.toFloat()?.coerceIn(0.5f, 2.0f) ?: 1f,
            likedByUids = parseStringList(doc.get("likedByUids")),
            commentCount = (doc.get("commentCount") as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
            createdAtMillis = FirestoreSnapshotMappers.timestampToMillis(doc.getTimestamp("createdAt", estimate))
        )
    }

    private fun parseComment(doc: DocumentSnapshot): PostComment? {
        if (!doc.exists()) return null
        val postId = doc.getString("postId") ?: return null
        val authorId = doc.getString("authorId") ?: return null
        val text = doc.getString("text") ?: return null
        return PostComment(
            id = doc.id,
            postId = postId,
            authorId = authorId,
            authorUsername = doc.getString("authorUsername").orEmpty().ifBlank { "esosa_user" },
            authorPhotoUrl = doc.getString("authorPhotoUrl").orEmpty(),
            text = text,
            createdAtMillis = FirestoreSnapshotMappers.timestampToMillis(doc.getTimestamp("createdAt", estimate))
        )
    }

    private fun parseStory(doc: DocumentSnapshot): SocialStory? {
        if (!doc.exists()) return null
        val authorId = doc.getString("authorId") ?: return null
        val videoUrl = doc.getString("videoUrl").orEmpty()
        val imageUrl = doc.getString("imageUrl").orEmpty().ifBlank { videoUrl }
        return SocialStory(
            id = doc.id,
            authorId = authorId,
            authorUsername = doc.getString("authorUsername").orEmpty().ifBlank { "esosa_user" },
            authorPhotoUrl = doc.getString("authorPhotoUrl").orEmpty(),
            imageUrl = imageUrl,
            videoUrl = videoUrl,
            storagePath = doc.getString("storagePath"),
            caption = doc.getString("caption").orEmpty(),
            viewedByUids = parseStringList(doc.get("viewedByUids")),
            createdAtMillis = FirestoreSnapshotMappers.timestampToMillis(doc.getTimestamp("createdAt", estimate))
        )
    }

    private fun parseStoryReply(doc: DocumentSnapshot): StoryReply? {
        if (!doc.exists()) return null
        val storyId = doc.getString("storyId") ?: return null
        val storyAuthorId = doc.getString("storyAuthorId") ?: return null
        val authorId = doc.getString("authorId") ?: return null
        val text = doc.getString("text") ?: return null
        return StoryReply(
            id = doc.id,
            storyId = storyId,
            storyAuthorId = storyAuthorId,
            authorId = authorId,
            authorUsername = doc.getString("authorUsername").orEmpty().ifBlank { "esosa_user" },
            authorPhotoUrl = doc.getString("authorPhotoUrl").orEmpty(),
            text = text,
            createdAtMillis = FirestoreSnapshotMappers.timestampToMillis(doc.getTimestamp("createdAt", estimate))
        )
    }

    private fun parseFollowEdge(doc: DocumentSnapshot): UserFollowEdge? {
        if (!doc.exists()) return null
        val followerId = doc.getString("followerId") ?: return null
        val targetUid = doc.getString("targetUid") ?: return null
        return UserFollowEdge(
            id = doc.id,
            followerId = followerId,
            targetUid = targetUid,
            targetUsername = doc.getString("targetUsername").orEmpty(),
            createdAtMillis = FirestoreSnapshotMappers.timestampToMillis(doc.getTimestamp("createdAt", estimate))
        )
    }

    private fun parseStringList(raw: Any?): List<String> {
        val list = raw as? List<*> ?: return emptyList()
        return list.mapNotNull { (it as? String)?.takeIf { s -> s.isNotBlank() } }
    }

    companion object {
        private const val POSTS_COLLECTION = "posts"
        private const val COMMENTS_SUBCOLLECTION = "comments"
        private const val STORIES_COLLECTION = "stories"
        private const val REPLIES_SUBCOLLECTION = "replies"
        private const val USERS_COLLECTION = "users"
        private const val SAVED_POSTS_SUBCOLLECTION = "saved_posts"
        private const val FOLLOWS_COLLECTION = "follows"
    }
}
