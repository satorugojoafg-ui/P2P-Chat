package com.example.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.database.DateTimeUtils
import com.example.localization.LocalAppStrings
import com.example.models.ChatMessage
import com.example.models.ChatWallpaper
import com.example.models.MediaTransferItem
import com.example.models.TransferDirection
import com.example.models.TransferStatus
import com.example.models.WebRtcConnectionState
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.ChatWallpaperBackground
import com.example.ui.theme.ElectricCyanDeep
import com.example.ui.theme.ErrorCrimson
import com.example.ui.theme.GlassCard
import com.example.ui.theme.LocalHapticEnabled
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.NeonAvatar
import com.example.ui.theme.NeonOutlinedButton
import com.example.ui.theme.NeonPrimaryButton
import com.example.ui.theme.NeonVioletDeep
import com.example.ui.theme.OrbitalPulseLoader
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.StatusPill
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateChatScreen(
    peerUsername: String,
    wallpaper: ChatWallpaper,
    mediaTransferNotificationsEnabled: Boolean,
    viewModel: PrivateChatViewModel,
    onOpenPeerProfile: (String) -> Unit,
    onBack: () -> Unit
) {
    val strings = LocalAppStrings.current
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val hapticEnabled = LocalHapticEnabled.current
    val scope = rememberCoroutineScope()

    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val p2pState by viewModel.p2pState.collectAsStateWithLifecycle()
    val isPeerTyping by viewModel.isPeerTyping.collectAsStateWithLifecycle()
    val transfers by viewModel.transfers.collectAsStateWithLifecycle()

    var showAttachTray by remember { mutableStateOf(false) }
    var showP2PSheet by remember { mutableStateOf(false) }
    var previewImagePath by remember { mutableStateOf<String?>(null) }
    var messageActionTarget by remember { mutableStateOf<ChatMessage?>(null) }

    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    val showScrollToLatest by remember {
        derivedStateOf {
            messages.isNotEmpty() &&
                listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index != messages.lastIndex
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.sendP2PMedia(uri)
            showAttachTray = false
            showP2PSheet = true
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.sendP2PMedia(uri)
            showAttachTray = false
            showP2PSheet = true
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && !uiState.isSearchingMessages) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.completedTransferAlerts.collect { item ->
            if (mediaTransferNotificationsEnabled) {
                snackbarHostState.showSnackbar("${strings.p2pTransferComplete}: ${item.fileName}")
            }
        }
    }

    val p2pLabel = when (p2pState) {
        WebRtcConnectionState.IDLE -> strings.p2pIdle
        WebRtcConnectionState.SIGNALING -> strings.p2pSignaling
        WebRtcConnectionState.CONNECTING -> strings.p2pConnecting
        WebRtcConnectionState.CONNECTED -> strings.p2pConnected
        WebRtcConnectionState.TRANSFERRING -> strings.p2pTransferring
        WebRtcConnectionState.FAILED -> strings.p2pFailed
    }
    val p2pColor = when (p2pState) {
        WebRtcConnectionState.CONNECTED, WebRtcConnectionState.TRANSFERRING -> SignalEmerald
        WebRtcConnectionState.SIGNALING, WebRtcConnectionState.CONNECTING -> MaterialTheme.colorScheme.secondary
        WebRtcConnectionState.FAILED -> ErrorCrimson
        WebRtcConnectionState.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    ChatWallpaperBackground(wallpaper = wallpaper) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                if (uiState.selectedMessageKeys.isNotEmpty()) {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(
                                onClick = { viewModel.clearSelection() },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Outlined.Close, contentDescription = strings.cancelAction)
                            }
                        },
                        title = {
                            Text("${uiState.selectedMessageKeys.size} ${strings.selectedCountLabel}")
                        },
                        actions = {
                            if (uiState.selectedMessageKeys.size == 1) {
                                val selected = messages.firstOrNull {
                                    uiState.selectedMessageKeys.contains(it.localKey)
                                }
                                if (selected != null) {
                                    IconButton(
                                        onClick = {
                                            viewModel.setReplyTarget(selected)
                                        },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Reply,
                                            contentDescription = strings.replyAction,
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(selected.content))
                                            viewModel.clearSelection()
                                            scope.launch {
                                                snackbarHostState.showSnackbar(strings.messageCopiedToast)
                                            }
                                        },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ContentCopy,
                                            contentDescription = strings.copyAction,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                            IconButton(
                                onClick = { viewModel.deleteSelectedLocally() },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = strings.deleteLocallyAction,
                                    tint = ErrorCrimson
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                        )
                    )
                } else {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("chat_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = strings.backAction
                                )
                            }
                        },
                        title = {
                            val peer = uiState.peerProfile
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onOpenPeerProfile(peerUsername) }
                                    .padding(vertical = 4.dp, horizontal = 4.dp)
                                    .testTag("chat_peer_header")
                            ) {
                                NeonAvatar(
                                    photoUrl = peer?.photoUrl,
                                    displayName = peer?.displayName ?: peerUsername,
                                    isOnline = peer?.isOnline ?: false,
                                    size = 40.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = peer?.displayName ?: peerUsername,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = when {
                                            isPeerTyping -> strings.typingIndicatorText
                                            peer?.isOnline == true -> strings.onlineNow
                                            !peer?.lastSeen.isNullOrBlank() -> "${strings.lastSeenLabel}: ${
                                                DateTimeUtils.formatLastSeen(peer?.lastSeen, strings.language)
                                            }"
                                            else -> "@$peerUsername"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when {
                                            isPeerTyping -> MaterialTheme.colorScheme.secondary
                                            peer?.isOnline == true -> SignalEmerald
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { viewModel.toggleInChatSearch() },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("chat_search_toggle_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = strings.searchInChatHint,
                                    tint = if (uiState.isSearchingMessages) {
                                        MaterialTheme.colorScheme.secondary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                            IconButton(
                                onClick = { showP2PSheet = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("chat_webrtc_status_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Hub,
                                    contentDescription = strings.p2pChannelLabel,
                                    tint = p2pColor
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding()
                    .navigationBarsPadding()
            ) {
                // In-chat search bar
                AnimatedVisibility(visible = uiState.isSearchingMessages) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = viewModel::onInChatSearchQueryChange,
                            placeholder = { Text(strings.searchInChatHint) },
                            leadingIcon = {
                                Icon(Icons.Outlined.Search, contentDescription = null)
                            },
                            trailingIcon = {
                                IconButton(onClick = { viewModel.toggleInChatSearch() }) {
                                    Icon(Icons.Outlined.Close, contentDescription = strings.cancelAction)
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .testTag("in_chat_search_input")
                        )
                    }
                }

                // Direct media link status bar
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showP2PSheet = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill(label = p2pLabel, dotColor = p2pColor)
                        Text(
                            text = strings.p2pChannelLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                AnimatedVisibility(visible = uiState.error != null || uiState.mediaLimitError) {
                    Surface(
                        color = ErrorCrimson.copy(alpha = 0.16f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (uiState.mediaLimitError) {
                                strings.p2pFileTooLarge
                            } else {
                                uiState.error?.message(strings).orEmpty()
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = ErrorCrimson,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when {
                        uiState.isLoadingInitial && messages.isEmpty() -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                OrbitalPulseLoader(size = 44.dp)
                            }
                        }

                        messages.isEmpty() -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                GlassCard(
                                    modifier = Modifier.widthIn(max = 460.dp),
                                    borderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
                                ) {
                                    Text(
                                        text = if (uiState.isSearchingMessages) {
                                            strings.noMatchingMessages
                                        } else {
                                            strings.emptyMessagesTitle
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    if (!uiState.isSearchingMessages) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = strings.emptyMessagesSubtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        else -> {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(
                                    items = messages,
                                    key = { it.localKey }
                                ) { msg ->
                                    val animState = remember(msg.localKey) {
                                        MutableTransitionState(false).apply { targetState = true }
                                    }
                                    val isSelected = uiState.selectedMessageKeys.contains(msg.localKey)
                                    androidx.compose.animation.AnimatedVisibility(
                                        visibleState = animState,
                                        enter = fadeIn() + scaleIn(
                                            initialScale = 0.94f,
                                            animationSpec = spring(dampingRatio = 0.8f)
                                        )
                                    ) {
                                        MessageBubble(
                                            message = msg,
                                            peerPhotoUrl = uiState.peerProfile?.photoUrl,
                                            isSelected = isSelected,
                                            onTap = {
                                                if (uiState.selectedMessageKeys.isNotEmpty()) {
                                                    viewModel.toggleMessageSelection(msg.localKey)
                                                } else {
                                                    messageActionTarget = msg
                                                }
                                            },
                                            onLongPress = {
                                                if (hapticEnabled) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                }
                                                viewModel.toggleMessageSelection(msg.localKey)
                                            },
                                            onOpenImage = { path -> previewImagePath = path }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Scroll-to-latest button
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showScrollToLatest,
                        enter = fadeIn() + scaleIn(),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                    ) {
                        SmallFloatingActionButton(
                            onClick = {
                                scope.launch {
                                    if (messages.isNotEmpty()) {
                                        listState.animateScrollToItem(messages.lastIndex)
                                    }
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.secondary,
                            shape = CircleShape,
                            modifier = Modifier.testTag("scroll_to_latest_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.KeyboardArrowDown,
                                contentDescription = strings.scrollToLatestDesc
                            )
                        }
                    }
                }

                // Expandable attachment panel
                AnimatedVisibility(visible = showAttachTray) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AttachmentTile(
                                icon = Icons.Outlined.Image,
                                label = strings.p2pPickPhotoAction,
                                accent = MaterialTheme.colorScheme.secondary,
                                onClick = {
                                    photoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                            AttachmentTile(
                                icon = Icons.Outlined.Videocam,
                                label = strings.p2pPickVideoAction,
                                accent = MaterialTheme.colorScheme.primary,
                                onClick = {
                                    videoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                            AttachmentTile(
                                icon = Icons.Outlined.Hub,
                                label = strings.p2pChannelLabel,
                                accent = SignalEmerald,
                                onClick = {
                                    showAttachTray = false
                                    showP2PSheet = true
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Reply preview strip
                val replyingTo = uiState.replyingTo
                AnimatedVisibility(visible = replyingTo != null) {
                    if (replyingTo != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Reply,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${strings.replyingToLabel} ${replyingTo.senderName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        text = replyingTo.content,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { viewModel.setReplyTarget(null) }) {
                                    Icon(Icons.Outlined.Close, contentDescription = strings.cancelAction)
                                }
                            }
                        }
                    }
                }

                // Bottom composer
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { showAttachTray = !showAttachTray },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("chat_attach_media_button")
                        ) {
                            Icon(
                                imageVector = if (showAttachTray) Icons.Outlined.Close else Icons.Outlined.AttachFile,
                                contentDescription = strings.attachMediaDesc,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }

                        OutlinedTextField(
                            value = uiState.draftText,
                            onValueChange = viewModel::onDraftChange,
                            placeholder = { Text(strings.messageInputHint) },
                            maxLines = 4,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (hapticEnabled) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    viewModel.sendMessage()
                                }
                            ),
                            shape = RoundedCornerShape(22.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_message_input")
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Surface(
                            shape = CircleShape,
                            color = if (uiState.draftText.isNotBlank()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .testTag("chat_send_button")
                                .clickable(enabled = uiState.draftText.isNotBlank() && !uiState.isSendingText) {
                                    if (hapticEnabled) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    viewModel.sendMessage()
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (uiState.isSendingText) {
                                    OrbitalPulseLoader(size = 20.dp)
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = strings.sendMessageDesc,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Message quick-action sheet
        val actionMsg = messageActionTarget
        if (actionMsg != null) {
            ModalBottomSheet(
                onDismissRequest = { messageActionTarget = null },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = actionMsg.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                viewModel.setReplyTarget(actionMsg)
                                messageActionTarget = null
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Text(strings.replyAction, style = MaterialTheme.typography.bodyLarge)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                clipboard.setText(AnnotatedString(actionMsg.content))
                                messageActionTarget = null
                                scope.launch { snackbarHostState.showSnackbar(strings.messageCopiedToast) }
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(strings.copyAction, style = MaterialTheme.typography.bodyLarge)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                viewModel.deleteMessageLocally(actionMsg.localKey)
                                messageActionTarget = null
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = ErrorCrimson)
                        Text(strings.deleteLocallyAction, style = MaterialTheme.typography.bodyLarge, color = ErrorCrimson)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
        }

        if (showP2PSheet) {
            ModalBottomSheet(
                onDismissRequest = { showP2PSheet = false },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                P2PTransferBottomSheet(
                    p2pState = p2pState,
                    p2pLabel = p2pLabel,
                    p2pColor = p2pColor,
                    transfers = transfers,
                    onConnectOrRetry = { viewModel.establishP2PLink() },
                    onPickPhoto = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onPickVideo = {
                        videoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                    onCancelTransfer = { id -> viewModel.cancelTransfer(id) },
                    onRetryTransfer = { id -> viewModel.retryTransfer(id) }
                )
            }
        }

        val fullImagePath = previewImagePath
        if (fullImagePath != null) {
            Dialog(onDismissRequest = { previewImagePath = null }) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AsyncImage(
                        model = File(fullImagePath),
                        contentDescription = strings.p2pImageTag,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { previewImagePath = null }
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachmentTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = accent)
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessage,
    peerPhotoUrl: String?,
    isSelected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onOpenImage: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    val isDark = LocalIsDarkTheme.current
    val timeLabel = DateTimeUtils.formatTime(message.timestampMillis, strings.language)

    val bubbleGradient = when {
        isSelected -> Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f),
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            )
        )
        message.isOutgoing -> if (isDark) {
            Brush.linearGradient(listOf(NeonVioletDeep, ElectricCyanDeep))
        } else {
            Brush.linearGradient(listOf(Color(0xFF6D28D9), Color(0xFF0284C7)))
        }
        else -> Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.surfaceContainerHigh
            )
        )
    }

    val bubbleShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (message.isOutgoing) 18.dp else 4.dp,
        bottomEnd = if (message.isOutgoing) 4.dp else 18.dp
    )

    val textColor = if (message.isOutgoing) Color.White else MaterialTheme.colorScheme.onSurface
    val metaColor = if (message.isOutgoing) Color.White.copy(alpha = 0.78f) else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isOutgoing) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!message.isOutgoing) {
            NeonAvatar(
                photoUrl = message.senderPhotoUrl ?: peerPhotoUrl,
                displayName = message.senderName,
                isOnline = false,
                size = 30.dp
            )
            Spacer(modifier = Modifier.width(6.dp))
        }

        Surface(
            shape = bubbleShape,
            color = Color.Transparent,
            border = BorderStroke(
                if (isSelected) 1.6.dp else 1.dp,
                when {
                    isSelected -> MaterialTheme.colorScheme.secondary
                    message.isOutgoing -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                }
            ),
            modifier = Modifier
                .widthIn(max = 304.dp)
                .clip(bubbleShape)
                .background(bubbleGradient, bubbleShape)
                .combinedClickable(
                    onClick = onTap,
                    onLongClick = onLongPress
                )
        ) {
            Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp)) {
                if (!message.replyToText.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.Black.copy(alpha = 0.22f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text(
                                text = message.replyToSender.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = message.replyToText,
                                style = MaterialTheme.typography.bodySmall,
                                color = metaColor,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (message.isP2PMedia && !message.mediaPath.isNullOrBlank()) {
                    val isVideo = message.mediaMimeType?.startsWith("video/") == true
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.35f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isVideo) {
                                onOpenImage(message.mediaPath)
                            }
                    ) {
                        if (!isVideo) {
                            AsyncImage(
                                model = File(message.mediaPath),
                                contentDescription = message.mediaFileName ?: strings.p2pImageTag,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(170.dp)
                            )
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.PlayCircleOutline,
                                    contentDescription = strings.p2pVideoTag,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(34.dp)
                                )
                                Column {
                                    Text(
                                        text = strings.p2pVideoTag,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        text = message.mediaFileName.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = metaColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isVideo) strings.p2pVideoTag else strings.p2pImageTag,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                } else {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = SpaceGroteskFontFamily
                        ),
                        color = metaColor
                    )
                    if (message.isOutgoing && !message.isP2PMedia) {
                        val isRead = !message.readAtIso.isNullOrBlank()
                        Icon(
                            imageVector = if (isRead) Icons.Outlined.DoneAll else Icons.Outlined.Done,
                            contentDescription = if (isRead) strings.readReceiptLabel else strings.sentReceiptLabel,
                            tint = if (isRead) MaterialTheme.colorScheme.secondary else metaColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun P2PTransferBottomSheet(
    p2pState: WebRtcConnectionState,
    p2pLabel: String,
    p2pColor: Color,
    transfers: List<MediaTransferItem>,
    onConnectOrRetry: () -> Unit,
    onPickPhoto: () -> Unit,
    onPickVideo: () -> Unit,
    onCancelTransfer: (String) -> Unit,
    onRetryTransfer: (String) -> Unit
) {
    val strings = LocalAppStrings.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.p2pTransferTitle,
                style = MaterialTheme.typography.titleLarge
            )
            StatusPill(label = p2pLabel, dotColor = p2pColor)
        }

        Text(
            text = strings.p2pDirectNotice,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = AlertAmber.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, AlertAmber.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = strings.p2pNetworkNotice,
                style = MaterialTheme.typography.bodySmall,
                color = AlertAmber,
                modifier = Modifier.padding(12.dp)
            )
        }

        NeonOutlinedButton(
            text = if (p2pState == WebRtcConnectionState.FAILED) strings.p2pRetryAction else strings.p2pConnectPeerAction,
            onClick = onConnectOrRetry,
            accentColor = if (p2pState == WebRtcConnectionState.FAILED) ErrorCrimson else MaterialTheme.colorScheme.secondary,
            modifier = Modifier.fillMaxWidth(),
            testTag = "p2p_connect_retry_button"
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            NeonPrimaryButton(
                text = strings.p2pPickPhotoAction,
                onClick = onPickPhoto,
                modifier = Modifier.weight(1f),
                testTag = "p2p_pick_photo_button"
            )
            NeonPrimaryButton(
                text = strings.p2pPickVideoAction,
                onClick = onPickVideo,
                modifier = Modifier.weight(1f),
                testTag = "p2p_pick_video_button"
            )
        }

        if (transfers.isNotEmpty()) {
            transfers.takeLast(4).reversed().forEach { item ->
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.fileName,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val statusText = when (item.statusTextKey) {
                            TransferStatus.NEGOTIATING -> strings.p2pSignaling
                            TransferStatus.TRANSFERRING -> if (item.direction == TransferDirection.OUTGOING) {
                                strings.p2pSendingMedia
                            } else {
                                strings.p2pReceivingMedia
                            }
                            TransferStatus.COMPLETED -> strings.p2pTransferComplete
                            TransferStatus.FAILED -> strings.p2pTransferFailed
                            TransferStatus.CANCELLED -> strings.p2pTransferCancelled
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = when (item.statusTextKey) {
                                TransferStatus.COMPLETED -> SignalEmerald
                                TransferStatus.FAILED, TransferStatus.CANCELLED -> ErrorCrimson
                                else -> MaterialTheme.colorScheme.secondary
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { item.progress },
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                    )
                    if (item.statusTextKey == TransferStatus.NEGOTIATING || item.statusTextKey == TransferStatus.TRANSFERRING) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { onCancelTransfer(item.transferId) }) {
                                Text(strings.p2pCancelAction, color = ErrorCrimson)
                            }
                        }
                    } else if (
                        (item.statusTextKey == TransferStatus.FAILED || item.statusTextKey == TransferStatus.CANCELLED) &&
                        item.direction == TransferDirection.OUTGOING
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { onRetryTransfer(item.transferId) }) {
                                Text(strings.p2pRetryAction, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}
