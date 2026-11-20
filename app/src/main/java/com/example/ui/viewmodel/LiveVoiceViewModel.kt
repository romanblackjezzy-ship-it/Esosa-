package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.ThreadSelectionPreferences
import com.example.core.UiState
import com.example.data.audio.VoiceAudioStreamer
import com.example.data.model.LiveVoiceMessage
import com.example.data.model.LiveVoiceThread
import com.example.data.repository.LiveVoiceRepository
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
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
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
    val id: String,
    val speaker: String, // "You" or "Esosa Live"
    val text: String,
    val isStreaming: Boolean = false
)

@OptIn(PublicPreviewAPI::class, ExperimentalCoroutinesApi::class)
class LiveVoiceViewModel(
    private val audioStreamer: VoiceAudioStreamer,
    private val repository: LiveVoiceRepository? = null,
    private val currentUserId: String = "",
    private val selectionPreferences: ThreadSelectionPreferences? = null
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

    private val _selectedThreadId = MutableStateFlow(
        if (currentUserId.isNotBlank()) selectionPreferences?.getLastLiveThreadId(currentUserId) else null
    )
    val selectedThreadId: StateFlow<String?> = _selectedThreadId.asStateFlow()

    private val _status = MutableStateFlow(LiveVoiceStatus.IDLE)
    val status: StateFlow<LiveVoiceStatus> = _status.asStateFlow()

    private val _statusMessage = MutableStateFlow("Tap Start Live Conversation to speak with gemini-3.8-live")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _inProgressTurn = MutableStateFlow<LiveTranscriptItem?>(null)
    private val _isModelSpeaking = MutableStateFlow(false)
    val isModelSpeaking: StateFlow<Boolean> = _isModelSpeaking.asStateFlow()

    val isMuted: StateFlow<Boolean> = audioStreamer.isMuted
    val isSpeakerOn: StateFlow<Boolean> = audioStreamer.isSpeakerOn
    val micLevel: StateFlow<Float> = audioStreamer.micLevel
    val isAecEnabled: StateFlow<Boolean> = audioStreamer.isAecEnabled

    val liveThreadsState: StateFlow<UiState<List<LiveVoiceThread>>> =
        if (repository == null || currentUserId.isBlank()) {
            MutableStateFlow(UiState.Success(emptyList()))
        } else {
            repository.observeThreads(currentUserId)
                .onEach { threads ->
                    val currentId = _selectedThreadId.value
                    if (currentId == null && threads.isNotEmpty()) {
                        setSelectedThread(threads.first().id)
                    } else if (currentId != null && threads.none { it.id == currentId }) {
                        setSelectedThread(threads.firstOrNull()?.id)
                    }
                }
                .map<List<LiveVoiceThread>, UiState<List<LiveVoiceThread>>> { UiState.Success(it) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    Log.w(TAG, "Error observing live voice threads", rootError)
                    emit(UiState.Error(rootError.localizedMessage ?: "Failed to load voice sessions"))
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.Eagerly,
                    initialValue = UiState.Loading
                )
        }

    private val persistedMessagesFlow: StateFlow<List<LiveVoiceMessage>> =
        if (repository == null || currentUserId.isBlank()) {
            MutableStateFlow(emptyList())
        } else {
            _selectedThreadId
                .flatMapLatest { threadId ->
                    if (threadId.isNullOrBlank()) {
                        flowOf(emptyList())
                    } else {
                        repository.observeThreadMessages(currentUserId, threadId)
                            .catch { error ->
                                val firestoreError = generateSequence(error) { it.cause }
                                    .filterIsInstance<FirebaseFirestoreException>()
                                    .firstOrNull()
                                if (firestoreError == null && error is CancellationException) throw error
                                Log.w(TAG, "Error observing live transcript history", firestoreError ?: error)
                                emit(emptyList())
                            }
                    }
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.Eagerly,
                    initialValue = emptyList()
                )
        }

    val transcripts: StateFlow<List<LiveTranscriptItem>> =
        combine(persistedMessagesFlow, _inProgressTurn) { persisted, activeTurn ->
            val items = persisted.map { msg ->
                LiveTranscriptItem(
                    id = msg.id,
                    speaker = msg.speaker,
                    text = msg.text,
                    isStreaming = false
                )
            }
            if (activeTurn != null && activeTurn.text.isNotBlank()) {
                items + activeTurn
            } else {
                items
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    private fun setSelectedThread(threadId: String?) {
        _selectedThreadId.value = threadId
        if (currentUserId.isNotBlank()) {
            selectionPreferences?.setLastLiveThreadId(currentUserId, threadId)
        }
    }

    fun selectThread(threadId: String) {
        if (threadId == _selectedThreadId.value) return
        viewModelScope.launch {
            flushInProgressTurn()
            setSelectedThread(threadId)
        }
    }

    fun startNewThread() {
        viewModelScope.launch {
            flushInProgressTurn()
            val repo = repository ?: return@launch
            repo.createThread(title = "Voice Session")
                .onSuccess { created -> setSelectedThread(created.id) }
        }
    }

    fun deleteThread(threadId: String) {
        viewModelScope.launch {
            repository?.deleteThread(threadId)
        }
    }

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
                ensureActiveThreadId()
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

    private suspend fun ensureActiveThreadId(): String? {
        val existingId = _selectedThreadId.value
        if (!existingId.isNullOrBlank()) return existingId
        val repo = repository ?: return null
        val created = repo.createThread(title = "Voice Session").getOrNull() ?: return null
        setSelectedThread(created.id)
        return created.id
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
                            flushInProgressTurn()
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

                            val userText = message.inputTranscription?.text?.trim()
                            val modelText = message.outputTranscription?.text?.trim()
                            if (!userText.isNullOrEmpty()) {
                                _isModelSpeaking.value = false
                                appendStreamingTranscriptChunk("You", userText)
                            }
                            if (!modelText.isNullOrEmpty()) {
                                appendStreamingTranscriptChunk("Esosa Live", modelText)
                            }

                            if (message.turnComplete || message.generationComplete) {
                                _isModelSpeaking.value = false
                                flushInProgressTurn()
                                _statusMessage.value = if (isMuted.value) {
                                    "Microphone muted • Tap unmute to speak"
                                } else {
                                    "Listening live • 16kHz PCM stream active"
                                }
                            }
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

    private suspend fun appendStreamingTranscriptChunk(speaker: String, chunk: String) {
        val current = _inProgressTurn.value
        if (current != null && current.speaker != speaker) {
            flushInProgressTurn()
        }
        val latest = _inProgressTurn.value
        _inProgressTurn.value = if (latest == null) {
            LiveTranscriptItem(
                id = "live_streaming_${speaker}",
                speaker = speaker,
                text = chunk,
                isStreaming = true
            )
        } else {
            val separator = if (latest.text.endsWith(" ") || chunk.startsWith(" ")) "" else " "
            latest.copy(text = latest.text + separator + chunk)
        }
    }

    private suspend fun flushInProgressTurn() {
        val turn = _inProgressTurn.value ?: return
        _inProgressTurn.value = null
        val cleanText = turn.text.trim()
        if (cleanText.isEmpty()) return
        val threadId = ensureActiveThreadId() ?: return
        val isFirstUserTurn = persistedMessagesFlow.value.isEmpty() && turn.speaker == "You"
        repository?.persistMessage(
            threadId = threadId,
            speaker = turn.speaker,
            text = cleanText,
            updateTitleIfDefault = isFirstUserTurn
        )
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
                flushInProgressTurn()
                val threadId = ensureActiveThreadId()
                if (threadId != null) {
                    val isFirstUserTurn = persistedMessagesFlow.value.isEmpty()
                    repository?.persistMessage(
                        threadId = threadId,
                        speaker = "You",
                        text = text,
                        updateTitleIfDefault = isFirstUserTurn
                    )
                }
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
            _statusMessage.value = "Session ended • Transcripts saved automatically"
        }
    }

    fun clearTranscripts() {
        viewModelScope.launch {
            _inProgressTurn.value = null
            val currentId = _selectedThreadId.value
            if (currentId != null && repository != null) {
                repository.deleteThread(currentId)
            }
            setSelectedThread(null)
        }
    }

    private suspend fun cleanupSession() {
        withContext(NonCancellable) {
            flushInProgressTurn()

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
