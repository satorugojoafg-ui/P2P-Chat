package com.example.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.ApiConfig
import com.example.api.ApiResult
import com.example.api.AppError
import com.example.models.ChatMessage
import com.example.models.MediaTransferItem
import com.example.models.UserProfile
import com.example.models.WebRtcConnectionState
import com.example.repository.ChatRepository
import com.example.repository.UserRepository
import com.example.webrtc.WebRtcManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PrivateChatUiState(
    val peerProfile: UserProfile? = null,
    val isLoadingInitial: Boolean = true,
    val isSendingText: Boolean = false,
    val error: AppError? = null,
    val mediaLimitError: Boolean = false,
    val mediaReadError: Boolean = false,
    val draftText: String = "",
    val replyingTo: ChatMessage? = null,
    val selectedMessageKeys: Set<String> = emptySet(),
    val isSearchingMessages: Boolean = false,
    val searchQuery: String = ""
)

class PrivateChatViewModel(
    private val peerUsername: String,
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val webRtcManager: WebRtcManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrivateChatUiState())
    val uiState: StateFlow<PrivateChatUiState> = _uiState.asStateFlow()

    val messages: StateFlow<List<ChatMessage>> = combine(
        chatRepository.observeMessages(peerUsername),
        _uiState
    ) { all, state ->
        val q = state.searchQuery.trim().lowercase()
        if (state.isSearchingMessages && q.isNotEmpty()) {
            all.filter { it.content.lowercase().contains(q) }
        } else {
            all
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val p2pState: StateFlow<WebRtcConnectionState> = webRtcManager.connectionStates
        .map { it[peerUsername] ?: WebRtcConnectionState.IDLE }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = WebRtcConnectionState.IDLE
        )

    val isPeerTyping: StateFlow<Boolean> = webRtcManager.typingPeers
        .map { it.contains(peerUsername) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val transfers: StateFlow<List<MediaTransferItem>> = webRtcManager.transfers
        .map { it[peerUsername].orEmpty() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _completedTransferAlerts = MutableSharedFlow<MediaTransferItem>(extraBufferCapacity = 4)
    val completedTransferAlerts: SharedFlow<MediaTransferItem> = _completedTransferAlerts.asSharedFlow()

    private var pollingJob: Job? = null
    private var draftSaveJob: Job? = null
    private var typingStopJob: Job? = null

    init {
        loadPeerAndInitialHistory()
        startPolling()
        viewModelScope.launch {
            webRtcManager.completedTransfers
                .filter { it.peerUsername == peerUsername }
                .collect { _completedTransferAlerts.tryEmit(it) }
        }
    }

    private fun loadPeerAndInitialHistory() {
        viewModelScope.launch {
            val savedDraft = chatRepository.getDraft(peerUsername)
            if (savedDraft.isNotBlank()) {
                _uiState.value = _uiState.value.copy(draftText = savedDraft)
            }
            val cached = chatRepository.getCachedUser(peerUsername)
            if (cached != null) {
                _uiState.value = _uiState.value.copy(peerProfile = cached)
            }
            when (val userRes = userRepository.getUser(peerUsername)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(peerProfile = userRes.data)
                }
                is ApiResult.Failure -> Unit
            }
            when (val msgRes = chatRepository.syncMessages(peerUsername, fullSync = true)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(isLoadingInitial = false, error = null)
                }
                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isLoadingInitial = false,
                        error = msgRes.error
                    )
                }
            }
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(ApiConfig.CHAT_POLL_INTERVAL_MS)
                chatRepository.syncMessages(peerUsername, fullSync = false)
                when (val peerRes = userRepository.getUser(peerUsername)) {
                    is ApiResult.Success -> {
                        _uiState.value = _uiState.value.copy(peerProfile = peerRes.data)
                    }
                    is ApiResult.Failure -> Unit
                }
            }
        }
    }

    fun onDraftChange(newText: String) {
        _uiState.value = _uiState.value.copy(
            draftText = newText,
            error = null,
            mediaLimitError = false,
            mediaReadError = false
        )
        draftSaveJob?.cancel()
        draftSaveJob = viewModelScope.launch {
            delay(250L)
            chatRepository.saveDraft(peerUsername, newText)
        }

        if (newText.isNotBlank()) {
            webRtcManager.sendTypingIndicator(peerUsername, true)
            typingStopJob?.cancel()
            typingStopJob = viewModelScope.launch {
                delay(2200L)
                webRtcManager.sendTypingIndicator(peerUsername, false)
            }
        } else {
            typingStopJob?.cancel()
            webRtcManager.sendTypingIndicator(peerUsername, false)
        }
    }

    fun sendMessage() {
        val clean = _uiState.value.draftText.trim()
        if (clean.isEmpty() || _uiState.value.isSendingText) return
        val replyTarget = _uiState.value.replyingTo
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSendingText = true, error = null)
            webRtcManager.sendTypingIndicator(peerUsername, false)
            when (val res = chatRepository.sendTextMessage(peerUsername, clean, replyTarget)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isSendingText = false,
                        draftText = "",
                        replyingTo = null,
                        error = null
                    )
                }
                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isSendingText = false,
                        error = res.error
                    )
                }
            }
        }
    }

    fun setReplyTarget(message: ChatMessage?) {
        _uiState.value = _uiState.value.copy(replyingTo = message, selectedMessageKeys = emptySet())
    }

    fun toggleMessageSelection(localKey: String) {
        val current = _uiState.value.selectedMessageKeys
        val updated = if (current.contains(localKey)) current - localKey else current + localKey
        _uiState.value = _uiState.value.copy(selectedMessageKeys = updated)
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedMessageKeys = emptySet())
    }

    fun deleteSelectedLocally() {
        val keys = _uiState.value.selectedMessageKeys.toList()
        if (keys.isEmpty()) return
        viewModelScope.launch {
            keys.forEach { chatRepository.deleteMessageLocally(it) }
            _uiState.value = _uiState.value.copy(selectedMessageKeys = emptySet())
        }
    }

    fun deleteMessageLocally(localKey: String) {
        viewModelScope.launch {
            chatRepository.deleteMessageLocally(localKey)
        }
    }

    fun toggleInChatSearch() {
        val next = !_uiState.value.isSearchingMessages
        _uiState.value = _uiState.value.copy(
            isSearchingMessages = next,
            searchQuery = if (next) _uiState.value.searchQuery else ""
        )
    }

    fun onInChatSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun establishP2PLink() {
        webRtcManager.connectToPeer(peerUsername)
    }

    fun sendP2PMedia(uri: Uri) {
        _uiState.value = _uiState.value.copy(mediaLimitError = false, mediaReadError = false)
        webRtcManager.sendMedia(
            peerUsername = peerUsername,
            uri = uri,
            onFileTooLarge = {
                _uiState.value = _uiState.value.copy(mediaLimitError = true)
            },
            onReadFailed = {
                _uiState.value = _uiState.value.copy(mediaReadError = true)
            }
        )
    }

    fun cancelTransfer(transferId: String) {
        webRtcManager.cancelTransfer(peerUsername, transferId)
    }

    fun retryTransfer(transferId: String) {
        webRtcManager.retryTransfer(peerUsername, transferId)
    }

    fun clearErrors() {
        _uiState.value = _uiState.value.copy(error = null, mediaLimitError = false, mediaReadError = false)
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
        webRtcManager.sendTypingIndicator(peerUsername, false)
    }
}
