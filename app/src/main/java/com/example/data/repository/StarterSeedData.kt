package com.example.data.repository

import com.example.core.LocalProfilePreferences
import com.example.data.local.CallLogEntity
import com.example.data.local.ChannelCommentEntity
import com.example.data.local.ChatRoomEntity
import com.example.data.local.ReactionsJsonCodec
import com.example.data.local.RoomMessageEntity
import com.example.data.local.StoryEntity
import com.example.data.local.StringListJsonCodec
import com.example.data.model.CallDirection
import com.example.data.model.CallType
import com.example.data.model.ChatKind

internal object StarterSeedData {

    fun starterRooms(now: Long): List<ChatRoomEntity> {
        val m1 = now - 3 * 60_000L
        val m2 = now - 14 * 60_000L
        val m3 = now - 42 * 60_000L
        val m4 = now - 2 * 3_600_000L
        val m5 = now - 5 * 3_600_000L
        val m6 = now - 16 * 3_600_000L
        val m7 = now - 28 * 3_600_000L
        val m8 = now - 48 * 3_600_000L

        return listOf(
            ChatRoomEntity(
                id = ChatRepository.SAVED_MESSAGES_ROOM_ID,
                title = "Saved Messages",
                topic = "Personal cloudless vault for notes, links, voice memos & photos",
                chatKind = ChatKind.SAVED.id,
                roomCode = "SAVED",
                memberCount = 1,
                isPinned = true,
                unreadCount = 0,
                lastMessageText = "Design tokens & v2.0 launch checklist",
                lastMessageSenderName = "",
                lastMessageIsMine = true,
                lastMessageAtMillis = m1,
                createdAtMillis = m8,
                updatedAtMillis = m1,
                avatarKey = "saved",
                handle = "saved",
                isOnline = true,
                onlineStatusText = "Private personal storage"
            ),
            ChatRoomEntity(
                id = "dm_maya_lin",
                title = "Maya Lin",
                topic = "Product Design Lead • San Francisco",
                chatKind = ChatKind.DIRECT.id,
                roomCode = "MAYA01",
                memberCount = 2,
                isPinned = true,
                unreadCount = 2,
                lastMessageText = "Just uploaded the interactive prototype & dark mode palette!",
                lastMessageSenderName = "Maya Lin",
                lastMessageIsMine = false,
                lastMessageAtMillis = m2,
                createdAtMillis = m8,
                updatedAtMillis = m2,
                avatarKey = "maya",
                handle = "maya_lin",
                isOnline = true,
                onlineStatusText = "online",
                pinnedMessageId = "seed_maya_2",
                pinnedMessageText = "Design review today at 3:30 PM PST"
            ),
            ChatRoomEntity(
                id = "group_design_systems",
                title = "Design Systems Team",
                topic = "Cross-platform motion specs, typography & component QA",
                chatKind = ChatKind.GROUP.id,
                roomCode = "DSGN88",
                memberCount = 8,
                isPinned = false,
                unreadCount = 4,
                lastMessageText = "Voice note (0:18) • Motion curve tweaks",
                lastMessageSenderName = "Liam O'Connor",
                lastMessageIsMine = false,
                lastMessageAtMillis = m3,
                createdAtMillis = m8,
                updatedAtMillis = m3,
                avatarKey = "design_group",
                handle = "design_systems",
                isOnline = true,
                onlineStatusText = "8 members, 3 online",
                pinnedMessageId = "seed_group_1",
                pinnedMessageText = "Sprint 14 Component Freeze: Friday 5 PM",
                memberNamesJson = StringListJsonCodec.encode(
                    listOf("You", "Maya Lin", "Liam O'Connor", "Elena Rostova", "Kaito Tanaka", "Nadia Patel", "Devon Brooks", "Sofia Kim")
                ),
                adminNamesJson = StringListJsonCodec.encode(listOf("You", "Maya Lin"))
            ),
            ChatRoomEntity(
                id = "dm_liam_oconnor",
                title = "Liam O'Connor",
                topic = "Principal Systems Engineer • Dublin",
                chatKind = ChatKind.DIRECT.id,
                roomCode = "LIAM02",
                memberCount = 2,
                isPinned = false,
                unreadCount = 0,
                lastMessageText = "Latency dropped below 28ms after switching codecs.",
                lastMessageSenderName = "Liam O'Connor",
                lastMessageIsMine = false,
                lastMessageAtMillis = m4,
                createdAtMillis = m8,
                updatedAtMillis = m4,
                avatarKey = "liam",
                handle = "liam_dev",
                isOnline = false,
                onlineStatusText = "last seen 18 minutes ago"
            ),
            ChatRoomEntity(
                id = "channel_esosa_insider",
                title = "Esosa Product Insider",
                topic = "Official product releases, privacy deep dives & roadmap previews",
                chatKind = ChatKind.CHANNEL.id,
                roomCode = "ESOSA1",
                memberCount = 1,
                isPinned = false,
                unreadCount = 1,
                lastMessageText = "Introducing Esosa 2.0: Channels, Voice Notes & 3-Column Desktop",
                lastMessageSenderName = "Esosa Product Insider",
                lastMessageIsMine = false,
                lastMessageAtMillis = m5,
                createdAtMillis = m8,
                updatedAtMillis = m5,
                avatarKey = "channel_banner",
                handle = "esosa_insider",
                onlineStatusText = "Public Channel",
                subscriberCount = 18420,
                isSubscribed = true,
                isPublic = true,
                adminNamesJson = StringListJsonCodec.encode(listOf("You"))
            ),
            ChatRoomEntity(
                id = "channel_tech_digest",
                title = "Global Tech Digest",
                topic = "Daily engineering architecture, AI research & open source highlights",
                chatKind = ChatKind.CHANNEL.id,
                roomCode = "TECH24",
                memberCount = 1,
                isPinned = false,
                unreadCount = 0,
                lastMessageText = "Zero-knowledge key rotation & end-to-end call encryption",
                lastMessageSenderName = "Global Tech Digest",
                lastMessageIsMine = false,
                lastMessageAtMillis = m6,
                createdAtMillis = m8,
                updatedAtMillis = m6,
                avatarKey = "tech_digest",
                handle = "tech_digest",
                onlineStatusText = "Public Channel",
                subscriberCount = 94310,
                isSubscribed = true,
                isPublic = true,
                adminNamesJson = StringListJsonCodec.encode(listOf("Editorial Desk"))
            ),
            ChatRoomEntity(
                id = "bot_esosa_concierge",
                title = "Esosa Concierge Bot",
                topic = "Automated shortcuts, reminders & release assistant",
                chatKind = ChatKind.BOT.id,
                roomCode = "BOT900",
                memberCount = 2,
                isPinned = false,
                unreadCount = 0,
                lastMessageText = "Try /status, /schedule, or open the AI Bot tab for Gemini!",
                lastMessageSenderName = "Esosa Concierge Bot",
                lastMessageIsMine = false,
                lastMessageAtMillis = m7,
                createdAtMillis = m8,
                updatedAtMillis = m7,
                avatarKey = "esosa_bot",
                handle = "esosa_bot",
                isOnline = true,
                onlineStatusText = "bot"
            ),
            ChatRoomEntity(
                id = "dm_kaito_archived",
                title = "Kaito Tanaka",
                topic = "Tokyo Architecture Studio",
                chatKind = ChatKind.DIRECT.id,
                roomCode = "KAITO7",
                memberCount = 2,
                isPinned = false,
                unreadCount = 0,
                lastMessageText = "Let's catch up next quarter when you visit Tokyo!",
                lastMessageSenderName = "Kaito Tanaka",
                lastMessageIsMine = false,
                lastMessageAtMillis = m8,
                createdAtMillis = m8,
                updatedAtMillis = m8,
                avatarKey = "kaito",
                handle = "kaito_arch",
                isArchived = true,
                isMuted = true,
                isOnline = false,
                onlineStatusText = "last seen yesterday"
            )
        )
    }

