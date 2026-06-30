package nac.chat.activities

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import android.view.Menu
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOutExpo
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import nac.chat.BuildConfig
import nac.chat.R
import nac.chat.NACApplication
import nac.chat.api.HitRateLimitException
import nac.chat.api.StoatAPI
import nac.chat.api.StoatHttp
import nac.chat.api.UpgradeRequiredState
import nac.chat.api.api
import nac.chat.api.routes.microservices.geo.queryGeo
import nac.chat.api.routes.microservices.health.healthCheck
import nac.chat.api.routes.onboard.needsOnboarding
import nac.chat.api.settings.Experiments
import nac.chat.api.settings.GeoStateProvider
import nac.chat.api.settings.LoadedSettings
import nac.chat.api.settings.SyncedSettings
import nac.chat.c2dm.NotificationDeepLink
import nac.chat.composables.generic.HealthAlert
import nac.chat.composables.voice.VoicePermissionSwitch
import nac.chat.composables.voice.VoiceSheet
import nac.chat.core.model.schemas.HealthNotice
import nac.chat.material.EasingTokens
import nac.chat.persistence.KVStorage
import nac.chat.screens.DefaultDestinationScreen
import nac.chat.services.VoiceCallService
import nac.chat.screens.about.AboutScreen
import nac.chat.screens.about.AttributionScreen
import nac.chat.screens.changelogs.ReadChangelogScreen
import nac.chat.screens.chat.ChannelPinsScreen
import nac.chat.screens.create.AddGroupMemberScreen
import nac.chat.screens.chat.views.forum.NewForumPostScreen
import nac.chat.screens.chat.views.forum.ForumPostDetailScreen
import nac.chat.screens.chat.ChatRouterScreen
import nac.chat.screens.chat.standalone.CatchUpScreen
import nac.chat.screens.chat.views.channel.ChannelScreen
import nac.chat.screens.create.CreateGroupScreen
import nac.chat.screens.labs.LabsRootScreen
import nac.chat.screens.login.LoginGreetingScreen
import nac.chat.screens.login.LoginScreen
import nac.chat.screens.login.MfaScreen
import nac.chat.screens.login2.InitScreen
import nac.chat.screens.main.MainScreen
import nac.chat.screens.register.OnboardingScreen
import nac.chat.screens.register.RegisterDetailsScreen
import nac.chat.screens.register.RegisterGreetingScreen
import nac.chat.screens.register.RegisterVerifyScreen
import nac.chat.screens.services.DiscoverScreen
import nac.chat.screens.settings.AccountSettingsScreen
import nac.chat.screens.settings.AppearanceSettingsScreen
import nac.chat.screens.settings.ChatSettingsScreen
import nac.chat.screens.settings.DebugSettingsScreen
import nac.chat.screens.settings.ExperimentsSettingsScreen
import nac.chat.screens.settings.LanguagePickerSettingsScreen
import nac.chat.screens.settings.MfaSettingsScreen
import nac.chat.screens.settings.NotificationsSettingsScreen
import nac.chat.screens.settings.ProfileSettingsScreen
import nac.chat.screens.settings.SessionSettingsScreen
import nac.chat.screens.settings.SettingsScreen
import nac.chat.screens.settings.channel.ChannelPermissionEditor
import nac.chat.screens.settings.channel.ChannelSettingsHome
import nac.chat.screens.settings.channel.ChannelSettingsOverview
import nac.chat.screens.settings.channel.ChannelSettingsPermissions
import nac.chat.screens.settings.channel.ChannelSettingsWebhookView
import nac.chat.screens.settings.channel.ChannelSettingsWebhooks
import nac.chat.ui.theme.NACTheme
import com.google.android.material.color.DynamicColors
import io.ktor.client.request.get
import io.sentry.android.core.SentryAndroid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivityViewModel(
    private val kvStorage: KVStorage,
    private val context: Context
) : ViewModel() {
    val nextDestination = MutableStateFlow<String?>(null)
    var isConnected = MutableStateFlow(false)
    val isReady = MutableStateFlow(false)
    val couldNotLogIn = MutableStateFlow(false)

    private fun hasInternetConnection(): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = connectivityManager.activeNetwork ?: return false
        val capabilities =
            connectivityManager.getNetworkCapabilities(network) ?: return false

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> true
            else -> false
        }
    }

    private suspend fun canReachStoat(): Boolean {
        try {
            val res = StoatHttp.get("/".api())
            return res.status.value == 200
        } catch (e: Exception) {
            return false
        }
    }

    private suspend fun startWithDestination(destination: String) {
        nextDestination.emit(destination)
        isReady.emit(true)
    }

    private suspend fun startWithoutDestination() {
        isReady.emit(true)
    }

    private fun doPreStartupTasks() {
        Log.d("MainActivity", "Performing pre-startup tasks")
        viewModelScope.launch {
            Log.d("MainActivity", "Hydrating Experiments from KV")
            Experiments.hydrateWithKv()
            Log.d("MainActivity", "Hydrating Favorites from KV")
            nac.chat.api.internals.Favorites.hydrate()
            Log.d("MainActivity", "Performing health check")
            doHealthCheck()
            Log.d("MainActivity", "Performing update geo state")
            updateGeoState()
        }
    }

    private suspend fun updateGeoState() {
        try {
            Log.d("MainActivity", "Querying geo state")
            GeoStateProvider.updateGeoState(queryGeo())
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to query geo state", e)
        }
    }

    fun checkLoggedInState() {
        viewModelScope.launch {
            Log.d("MainActivity", "Checking logged in state")

            isConnected.emit(hasInternetConnection())

            Log.d("MainActivity", "Checking if we can reach Stoat")

            if (!isConnected.value) return@launch startWithoutDestination()

            Log.d("MainActivity", "We can reach Stoat, checking if we're logged in")

            val token = kvStorage.get("sessionToken")
                ?: return@launch startWithDestination("login/greeting")
            val id = kvStorage.get("sessionId") ?: ""

            Log.d(
                "MainActivity",
                "We have a session token, checking if it's valid and if we can still reach Stoat"
            )

            val canReachStoat = canReachStoat()
            val valid = try {
                StoatAPI.checkSessionToken(token)
            } catch (e: Throwable) {
                false
            }

            if (canReachStoat && !valid) {
                Log.d("MainActivity", "Session token is invalid, could not log in")
                couldNotLogIn.emit(true)
            } else {
                try {
                    Log.d("MainActivity", "Session token is valid, checking onboarding state")
                    val onboard = needsOnboarding(token)
                    if (onboard) {
                        Log.d("MainActivity", "Onboarding state is incomplete, starting onboarding")
                        startWithDestination("register/onboarding")
                        return@launch
                    }
                } catch (e: HitRateLimitException) {
                    Log.e("MainActivity", "Rate limited while checking onboarding state", e)
                    Toast.makeText(
                        context,
                        context.getString(R.string.rate_limit_toast),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch startWithoutDestination()
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to check onboarding state, could not log in", e)
                    couldNotLogIn.emit(true)
                }

                try {
                    Log.d("MainActivity", "Onboarding state is complete, logging in")
                    StoatAPI.loginAs(token)
                    StoatAPI.setSessionId(id)
                    if (Experiments.usePolar.isEnabled) {
                        startWithDestination("main")
                    } else {
                        startWithDestination("chat")
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to login, could not log in", e)
                    couldNotLogIn.emit(true)
                }
            }
        }
    }

    fun logOut() {
        viewModelScope.launch {
            kvStorage.remove("sessionToken")
            kvStorage.remove("sessionId")
            kvStorage.remove("selfId")
            kvStorage.remove("selfName")
            kvStorage.remove("selfAvatarUrl")
            startWithDestination("login/greeting")
        }
    }

    fun updateNextDestination(destination: String) {
        viewModelScope.launch {
            nextDestination.emit(null)
            nextDestination.emit(destination)
        }
    }

    val activeAlert = MutableStateFlow<HealthNotice?>(null)
    val isAlertActive = MutableStateFlow(false)

    private fun doHealthCheck() {
        viewModelScope.launch {
            try {
                val health = healthCheck()
                if (health.alert != null) {
                    activeAlert.emit(health)
                    isAlertActive.emit(true)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to perform health check", e)
            }
        }
    }

    fun onDismissHealthAlert() {
        viewModelScope.launch {
            activeAlert.emit(null)
            isAlertActive.emit(false)
        }
    }

    fun onDismissLoginError() {
        viewModelScope.launch {
            couldNotLogIn.emit(false)
        }
    }

    init {
        Log.d("MainActivity", "Starting up")
        doPreStartupTasks()
        checkLoggedInState()
    }
}

class MainActivity : AppCompatActivity() {
    private val viewModel: MainActivityViewModel by viewModel()

    // Fix for SDK >=31, where core-splashscreen accidentally removes dynamic colours
    // See the other one in DefaultDestinationScreen.kt
    override fun onResume() {
        super.onResume()
        DynamicColors.applyToActivityIfAvailable(this)
        DynamicColors.applyToActivitiesIfAvailable(NACApplication.instance)
        @Suppress("DEPRECATION") // We are fixing a bug in the splash screen
        window.statusBarColor = Color.Transparent.toArgb()
    }

    // Same as above for configuration changes (rotation, dark mode, etc.)
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        DynamicColors.applyToActivityIfAvailable(this)
        DynamicColors.applyToActivitiesIfAvailable(NACApplication.instance)
        @Suppress("DEPRECATION") // We are fixing a bug in the splash screen
        window.statusBarColor = Color.Transparent.toArgb()
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        SentryAndroid.init(this) { options ->
            options.dsn = BuildConfig.SENTRY_DSN
            options.release = BuildConfig.VERSION_NAME
        }

        @Suppress("DEPRECATION") // We are fixing a bug in the splash screen
        window.statusBarColor = Color.Transparent.toArgb()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        StoatAPI.hydrateFromPersistentCache()

        intent.getStringExtra("channelId")?.let { NotificationDeepLink.pendingChannelId.value = it }

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            AppEntrypoint(
                windowSizeClass,
                viewModel.nextDestination.collectAsState().value,
                viewModel.isConnected.collectAsState().value,
                viewModel.activeAlert.collectAsState().value,
                viewModel.isAlertActive.collectAsState().value,
                viewModel.couldNotLogIn.collectAsState().value,
                viewModel::logOut,
                viewModel::onDismissHealthAlert,
                viewModel::onDismissLoginError,
                viewModel::checkLoggedInState,
                viewModel::updateNextDestination
            )
        }

        val content: View = findViewById(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    // Check whether the initial data is ready.
                    return if (viewModel.isReady.value) {
                        // The content is ready. Start drawing.
                        content.viewTreeObserver.removeOnPreDrawListener(this)
                        true
                    } else {
                        // The content isn't ready. Suspend.
                        false
                    }
                }
            }
        )
    }

    override fun onProvideKeyboardShortcuts(
        data: MutableList<KeyboardShortcutGroup>?,
        menu: Menu?,
        deviceId: Int
    ) {
        val messaging = KeyboardShortcutGroup(
            getString(R.string.keyboard_shortcut_messaging),
            listOf(
                KeyboardShortcutInfo(
                    getString(R.string.keyboard_shortcut_messaging_new_line),
                    KeyEvent.KEYCODE_ENTER,
                    0
                ),
                KeyboardShortcutInfo(
                    getString(R.string.keyboard_shortcut_messaging_send_message),
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.META_CTRL_ON
                )
            )
        )

        data?.add(messaging)
    }

}

