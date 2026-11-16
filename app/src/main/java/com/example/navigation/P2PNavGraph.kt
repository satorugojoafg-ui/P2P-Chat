package com.example.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.AppContainer
import com.example.authentication.AuthViewModel
import com.example.authentication.SessionStatus
import com.example.chat.ChatListScreen
import com.example.chat.ChatListViewModel
import com.example.chat.PrivateChatScreen
import com.example.chat.PrivateChatViewModel
import com.example.chat.UserSearchScreen
import com.example.chat.UserSearchViewModel
import com.example.localization.LocalAppStrings
import com.example.models.UserProfile
import com.example.models.UserSettings
import com.example.profile.EditProfileScreen
import com.example.profile.EditProfileViewModel
import com.example.profile.UserProfileScreen
import com.example.profile.UserProfileViewModel
import com.example.screens.AboutScreen
import com.example.screens.LoginScreen
import com.example.screens.RegistrationScreen
import com.example.screens.SplashScreen
import com.example.screens.WelcomeScreen
import com.example.settings.NotificationPreferencesScreen
import com.example.settings.PrivacyDashboardScreen
import com.example.settings.SettingsScreen
import com.example.settings.SettingsViewModel
import com.example.settings.StorageManagementScreen
import com.example.ui.theme.FuturisticBackground

