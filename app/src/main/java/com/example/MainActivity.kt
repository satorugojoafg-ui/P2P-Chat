package com.example

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.authentication.AuthViewModel
import com.example.authentication.BiometricLockHelper
import com.example.authentication.SessionStatus
import com.example.localization.LocalAppStrings
import com.example.localization.ProvideLocalization
import com.example.models.ThemeMode
import com.example.navigation.P2PNavGraph
import com.example.settings.SettingsViewModel
import com.example.ui.theme.ErrorCrimson
import com.example.ui.theme.FrostBackground
import com.example.ui.theme.FuturisticBackground
import com.example.ui.theme.GlassCard
import com.example.ui.theme.GraphiteVoid
import com.example.ui.theme.NeonPrimaryButton
import com.example.ui.theme.P2PChatTheme

class MainActivity : FragmentActivity() {

    private var lockStateCallback: ((Boolean) -> Unit)? = null
    private var biometricPromptActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = (application as P2PChatApplication).container

        setContent {
            val authViewModel: AuthViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return AuthViewModel(container.authRepository) as T
                    }
                }
            )
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return SettingsViewModel(container.settingsRepository) as T
                    }
                }
            )

            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            val sessionStatus by authViewModel.sessionStatus.collectAsStateWithLifecycle()
            var isUnlockedThisSession by rememberSaveable { mutableStateOf(false) }
            var authError by remember { mutableStateOf(false) }

            lockStateCallback = { isUnlockedThisSession = it }

            val systemDark = isSystemInDarkTheme()
            val useDarkBars = when (settings.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> systemDark
            }

            LaunchedEffect(useDarkBars) {
                if (useDarkBars) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.dark(GraphiteVoid.toArgb()),
                        navigationBarStyle = SystemBarStyle.dark(GraphiteVoid.toArgb())
                    )
                } else {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.light(
                            FrostBackground.toArgb(),
                            FrostBackground.toArgb()
                        ),
                        navigationBarStyle = SystemBarStyle.light(
                            FrostBackground.toArgb(),
                            FrostBackground.toArgb()
                        )
                    )
                }
            }

            val shouldShowLock = settings.appLockEnabled &&
                sessionStatus is SessionStatus.Authenticated &&
                !isUnlockedThisSession &&
                BiometricLockHelper.canAuthenticate(this@MainActivity)

            ProvideLocalization(language = settings.language) {
                P2PChatTheme(
                    themeMode = settings.themeMode,
                    language = settings.language,
                    fontSize = settings.fontSize,
                    hapticEnabled = settings.hapticEnabled
                ) {
                    if (shouldShowLock) {
                        val strings = LocalAppStrings.current
                        val triggerPrompt = {
                            biometricPromptActive = true
                            authError = false
                            BiometricLockHelper.authenticate(
                                activity = this@MainActivity,
                                title = strings.lockScreenTitle,
                                subtitle = strings.lockScreenSubtitle,
                                onSuccess = {
                                    biometricPromptActive = false
                                    isUnlockedThisSession = true
                                },
                                onFailure = {
                                    biometricPromptActive = false
                                    authError = true
                                }
                            )
                        }

                        LaunchedEffect(Unit) {
                            triggerPrompt()
                        }

                        AppLockOverlay(
                            showAuthError = authError,
                            onUnlockClick = { triggerPrompt() }
                        )
                    } else {
                        P2PNavGraph(
                            container = container,
                            authViewModel = authViewModel,
                            settingsViewModel = settingsViewModel
                        )
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!biometricPromptActive) {
            lockStateCallback?.invoke(false)
        }
    }
}

@Composable
private fun AppLockOverlay(
    showAuthError: Boolean,
    onUnlockClick: () -> Unit
) {
    val strings = LocalAppStrings.current
    FuturisticBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f),
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Fingerprint,
                                contentDescription = strings.lockScreenTitle,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }

                    Text(
                        text = strings.lockScreenTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = strings.lockScreenSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    if (showAuthError) {
                        Text(
                            text = strings.lockAuthFailed,
                            style = MaterialTheme.typography.labelMedium,
                            color = ErrorCrimson,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    NeonPrimaryButton(
                        text = strings.lockUnlockButton,
                        onClick = onUnlockClick,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "app_lock_unlock_button"
                    )
                }
            }
        }
    }
}
