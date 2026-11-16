package com.example.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.database.DateTimeUtils
import com.example.localization.LocalAppStrings
import com.example.ui.theme.ErrorCrimson
import com.example.ui.theme.FuturisticBackground
import com.example.ui.theme.GlassCard
import com.example.ui.theme.NeonAvatar
import com.example.ui.theme.NeonOutlinedButton
import com.example.ui.theme.NeonPrimaryButton
import com.example.ui.theme.OrbitalPulseLoader
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.StatusPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    isOwnProfile: Boolean,
    viewModel: UserProfileViewModel,
    onEditProfile: () -> Unit,
    onMessagePeer: (String) -> Unit,
    onBack: (() -> Unit)? = null
) {
    val strings = LocalAppStrings.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    FuturisticBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(if (isOwnProfile) strings.myProfileTitle else strings.profileTitle)
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("profile_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = strings.backAction
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.load() },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("profile_refresh_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = strings.refreshLabel,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                        if (isOwnProfile) {
                            IconButton(
                                onClick = onEditProfile,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("profile_edit_icon")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = strings.editProfileAction,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when {
                    state.isLoading && state.profile == null -> {
                        OrbitalPulseLoader(
                            size = 46.dp,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    state.error != null && state.profile == null -> {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp)
                                .widthIn(max = 460.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = state.error!!.message(strings),
                                style = MaterialTheme.typography.bodyMedium,
                                color = ErrorCrimson
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            NeonOutlinedButton(
                                text = strings.retryAction,
                                onClick = { viewModel.load() }
                            )
                        }
                    }

                    state.profile != null -> {
                        val profile = state.profile!!
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(20.dp)
                                .widthIn(max = 580.dp)
                                .align(Alignment.TopCenter),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            NeonAvatar(
                                photoUrl = profile.photoUrl,
                                displayName = profile.displayName,
                                isOnline = profile.isOnline,
                                size = 122.dp
                            )

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = profile.displayName,
                                    style = MaterialTheme.typography.headlineMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "@${profile.username}",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = SpaceGroteskFontFamily
                                    ),
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                StatusPill(
                                    label = if (profile.isOnline) {
                                        strings.onlineNow
                                    } else if (!profile.lastSeen.isNullOrBlank()) {
                                        "${strings.lastSeenLabel}: ${
                                            DateTimeUtils.formatLastSeen(profile.lastSeen, strings.language)
                                        }"
                                    } else {
                                        strings.offlineStatus
                                    },
                                    dotColor = if (profile.isOnline) SignalEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = strings.bioLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = profile.bio.ifBlank { strings.emptyBioPlaceholder },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (profile.bio.isBlank()) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = strings.usernameFieldLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "@${profile.username}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = SpaceGroteskFontFamily
                                        ),
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }

                            if (isOwnProfile) {
                                NeonPrimaryButton(
                                    text = strings.editProfileAction,
                                    onClick = onEditProfile,
                                    modifier = Modifier.fillMaxWidth(),
                                    testTag = "profile_edit_button"
                                )
                            } else {
                                NeonPrimaryButton(
                                    text = strings.messageUserAction,
                                    onClick = { onMessagePeer(profile.username) },
                                    modifier = Modifier.fillMaxWidth(),
                                    testTag = "profile_message_peer_button"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
