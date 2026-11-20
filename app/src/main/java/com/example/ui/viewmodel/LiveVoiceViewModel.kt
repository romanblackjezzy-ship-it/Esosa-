package com.example.ui.viewmodel

import android.annotation.SuppressLint
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AudioTranscriptionConfig
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.liveAudioConversationConfig
import com.google.firebase.ai.type.liveGenerationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

enum class LiveVoiceStatus {
    IDLE,
    CONNECTING,
    ACTIVE,
    ERROR
}

data class LiveTranscriptItem(
    val id: Long,
    val speaker: String, // "You" or "Esosa Live"
    val text: String
)

@OptIn(PublicPreviewAPI::class)
class LiveVoiceViewModel : ViewModel() {

    private val liveModel by lazy {
        Firebase.ai.liveModel(
            modelName = LIVE_MODEL_NAME,
            generationConfig = liveGenerationConfig {
                responseModality = ResponseModality.AUDIO
                inputAudioTranscription = AudioTranscriptionConfig()
                outputAudioTranscription = AudioTranscriptionConfig()
            },
            systemInstruction = content {
                text(
                    "You are Esosa Live, a natural, warm, articulate real-time voice companion. " +
                        "Keep your spoken responses concise, conversational, and helpful."
                )
            }
        )
    }

    private var activeSession: LiveSession? = null
    private var entryCounter = 0L

    private val _status = MutableStateFlow(LiveVoiceStatus.IDLE)
    val status: StateFlow<LiveVoiceStatus> = _status.asStateFlow()

    private val _statusMessage = MutableStateFlow("Tap Start Voice to speak with gemini-3.8-live")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _transcripts = MutableStateFlow<List<LiveTranscriptItem>>(emptyList())
    val transcripts: StateFlow<List<LiveTranscriptItem>> = _transcripts.asStateFlow()

    private val _isModelSpeaking = MutableStateFlow(false)
    val isModelSpeaking: StateFlow<Boolean> = _isModelSpeaking.asStateFlow()

    @SuppressLint("MissingPermission")
    fun startConversation() {
        if (_status.value == LiveVoiceStatus.CONNECTING || _status.value == LiveVoiceStatus.ACTIVE) {
            return
        }
        viewModelScope.launch {
            _status.value = LiveVoiceStatus.CONNECTING
            _statusMessage.value = "Connecting to $LIVE_MODEL_NAME…"
            _isModelSpeaking.value = false

            try {
                val session = withTimeout(30_000L) {
                    liveModel.connect()
                }
                activeSession = session

                val audioConfig = liveAudioConversationConfig {
                    enableInterruptions = true
                    transcriptHandler = { inputTranscription, outputTranscription ->
                        val userText = inputTranscription?.text?.trim()
                        val modelText = outputTranscription?.text?.trim()

                        if (!userText.isNullOrEmpty()) {
                            _isModelSpeaking.value = false
                            appendTranscriptChunk("You", userText)
                        }
                        if (!modelText.isNullOrEmpty()) {
                            _isModelSpeaking.value = true
                            appendTranscriptChunk("Esosa Live", modelText)
                        }
                    }
                    goAwayHandler = {
                        viewModelScope.launch {
                            _statusMessage.value = "Live session wrapped up by server."
                            stopConversation()
                        }
                    }
                }

                _status.value = LiveVoiceStatus.ACTIVE
                _statusMessage.value = "Listening live • Speak naturally"
                session.startAudioConversation(audioConfig)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Live session connection timed out", e)
                cleanupSession()
                _status.value = LiveVoiceStatus.ERROR
                _statusMessage.value = "Connection timed out after 30s. Check network and retry."
            } catch (e: CancellationException) {
                cleanupSession()
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start Gemini Live conversation", e)
                cleanupSession()
                _status.value = LiveVoiceStatus.ERROR
                _statusMessage.value = e.localizedMessage ?: "Live voice session failed."
            }
        }
    }

    fun sendTextDuringLive(rawText: String) {
        val text = rawText.trim()
        val session = activeSession ?: return
        if (text.isEmpty() || _status.value != LiveVoiceStatus.ACTIVE) return

        viewModelScope.launch {
            try {
                appendTranscriptChunk("You", text)
                session.send(text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed sending text to live session", e)
                _statusMessage.value = e.localizedMessage ?: "Could not send message to live session."
            }
        }
    }

    fun stopConversation() {
        viewModelScope.launch {
            cleanupSession()
            _status.value = LiveVoiceStatus.IDLE
            _isModelSpeaking.value = false
            _statusMessage.value = "Session ended • Tap Start Voice to reconnect"
        }
    }

    fun clearTranscripts() {
        _transcripts.value = emptyList()
    }

    private fun appendTranscriptChunk(speaker: String, chunk: String) {
        val current = _transcripts.value.toMutableList()
        val last = current.lastOrNull()
        if (last != null && last.speaker == speaker) {
            val separator = if (last.text.endsWith(" ") || chunk.startsWith(" ")) "" else " "
            current[current.lastIndex] = last.copy(text = last.text + separator + chunk)
        } else {
            entryCounter += 1
            current.add(LiveTranscriptItem(id = entryCounter, speaker = speaker, text = chunk))
        }
        _transcripts.value = current
    }

    private suspend fun cleanupSession() {
        withContext(NonCancellable) {
            val session = activeSession
            activeSession = null
            if (session != null) {
                try {
                    session.stopAudioConversation()
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping audio conversation: ${e.message}")
                }
                try {
                    session.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing live session: ${e.message}")
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        val session = activeSession
        activeSession = null
        try {
            session?.stopAudioConversation()
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val TAG = "LiveVoiceVM"
        const val LIVE_MODEL_NAME = "gemini-3.8-live"
    }
}
