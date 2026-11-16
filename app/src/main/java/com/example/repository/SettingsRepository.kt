package com.example.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.database.ChatDao
import com.example.models.ChatWallpaper
import com.example.models.FontSizeOption
import com.example.models.StorageBreakdown
import com.example.models.ThemeMode
import com.example.models.UiLanguage
import com.example.models.UserSettings
import com.example.models.VisibilityScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

private val Context.settingsDataStore by preferencesDataStore(name = "p2p_chat_settings")

class SettingsRepository(
    private val context: Context,
    private val chatDao: ChatDao
) {
    private val keyLanguage = stringPreferencesKey("ui_language")
    private val keyTheme = stringPreferencesKey("theme_mode")
    private val keyFontSize = stringPreferencesKey("font_size")
    private val keyWallpaper = stringPreferencesKey("chat_wallpaper")
    private val keyNotifications = booleanPreferencesKey("notif_enabled")
    private val keyMediaNotifications = booleanPreferencesKey("notif_media_enabled")
    private val keySound = booleanPreferencesKey("notif_sound")
    private val keyHaptic = booleanPreferencesKey("haptic_enabled")
    private val keyNotifPreview = booleanPreferencesKey("notif_preview")
    private val keyProfileVisibility = stringPreferencesKey("privacy_profile_visibility")
    private val keyShowLastSeen = booleanPreferencesKey("privacy_last_seen")
    private val keyShowOnlineStatus = booleanPreferencesKey("privacy_online_status")
    private val keyAppLock = booleanPreferencesKey("privacy_app_lock")

    val settingsFlow: Flow<UserSettings> = context.settingsDataStore.data.map { prefs ->
        UserSettings(
            language = UiLanguage.fromCode(prefs[keyLanguage]),
            themeMode = ThemeMode.fromKey(prefs[keyTheme]),
            fontSize = FontSizeOption.fromKey(prefs[keyFontSize]),
            wallpaper = ChatWallpaper.fromKey(prefs[keyWallpaper]),
            notificationsEnabled = prefs[keyNotifications] ?: true,
            mediaTransferNotifications = prefs[keyMediaNotifications] ?: true,
            soundEnabled = prefs[keySound] ?: true,
            hapticEnabled = prefs[keyHaptic] ?: true,
            notificationPreview = prefs[keyNotifPreview] ?: true,
            profileVisibility = VisibilityScope.fromKey(prefs[keyProfileVisibility]),
            showLastSeen = prefs[keyShowLastSeen] ?: true,
            showOnlineStatus = prefs[keyShowOnlineStatus] ?: true,
            appLockEnabled = prefs[keyAppLock] ?: false
        )
    }

    val languageFlow: Flow<UiLanguage> = settingsFlow.map { it.language }

    suspend fun setLanguage(language: UiLanguage) {
        context.settingsDataStore.edit { it[keyLanguage] = language.code }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[keyTheme] = mode.key }
    }

    suspend fun setFontSize(size: FontSizeOption) {
        context.settingsDataStore.edit { it[keyFontSize] = size.key }
    }

    suspend fun setWallpaper(wallpaper: ChatWallpaper) {
        context.settingsDataStore.edit { it[keyWallpaper] = wallpaper.key }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyNotifications] = enabled }
    }

    suspend fun setMediaTransferNotifications(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyMediaNotifications] = enabled }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[keySound] = enabled }
    }

    suspend fun setHapticEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyHaptic] = enabled }
    }

    suspend fun setNotificationPreview(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyNotifPreview] = enabled }
    }

    suspend fun setProfileVisibility(scope: VisibilityScope) {
        context.settingsDataStore.edit { it[keyProfileVisibility] = scope.key }
    }

    suspend fun setShowLastSeen(show: Boolean) {
        context.settingsDataStore.edit { it[keyShowLastSeen] = show }
    }

    suspend fun setShowOnlineStatus(show: Boolean) {
        context.settingsDataStore.edit { it[keyShowOnlineStatus] = show }
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyAppLock] = enabled }
    }

    suspend fun calculateStorageBreakdown(): StorageBreakdown = withContext(Dispatchers.IO) {
        val mediaDir = File(context.filesDir, "p2p_media")
        val mediaBytes = directorySize(mediaDir)
        val tempBytes = directorySize(context.cacheDir)
        val dbFile = context.getDatabasePath("p2p_chat_cache.db")
        val dbWal = context.getDatabasePath("p2p_chat_cache.db-wal")
        val dbBytes = (if (dbFile.exists()) dbFile.length() else 0L) +
            (if (dbWal.exists()) dbWal.length() else 0L)
        StorageBreakdown(
            p2pMediaBytes = mediaBytes,
            tempCacheBytes = tempBytes,
            databaseBytes = dbBytes
        )
    }

    suspend fun clearTempCache() = withContext(Dispatchers.IO) {
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    suspend fun clearP2PMediaFiles() = withContext(Dispatchers.IO) {
        File(context.filesDir, "p2p_media").deleteRecursively()
    }

    suspend fun clearAllLocalCache() = withContext(Dispatchers.IO) {
        clearTempCache()
        clearP2PMediaFiles()
        chatDao.clearMessages()
        chatDao.clearConversations()
        chatDao.clearUsers()
    }

    private fun directorySize(dir: File): Long {
        if (!dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        return dir.listFiles()?.sumOf { directorySize(it) } ?: 0L
    }
}
