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
    val videoUrl: String = "",
    val storagePath: String? = null,
    val caption: String = "",
    val location: String = "",
    val audioLabel: String = "",
    val taggedUsernames: List<String> = emptyList(),
    val aspectRatio: Float = 1f,
    val likedByUids: List<String> = emptyList(),
    val commentCount: Int = 0,
    val createdAtMillis: Long = 0L
) {
    fun isLikedBy(uid: String): Boolean = uid.isNotBlank() && likedByUids.contains(uid)

    val hasVideo: Boolean
        get() = videoUrl.isNotBlank() ||
            imageUrl.contains(".mp4", ignoreCase = true) ||
            imageUrl.contains(".mov", ignoreCase = true) ||
            imageUrl.contains(".webm", ignoreCase = true) ||
            imageUrl.contains(".m3u8", ignoreCase = true)
}

data class MediaCropSpec(
    val targetAspectRatio: Float = 1f,
    val scale: Float = 1f,
    val offsetXFraction: Float = 0f,
    val offsetYFraction: Float = 0f
)

enum class PostFilter(
    val id: String,
    val label: String,
    private val matrixValues: FloatArray
) {
    NORMAL(
        id = "normal",
        label = "Normal",
        matrixValues = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    CLARENDON(
        id = "clarendon",
        label = "Clarendon",
        matrixValues = floatArrayOf(
            1.15f, -0.05f, 0.0f, 0f, 4f,
            -0.02f, 1.18f, 0.04f, 0f, 6f,
            0.0f, 0.05f, 1.24f, 0f, 10f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    JUNO(
        id = "juno",
        label = "Juno",
        matrixValues = floatArrayOf(
            1.22f, 0.06f, -0.04f, 0f, 8f,
            0.03f, 1.12f, -0.02f, 0f, 4f,
            -0.05f, 0.0f, 1.02f, 0f, -6f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    LARK(
        id = "lark",
        label = "Lark",
        matrixValues = floatArrayOf(
            1.08f, 0.02f, 0.0f, 0f, 14f,
            0.0f, 1.14f, 0.02f, 0f, 14f,
            0.0f, 0.04f, 1.18f, 0f, 18f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    GINGHAM(
        id = "gingham",
        label = "Gingham",
        matrixValues = floatArrayOf(
            0.96f, 0.08f, 0.04f, 0f, 18f,
            0.04f, 0.94f, 0.04f, 0f, 16f,
            0.02f, 0.04f, 0.88f, 0f, 14f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    MOON(
        id = "moon",
        label = "Moon",
        matrixValues = floatArrayOf(
            0.35f, 0.68f, 0.13f, 0f, -12f,
            0.35f, 0.68f, 0.13f, 0f, -12f,
            0.35f, 0.68f, 0.13f, 0f, -12f,
            0f, 0f, 0f, 1f, 0f
        )
    );

    fun colorMatrixValues(): FloatArray = matrixValues.copyOf()
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