@Composable
fun P2PNavGraph(
    container: AppContainer,
    authViewModel: AuthViewModel,
    settingsViewModel: SettingsViewModel
) {
    val navController = rememberNavController()
    val sessionStatus by authViewModel.sessionStatus.collectAsStateWithLifecycle()
    val authFormState by authViewModel.formState.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val currentUser by container.authRepository.currentUser.collectAsStateWithLifecycle()

    LaunchedEffect(sessionStatus) {
        when (sessionStatus) {
            SessionStatus.Checking -> Unit
            SessionStatus.Unauthenticated -> {
                container.webRtcManager.stopSignalingPolling()
                val target: AppRoute = if (authFormState.sessionExpiredNotice) {
                    AppRoute.Login
                } else {
                    AppRoute.Welcome
                }
                navController.navigate(target) {
                    popUpTo(0) { inclusive = true }
                }
            }
            is SessionStatus.Authenticated -> {
                container.webRtcManager.startSignalingPolling()
                navController.navigate(AppRoute.MainHome) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            container.webRtcManager.stopSignalingPolling()
        }
    }

    NavHost(
        navController = navController,
        startDestination = AppRoute.Splash,
        enterTransition = {
            fadeIn(animationSpec = tween(220)) + slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(220)
            )
        },
        exitTransition = {
            fadeOut(animationSpec = tween(180))
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(220))
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(180)) + slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(220)
            )
        }
    ) {
        composable<AppRoute.Splash> {
            SplashScreen()
        }

        composable<AppRoute.Welcome> {
            WelcomeScreen(
                currentLanguage = settings.language,
                onToggleLanguage = { settingsViewModel.setLanguage(it) },
                onNavigateToRegister = {
                    authViewModel.clearErrors()
                    navController.navigate(AppRoute.Register)
                },
                onNavigateToLogin = {
                    authViewModel.clearErrors()
                    navController.navigate(AppRoute.Login)
                }
            )
        }

        composable<AppRoute.Login> {
            LoginScreen(
                formState = authFormState,
                onLogin = { user, pass ->
                    authViewModel.dismissSessionExpiredNotice()
                    authViewModel.login(user, pass)
                },
                onClearErrors = { authViewModel.clearErrors() },
                onNavigateToRegister = {
                    authViewModel.clearErrors()
                    navController.navigate(AppRoute.Register)
                },
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(AppRoute.Welcome) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable<AppRoute.Register> {
            RegistrationScreen(
                formState = authFormState,
                onRegister = { user, display, pass, confirm ->
                    authViewModel.register(user, display, pass, confirm)
                },
                onClearErrors = { authViewModel.clearErrors() },
                onNavigateToLogin = {
                    authViewModel.clearErrors()
                    navController.navigate(AppRoute.Login)
                },
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(AppRoute.Welcome) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable<AppRoute.MainHome> {
            MainHomeShell(
                container = container,
                currentUser = currentUser,
                settings = settings,
                settingsViewModel = settingsViewModel,
                onOpenChat = { peer -> navController.navigate(AppRoute.PrivateChat(peer)) },
                onOpenPeerProfile = { peer -> navController.navigate(AppRoute.PeerProfile(peer)) },
                onOpenEditProfile = { navController.navigate(AppRoute.EditProfile) },
                onOpenNotifications = { navController.navigate(AppRoute.NotificationPreferences) },
                onOpenPrivacy = { navController.navigate(AppRoute.PrivacyDashboard) },
                onOpenStorage = { navController.navigate(AppRoute.StorageManagement) },
                onOpenAbout = { navController.navigate(AppRoute.About) },
                onLogout = { authViewModel.logout() }
            )
        }

        composable<AppRoute.PrivateChat> { backStackEntry ->
            val route = backStackEntry.toRoute<AppRoute.PrivateChat>()
            val vm: PrivateChatViewModel = viewModel(
                key = "chat_${route.peerUsername}",
                factory = simpleFactory {
                    PrivateChatViewModel(
                        peerUsername = route.peerUsername,
                        chatRepository = container.chatRepository,
                        userRepository = container.userRepository,
                        webRtcManager = container.webRtcManager
                    )
                }
            )
            PrivateChatScreen(
                peerUsername = route.peerUsername,
                wallpaper = settings.wallpaper,
                mediaTransferNotificationsEnabled = settings.mediaTransferNotifications,
                viewModel = vm,
                onOpenPeerProfile = { username ->
                    navController.navigate(AppRoute.PeerProfile(username))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable<AppRoute.PeerProfile> { backStackEntry ->
            val route = backStackEntry.toRoute<AppRoute.PeerProfile>()
            val vm: UserProfileViewModel = viewModel(
                key = "profile_${route.username}",
                factory = simpleFactory {
                    UserProfileViewModel(
                        username = route.username,
                        profileRepository = container.profileRepository,
                        userRepository = container.userRepository
                    )
                }
            )
            UserProfileScreen(
                isOwnProfile = false,
                viewModel = vm,
                onEditProfile = {},
                onMessagePeer = { peer ->
                    navController.navigate(AppRoute.PrivateChat(peer))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable<AppRoute.EditProfile> {
            val vm: EditProfileViewModel = viewModel(
                factory = simpleFactory {
                    EditProfileViewModel(container.profileRepository)
                }
            )
            EditProfileScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }

        composable<AppRoute.PrivacyDashboard> {
            PrivacyDashboardScreen(
                settings = settings,
                onSelectProfileVisibility = { settingsViewModel.setProfileVisibility(it) },
                onToggleLastSeen = { settingsViewModel.setShowLastSeen(it) },
                onToggleOnlineStatus = { settingsViewModel.setShowOnlineStatus(it) },
                onToggleNotificationPreview = { settingsViewModel.setNotificationPreview(it) },
                onToggleAppLock = { settingsViewModel.setAppLockEnabled(it) },
                onBack = { navController.popBackStack() }
            )
        }

        composable<AppRoute.NotificationPreferences> {
            NotificationPreferencesScreen(
                settings = settings,
                onToggleMessages = { settingsViewModel.setNotificationsEnabled(it) },
                onToggleMediaTransfers = { settingsViewModel.setMediaTransferNotifications(it) },
                onToggleSound = { settingsViewModel.setSoundEnabled(it) },
                onTogglePreview = { settingsViewModel.setNotificationPreview(it) },
                onBack = { navController.popBackStack() }
            )
        }

        composable<AppRoute.StorageManagement> {
            StorageManagementScreen(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable<AppRoute.About> {
            AboutScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
private fun MainHomeShell(
    container: AppContainer,
    currentUser: UserProfile?,
    settings: UserSettings,
    settingsViewModel: SettingsViewModel,
    onOpenChat: (String) -> Unit,
    onOpenPeerProfile: (String) -> Unit,
    onOpenEditProfile: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenAbout: () -> Unit,
    onLogout: () -> Unit
) {
    val strings = LocalAppStrings.current
    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.CHATS) }

    val chatListVm: ChatListViewModel = viewModel(
        factory = simpleFactory { ChatListViewModel(container.chatRepository) }
    )
    val searchVm: UserSearchViewModel = viewModel(
        factory = simpleFactory { UserSearchViewModel(container.userRepository) }
    )
    val myProfileVm: UserProfileViewModel = viewModel(
        key = "my_profile",
        factory = simpleFactory {
            UserProfileViewModel(
                username = null,
                profileRepository = container.profileRepository,
                userRepository = container.userRepository
            )
        }
    )

    val tabs = listOf(
        Triple(HomeTab.CHATS, strings.navChats, Pair(Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline)),
        Triple(HomeTab.SEARCH, strings.navSearch, Pair(Icons.Filled.Search, Icons.Outlined.Search)),
        Triple(HomeTab.PROFILE, strings.navProfile, Pair(Icons.Filled.Person, Icons.Outlined.PersonOutline)),
        Triple(HomeTab.SETTINGS, strings.navSettings, Pair(Icons.Filled.Settings, Icons.Outlined.Settings))
    )

    FuturisticBackground {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp

            if (useRail) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                ) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        tabs.forEach { (tab, label, icons) ->
                            val isSelected = selectedTab == tab
                            NavigationRailItem(
                                selected = isSelected,
                                onClick = { selectedTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (isSelected) icons.first else icons.second,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.secondary,
                                    selectedTextColor = MaterialTheme.colorScheme.secondary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag("nav_rail_${tab.name.lowercase()}")
                            )
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        HomeTabContent(
                            selectedTab = selectedTab,
                            currentUser = currentUser,
                            settings = settings,
                            chatListVm = chatListVm,
                            searchVm = searchVm,
                            myProfileVm = myProfileVm,
                            settingsViewModel = settingsViewModel,
                            onSelectTab = { selectedTab = it },
                            onOpenChat = onOpenChat,
                            onOpenPeerProfile = onOpenPeerProfile,
                            onOpenEditProfile = onOpenEditProfile,
                            onOpenNotifications = onOpenNotifications,
                            onOpenPrivacy = onOpenPrivacy,
                            onOpenStorage = onOpenStorage,
                            onOpenAbout = onOpenAbout,
                            onLogout = onLogout
                        )
                    }
                }
            } else {
                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets.navigationBars,
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                            tonalElevation = 4.dp
                        ) {
                            tabs.forEach { (tab, label, icons) ->
                                val isSelected = selectedTab == tab
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = { selectedTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (isSelected) icons.first else icons.second,
                                            contentDescription = label
                                        )
                                    },
                                    label = { Text(label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.secondary,
                                        selectedTextColor = MaterialTheme.colorScheme.secondary,
                                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.testTag("nav_bar_${tab.name.lowercase()}")
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .statusBarsPadding()
                    ) {
                        HomeTabContent(
                            selectedTab = selectedTab,
                            currentUser = currentUser,
                            settings = settings,
                            chatListVm = chatListVm,
                            searchVm = searchVm,
                            myProfileVm = myProfileVm,
                            settingsViewModel = settingsViewModel,
                            onSelectTab = { selectedTab = it },
                            onOpenChat = onOpenChat,
                            onOpenPeerProfile = onOpenPeerProfile,
                            onOpenEditProfile = onOpenEditProfile,
                            onOpenNotifications = onOpenNotifications,
                            onOpenPrivacy = onOpenPrivacy,
                            onOpenStorage = onOpenStorage,
                            onOpenAbout = onOpenAbout,
                            onLogout = onLogout
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTabContent(
    selectedTab: HomeTab,
    currentUser: UserProfile?,
    settings: UserSettings,
    chatListVm: ChatListViewModel,
    searchVm: UserSearchViewModel,
    myProfileVm: UserProfileViewModel,
    settingsViewModel: SettingsViewModel,
    onSelectTab: (HomeTab) -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenPeerProfile: (String) -> Unit,
    onOpenEditProfile: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenAbout: () -> Unit,
    onLogout: () -> Unit
) {
    when (selectedTab) {
        HomeTab.CHATS -> ChatListScreen(
            currentUser = currentUser,
            viewModel = chatListVm,
            onOpenChat = onOpenChat,
            onOpenSearch = { onSelectTab(HomeTab.SEARCH) },
            onOpenMyProfile = { onSelectTab(HomeTab.PROFILE) }
        )

        HomeTab.SEARCH -> UserSearchScreen(
            viewModel = searchVm,
            onOpenChat = onOpenChat,
            onOpenProfile = onOpenPeerProfile
        )

        HomeTab.PROFILE -> UserProfileScreen(
            isOwnProfile = true,
            viewModel = myProfileVm,
            onEditProfile = onOpenEditProfile,
            onMessagePeer = {}
        )

        HomeTab.SETTINGS -> SettingsScreen(
            currentUser = currentUser,
            settings = settings,
            onSelectLanguage = { settingsViewModel.setLanguage(it) },
            onSelectThemeMode = { settingsViewModel.setThemeMode(it) },
            onSelectFontSize = { settingsViewModel.setFontSize(it) },
            onSelectWallpaper = { settingsViewModel.setWallpaper(it) },
            onToggleHaptic = { settingsViewModel.setHapticEnabled(it) },
            onOpenEditProfile = onOpenEditProfile,
            onOpenNotifications = onOpenNotifications,
            onOpenPrivacy = onOpenPrivacy,
            onOpenStorage = onOpenStorage,
            onOpenAbout = onOpenAbout,
            onLogout = onLogout
        )
    }
}

fun <T : ViewModel> simpleFactory(creator: () -> T): ViewModelProvider.Factory =
    rememberFactory(creator)

@Suppress("UNCHECKED_CAST")
private fun <T : ViewModel> rememberFactory(creator: () -> T): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = creator() as VM
    }
