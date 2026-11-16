package com.example.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.localization.LocalAppStrings
import com.example.models.UserSettings
import com.example.ui.theme.FuturisticBackground
import com.example.ui.theme.GlassCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationPreferencesScreen(
    settings: UserSettings,
    onToggleMessages: (Boolean) -> Unit,
    onToggleMediaTransfers: (Boolean) -> Unit,
    onToggleSound: (Boolean) -> Unit,
    onTogglePreview: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val strings = LocalAppStrings.current

    FuturisticBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    ),
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("notifications_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = strings.backAction
                            )
                        }
                    },
                    title = {
                        Text(
                            text = strings.notificationsTitle,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .widthIn(max = 640.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                NotificationToggleCard(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    accent = MaterialTheme.colorScheme.secondary,
                    title = strings.notifMessagesTitle,
                    subtitle = strings.notifMessagesDesc,
                    checked = settings.notificationsEnabled,
                    onCheckedChange = onToggleMessages,
                    testTag = "notif_messages_switch"
                )
                NotificationToggleCard(
                    icon = Icons.Outlined.FileDownload,
                    accent = MaterialTheme.colorScheme.primary,
                    title = strings.notifMediaTitle,
                    subtitle = strings.notifMediaDesc,
                    checked = settings.mediaTransferNotifications,
                    onCheckedChange = onToggleMediaTransfers,
                    testTag = "notif_media_switch"
                )
                NotificationToggleCard(
                    icon = Icons.Outlined.GraphicEq,
                    accent = MaterialTheme.colorScheme.secondary,
                    title = strings.notifSoundTitle,
                    subtitle = strings.notifSoundDesc,
                    checked = settings.soundEnabled,
                    onCheckedChange = onToggleSound,
                    testTag = "notif_sound_switch"
                )
                NotificationToggleCard(
                    icon = Icons.Outlined.Visibility,
                    accent = MaterialTheme.colorScheme.primary,
                    title = strings.notifPreviewTitle,
                    subtitle = strings.notifPreviewDesc,
                    checked = settings.notificationPreview,
                    onCheckedChange = onTogglePreview,
                    testTag = "notif_preview_switch"
                )
            }
        }
    }
}

@Composable
private fun NotificationToggleCard(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = accent)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(testTag)
            )
        }
    }
}
