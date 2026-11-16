package com.example.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Cached
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.PermMedia
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.localization.LocalAppStrings
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.ErrorCrimson
import com.example.ui.theme.FuturisticBackground
import com.example.ui.theme.GlassCard
import com.example.ui.theme.NeonOutlinedButton
import com.example.ui.theme.SpaceGroteskFontFamily
import kotlinx.coroutines.launch
import java.util.Locale

private enum class StorageClearTarget {
    MEDIA,
    ALL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageManagementScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val strings = LocalAppStrings.current
    val breakdown by viewModel.storageBreakdown.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pendingConfirm by remember { mutableStateOf<StorageClearTarget?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refreshStorageBreakdown()
    }

    FuturisticBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
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
                                .testTag("storage_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = strings.backAction
                            )
                        }
                    },
                    title = {
                        Text(
                            text = strings.storageTitle,
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Column {
                            Text(
                                text = strings.storageTotalUsed,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatBytes(breakdown.totalBytes),
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }

                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    StorageMetricRow(
                        icon = Icons.Outlined.PermMedia,
                        accent = MaterialTheme.colorScheme.primary,
                        label = strings.storageMediaFiles,
                        value = formatBytes(breakdown.p2pMediaBytes)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    StorageMetricRow(
                        icon = Icons.Outlined.Cached,
                        accent = AlertAmber,
                        label = strings.storageTempCache,
                        value = formatBytes(breakdown.tempCacheBytes)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    StorageMetricRow(
                        icon = Icons.Outlined.ChatBubbleOutline,
                        accent = MaterialTheme.colorScheme.secondary,
                        label = strings.storageMessageCache,
                        value = formatBytes(breakdown.databaseBytes)
                    )
                }

                NeonOutlinedButton(
                    text = strings.storageClearTempCache,
                    onClick = {
                        viewModel.clearTempCache {
                            scope.launch {
                                snackbarHostState.showSnackbar(strings.storageClearedToast)
                            }
                        }
                    },
                    accentColor = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "storage_clear_temp_button"
                )

                NeonOutlinedButton(
                    text = strings.storageClearMediaFiles,
                    onClick = { pendingConfirm = StorageClearTarget.MEDIA },
                    accentColor = AlertAmber,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "storage_clear_media_button"
                )

                NeonOutlinedButton(
                    text = strings.storageClearAllLocal,
                    onClick = { pendingConfirm = StorageClearTarget.ALL },
                    accentColor = ErrorCrimson,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "storage_clear_all_button"
                )
            }
        }
    }

    pendingConfirm?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingConfirm = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = if (target == StorageClearTarget.MEDIA) {
                        strings.storageClearMediaFiles
                    } else {
                        strings.storageClearAllLocal
                    }
                )
            },
            text = {
                Text(
                    text = strings.clearHistoryConfirmBody,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingConfirm = null
                        val actionDone = {
                            scope.launch {
                                snackbarHostState.showSnackbar(strings.storageClearedToast)
                            }
                            Unit
                        }
                        if (target == StorageClearTarget.MEDIA) {
                            viewModel.clearP2PMediaFiles(actionDone)
                        } else {
                            viewModel.clearAllLocalCache(actionDone)
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ErrorCrimson)
                ) {
                    Text(strings.confirmAction)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirm = null }) {
                    Text(strings.cancelAction)
                }
            }
        )
    }
}

@Composable
private fun StorageMetricRow(
    icon: ImageVector,
    accent: Color,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = accent)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(fontFamily = SpaceGroteskFontFamily),
            color = accent
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    if (kb < 1.0) return "$bytes B"
    val mb = kb / 1024.0
    if (mb < 1.0) return String.format(Locale.US, "%.1f KB", kb)
    return String.format(Locale.US, "%.2f MB", mb)
}
