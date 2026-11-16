package com.example.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.example.localization.LocalAppStrings
import com.example.ui.theme.ErrorCrimson
import com.example.ui.theme.FuturisticBackground
import com.example.ui.theme.GlassCard
import com.example.ui.theme.NeonAvatar
import com.example.ui.theme.NeonOutlinedButton
import com.example.ui.theme.NeonPrimaryButton
import com.example.ui.theme.OrbitalPulseLoader
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.StatusPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    viewModel: EditProfileViewModel,
    onBack: () -> Unit
) {
    val strings = LocalAppStrings.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.uploadPhoto(uri)
        }
    }

    FuturisticBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(strings.editProfileTitle) },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("edit_profile_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = strings.backAction
                            )
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
                    .imePadding()
            ) {
                if (state.isLoadingProfile) {
                    OrbitalPulseLoader(
                        size = 44.dp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp)
                            .widthIn(max = 560.dp)
                            .align(Alignment.TopCenter),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Live profile preview card
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            borderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
                        ) {
                            Text(
                                text = strings.profilePreviewTitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                NeonAvatar(
                                    photoUrl = state.photoUrl,
                                    displayName = state.displayName.ifBlank { "?" },
                                    isOnline = true,
                                    size = 76.dp
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = state.displayName.ifBlank { strings.displayNameLabel },
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = state.bio.ifBlank { strings.emptyBioPlaceholder },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    StatusPill(label = strings.onlineNow, dotColor = SignalEmerald)
                                }
                            }
                        }

                        NeonOutlinedButton(
                            text = if (state.isUploadingPhoto) strings.uploadingPhoto else strings.changePhotoAction,
                            enabled = !state.isUploadingPhoto,
                            onClick = {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "edit_profile_photo_button"
                        )

                        AnimatedVisibility(visible = state.photoUploadedSuccess) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SignalEmerald.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, SignalEmerald.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = strings.photoUpdated,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SignalEmerald,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = state.displayName,
                                onValueChange = viewModel::onDisplayNameChange,
                                label = { Text(strings.displayNameLabel) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.Badge,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.secondary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("edit_profile_display_name")
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            OutlinedTextField(
                                value = state.bio,
                                onValueChange = viewModel::onBioChange,
                                label = { Text(strings.bioLabel) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.Description,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                minLines = 3,
                                maxLines = 5,
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("edit_profile_bio")
                            )

                            val errMsg = when {
                                state.validationDisplayNameEmpty -> strings.validationDisplayName
                                state.error != null -> state.error!!.message(strings)
                                else -> null
                            }

                            AnimatedVisibility(visible = errMsg != null) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = ErrorCrimson.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, ErrorCrimson.copy(alpha = 0.45f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp)
                                ) {
                                    Text(
                                        text = errMsg.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ErrorCrimson,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }

                            AnimatedVisibility(visible = state.savedSuccess) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = SignalEmerald.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, SignalEmerald.copy(alpha = 0.45f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp)
                                ) {
                                    Text(
                                        text = strings.profileSaved,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SignalEmerald,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            NeonPrimaryButton(
                                text = strings.saveChangesAction,
                                isLoading = state.isSaving,
                                onClick = { viewModel.saveProfile() },
                                modifier = Modifier.fillMaxWidth(),
                                testTag = "edit_profile_save_button"
                            )
                        }
                    }
                }
            }
        }
    }
}
