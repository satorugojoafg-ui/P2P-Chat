package com.example.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.localization.LocalAppStrings
import com.example.models.ChatWallpaper
import com.example.models.FontSizeOption
import com.example.models.ThemeMode
import com.example.models.UiLanguage
import com.example.models.UserProfile
import com.example.models.UserSettings
import com.example.ui.theme.ErrorCrimson
import com.example.ui.theme.GlassCard
import com.example.ui.theme.NeonAvatar
import com.example.ui.theme.NeonOutlinedButton
import com.example.ui.theme.SignalEmerald

@Composable
fun SettingsScreen(
    currentUser: UserProfile?,
    settings: UserSettings,
    onSelectLanguage: (UiLanguage) -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onSelectFontSize: (FontSizeOption) -> Unit,
    onSelectWallpaper: (ChatWallpaper) -> Unit,
    onToggleHaptic: (Boolean) -> Unit,
    onOpenEditProfile: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenAbout: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    var showLogoutDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .widthIn(max = 640.dp)
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = strings.settingsTitle,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            // 1. Account Section
            SectionHeader(title = strings.sectionAccount, color = MaterialTheme.colorScheme.primary)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .testTag("settings_edit_profile_row")
                    .clickable(onClick = onOpenEditProfile)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NeonAvatar(
                        photoUrl = currentUser?.photoUrl,
                        displayName = currentUser?.displayName ?: "U",
                        isOnline = true,
                        size = 52.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentUser?.displayName ?: strings.myProfileTitle,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!currentUser?.username.isNullOrBlank()) {
                            Text(
                                text = "@${currentUser?.username}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = strings.editProfileTitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = strings.editProfileTitle,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 2. Appearance Section
            SectionHeader(title = strings.sectionAppearance, color = MaterialTheme.colorScheme.secondary)
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Outlined.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Text(strings.themeModeTitle, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val themeItems = listOf(
                        ThemeMode.DARK to strings.themeDark,
                        ThemeMode.LIGHT to strings.themeLight,
                        ThemeMode.SYSTEM to strings.themeSystem
                    )
                    themeItems.forEach { (mode, label) ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { onSelectThemeMode(mode) },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                selectedLabelColor = MaterialTheme.colorScheme.secondary
                            ),
                            modifier = Modifier.testTag("theme_chip_${mode.name.lowercase()}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Outlined.FormatSize, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(strings.fontSizeTitle, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val sizeItems = listOf(
                        FontSizeOption.COMPACT to strings.fontCompact,
                        FontSizeOption.NORMAL to strings.fontNormal,
                        FontSizeOption.LARGE to strings.fontLarge
                    )
                    sizeItems.forEach { (option, label) ->
                        FilterChip(
                            selected = settings.fontSize == option,
                            onClick = { onSelectFontSize(option) },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                selectedLabelColor = MaterialTheme.colorScheme.secondary
                            ),
                            modifier = Modifier.testTag("font_chip_${option.name.lowercase()}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Outlined.Wallpaper, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Text(strings.wallpaperTitle, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val wallpapers = listOf(
                        ChatWallpaper.COSMIC_GRAPHITE to strings.wallpaperCosmic,
                        ChatWallpaper.NEON_AURORA to strings.wallpaperAurora,
                        ChatWallpaper.MIDNIGHT_GRID to strings.wallpaperGrid,
                        ChatWallpaper.MINIMAL_CLEAN to strings.wallpaperClean
                    )
                    wallpapers.forEach { (wp, label) ->
                        FilterChip(
                            selected = settings.wallpaper == wp,
                            onClick = { onSelectWallpaper(wp) },
                            label = { Text(label) },
                            leadingIcon = if (settings.wallpaper == wp) {
                                {
                                    Icon(
                                        Icons.Outlined.Palette,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                selectedLabelColor = MaterialTheme.colorScheme.secondary
                            ),
                            modifier = Modifier.testTag("wallpaper_chip_${wp.name.lowercase()}")
                        )
                    }
                }
            }

            // 3. Language Section
            SectionHeader(title = strings.sectionLanguage, color = MaterialTheme.colorScheme.primary)
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Translate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = strings.languageSettingTitle,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                LanguageOptionRow(
                    title = strings.languagePersian,
                    selected = settings.language == UiLanguage.PERSIAN,
                    onClick = { onSelectLanguage(UiLanguage.PERSIAN) },
                    testTag = "lang_option_persian"
                )
                Spacer(modifier = Modifier.height(8.dp))
                LanguageOptionRow(
                    title = strings.languageEnglish,
                    selected = settings.language == UiLanguage.ENGLISH,
                    onClick = { onSelectLanguage(UiLanguage.ENGLISH) },
                    testTag = "lang_option_english"
                )
            }

            // 4. Notifications Section
            SectionHeader(title = strings.sectionNotifications, color = MaterialTheme.colorScheme.secondary)
            SettingsNavRow(
                icon = Icons.Outlined.Notifications,
                accent = MaterialTheme.colorScheme.secondary,
                title = strings.notificationsRowTitle,
                subtitle = strings.notificationsRowSubtitle,
                testTag = "settings_notifications_row",
                onClick = onOpenNotifications
            )

            // 5. Privacy Section
            SectionHeader(title = strings.sectionPrivacy, color = SignalEmerald)
            SettingsNavRow(
                icon = Icons.Outlined.Security,
                accent = SignalEmerald,
                title = strings.privacyRowTitle,
                subtitle = strings.privacyRowSubtitle,
                testTag = "settings_privacy_row",
                onClick = onOpenPrivacy
            )

            // 6. Chat Settings Section
            SectionHeader(title = strings.sectionChatSettings, color = MaterialTheme.colorScheme.primary)
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.TouchApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.hapticFeedbackTitle,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = strings.hapticFeedbackSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.hapticEnabled,
                        onCheckedChange = onToggleHaptic,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("settings_haptic_switch")
                    )
                }
            }

            // 7. Storage & Cache Section
            SectionHeader(title = strings.sectionStorageCache, color = MaterialTheme.colorScheme.secondary)
            SettingsNavRow(
                icon = Icons.Outlined.Storage,
                accent = MaterialTheme.colorScheme.secondary,
                title = strings.storageRowTitle,
                subtitle = strings.storageRowSubtitle,
                testTag = "settings_storage_row",
                onClick = onOpenStorage
            )

            // 8. About P2P CHAT Section
            SectionHeader(title = strings.sectionAbout, color = MaterialTheme.colorScheme.primary)
            SettingsNavRow(
                icon = Icons.Outlined.Info,
                accent = MaterialTheme.colorScheme.primary,
                title = strings.aboutRowTitle,
                subtitle = strings.aboutRowSubtitle,
                testTag = "settings_about_row",
                onClick = onOpenAbout
            )

            Spacer(modifier = Modifier.height(4.dp))

            NeonOutlinedButton(
                text = strings.logoutAction,
                onClick = { showLogoutDialog = true },
                accentColor = ErrorCrimson,
                modifier = Modifier.fillMaxWidth(),
                testTag = "settings_logout_button"
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(strings.logoutConfirmTitle) },
            text = {
                Text(
                    strings.logoutConfirmMessage,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ErrorCrimson),
                    modifier = Modifier.testTag("logout_confirm_button")
                ) {
                    Text(strings.confirmAction)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(strings.cancelAction, color = MaterialTheme.colorScheme.secondary)
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String, color: Color) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = color
    )
}

@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .testTag(testTag)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = accent)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LanguageOptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
        },
        border = BorderStroke(
            1.dp,
            if (selected) {
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .testTag(testTag)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.secondary,
                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
