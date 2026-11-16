package com.example.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.ApiResult
import com.example.api.AppError
import com.example.models.ConversationSummary
import com.example.models.HomeFilter
import com.example.models.UserProfile
import com.example.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatListUiState(
    val isRefreshing: Boolean = false,
    val hasLoadedOnce: Boolean = false,
    val error: AppError? = null,
    val searchQuery: String = "",
    val filter: HomeFilter = HomeFilter.ALL
)

class ChatListViewModel(
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()

    val onlineContacts: StateFlow<List<UserProfile>> = chatRepository
        .observeOnlineContacts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val conversations: StateFlow<List<ConversationSummary>> = combine(
        chatRepository.observeConversations(),
        _uiState
    ) { all, state ->
        val query = state.searchQuery.trim().lowercase()
        all.filter { conv ->
            val matchesFilter = when (state.filter) {
                HomeFilter.ALL -> !conv.isArchived
                HomeFilter.UNREAD -> !conv.isArchived && conv.unreadCount > 0
                HomeFilter.ARCHIVED -> conv.isArchived
            }
            val matchesQuery = query.isEmpty() ||
                conv.peerDisplayName.lowercase().contains(query) ||
                conv.peerUsername.lowercase().contains(query) ||
                conv.lastMessage.lowercase().contains(query)
            matchesFilter && matchesQuery
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        refresh()
    }

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun onFilterChange(filter: HomeFilter) {
        _uiState.value = _uiState.value.copy(filter = filter)
    }

    fun togglePin(peerUsername: String, currentlyPinned: Boolean) {
        viewModelScope.launch {
            chatRepository.setPinned(peerUsername, !currentlyPinned)
        }
    }

    fun toggleMute(peerUsername: String, currentlyMuted: Boolean) {
        viewModelScope.launch {
            chatRepository.setMuted(peerUsername, !currentlyMuted)
        }
    }

    fun toggleArchive(peerUsername: String, currentlyArchived: Boolean) {
        viewModelScope.launch {
            chatRepository.setArchived(peerUsername, !currentlyArchived)
        }
    }

    fun clearLocalHistory(peerUsername: String) {
        viewModelScope.launch {
            chatRepository.clearLocalHistory(peerUsername)
        }
    }

    fun deleteConversationLocally(peerUsername: String) {
        viewModelScope.launch {
            chatRepository.deleteConversationLocally(peerUsername)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)
            when (val res = chatRepository.syncConversationList()) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isRefreshing = false,
                        hasLoadedOnce = true,
                        error = null
                    )
                }
                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isRefreshing = false,
                        hasLoadedOnce = true,
                        error = res.error
                    )
                }
            }
        }
    }
}
