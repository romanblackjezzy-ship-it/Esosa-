package com.example.data.repository

import android.content.Context
import com.example.data.local.EsosaDao
import com.example.data.local.EsosaDatabase
import com.example.data.local.LiveVoiceMessageEntity
import com.example.data.local.LiveVoiceThreadEntity
import com.example.data.model.LiveVoiceMessage
import com.example.data.model.LiveVoiceThread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class LiveVoiceRepository(
    appContext: Context,
    private val dao: EsosaDao = EsosaDatabase.getInstance(appContext).esosaDao()
) {
    fun observeThreads(): Flow<List<LiveVoiceThread>> {
        return dao.observeLiveThreads().map { list -> list.map { it.toDomain() } }
    }

    fun observeThreadMessages(threadId: String): Flow<List<LiveVoiceMessage>> {
        return dao.observeLiveMessages(threadId).map { list -> list.map { it.toDomain() } }
    }

    suspend fun createThread(
        title: String = "Live Voice Session",
        modelName: String = "gemini-3.8-live"
    ): Result<LiveVoiceThread> {
        return try {
            val now = System.currentTimeMillis()
            val id = "live_${now}_${UUID.randomUUID().toString().take(8)}"
            val cleanTitle = title.trim().ifBlank { "Live Voice Session" }.take(100)
            val cleanModel = modelName.trim().ifBlank { "gemini-3.8-live" }.take(80)
            val entity = LiveVoiceThreadEntity(
                id = id,
                title = cleanTitle,
                modelName = cleanModel,
                lastTranscriptPreview = "Ready • $cleanModel",
                createdAtMillis = now,
                updatedAtMillis = now
            )
            dao.upsertLiveThread(entity)
            Result.success(entity.toDomain())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun persistMessage(
        threadId: String,
        speaker: String,
        text: String,
        updateTitleIfDefault: Boolean = false
    ): Result<Unit> {
        return try {
            val normalizedSpeaker = if (speaker == "You") "You" else "Esosa Live"
            val cleanText = text.trim().take(10000)
            require(cleanText.isNotEmpty()) { "Transcript text cannot be empty" }
            val now = System.currentTimeMillis()
            val msgId = "lmsg_${now}_${UUID.randomUUID().toString().take(8)}"

            dao.upsertLiveMessage(
                LiveVoiceMessageEntity(
                    id = msgId,
                    threadId = threadId,
                    speaker = normalizedSpeaker,
                    text = cleanText,
                    createdAtMillis = now
                )
            )
            val existing = dao.getLiveThreadById(threadId)
            val nextTitle = if (updateTitleIfDefault && normalizedSpeaker == "You") {
                cleanText.take(48)
            } else {
                existing?.title ?: "Voice Session"
            }
            dao.updateLiveThreadSummary(
                threadId = threadId,
                title = nextTitle,
                preview = "$normalizedSpeaker: ${cleanText.take(250)}",
                updatedAt = now
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteThread(threadId: String): Result<Unit> {
        return try {
            dao.deleteLiveThreadById(threadId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