    fun starterMessages(now: Long): List<RoomMessageEntity> {
        val me = LocalProfilePreferences.LOCAL_USER_ID
        val oneHour = 3_600_000L
        val oneDay = 24 * oneHour
        return listOf(
            // Saved Messages
            RoomMessageEntity(
                id = "seed_saved_1",
                roomId = ChatRepository.SAVED_MESSAGES_ROOM_ID,
                senderId = me,
                senderName = "You",
                text = "Key principles for Esosa: calm spacing, crisp typography, instant local search, zero clutter.",
                imageUrl = null,
                storagePath = null,
                messageType = "text",
                reactionsJson = ReactionsJsonCodec.encode(mapOf("🔥" to listOf(me))),
                isRead = true,
                createdAtMillis = now - oneDay
            ),
            RoomMessageEntity(
                id = "seed_saved_2",
                roomId = ChatRepository.SAVED_MESSAGES_ROOM_ID,
                senderId = me,
                senderName = "You",
                text = "Design tokens & v2.0 launch checklist",
                imageUrl = null,
                storagePath = null,
                messageType = "document",
                attachmentName = "Esosa_Launch_Checklist_v2.pdf",
                attachmentSizeLabel = "1.8 MB",
                reactionsJson = "{}",
                isRead = true,
                createdAtMillis = now - 3 * 60_000L
            ),

            // Maya Lin DM
            RoomMessageEntity(
                id = "seed_maya_1",
                roomId = "dm_maya_lin",
                senderId = "peer_maya",
                senderName = "Maya Lin",
                text = "Hey! Have you had a chance to review the updated messaging bubble radius?",
                imageUrl = null,
                storagePath = null,
                messageType = "text",
                reactionsJson = "{}",
                isRead = true,
                createdAtMillis = now - 2 * oneHour
            ),
            RoomMessageEntity(
                id = "seed_maya_2",
                roomId = "dm_maya_lin",
                senderId = me,
                senderName = "You",
                text = "Design review today at 3:30 PM PST — looks super crisp on both phone and tablet!",
                imageUrl = null,
                storagePath = null,
                messageType = "text",
                reactionsJson = ReactionsJsonCodec.encode(mapOf("❤️" to listOf("peer_maya"))),
                isRead = true,
                isPinned = true,
                createdAtMillis = now - 90 * 60_000L
            ),
            RoomMessageEntity(
                id = "seed_maya_3",
                roomId = "dm_maya_lin",
                senderId = "peer_maya",
                senderName = "Maya Lin",
                text = "Here is the color spec file if you want to test the Oceanic wallpaper preset.",
                imageUrl = null,
                storagePath = null,
                messageType = "document",
                attachmentName = "Esosa_Color_Tokens.fig",
                attachmentSizeLabel = "4.2 MB",
                replyToMessageId = "seed_maya_2",
                replyToSenderName = "You",
                replyToPreviewText = "Design review today at 3:30 PM PST",
                reactionsJson = "{}",
                isRead = true,
                createdAtMillis = now - 22 * 60_000L
            ),
            RoomMessageEntity(
                id = "seed_maya_4",
                roomId = "dm_maya_lin",
                senderId = "peer_maya",
                senderName = "Maya Lin",
                text = "Just uploaded the interactive prototype & dark mode palette!",
                imageUrl = null,
                storagePath = null,
                messageType = "text",
                reactionsJson = ReactionsJsonCodec.encode(mapOf("👏" to listOf(me, "peer_maya"))),
                isRead = false,
                createdAtMillis = now - 14 * 60_000L
            ),

            // Design Systems Group
            RoomMessageEntity(
                id = "seed_group_1",
                roomId = "group_design_systems",
                senderId = "peer_maya",
                senderName = "Maya Lin",
                text = "Sprint 14 Component Freeze: Friday 5 PM. Please drop any contrast issues here.",
                imageUrl = null,
                storagePath = null,
                messageType = "text",
                reactionsJson = ReactionsJsonCodec.encode(mapOf("👍" to listOf(me, "peer_liam", "peer_elena"))),
                isRead = true,
                isPinned = true,
                createdAtMillis = now - 4 * oneHour
            ),
            RoomMessageEntity(
                id = "seed_group_2",
                roomId = "group_design_systems",
                senderId = "peer_elena",
                senderName = "Elena Rostova",
                text = "Verified all 48dp touch targets across the chat composer and call controls.",
                imageUrl = null,
                storagePath = null,
                messageType = "text",
                replyToMessageId = "seed_group_1",
                replyToSenderName = "Maya Lin",
                replyToPreviewText = "Sprint 14 Component Freeze: Friday 5 PM",
                reactionsJson = "{}",
                isRead = true,
                createdAtMillis = now - 75 * 60_000L
            ),
            RoomMessageEntity(
                id = "seed_group_3",
                roomId = "group_design_systems",
                senderId = "peer_liam",
                senderName = "Liam O'Connor",
                text = "Motion curve tweaks for voice notes & incoming call transitions",
                imageUrl = null,
                storagePath = null,
                messageType = "voice",
                voiceDurationSec = 18,
                reactionsJson = ReactionsJsonCodec.encode(mapOf("🔥" to listOf("peer_maya"))),
                isRead = true,
                createdAtMillis = now - 42 * 60_000L
            ),

            // Liam O'Connor DM
            RoomMessageEntity(
                id = "seed_liam_1",
                roomId = "dm_liam_oconnor",
                senderId = me,
                senderName = "You",
                text = "Did the WebRTC / PCM audio jitter buffer pass stress testing?",
                imageUrl = null,
                storagePath = null,
                messageType = "text",
                reactionsJson = "{}",
                isRead = true,
                createdAtMillis = now - 3 * oneHour
            ),
            RoomMessageEntity(
                id = "seed_liam_2",
                roomId = "dm_liam_oconnor",
                senderId = "peer_liam",
                senderName = "Liam O'Connor",
                text = "Latency dropped below 28ms after switching codecs.",
                imageUrl = null,
                storagePath = null,
                messageType = "text",
                reactionsJson = ReactionsJsonCodec.encode(mapOf("🚀" to listOf(me))),
                isRead = true,
                createdAtMillis = now - 2 * oneHour
            ),

            // Esosa Product Insider Channel
            RoomMessageEntity(
                id = "seed_ch_esosa_1",
                roomId = "channel_esosa_insider",
                senderId = "channel_esosa",
                senderName = "Esosa Product Insider",
                text = "Privacy Architecture Note: Disappearing timers, two-step PIN verification, and active session controls give you granular control over every device.",
                imageUrl = null,
                storagePath = null,
                messageType = "text",
                reactionsJson = ReactionsJsonCodec.encode(mapOf("❤️" to listOf("u1", "u2"), "👍" to listOf("u3"))),
                isRead = true,
                viewCount = 1480,
                createdAtMillis = now - oneDay
            ),
            RoomMessageEntity(
                id = "seed_ch_esosa_2",
                roomId = "channel_esosa_insider",
                senderId = "channel_esosa",
                senderName = "Esosa Product Insider",
                text = "Introducing Esosa 2.0: Channels with live comments, Voice Notes, Video Calling, and adaptive 3-column desktop workspaces.",
                imageUrl = null,
                storagePath = null,
                messageType = "text",
                reactionsJson = ReactionsJsonCodec.encode(mapOf("🔥" to listOf(me, "u1"), "👏" to listOf("u2"))),
                isRead = true,
                viewCount = 2640,
                createdAtMillis = now - 5 * oneHour
            ),

            // Global Tech Digest Channel
            RoomMessageEntity(
                id = "seed_ch_tech_1",
                roomId = "channel_tech_digest",
                senderId = "channel_tech",
                senderName = "Global Tech Digest",
                text = "Zero-knowledge key rotation & end-to-end call encryption: how modern messengers verify ephemeral sessions without slowing down handshakes.",
                imageUrl = null,
                storagePath = null,
                messageType = "text",
                reactionsJson = ReactionsJsonCodec.encode(mapOf("😮" to listOf("u1"))),
                isRead = true,
                viewCount = 9120,
                createdAtMillis = now - 16 * oneHour
            ),

            // Concierge Bot
            RoomMessageEntity(
                id = "seed_bot_1",
                roomId = "bot_esosa_concierge",
                senderId = "bot_concierge",
                senderName = "Esosa Concierge Bot",
                text = "Welcome to Esosa! Need full Gemini AI reasoning or real-time Live Voice? Switch to the AI Bot or Voice tabs anytime, or tap a quick command here.",
                imageUrl = null,
                storagePath = null,
                messageType = "text",
                reactionsJson = "{}",
                isRead = true,
                createdAtMillis = now - 28 * oneHour
            ),

            // Kaito Archived DM
            RoomMessageEntity(
                id = "seed_kaito_1",
                roomId = "dm_kaito_archived",
                senderId = "peer_kaito",
                senderName = "Kaito Tanaka",
                text = "Let's catch up next quarter when you visit Tokyo!",
                imageUrl = null,
                storagePath = null,
                messageType = "text",
                reactionsJson = "{}",
                isRead = true,
                createdAtMillis = now - 48 * oneHour
            )
        )
    }

