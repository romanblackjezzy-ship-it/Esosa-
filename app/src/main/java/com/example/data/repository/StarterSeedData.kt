package com.example.data.repository

import com.example.data.local.CallLogEntity
import com.example.data.local.ChannelCommentEntity
import com.example.data.local.ChatRoomEntity
import com.example.data.local.RoomMessageEntity
import com.example.data.local.StoryEntity
import com.example.data.model.ChatKind

internal object StarterSeedData {

    val LEGACY_DEMO_ROOM_IDS = listOf(
        "dm_maya_lin",
        "group_design_systems",
        "dm_liam_oconnor",
        "channel_esosa_insider",
        "channel_tech_digest",
        "dm_kaito_archived"
    )

    val LEGACY_DEMO_STORY_IDS = listOf(
        "story_maya",
        "story_liam",
        "story_esosa",
        "story_elena"
    )

    val LEGACY_DEMO_CALL_IDS = listOf(
        "call_1",
        "call_2",
        "call_3",
        "call_4"
    )

    val LEGACY_DEMO_COMMENT_IDS = listOf(
        "cmt_1",
        "cmt_2"
    )

    fun starterRooms(now: Long): List<ChatRoomEntity> {
        return listOf(
            ChatRoomEntity(
                id = ChatRepository.SAVED_MESSAGES_ROOM_ID,
                title = "Saved Messages",
                topic = "Personal vault for notes, links, voice memos & photos",
                chatKind = ChatKind.SAVED.id,
                roomCode = "SAVED",
                memberCount = 1,
                isPinned = true,
                unreadCount = 0,
                lastMessageText = "",
                lastMessageSenderName = "",
                lastMessageIsMine = true,
                lastMessageAtMillis = now,
                createdAtMillis = now,
                updatedAtMillis = now,
                avatarKey = "saved",
                handle = "saved",
                isOnline = true,
                onlineStatusText = "Private personal storage"
            )
        )
    }

    fun starterMessages(@Suppress("UNUSED_PARAMETER") now: Long): List<RoomMessageEntity> = emptyList()

    fun starterStories(@Suppress("UNUSED_PARAMETER") now: Long): List<StoryEntity> = emptyList()

    fun starterCallLogs(@Suppress("UNUSED_PARAMETER") now: Long): List<CallLogEntity> = emptyList()

    fun starterChannelComments(@Suppress("UNUSED_PARAMETER") now: Long): List<ChannelCommentEntity> = emptyList()
}
