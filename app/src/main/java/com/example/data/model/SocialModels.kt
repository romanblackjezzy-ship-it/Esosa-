package com.example.data.model

import java.util.concurrent.TimeUnit

enum class SocialMediaType(val id: String) {
    PHOTO("photo"),
    REEL("reel");

    companion object {
        fun fromId(raw: String?): SocialMediaType = entries.firstOrNull { it.id == raw } ?: PHOTO
    }
}

enum class CreateFormat(val label: String) {
    POST("Post"),
    REEL("Reel"),
    STORY("Story")
}

data class SocialPost(
    val id: String = "",
    val authorId: String = "",
    val authorUsername: String = "",
    val authorDisplayName: String = "",
    val authorPhotoUrl: String = "",
    val mediaType: SocialMediaType = SocialMediaType.PHOTO,
    val imageUrl: String = "",
    val storagePath: String? = null,
    val caption: String = "",
    val location: String = "",
    val audioLabel: String = "",
    val aspectRatio: Float = 1f,
    val likedByUids: List<String> = emptyList(),
    val commentCount: Int = 0,
    val createdAtMillis: Long = 0L
) {
    fun isLikedBy(uid: String): Boolean = uid.isNotBlank() && likedByUids.contains(uid)
}

data class PostComment(
    val id: String = "",
    val postId: String = "",
    val authorId: String = "",
    val authorUsername: String = "",
    val authorPhotoUrl: String = "",
    val text: String = "",
    val createdAtMillis: Long = 0L
)

data class SocialStory(
    val id: String = "",
    val authorId: String = "",
    val authorUsername: String = "",
    val authorPhotoUrl: String = "",
    val imageUrl: String = "",
    val storagePath: String? = null,
    val caption: String = "",
    val viewedByUids: List<String> = emptyList(),
    val createdAtMillis: Long = 0L
) {
    fun isViewedBy(uid: String): Boolean = uid.isNotBlank() && (authorId == uid || viewedByUids.contains(uid))
}

data class UserFollowEdge(
    val id: String = "",
    val followerId: String = "",
    val targetUid: String = "",
    val targetUsername: String = "",
    val createdAtMillis: Long = 0L
)

object SocialFormatters {
    fun formatCompactCount(count: Int): String {
        val safe = count.coerceAtLeast(0)
        return when {
            safe >= 1_000_000 -> String.format("%.1fM", safe / 1_000_000f).removeSuffix(".0")
            safe >= 10_000 -> "${safe / 1_000}K"
            safe >= 1_000 -> String.format("%.1fK", safe / 1_000f).removeSuffix(".0")
            else -> safe.toString()
        }
    }

    fun formatLikesLabel(count: Int): String = when (count.coerceAtLeast(0)) {
        0 -> "Be the first to like this"
        1 -> "1 like"
        else -> "${formatCompactCount(count)} likes"
    }

    fun formatTimeAgo(timestampMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        if (timestampMillis <= 0L) return "Just now"
        val diff = (nowMillis - timestampMillis).coerceAtLeast(0L)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val days = TimeUnit.MILLISECONDS.toDays(diff)
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days < 7 -> "${days}d ago"
            else -> "${days / 7}w ago"
        }
    }

    fun formatShortTime(timestampMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        if (timestampMillis <= 0L) return "now"
        val diff = (nowMillis - timestampMillis).coerceAtLeast(0L)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val days = TimeUnit.MILLISECONDS.toDays(diff)
        return when {
            minutes < 1 -> "now"
            minutes < 60 -> "${minutes}m"
            hours < 24 -> "${hours}h"
            days < 7 -> "${days}d"
            else -> "${days / 7}w"
        }
    }
}