    fun starterStories(now: Long): List<StoryEntity> = listOf(
        StoryEntity(
            id = "story_maya",
            authorName = "Maya Lin",
            authorHandle = "@maya_lin",
            avatarKey = "maya",
            previewText = "Morning espresso + testing Esosa dark mode gradients in SF.",
            accentSeed = "maya",
            isViewed = false,
            isMine = false,
            createdAtMillis = now - 35 * 60_000L
        ),
        StoryEntity(
            id = "story_liam",
            authorName = "Liam O'Connor",
            authorHandle = "@liam_dev",
            avatarKey = "liam",
            previewText = "28ms real-time voice roundtrip on mobile data today!",
            accentSeed = "liam",
            isViewed = false,
            isMine = false,
            createdAtMillis = now - 2 * 3_600_000L
        ),
        StoryEntity(
            id = "story_esosa",
            authorName = "Esosa Insider",
            authorHandle = "@esosa_insider",
            avatarKey = "channel_banner",
            previewText = "Esosa 2.0 Preview: Scheduled messages & disappearing timers are live.",
            accentSeed = "esosa",
            isViewed = false,
            isMine = false,
            createdAtMillis = now - 4 * 3_600_000L
        ),
        StoryEntity(
            id = "story_elena",
            authorName = "Elena Rostova",
            authorHandle = "@elena_qa",
            avatarKey = "elena",
            previewText = "Zero failing UI checks across foldables and phones.",
            accentSeed = "elena",
            isViewed = true,
            isMine = false,
            createdAtMillis = now - 7 * 3_600_000L
        )
    )

