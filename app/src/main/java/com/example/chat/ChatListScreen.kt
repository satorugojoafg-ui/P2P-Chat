package com.example.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.database.DateTimeUtils
import com.example.localization.LocalAppStrings
import com.example.models.ConversationSummary
import com.example.models.HomeFilter
import com.example.models.UserProfile
import com.example.ui.theme.ErrorCrimson
import com.example.ui.theme.GlassCard
import com.example.ui.theme.LocalHapticEnabled
import com.example.ui.theme.NeonAvatar
import com.example.ui.theme.NeonPrimaryButton
import com.example.ui.theme.OrbitalPulseLoader
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.SpaceGroteskFontFamily
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    currentUser: UserProfile?,
    viewModel: ChatListViewModel,
    onOpenChat: (username: String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenMyProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val haptic = LocalHapticFeedback.current
    val hapticEnabled = LocalHapticEnabled.current

    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val onlineContacts by viewModel.onlineContacts.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var selectedConversationForMenu by remember { mutableStateOf<ConversationSummary?>(null) }
    var pendingClearPeer by remember { mutableStateOf<String?>(null) }
    var pendingDeletePeer by remember { mutableStateOf<String?>(null) }

    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greetingPrefix = when {
        hour < 12 -> strings.greetingMorning
        hour < 18 -> strings.greetingAfternoon
        else -> strings.greetingEvening
    }
    val greetingText = if (currentUser != null) {
        "$greetingPrefix، ${currentUser.displayName}"
    } else {
        greetingPrefix
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 700.dp)
                .align(Alignment.TopCenter)
        ) {
            // Personalized header with avatar & actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onOpenMyProfile() }
                        .padding(end = 8.dp)
                        .testTag("home_profile_header")
                ) {
                    NeonAvatar(
                        photoUrl = currentUser?.photoUrl,
                        displayName = currentUser?.displayName ?: strings.appName,
                        isOnline = true,
                        size = 46.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (strings.language == com.example.models.UiLanguage.PERSIAN) {
                                greetingText
                            } else {
                                if (currentUser != null) "$greetingPrefix, ${currentUser.displayName}" else greetingPrefix
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = strings.chatsTitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.refresh() },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("chats_refresh_button")
                    ) {
                        if (uiState.isRefreshing) {
                            OrbitalPulseLoader(size = 22.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = strings.refreshLabel,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onOpenSearch,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("chats_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = strings.searchUsersAction,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Conversation search field
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                placeholder = { Text(strings.searchConversationsHint) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(Icons.Outlined.Close, contentDescription = strings.clearAction)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.secondary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("home_conversation_search_input")
            )

            // Online contacts horizontal strip
            val activeContacts = onlineContacts.filter { it.isOnline }.ifEmpty { onlineContacts.take(10) }
            if (activeContacts.isNotEmpty()) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Text(
                        text = strings.onlineContactsTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(activeContacts, key = { it.username }) { contact ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(68.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onOpenChat(contact.username) }
                                    .padding(vertical = 4.dp)
                                    .testTag("online_contact_${contact.username}")
                            ) {
                                NeonAvatar(
                                    photoUrl = contact.photoUrl,
                                    displayName = contact.displayName,
                                    isOnline = contact.isOnline,
                                    size = 50.dp
                                )
                                Spacer(modifier = Modifier.height(5.dp))
                                Text(
                                    text = contact.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Filter chips: All / Unread / Archived
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    HomeFilter.ALL to strings.filterAll,
                    HomeFilter.UNREAD to strings.filterUnread,
                    HomeFilter.ARCHIVED to strings.filterArchived
                ).forEach { (filter, label) ->
                    val selected = uiState.filter == filter
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.onFilterChange(filter) },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                            selectedLabelColor = MaterialTheme.colorScheme.secondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            selectedBorderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.testTag("home_filter_${filter.name.lowercase()}")
                    )
                }
            }

            AnimatedVisibility(visible = uiState.error != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ErrorCrimson.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, ErrorCrimson.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uiState.error?.message(strings).orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = ErrorCrimson,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = strings.retryAction,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.refresh() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            when {
                !uiState.hasLoadedOnce && conversations.isEmpty() && uiState.isRefreshing -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        OrbitalPulseLoader(size = 48.dp)
                    }
                }

                conversations.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_empty_chats),
                                    contentDescription = strings.emptyChatsTitle,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(148.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = when (uiState.filter) {
                                        HomeFilter.ALL -> strings.emptyChatsTitle
                                        HomeFilter.UNREAD -> strings.emptyUnreadTitle
                                        HomeFilter.ARCHIVED -> strings.emptyArchivedTitle
                                    },
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = strings.emptyChatsSubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (uiState.filter == HomeFilter.ALL) {
                                    Spacer(modifier = Modifier.height(18.dp))
                                    NeonPrimaryButton(
                                        text = strings.startNewChat,
                                        onClick = onOpenSearch,
                                        modifier = Modifier.fillMaxWidth(),
                                        testTag = "empty_start_chat_button"
                                    )
                                }
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 4.dp,
                            bottom = 92.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(
                            items = conversations,
                            key = { _, item -> item.peerUsername }
                        ) { index, conv ->
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn() + slideInVertically { (index + 1) * 14 }
                            ) {
                                ConversationCard(
                                    conversation = conv,
                                    onClick = { onOpenChat(conv.peerUsername) },
                                    onLongClick = {
                                        if (hapticEnabled) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                        selectedConversationForMenu = conv
                                    },
                                    onMenuClick = { selectedConversationForMenu = conv }
                                )
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onOpenSearch,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("chats_new_chat_fab")
        ) {
            Icon(
                imageVector = Icons.Outlined.AddComment,
                contentDescription = strings.startNewChat
            )
        }
    }

    val menuConv = selectedConversationForMenu
    if (menuConv != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedConversationForMenu = null },
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = menuConv.peerDisplayName,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = strings.deviceLocalBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                ConversationActionItem(
                    icon = Icons.Outlined.PushPin,
                    label = if (menuConv.isPinned) strings.unpinConversation else strings.pinConversation,
                    onClick = {
                        viewModel.togglePin(menuConv.peerUsername, menuConv.isPinned)
                        selectedConversationForMenu = null
                    }
                )
                ConversationActionItem(
                    icon = if (menuConv.isMuted) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsOff,
                    label = if (menuConv.isMuted) strings.unmuteConversation else strings.muteConversation,
                    onClick = {
                        viewModel.toggleMute(menuConv.peerUsername, menuConv.isMuted)
                        selectedConversationForMenu = null
                    }
                )
                ConversationActionItem(
                    icon = if (menuConv.isArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                    label = if (menuConv.isArchived) strings.unarchiveConversation else strings.archiveConversation,
                    onClick = {
                        viewModel.toggleArchive(menuConv.peerUsername, menuConv.isArchived)
                        selectedConversationForMenu = null
                    }
                )
                ConversationActionItem(
                    icon = Icons.Outlined.CleaningServices,
                    label = strings.clearLocalHistory,
                    tint = MaterialTheme.colorScheme.secondary,
                    onClick = {
                        pendingClearPeer = menuConv.peerUsername
                        selectedConversationForMenu = null
                    }
                )
                ConversationActionItem(
                    icon = Icons.Outlined.DeleteOutline,
                    label = strings.deleteConversationAction,
                    tint = ErrorCrimson,
                    onClick = {
                        pendingDeletePeer = menuConv.peerUsername
                        selectedConversationForMenu = null
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (pendingClearPeer != null) {
        AlertDialog(
            onDismissRequest = { pendingClearPeer = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(strings.clearHistoryConfirmTitle) },
            text = { Text(strings.clearHistoryConfirmBody, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingClearPeer?.let { viewModel.clearLocalHistory(it) }
                        pendingClearPeer = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ErrorCrimson)
                ) {
                    Text(strings.confirmAction)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingClearPeer = null }) {
                    Text(strings.cancelAction)
                }
            }
        )
    }

    if (pendingDeletePeer != null) {
        AlertDialog(
            onDismissRequest = { pendingDeletePeer = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(strings.deleteConversationAction) },
            text = { Text(strings.deleteConversationConfirmBody, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeletePeer?.let { viewModel.deleteConversationLocally(it) }
                        pendingDeletePeer = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ErrorCrimson)
                ) {
                    Text(strings.confirmAction)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletePeer = null }) {
                    Text(strings.cancelAction)
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationCard(
    conversation: ConversationSummary,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    val strings = LocalAppStrings.current
    val timeText = DateTimeUtils.formatListTime(conversation.lastMessageTimestampMillis, strings.language)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
        border = BorderStroke(
            1.dp,
            when {
                conversation.isPinned -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                conversation.unreadCount > 0 -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)
                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .testTag("conversation_card_${conversation.peerUsername}")
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NeonAvatar(
                photoUrl = conversation.peerPhotoUrl,
                displayName = conversation.peerDisplayName,
                isOnline = conversation.peerOnline,
                size = 54.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = conversation.peerDisplayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (conversation.isPinned) {
                            Spacer(modifier = Modifier.width(5.dp))
                            Icon(
                                imageVector = Icons.Outlined.PushPin,
                                contentDescription = strings.pinConversation,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        if (conversation.isMuted) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Outlined.NotificationsOff,
                                contentDescription = strings.muteConversation,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = SpaceGroteskFontFamily
                        ),
                        color = if (conversation.unreadCount > 0) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    AnimatedContent(
                        targetState = Pair(conversation.draftText, conversation.lastMessage),
                        label = "msg_preview_anim",
                        modifier = Modifier.weight(1f)
                    ) { (draft, lastMsg) ->
                        if (draft.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${strings.draftPrefix} ",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = ErrorCrimson
                                )
                                Text(
                                    text = draft,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (conversation.isLastMessageOutgoing && lastMsg.isNotEmpty()) {
                                    Icon(
                                        imageVector = Icons.Outlined.DoneAll,
                                        contentDescription = null,
                                        tint = if (conversation.isLastMessageRead) {
                                            MaterialTheme.colorScheme.secondary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${strings.youPrefix} ",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = lastMsg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (conversation.unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondary
                        ) {
                            Text(
                                text = conversation.unreadCount.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSecondary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    } else if (conversation.peerOnline) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.onlineNow,
                            style = MaterialTheme.typography.labelSmall,
                            color = SignalEmerald
                        )
                    }
                }
            }

            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = strings.chatsTitle,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ConversationActionItem(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint
        )
    }
}
