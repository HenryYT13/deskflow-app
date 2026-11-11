package com.deskflow.app.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import android.content.Intent
import android.widget.Toast
import com.deskflow.app.DeskFlowApp
import com.deskflow.app.data.local.SecurePreferences
import com.deskflow.app.data.repository.ApiResult
import com.deskflow.app.domain.model.ChallengeInfo
import com.deskflow.app.service.FocusNotificationService
import com.deskflow.app.ui.focus.ChallengeDialog
import com.deskflow.app.ui.focus.FocusActiveNoticeDialog
import com.deskflow.app.ui.navigation.Screen
import com.deskflow.app.ui.onboarding.OnboardingScreen
import com.deskflow.app.ui.pairing.PairingScreen
import com.deskflow.app.ui.theme.DeskFlowTheme
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val pendingOpenChallengeState = mutableStateOf(false)

    override fun attachBaseContext(newBase: Context?) {
        if (newBase == null) {
            super.attachBaseContext(null)
            return
        }
        val lang = try {
            SecurePreferences(newBase).language
        } catch (_: Exception) {
            "vi"
        }
        val locale = Locale(lang.ifBlank { "vi" })
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        val localizedContext = newBase.createConfigurationContext(config)
        super.attachBaseContext(localizedContext)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_CHALLENGE, false)) {
            pendingOpenChallengeState.value = true
        }
    }

    override fun onResume() {
        super.onResume()
        FocusNotificationService.onAppForegroundChanged(true)
    }

    override fun onStop() {
        super.onStop()
        FocusNotificationService.onAppForegroundChanged(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.getBooleanExtra(EXTRA_OPEN_CHALLENGE, false) == true) {
            pendingOpenChallengeState.value = true
        }

        val app = application as DeskFlowApp
        val repository = app.focusRepository
        val securePreferences = app.securePreferences
        val activityStore = app.activityStore

        setContent {
            // Notification permission launcher for Android 13+ (created in root Activity composition)
            val notificationLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { _ -> }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            // Dynamic Language Switching via CompositionLocalProvider
            val currentLanguage by securePreferences.languageFlow.collectAsState()
            val appTheme by securePreferences.appThemeFlow.collectAsState()
            val darkModeStart by securePreferences.darkModeStartFlow.collectAsState()
            val darkModeEnd by securePreferences.darkModeEndFlow.collectAsState()
            val dynamicColor by securePreferences.dynamicColorFlow.collectAsState()
            val locale = remember(currentLanguage) { Locale(currentLanguage) }
            val currentConfig = LocalConfiguration.current
            val configuration = remember(currentLanguage, currentConfig) {
                Configuration(currentConfig).apply {
                    setLocale(locale)
                }
            }
            val configContext = remember(currentLanguage) {
                baseContext.createConfigurationContext(configuration)
            }
            val localizedContext = remember(currentLanguage, configContext) {
                LocalizedActivityContext(this@MainActivity, configContext)
            }

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides configuration,
                LocalActivityResultRegistryOwner provides this@MainActivity
            ) {
                DeskFlowTheme(
                    themeMode = appTheme,
                    darkModeStart = darkModeStart,
                    darkModeEnd = darkModeEnd,
                    dynamicColor = dynamicColor
                ) {
                    key(currentLanguage) {
                        val context = LocalContext.current
                        val coroutineScope = rememberCoroutineScope()
                        val focusState by repository.focusState.collectAsState()

                        // State to ensure the Active Focus popup appears once per session transition
                        var hasShownActiveDialogForCurrentSession by rememberSaveable { mutableStateOf(false) }
                        var showActiveNoticeDialog by remember { mutableStateOf(false) }
                        var activeChallengeInfo by remember { mutableStateOf<ChallengeInfo?>(null) }

                        LaunchedEffect(pendingOpenChallengeState.value) {
                            if (pendingOpenChallengeState.value) {
                                pendingOpenChallengeState.value = false
                                val res = repository.requestChallenge()
                                if (res is ApiResult.Success) {
                                    activeChallengeInfo = res.data
                                } else if (res is ApiResult.Error) {
                                    Toast.makeText(context, "Không thể tạo thử thách: ${res.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }

                        if (activeChallengeInfo != null) {
                            ChallengeDialog(
                                initialChallenge = activeChallengeInfo!!,
                                repository = repository,
                                onDismiss = { activeChallengeInfo = null },
                                onSuccessUnlocked = {
                                    activeChallengeInfo = null
                                    coroutineScope.launch {
                                        repository.refreshAll()
                                        FocusNotificationService.stopService(this@MainActivity)
                                    }
                                }
                            )
                        }

                        // Automatically manage persistent notification service based on Focus Mode state
                        LaunchedEffect(focusState.isActive) {
                            if (focusState.isActive) {
                                FocusNotificationService.startService(context)
                                if (!hasShownActiveDialogForCurrentSession) {
                                    showActiveNoticeDialog = true
                                    hasShownActiveDialogForCurrentSession = true
                                }
                            } else {
                                FocusNotificationService.stopService(context)
                                hasShownActiveDialogForCurrentSession = false
                                showActiveNoticeDialog = false
                            }
                        }

                        // Polished Focus Mode Active Dialog
                        if (showActiveNoticeDialog && focusState.isActive) {
                            FocusActiveNoticeDialog(
                                onDismiss = {
                                    showActiveNoticeDialog = false
                                },
                                onRequestDeactivation = {
                                    showActiveNoticeDialog = false
                                    coroutineScope.launch {
                                        val res = repository.requestChallenge()
                                        if (res is ApiResult.Success) {
                                            activeChallengeInfo = res.data
                                        }
                                    }
                                }
                            )
                        }

                        // Refresh status on lifecycle resume
                        val lifecycleOwner = LocalLifecycleOwner.current
                        DisposableEffect(lifecycleOwner) {
                            val observer = LifecycleEventObserver { _, event ->
                                if (event == Lifecycle.Event.ON_RESUME) {
                                    coroutineScope.launch {
                                        repository.refreshAll()
                                    }
                                }
                            }
                            lifecycleOwner.lifecycle.addObserver(observer)
                            onDispose {
                                lifecycleOwner.lifecycle.removeObserver(observer)
                            }
                        }

                        // Navigation: Root NavHost switches between Onboarding, Pairing, and the Unified DeskFlow screen
                        val rootNavController = rememberNavController()
                        val startDestination = when {
                            !securePreferences.hasCompletedOnboarding -> Screen.Onboarding.route
                            !securePreferences.isPaired -> Screen.Pairing.route
                            else -> Screen.Main.route
                        }

                        NavHost(
                            navController = rootNavController,
                            startDestination = startDestination,
                            modifier = Modifier.fillMaxSize(),
                            enterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(300)) { it / 4 } },
                            exitTransition = { fadeOut(tween(250)) + slideOutHorizontally(tween(250)) { -it / 4 } },
                            popEnterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(300)) { -it / 4 } },
                            popExitTransition = { fadeOut(tween(250)) + slideOutHorizontally(tween(250)) { it / 4 } }
                        ) {
                            composable(Screen.Onboarding.route) {
                                OnboardingScreen(
                                    onFinish = {
                                        securePreferences.hasCompletedOnboarding = true
                                        val target = if (securePreferences.isPaired) Screen.Main.route else Screen.Pairing.route
                                        rootNavController.navigate(target) {
                                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                                        }
                                    }
                                )
                            }

                            composable(Screen.Pairing.route) {
                                PairingScreen(
                                    repository = repository,
                                    onPairedSuccess = {
                                        rootNavController.navigate(Screen.Main.route) {
                                            popUpTo(Screen.Pairing.route) { inclusive = true }
                                        }
                                    }
                                )
                            }

                            // Single Unified DeskFlow Screen
                            composable(Screen.Main.route) {
                                DeskFlowUnifiedScreen(
                                    repository = repository,
                                    securePreferences = securePreferences,
                                    activityStore = activityStore,
                                    onUnpaired = {
                                        FocusNotificationService.stopService(context)
                                        rootNavController.navigate(Screen.Pairing.route) {
                                            popUpTo(Screen.Main.route) { inclusive = true }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * ContextWrapper that routes resources and assets to the dynamically configured locale
     * while preserving the parent ComponentActivity as the base context and delegating
     * ActivityResultRegistryOwner.
     */
    private class LocalizedActivityContext(
        private val activity: MainActivity,
        private val configContext: Context
    ) : ContextWrapper(activity), ActivityResultRegistryOwner {

        override fun getResources(): Resources = configContext.resources

        override fun getAssets(): AssetManager = configContext.assets

        override val activityResultRegistry: ActivityResultRegistry
            get() = activity.activityResultRegistry
    }

    companion object {
        const val EXTRA_OPEN_CHALLENGE = "com.deskflow.app.OPEN_CHALLENGE"
    }
}