val StoatTweenInt: FiniteAnimationSpec<IntOffset> = tween(400, easing = EaseInOutExpo)
val StoatTweenFloat: FiniteAnimationSpec<Float> = tween(400, easing = EaseInOutExpo)
val StoatTweenDp: FiniteAnimationSpec<Dp> = tween(400, easing = EaseInOutExpo)
val StoatTweenColour: FiniteAnimationSpec<Color> = tween(400, easing = EaseInOutExpo)

val NavTweenInt: FiniteAnimationSpec<IntOffset> = tween(350, easing = EaseInOutExpo)
val NavTweenFloat: FiniteAnimationSpec<Float> = tween(350, easing = EaseInOutExpo)

// This composable handles the main compose entrypoint of the app, provides the main navigation
// graph, and handles the animation and layout for the voice chat UI.
@Composable
fun AppEntrypoint(
    windowSizeClass: WindowSizeClass,
    nextDestination: String?,
    isConnected: Boolean,
    healthNotice: HealthNotice?,
    isHealthAlertActive: Boolean,
    couldNotLogIn: Boolean,
    onLogout: () -> Unit = {},
    onDismissHealthAlert: () -> Unit = {},
    onDismissLoginError: () -> Unit = {},
    onRetryConnection: () -> Unit,
    onUpdateNextDestination: (String) -> Unit = {}
) {
    var showVoiceUI by rememberSaveable { mutableStateOf(false) }
    var voiceChannelId by rememberSaveable { mutableStateOf<String?>(null) }
    // Rejoining the same channel right after hanging up can otherwise hand VoiceSheet
    // back stale viewModel()/RoomScope state instead of a truly fresh one, so the new
    // join token never actually gets used to connect - the join silently times out.
    // Bumping this on every join forces a brand-new composable instance each time.
    var voiceJoinAttempt by rememberSaveable { mutableStateOf(0) }

    val chatUIScale by animateFloatAsState(
        if (showVoiceUI) 0.8f else 1.0f,
        animationSpec = tween(
            durationMillis = 300,
            easing = EasingTokens.EmphasizedDecelerate
        )
    )
    val chatUIOpacity by animateFloatAsState(
        if (showVoiceUI) 0.8f else 1.0f,
        animationSpec = tween(
            durationMillis = 300,
            easing = EasingTokens.EmphasizedDecelerate
        )
    )

    BackHandler(showVoiceUI) {
        showVoiceUI = false
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(showVoiceUI) {
        if (showVoiceUI) keyboardController?.hide()
    }

    val navController = rememberNavController()

    NACTheme(
        requestedTheme = LoadedSettings.theme,
        requestedUserInterfaceFont = LoadedSettings.font,
        colourOverrides = SyncedSettings.android.colourOverrides
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .scale(chatUIScale)
                    .alpha(chatUIOpacity),
                color = MaterialTheme.colorScheme.background
            ) {
                if (isHealthAlertActive) {
                    healthNotice?.let {
                        HealthAlert(notice = healthNotice, onDismiss = onDismissHealthAlert)
                    }
                }

                if (couldNotLogIn) {
                    AlertDialog(
                        onDismissRequest = {
                            // no-op
                        },
                        title = {
                            Text(stringResource(R.string.could_not_log_in_heading))
                        },
                        text = {
                            Text(stringResource(R.string.could_not_log_in_body))
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    onDismissLoginError()
                                    onRetryConnection()
                                }
                            ) {
                                Text(stringResource(R.string.could_not_log_in_cta_try_again))
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    onDismissLoginError()
                                    onLogout()
                                }
                            ) {
                                Text(stringResource(R.string.could_not_log_in_cta_logout))
                            }
                        }
                    )
                }

                val isUpgradeRequired by UpgradeRequiredState.isRequired.collectAsState()
                if (isUpgradeRequired) {
                    val minVersion by UpgradeRequiredState.minVersion.collectAsState()
                    val context = LocalContext.current

                    // No onDismissRequest no-op is enough here - there is deliberately no
                    // dismiss/cancel button at all, this must stay on screen until the
                    // user updates, same intent as a forced Play Store update gate.
                    AlertDialog(
                        onDismissRequest = {},
                        title = { Text("Update required") },
                        text = {
                            Text(
                                if (minVersion != null) {
                                    "An update is required to continue using NAC (minimum version $minVersion)."
                                } else {
                                    "An update is required to continue using NAC."
                                }
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    CustomTabsIntent.Builder().build()
                                        .launchUrl(context, Uri.parse("https://get.nac.social"))
                                }
                            ) {
                                Text("Update now")
                            }
                        }
                    )
                }

                NavHost(
                    navController = navController,
                    startDestination = "default",
                    enterTransition = {
                        slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.Left,
                            animationSpec = NavTweenInt,
                            initialOffset = { it / 3 }
                        ) + fadeIn(animationSpec = NavTweenFloat)
                    },
                    exitTransition = {
                        slideOutOfContainer(
                            AnimatedContentTransitionScope.SlideDirection.Left,
                            animationSpec = NavTweenInt,
                            targetOffset = { it / 3 }
                        ) + fadeOut(animationSpec = NavTweenFloat)
                    },
                    popEnterTransition = {
                        slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.Right,
                            animationSpec = NavTweenInt,
                            initialOffset = { it / 3 }
                        ) + fadeIn(animationSpec = NavTweenFloat)
                    },
                    popExitTransition = {
                        slideOutOfContainer(
                            AnimatedContentTransitionScope.SlideDirection.Right,
                            animationSpec = NavTweenInt,
                            targetOffset = { it / 2 }
                        ) + fadeOut(animationSpec = NavTweenFloat)
                    }
                ) {
                    composable("default") {
                        DefaultDestinationScreen(
                            navController,
                            nextDestination,
                            isConnected,
                            onRetryConnection
                        )
                    }

                    composable("login/greeting") { LoginGreetingScreen(navController) }
                    composable("login/login") { LoginScreen(navController) }
                    composable("login/mfa/{mfaTicket}/{allowedAuthTypes}") { backStackEntry ->
                        val mfaTicket = backStackEntry.arguments?.getString("mfaTicket") ?: ""
                        val allowedAuthTypes =
                            backStackEntry.arguments?.getString("allowedAuthTypes") ?: ""

                        MfaScreen(navController, allowedAuthTypes, mfaTicket)
                    }

                    composable("register/greeting") { RegisterGreetingScreen(navController) }
                    composable("register/details") { RegisterDetailsScreen(navController) }
                    composable("register/verify/{email}") { backStackEntry ->
                        val email = backStackEntry.arguments?.getString("email") ?: ""

                        RegisterVerifyScreen(navController, email)
                    }
                    composable("register/onboarding") {
                        OnboardingScreen(
                            navController,
                            onOnboardingComplete = {
                                onUpdateNextDestination("chat")
                                navController.popBackStack(
                                    navController.graph.startDestinationRoute!!,
                                    inclusive = true
                                )
                                navController.navigate("default")
                            }
                        )
                    }

                    composable("login2/init") { InitScreen(navController, windowSizeClass) }

                    // This is only used outside of Polar mode
                    // Otherwise you may be looking for "main" right below
                    composable(
                        "chat",
                        enterTransition = {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.Up,
                                animationSpec = tween(
                                    400,
                                    easing = EasingTokens.EmphasizedDecelerate
                                ),
                                initialOffset = { it / 3 }
                            ) + fadeIn(animationSpec = StoatTweenFloat)
                        }
                    ) {
                        ChatRouterScreen(
                            navController,
                            windowSizeClass,
                            disableBackHandler = showVoiceUI,
                            onNullifiedUser = {
                                onRetryConnection()
                                navController.popBackStack(
                                    navController.graph.startDestinationRoute!!,
                                    inclusive = true
                                )
                                navController.navigate("default")
                            },
                            onEnterVoiceUI = { channelId ->
                                showVoiceUI = true
                                voiceChannelId = channelId
                                voiceJoinAttempt++
                            },
                        )
                    }

                    // This is only the main screen in Polar mode
                    // Otherwise you may be looking for "chat" right above
                    composable(
                        "main",
                        enterTransition = {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.Up,
                                animationSpec = tween(
                                    400,
                                    easing = EasingTokens.EmphasizedDecelerate
                                ),
                                initialOffset = { it / 3 }
                            ) + fadeIn(animationSpec = StoatTweenFloat) + scaleIn(
                                animationSpec = tween(
                                    400,
                                    easing = EasingTokens.EmphasizedDecelerate
                                ),
                                initialScale = 0.8f,
                                transformOrigin = TransformOrigin.Center
                            )
                        }
                    ) {
                        MainScreen(navController)
                    }
                    composable(
                        "main/conversation/{channelId}",
                        enterTransition = {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.Left,
                                animationSpec = tween(
                                    600,
                                    easing = EasingTokens.EmphasizedDecelerate
                                ),
                                initialOffset = { it }
                            ) + fadeIn(animationSpec = StoatTweenFloat)
                        },
                        exitTransition = {
                            slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.Right,
                                animationSpec = tween(
                                    600,
                                    easing = EasingTokens.EmphasizedDecelerate
                                ),
                                targetOffset = { it }
                            ) + fadeOut(animationSpec = StoatTweenFloat)
                        }
                    ) { backStackEntry ->
                        val channelId = backStackEntry.arguments?.getString("channelId") ?: ""
                        ChannelScreen(
                            channelId = channelId,
                            onToggleDrawer = {},
                            useDrawer = false,
                            useBackButton = true,
                            backButtonAction = {
                                navController.popBackStack()
                            },
                            useChatUI = true
                        )
                    }

                    composable("catchup") { CatchUpScreen(navController) }

                    composable("create/group") { CreateGroupScreen(navController) }

                    composable("discover") { DiscoverScreen(navController) }

                    composable("settings") { SettingsScreen(navController) }
                    composable("settings/account") { AccountSettingsScreen(navController) }
                    composable("settings/account/mfa") { MfaSettingsScreen(navController) }
                    composable("settings/profile") { ProfileSettingsScreen(navController) }
                    composable("settings/sessions") { SessionSettingsScreen(navController) }
                    composable("settings/appearance") { AppearanceSettingsScreen(navController) }
                    composable("settings/chat") { ChatSettingsScreen(navController) }
                    composable("settings/notifications") { NotificationsSettingsScreen(navController) }
                    composable("settings/debug") { DebugSettingsScreen(navController) }
                    composable("settings/experiments") { ExperimentsSettingsScreen(navController) }
                    composable("settings/language") { LanguagePickerSettingsScreen(navController) }

                    composable("settings/channel/{channelId}") { backStackEntry ->
                        val channelId = backStackEntry.arguments?.getString("channelId") ?: ""
                        ChannelSettingsHome(navController, channelId)
                    }
                    composable("settings/channel/{channelId}/overview") { backStackEntry ->
                        val channelId = backStackEntry.arguments?.getString("channelId") ?: ""
                        ChannelSettingsOverview(navController, channelId)
                    }
                    composable("settings/channel/{channelId}/permissions") { backStackEntry ->
                        val channelId = backStackEntry.arguments?.getString("channelId") ?: ""
                        ChannelSettingsPermissions(navController, channelId)
                    }
                    composable("settings/channel/{channelId}/permissions/{roleId}") { backStackEntry ->
                        val channelId = backStackEntry.arguments?.getString("channelId") ?: ""
                        val roleId = backStackEntry.arguments?.getString("roleId") ?: "default"
                        ChannelPermissionEditor(navController, channelId, roleId)
                    }
                    composable("settings/channel/{channelId}/webhooks") { backStackEntry ->
                        val channelId = backStackEntry.arguments?.getString("channelId") ?: ""
                        ChannelSettingsWebhooks(navController, channelId)
                    }
                    composable("settings/channel/{channelId}/webhooks/{webhookId}") { backStackEntry ->
                        val channelId = backStackEntry.arguments?.getString("channelId") ?: ""
                        val webhookId = backStackEntry.arguments?.getString("webhookId") ?: ""
                        ChannelSettingsWebhookView(navController, channelId, webhookId)
                    }

                    composable("channel/{channelId}/pins") { backStackEntry ->
                        val channelId = backStackEntry.arguments?.getString("channelId") ?: ""
                        ChannelPinsScreen(navController, channelId)
                    }

                    composable("channel/{channelId}/add_member") { backStackEntry ->
                        val channelId = backStackEntry.arguments?.getString("channelId") ?: ""
                        AddGroupMemberScreen(navController, channelId)
                    }

                    composable("forum/{channelId}/new_post") { backStackEntry ->
                        val channelId = backStackEntry.arguments?.getString("channelId") ?: ""
                        val channel = StoatAPI.channelCache[channelId]
                        if (channel != null) NewForumPostScreen(navController, channel)
                    }

                    composable("forum/{channelId}/post/{postId}") { backStackEntry ->
                        val channelId = backStackEntry.arguments?.getString("channelId") ?: ""
                        val postId = backStackEntry.arguments?.getString("postId") ?: ""
                        val channel = StoatAPI.channelCache[channelId]
                        if (channel != null) ForumPostDetailScreen(navController, channel, postId)
                    }

                    composable("about") { AboutScreen(navController) }
                    composable("about/oss") { AttributionScreen(navController) }

                    composable("labs") { LabsRootScreen(navController) }

                    composable("changelog/{id}") { ReadChangelogScreen(navController) }
                }
            }

            if (showVoiceUI) { // if tapped outside the voice UI, close it
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            showVoiceUI = false
                        }
                )
            }

            AnimatedVisibility(
                visible = showVoiceUI,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(
                    initialOffsetY = { it -> it },
                    animationSpec = tween(
                        durationMillis = 300,
                        easing = EasingTokens.EmphasizedDecelerate
                    )
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it -> it },
                    animationSpec = tween(
                        durationMillis = 300,
                        easing = EasingTokens.EmphasizedDecelerate
                    )
                )
            ) {
                // We need a box as applying the padding elsewhere leads to either
                // janky animation or layout
                Box(Modifier.safeDrawingPadding()) {
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .widthIn(max = 600.dp)
                            .padding(8.dp)
                    ) {
                        VoicePermissionSwitch(
                            onCancel = {
                                showVoiceUI = false
                            }
                        ) {
                            val voiceServiceContext = LocalContext.current
                            voiceChannelId?.let {
                                // Keyed on voiceJoinAttempt so rejoining the same channel
                                // always gets a fully fresh VoiceSheet/RoomScope/viewModel()
                                // instance rather than possibly stale state from the
                                // previous call - see nac-android#19.
                                key(voiceJoinAttempt) {
                                    VoiceSheet(
                                        it,
                                        onDisconnect = {
                                            // Stop this directly and immediately, rather than relying
                                            // on a reactive roomState listener that gets torn down in
                                            // the same composition pass as everything else below -
                                            // that race is exactly why the "Voice call active"
                                            // notification was sticking around after hanging up.
                                            voiceServiceContext.stopService(
                                                Intent(voiceServiceContext, VoiceCallService::class.java)
                                            )
                                            showVoiceUI = false
                                            voiceChannelId = null
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