    fun starterCallLogs(now: Long): List<CallLogEntity> = listOf(
        CallLogEntity(
            id = "call_1",
            peerRoomId = "dm_maya_lin",
            peerName = "Maya Lin",
            avatarKey = "maya",
            callType = CallType.VIDEO.id,
            direction = CallDirection.INCOMING.id,
            durationSeconds = 412,
            createdAtMillis = now - 55 * 60_000L
        ),
        CallLogEntity(
            id = "call_2",
            peerRoomId = "dm_liam_oconnor",
            peerName = "Liam O'Connor",
            avatarKey = "liam",
            callType = CallType.VOICE.id,
            direction = CallDirection.OUTGOING.id,
            durationSeconds = 185,
            createdAtMillis = now - 4 * 3_600_000L
        ),
        CallLogEntity(
            id = "call_3",
            peerRoomId = "group_design_systems",
            peerName = "Design Systems Team",
            avatarKey = "design_group",
            callType = CallType.VIDEO.id,
            direction = CallDirection.MISSED.id,
            durationSeconds = 0,
            createdAtMillis = now - 19 * 3_600_000L
        ),
        CallLogEntity(
            id = "call_4",
            peerRoomId = "dm_kaito_archived",
            peerName = "Kaito Tanaka",
            avatarKey = "kaito",
            callType = CallType.VOICE.id,
            direction = CallDirection.INCOMING.id,
            durationSeconds = 620,
            createdAtMillis = now - 46 * 3_600_000L
        )
    )

    fun starterChannelComments(now: Long): List<ChannelCommentEntity> = listOf(
        ChannelCommentEntity(
            id = "cmt_1",
            postId = "seed_ch_esosa_2",
            roomId = "channel_esosa_insider",
            authorName = "Maya Lin",
            text = "The 3-column desktop inspector makes switching channels effortless.",
            createdAtMillis = now - 4 * 3_600_000L
        ),
        ChannelCommentEntity(
            id = "cmt_2",
            postId = "seed_ch_esosa_2",
            roomId = "channel_esosa_insider",
            authorName = "Liam O'Connor",
            text = "Voice note playback waveforms feel super snappy!",
            createdAtMillis = now - 3 * 3_600_000L
        )
    )
}
