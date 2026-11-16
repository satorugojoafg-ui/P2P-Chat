package com.example.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.models.ChatWallpaper
import com.example.models.FontSizeOption
import com.example.models.StorageBreakdown
import com.example.models.ThemeMode
import com.example.models.UiLanguage
import com.example.models.UserSettings
import com.example.models.VisibilityScope
import com.example.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<UserSettings> = settingsRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = UserSettings()
    )

    val language: StateFlow<UiLanguage> = settingsRepository.settingsFlow
        .map { it.language }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = UiLanguage.PERSIAN
        )

    private val _storageBreakdown = MutableStateFlow(StorageBreakdown())
    val storageBreakdown: StateFlow<StorageBreakdown> = _storageBreakdown.asStateFlow()

    fun refreshStorageBreakdown() {
        viewModelScope.launch {
            _storageBreakdown.value = settingsRepository.calculateStorageBreakdown()
        }
    }

    fun setLanguage(language: UiLanguage) {
        viewModelScope.launch { settingsRepository.setLanguage(language) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setFontSize(size: FontSizeOption) {
        viewModelScope.launch { settingsRepository.setFontSize(size) }
    }

    fun setWallpaper(wallpaper: ChatWallpaper) {
        viewModelScope.launch { settingsRepository.setWallpaper(wallpaper) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setNotificationsEnabled(enabled) }
    }

    fun setMediaTransferNotifications(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setMediaTransferNotifications(enabled) }
    }

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSoundEnabled(enabled) }
    }

    fun setHapticEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setHapticEnabled(enabled) }
    }

    fun setNotificationPreview(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setNotificationPreview(enabled) }
    }

    fun setProfileVisibility(scope: VisibilityScope) {
        viewModelScope.launch { settingsRepository.setProfileVisibility(scope) }
    }

    fun setShowLastSeen(show: Boolean) {
        viewModelScope.launch { settingsRepository.setShowLastSeen(show) }
    }

    fun setShowOnlineStatus(show: Boolean) {
        viewModelScope.launch { settingsRepository.setShowOnlineStatus(show) }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAppLockEnabled(enabled) }
    }

    fun clearTempCache(onDone: () -> Unit) {
        viewModelScope.launch {
            settingsRepository.clearTempCache()
            _storageBreakdown.value = settingsRepository.calculateStorageBreakdown()
            onDone()
        }
    }

    fun clearP2PMediaFiles(onDone: () -> Unit) {
        viewModelScope.launch {
            settingsRepository.clearP2PMediaFiles()
            _storageBreakdown.value = settingsRepository.calculateStorageBreakdown()
            onDone()
        }
    }

    fun clearAllLocalCache(onDone: () -> Unit) {
        viewModelScope.launch {
            settingsRepository.clearAllLocalCache()
            _storageBreakdown.value = settingsRepository.calculateStorageBreakdown()
            onDone()
        }
    }
}
