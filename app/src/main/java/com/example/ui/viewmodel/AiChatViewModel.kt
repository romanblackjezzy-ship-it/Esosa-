package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.ThreadSelectionPreferences
import com.example.core.UiState
import com.example.data.model.AiMessage
import com.example.data.model.AiPersona
import com.example.data.model.AiThread
import com.example.data.repository.AiChatRepository
import com.example.notifications.EsosaNotificationManager
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AiChatViewModel(
    private val repository: AiChatRepository,
    private val notificationManager: EsosaNotificationManager,
    private val currentUserId: String,
    private val selectionPreferences: ThreadSelectionPreferences? = null
) : ViewModel() {

    private val _selectedThreadId = MutableStateFlow(selectionPreferences?.getLastAiThreadId(currentUserId))
    val selectedThreadId: StateFlow<String?> = _selectedThreadId.asStateFlow()

    private val _streamingReply = MutableStateFlow<String?>(null)
    val streamingReply: StateFlow<String?> = _streamingReply.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _errorBanner = MutableStateFlow<String?>(null)
    val errorBanner: StateFlow<String?> = _errorBanner.asStateFlow()

    private var isOnAiTab = false
    private var notificationsEnabled = true

    val threadsState: StateFlow<UiState<List<AiThread>>> = repository.observeThreads(currentUserId)
        .onEach { threads ->
            val currentId = _selectedThreadId.value
            if (currentId == null && threads.isNotEmpty()) {
                setSelectedThread(threads.first().id)
            } else if (currentId != null && threads.none { it.id == currentId }) {
                setSelectedThread(threads.firstOrNull()?.id)
            }
        }
        .map<List<AiThread>, UiState<List<AiThread>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing AI threads", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "Failed to load AI threads"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = UiState.Loading
        )

    val messagesState: StateFlow<UiState<List<AiMessage>>> = _selectedThreadId
        .flatMapLatest { threadId ->
            if (threadId.isNullOrBlank()) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeThreadMessages(currentUserId, threadId)
                    .map<List<AiMessage>, UiState<List<AiMessage>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        Log.w(TAG, "Error observing AI messages", rootError)
                        emit(UiState.Error(rootError.localizedMessage ?: "Failed to load AI history"))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = UiState.Loading
        )

    private fun setSelectedThread(threadId: String?) {
        _selectedThreadId.value = threadId
        selectionPreferences?.setLastAiThreadId(currentUserId, threadId)
    }

    fun setTabActive(active: Boolean) {
        isOnAiTab = active
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        notificationsEnabled = enabled
    }

    fun selectThread(threadId: String) {
        setSelectedThread(threadId)
        _errorBanner.value = null
    }

    fun clearError() {
        _errorBanner.value = null
    }

    fun createThread(persona: AiPersona, customTitle: String? = null) {
        viewModelScope.launch {
            _errorBanner.value = null
            repository.createThread(persona, customTitle)
                .onSuccess { created ->
                    setSelectedThread(created.id)
                }
                .onFailure { err ->
                    _errorBanner.value = err.localizedMessage ?: "Could not start new Gemini chat."
                }
        }
    }

    fun switchPersona(persona: AiPersona) {
        val currentId = _selectedThreadId.value
        if (currentId.isNullOrBlank()) {
            createThread(persona)
            return
        }
        viewModelScope.launch {
            _errorBanner.value = null
            repository.switchThreadPersona(currentId, persona)
                .onFailure { err ->
                    _errorBanner.value = err.localizedMessage ?: "Could not switch Gemini persona."
                }
        }
    }

    fun deleteThread(threadId: String) {
        viewModelScope.launch {
            repository.deleteThread(threadId)
                .onFailure { err ->
                    _errorBanner.value = err.localizedMessage ?: "Failed to delete AI thread."
                }
        }
    }

    fun sendPrompt(rawPrompt: String, activeThread: AiThread?, currentMessages: List<AiMessage>) {
        val prompt = rawPrompt.trim()
        if (prompt.isEmpty() || _isGenerating.value) return

        viewModelScope.launch {
            _errorBanner.value = null
            _isGenerating.value = true
            _streamingReply.value = ""

            try {
                val targetThread = activeThread ?: run {
                    val createdResult = repository.createThread(AiPersona.GENERAL)
                    createdResult.getOrThrow().also { setSelectedThread(it.id) }
                }

                val isFirstTurn = currentMessages.isEmpty()
                repository.persistAiTurnMessage(
                    threadId = targetThread.id,
                    role = "user",
                    text = prompt,
                    modelName = targetThread.modelName,
                    updateTitleIfDefault = isFirstTurn
                ).getOrThrow()

                val finalReply = StringBuilder()
                repository.streamGeminiReply(
                    thread = targetThread,
                    priorMessages = currentMessages,
                    userPrompt = prompt,
                    onChunk = { accumulated ->
                        _streamingReply.value = accumulated
                        finalReply.clear()
                        finalReply.append(accumulated)
                    }
                ).collect { /* updates emitted via onChunk */ }

                val replyText = finalReply.toString().trim()
                if (replyText.isNotEmpty()) {
                    repository.persistAiTurnMessage(
                        threadId = targetThread.id,
                        role = "model",
                        text = replyText,
                        modelName = targetThread.modelName
                    ).getOrThrow()

                    if (!isOnAiTab && notificationsEnabled) {
                        notificationManager.showAiReplyNotification(
                            threadTitle = targetThread.title,
                            preview = replyText.take(180)
                        )
                    }
                } else {
                    _errorBanner.value = "Gemini returned an empty response. Please try again."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini chat error", e)
                _errorBanner.value = e.localizedMessage ?: "Gemini request failed."
            } finally {
                _streamingReply.value = null
                _isGenerating.value = false
            }
        }
    }

    private companion object {
        const val TAG = "AiChatVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
