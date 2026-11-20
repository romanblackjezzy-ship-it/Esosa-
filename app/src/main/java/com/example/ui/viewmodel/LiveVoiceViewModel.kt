package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.audio.VoiceAudioStreamer
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AudioTranscriptionConfig
import com.google.firebase.ai.type.InlineData
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.LiveServerContent
import com.google.firebase.ai.type.LiveServerGoAway
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.liveGenerationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
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
class LiveVoiceViewModel(
    private val audioStreamer: VoiceAudioStreamer
) : ViewModel() {

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
    private var captureJob: Job? = null
    private var receiveJob: Job? = null
    private var playbackJob: Job? = null
    private var playbackChannel: Channel<ByteArray>? = null
    private var entryCounter = 0L

    private val _status = MutableStateFlow(LiveVoiceStatus.IDLE)
    val status: StateFlow<LiveVoiceStatus> = _status.asStateFlow()

    private val _statusMessage = MutableStateFlow("Tap Start Live Conversation to speak with gemini-3.8-live")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _transcripts = MutableStateFlow<List<LiveTranscriptItem>>(emptyList())
    val transcripts: StateFlow<List<LiveTranscriptItem>> = _transcripts.asStateFlow()

    private val _isModelSpeaking = MutableStateFlow(false)
    val isModelSpeaking: StateFlow<Boolean> = _isModelSpeaking.asStateFlow()

    val isMuted: StateFlow<Boolean> = audioStreamer.isMuted
    val isSpeakerOn: StateFlow<Boolean> = audioStreamer.isSpeakerOn
    val micLevel: StateFlow<Float> = audioStreamer.micLevel
    val isAecEnabled: StateFlow<Boolean> = audioStreamer.isAecEnabled

    fun startConversation() {
        if (_status.value == LiveVoiceStatus.CONNECTING || _status.value == LiveVoiceStatus.ACTIVE) {
            return
        }
        if (!audioStreamer.hasRecordPermission()) {
            _status.value = LiveVoiceStatus.ERROR
            _statusMessage.value = "Microphone permission is required to stream live voice."
            return
        }

        viewModelScope.launch {
            _status.value = LiveVoiceStatus.CONNECTING
            _statusMessage.value = "Connecting to $LIVE_MODEL_NAME…"
            _isModelSpeaking.value = false
            audioStreamer.setMuted(false)

            try {
                audioStreamer.prepareAudioSession()

                val session = withTimeout(30_000L) {
                    liveModel.connect()
                }
                activeSession = session

                val queue = Channel<ByteArray>(Channel.UNLIMITED)
                playbackChannel = queue

                playbackJob = launch {
                    for (audioChunk in queue) {
                        audioStreamer.enqueuePlayback(audioChunk)
                    }
                }

                receiveJob = launch {
                    collectLiveServerResponses(session, queue)
                }

                captureJob = launch {
                    streamMicrophoneToLiveSession(session)
                }

                _status.value = LiveVoiceStatus.ACTIVE
                _statusMessage.value = "Listening live • 16kHz PCM stream active"
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

    private suspend fun streamMicrophoneToLiveSession(session: LiveSession) {
        try {
            audioStreamer.capturedAudioFlow().collect { pcmFrame ->
                session.sendAudioRealtime(
                    InlineData(
                        data = pcmFrame,
                        mimeType = VoiceAudioStreamer.INPUT_MIME_TYPE
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Microphone capture/streaming error", e)
            _statusMessage.value = e.localizedMessage ?: "Microphone stream interrupted."
            cleanupSession()
            _status.value = LiveVoiceStatus.ERROR
        }
    }

    private suspend fun collectLiveServerResponses(
        session: LiveSession,
        queue: Channel<ByteArray>
    ) {
        try {
            session.receive().collect { message ->
                when (message) {
                    is LiveServerContent -> {
                        if (message.interrupted) {
                            drainPlaybackQueue(queue)
                            audioStreamer.flushPlayback()
                            _isModelSpeaking.value = false
                            _statusMessage.value = "Listening live • Interrupted"
                        } else {
                            val parts = message.content?.parts.orEmpty()
                            parts.filterIsInstance<InlineDataPart>()
                                .filter { it.mimeType.startsWith("audio/pcm", ignoreCase = true) }
                                .forEach { audioPart ->
                                    if (audioPart.inlineData.isNotEmpty()) {
                                        _isModelSpeaking.value = true
                                        queue.trySend(audioPart.inlineData)
                                    }
                                }

                            if (message.turnComplete || message.generationComplete) {
                                _isModelSpeaking.value = false
                                _statusMessage.value = if (isMuted.value) {
                                    "Microphone muted • Tap unmute to speak"
                                } else {
                                    "Listening live • 16kHz PCM stream active"
                                }
                            }
                        }

                        val userText = message.inputTranscription?.text?.trim()
                        val modelText = message.outputTranscription?.text?.trim()
                        if (!userText.isNullOrEmpty()) {
                            _isModelSpeaking.value = false
                            appendTranscriptChunk("You", userText)
                        }
                        if (!modelText.isNullOrEmpty()) {
                            appendTranscriptChunk("Esosa Live", modelText)
                        }
                    }

                    is LiveServerGoAway -> {
                        _statusMessage.value = "Live session wrapped up by server."
                        stopConversation()
                    }

                    else -> Unit
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error receiving live audio response", e)
            if (_status.value == LiveVoiceStatus.ACTIVE) {
                cleanupSession()
                _status.value = LiveVoiceStatus.ERROR
                _statusMessage.value = e.localizedMessage ?: "Lost connection to live voice session."
            }
        }
    }

    private fun drainPlaybackQueue(queue: Channel<ByteArray>) {
        while (queue.tryReceive().isSuccess) {
            // Drain stale buffered audio chunks on barge-in interruption
        }
    }

    fun toggleMute() {
        val nowMuted = audioStreamer.toggleMute()
        if (_status.value == LiveVoiceStatus.ACTIVE) {
            _statusMessage.value = if (nowMuted) {
                "Microphone muted • Tap unmute to speak"
            } else {
                "Listening live • 16kHz PCM stream active"
            }
        }
    }

    fun toggleSpeakerphone() {
        audioStreamer.toggleSpeakerphone()
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
            _statusMessage.value = "Session ended • Tap Start Live Conversation to reconnect"
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
            captureJob?.cancel()
            captureJob = null

            receiveJob?.cancel()
            receiveJob = null

            playbackChannel?.close()
            playbackChannel = null

            playbackJob?.cancel()
            playbackJob = null

            val session = activeSession
            activeSession = null
            if (session != null) {
                try {
                    session.stopReceiving()
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping live receiver: ${e.message}")
                }
                try {
                    session.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing live session: ${e.message}")
                }
            }
            audioStreamer.stopAndRelease()
        }
    }

    override fun onCleared() {
        super.onCleared()
        captureJob?.cancel()
        receiveJob?.cancel()
        playbackChannel?.close()
        playbackJob?.cancel()
        val session = activeSession
        activeSession = null
        try {
            session?.stopReceiving()
        } catch (_: Exception) {
        }
        audioStreamer.stopAndRelease()
    }

    companion object {
        private const val TAG = "LiveVoiceVM"
        const val LIVE_MODEL_NAME = "gemini-3.8-live"
    }
}
