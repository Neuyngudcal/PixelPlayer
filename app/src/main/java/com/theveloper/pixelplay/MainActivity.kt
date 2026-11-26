package com.theveloper.pixelplay

import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.navigation.navigateToTopLevelSafely

// import androidx.compose.ui.platform.LocalView // No longer needed for this
// import androidx.core.view.WindowInsetsCompat // No longer needed for this
import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader as AndroidShader
import androidx.compose.ui.graphics.asComposeRenderEffect
import android.os.Bundle
import android.os.Trace
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.CallSuper
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.foundation.gestures.detectTapGestures
import com.theveloper.pixelplay.presentation.components.player.TabletPlayerDisplayMode
import com.theveloper.pixelplay.presentation.components.player.resolveNowPlayingSheetBoundsSpec
import com.theveloper.pixelplay.ui.theme.computeEffectiveContentWindowInfo

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.theveloper.pixelplay.data.preferences.AppThemeMode
import com.theveloper.pixelplay.data.preferences.NavBarStyle
import com.theveloper.pixelplay.data.preferences.sanitizeNavBarCornerRadius
import com.theveloper.pixelplay.data.preferences.ThemePreferencesRepository
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.service.MusicService
import com.theveloper.pixelplay.data.worker.SyncManager
import com.theveloper.pixelplay.data.worker.SyncProgress
import com.theveloper.pixelplay.presentation.components.AllFilesAccessDialog
import com.theveloper.pixelplay.presentation.components.AppSidebarDrawer
import com.theveloper.pixelplay.presentation.components.CrashReportDialog
import com.theveloper.pixelplay.presentation.components.DismissUndoBar
import com.theveloper.pixelplay.presentation.components.DrawerDestination
import com.theveloper.pixelplay.presentation.components.MiniPlayerBottomSpacer
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.NavigationRailCollapsedWidth
import com.theveloper.pixelplay.presentation.components.NavigationRailExpandedWidth
import com.theveloper.pixelplay.presentation.components.PixelPlayNavigationRail
import com.theveloper.pixelplay.presentation.components.PlayerInternalNavigationBar
import com.theveloper.pixelplay.presentation.components.UnifiedPlayerSheetV2
import com.theveloper.pixelplay.presentation.components.calculatePlayerSheetCollapsedTargetY
import com.theveloper.pixelplay.presentation.components.resolveNavBarBottomPadding
import com.theveloper.pixelplay.presentation.components.resolveNavBarHorizontalPadding
import com.theveloper.pixelplay.presentation.components.resolveNavBarOccupiedHeight
import com.theveloper.pixelplay.presentation.components.resolveNavBarSurfaceHeight
import com.theveloper.pixelplay.presentation.components.sanitizeNavigationBarBottomInset
import com.theveloper.pixelplay.presentation.navigation.AppNavigation
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.screens.SetupScreen
import com.theveloper.pixelplay.presentation.viewmodel.MainViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.ui.theme.LocalPixelPlayWindowInfo
import com.theveloper.pixelplay.ui.theme.PixelPlayTheme
import com.theveloper.pixelplay.ui.theme.LocalShowScrollbar
import com.theveloper.pixelplay.utils.CrashHandler
import com.theveloper.pixelplay.utils.AppLocaleManager
import com.theveloper.pixelplay.utils.LogUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import com.theveloper.pixelplay.presentation.utils.AppHapticsConfig
import com.theveloper.pixelplay.presentation.utils.LocalAppHapticsConfig
import com.theveloper.pixelplay.presentation.utils.NoOpHapticFeedback
import com.theveloper.pixelplay.utils.CrashLogData
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map


@Immutable
data class BottomNavItem(
    val label: String,
    @StringRes val labelResId: Int,
    @DrawableRes val iconResId: Int,
    @DrawableRes val selectedIconResId: Int? = null,
    val screen: Screen
)

private data class DismissUndoBarSlice(
    val isVisible: Boolean = false,
    val durationMillis: Long = 4000L
)

@UnstableApi
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val playerViewModel: PlayerViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels()
    private var isUIVisiblyReady = false
    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository // Inject here
    @Inject
    lateinit var themePreferencesRepository: ThemePreferencesRepository
    @Inject
    lateinit var syncManager: SyncManager
    // For handling shortcut navigation - using StateFlow so composables can observe changes
    private val _pendingPlaylistNavigation = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    private val _pendingShuffleAll = kotlinx.coroutines.flow.MutableStateFlow(false)

    private val requestAllFilesAccessLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
        // Handle the result in onResume
    }

    @CallSuper
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocaleManager.wrapContext(newBase))
    }

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        LogUtils.d(this, "onCreate")
        val splashScreen = installSplashScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        super.onCreate(savedInstanceState)

        // Keep the system Splash Screen briefly while DataStore resolves initial setup & launch tab,
        // avoiding the intermediate "Preparing setup…" flash and double transition waterfall.
        val splashStartTime = System.currentTimeMillis()
        splashScreen.setKeepOnScreenCondition {
            val isBenchmark = intent?.getBooleanExtra("is_benchmark", false) == true
            val timedOut = System.currentTimeMillis() - splashStartTime > 400L
            !isBenchmark && !timedOut &&
                (mainViewModel.isSetupComplete.value == null || mainViewModel.launchTab.value == null)
        }

        // LEER SEÑAL DE BENCHMARK
        val isBenchmarkMode = intent.getBooleanExtra("is_benchmark", false)
        val shouldBenchmarkRebuildDatabase =
            isBenchmarkMode && intent.getBooleanExtra("benchmark_rebuild_database", false)
        Log.i(
            "PixelPlayBenchmark",
            "onCreate benchmark=$isBenchmarkMode rebuildDatabase=$shouldBenchmarkRebuildDatabase"
        )
        if (shouldBenchmarkRebuildDatabase) {
            lifecycleScope.launch {
                userPreferencesRepository.setInitialSetupDone(true)
                Log.i("PixelPlayBenchmark", "Enqueueing benchmark database rebuild")
                syncManager.rebuildDatabase()
                delay(1_500L)
                playerViewModel.prepareBenchmarkPlayerFromLibrary()
            }
        }

        setContent {
            val systemDarkTheme = isSystemInDarkTheme()
            val appThemeMode by themePreferencesRepository.appThemeModeFlow.collectAsStateWithLifecycle(initialValue = AppThemeMode.FOLLOW_SYSTEM)
            val showScrollbar by userPreferencesRepository.showScrollbarFlow.collectAsStateWithLifecycle(initialValue = true)
            val useDarkTheme = when (appThemeMode) {
                AppThemeMode.DARK -> true
                AppThemeMode.LIGHT -> false
                else -> systemDarkTheme
            }
            val isSetupComplete by mainViewModel.isSetupComplete.collectAsStateWithLifecycle()
            
            // Crash report dialog state
            var showCrashReportDialog by remember { mutableStateOf(false) }
            var crashLogData by remember { mutableStateOf<CrashLogData?>(null) }
            
            // Permissions Logic
            val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                listOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
            } else {
                listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            @OptIn(ExperimentalPermissionsApi::class)
            val permissionState = rememberMultiplePermissionsState(permissions = permissions)
            // Determine if we need to show Setup based on completion OR missing permissions
            val permissionsValid = permissionState.allPermissionsGranted
            val showSetupScreen = remember(isSetupComplete, permissionsValid, isBenchmarkMode) {
                when {
                    isBenchmarkMode -> false
                    isSetupComplete == null -> null
                    else -> !isSetupComplete!! || !permissionsValid
                }
            }

            // Sync Trigger: When we are NOT showing setup (meaning permissions are good and setup is done)
            LaunchedEffect(showSetupScreen) {
                if (showSetupScreen == false) {
                    // Defer background MediaStore sync on cold start if initial sync was already done,
                    // avoiding Room DB & disk I/O contention while the home screen loads initial data.
                    if (mainViewModel.hasCompletedInitialSync.value) {
                        delay(1_800L)
                    }
                    LogUtils.i(this, "Setup complete/skipped and permissions valid. Starting sync.")
                    mainViewModel.startSync()
                }
            }

            // Check for crash log when app starts on IO dispatcher to avoid blocking Main Thread
            LaunchedEffect(Unit) {
                if (!isBenchmarkMode) {
                    val loadedCrashLog = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        if (CrashHandler.hasCrashLog()) CrashHandler.getCrashLog() else null
                    }
                    if (loadedCrashLog != null) {
                        crashLogData = loadedCrashLog
                        showCrashReportDialog = true
                    }
                }
            }

            CompositionLocalProvider(LocalShowScrollbar provides showScrollbar) {
                PixelPlayTheme(
                    darkTheme = useDarkTheme
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(), 
                        color = MaterialTheme.colorScheme.background
                    ) {
                        if (showSetupScreen == null) {
                            SetupGateLoadingScreen()
                        } else {
                            AnimatedContent(
                                targetState = showSetupScreen,
                                transitionSpec = {
                                    if (targetState) {
                                        // Transition to Setup
                                        fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
                                    } else {
                                        // Transition from Setup to Main App
                                        scaleIn(initialScale = 0.95f, animationSpec = tween(450)) + fadeIn(animationSpec = tween(450)) togetherWith
                                                slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(450)) + fadeOut(animationSpec = tween(450))
                                    }
                                },
                                label = "SetupTransition"
                            ) { shouldShowSetup ->
                                if (shouldShowSetup) {
                                    SetupScreen(onSetupComplete = {
                                        // Repository-backed setup completion updates the gate automatically.
                                    })
                                } else {
                                    MainAppContent(playerViewModel, mainViewModel)
                                }
                            }
                        }

                        // Show crash report dialog if needed
                        if (showCrashReportDialog && crashLogData != null) {
                            CrashReportDialog(
                                crashLog = crashLogData!!,
                                onDismiss = {
                                    CrashHandler.clearCrashLog()
                                    crashLogData = null
                                    showCrashReportDialog = false
                                }
                            )
                        }
                    }
                }
            }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        when {
            // Handle shuffle all shortcut / tile
            intent.action == MainActivityIntentContract.ACTION_SHUFFLE_ALL -> {
                android.util.Log.d("TileDebug", "handleIntent: ACTION_SHUFFLE_ALL received")
                playerViewModel.triggerShuffleAllFromTile()
                intent.action = null // Clear action to prevent re-triggering
            }
            
            // Handle playlist shortcut
            intent.action == MainActivityIntentContract.ACTION_OPEN_PLAYLIST -> {
                intent.getStringExtra(MainActivityIntentContract.EXTRA_PLAYLIST_ID)?.let { playlistId ->
                    _pendingPlaylistNavigation.value = playlistId
                }
                intent.action = null
            }

            intent.getBooleanExtra("ACTION_SHOW_PLAYER", false) -> {
                playerViewModel.showPlayer()
            }

            intent.action == android.content.Intent.ACTION_VIEW && intent.data != null -> {
                intent.data?.let { uri ->
                    persistUriPermissionIfNeeded(intent, uri)
                    playerViewModel.playExternalUri(uri)
                }
                clearExternalIntentPayload(intent)
            }

            intent.action == android.content.Intent.ACTION_SEND && intent.type?.startsWith("audio/") == true -> {
                resolveStreamUri(intent)?.let { uri ->
                    persistUriPermissionIfNeeded(intent, uri)
                    playerViewModel.playExternalUri(uri)
                }
                clearExternalIntentPayload(intent)
            }
            
            intent.action == "com.theveloper.pixelplay.ACTION_PLAY_SONG" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                     intent.getParcelableExtra("song", com.theveloper.pixelplay.data.model.Song::class.java)?.let { song ->
                         playerViewModel.playSong(song)
                     }
                } else {
                     @Suppress("DEPRECATION")
                     intent.getParcelableExtra<com.theveloper.pixelplay.data.model.Song>("song")?.let { song ->
                         playerViewModel.playSong(song)
                     }
                }
                intent.action = null
            }
        }
    }
    
    private fun resolveStreamUri(intent: Intent): android.net.Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java)?.let { return it }
        } else {
            @Suppress("DEPRECATION")
            val legacyUri = intent.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)
            if (legacyUri != null) return legacyUri
        }

        intent.clipData?.let { clipData ->
            if (clipData.itemCount > 0) {
                return clipData.getItemAt(0).uri
            }
        }

        return intent.data
    }

    private fun persistUriPermissionIfNeeded(intent: Intent, uri: android.net.Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val hasPersistablePermission = intent.flags and android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0
            if (hasPersistablePermission) {
                val takeFlags = intent.flags and (android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                if (takeFlags != 0) {
                    try {
                        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (securityException: SecurityException) {
                        android.util.Log.w("MainActivity", "Unable to persist URI permission for $uri", securityException)
                    } catch (illegalArgumentException: IllegalArgumentException) {
                        android.util.Log.w("MainActivity", "Persistable URI permission not granted for $uri", illegalArgumentException)
                    }
                }
            }
        }
    }

    private fun clearExternalIntentPayload(intent: Intent) {
        intent.data = null
        intent.clipData = null
        intent.removeExtra(android.content.Intent.EXTRA_STREAM)
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun SetupGateLoadingScreen() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularWavyProgressIndicator()
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Preparing setup…",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    @Composable
    private fun MainAppContent(playerViewModel: PlayerViewModel, mainViewModel: MainViewModel) {
        Trace.beginSection("MainActivity.MainAppContent")
        val navController = rememberNavController()
        val isSyncing by mainViewModel.isSyncing.collectAsStateWithLifecycle()
        val isLibraryEmpty by mainViewModel.isLibraryEmpty.collectAsStateWithLifecycle()
        val hasCompletedInitialSync by mainViewModel.hasCompletedInitialSync.collectAsStateWithLifecycle()
        val syncProgress by mainViewModel.syncProgress.collectAsStateWithLifecycle()
        
        // isMediaControllerReady used below for playlist navigation gate
        val isMediaControllerReady by playerViewModel.isMediaControllerReady.collectAsStateWithLifecycle()
        
        // Observe pending playlist navigation
        val pendingPlaylistNav by _pendingPlaylistNavigation.collectAsStateWithLifecycle()
        var processedPlaylistId by remember { mutableStateOf<String?>(null) }
        
        LaunchedEffect(pendingPlaylistNav, isMediaControllerReady) {
            val playlistId = pendingPlaylistNav
            // Only process if we have a new playlist ID that hasn't been processed yet
            if (playlistId != null && playlistId != processedPlaylistId && isMediaControllerReady) {
                processedPlaylistId = playlistId
                // Wait for navigation graph to be ready (retry with delay)
                var success = false
                var attempts = 0
                while (!success && attempts < 50) { // 5 seconds max
                    try {
                        success = navController.navigateSafely(Screen.PlaylistDetail.createRoute(playlistId))
                        if (success) {
                            _pendingPlaylistNavigation.value = null
                        } else {
                            delay(100)
                            attempts++
                        }
                    } catch (e: IllegalArgumentException) {
                        delay(100)
                        attempts++
                    }
                }
            } else if (playlistId == null) {
                // Reset so the same playlist can be opened again
                processedPlaylistId = null
            }
        }

        // Estado para controlar si el indicador de carga puede mostrarse después de un delay
        var canShowLoadingIndicator by remember { mutableStateOf(false) }
        // Track when the loading indicator was first shown for minimum display time
        var loadingShownTimestamp by remember { mutableStateOf(0L) }
        val minimumDisplayDuration = 1500L // Show loading for at least 1.5 seconds

        val shouldPotentiallyShowLoading = isSyncing && isLibraryEmpty && !hasCompletedInitialSync

        LaunchedEffect(shouldPotentiallyShowLoading) {
            if (shouldPotentiallyShowLoading) {
                // Espera un breve período antes de permitir que se muestre el indicador de carga
                // Ajusta este valor según sea necesario (por ejemplo, 300-500 ms)
                delay(300L)
                // Vuelve a verificar la condición después del delay,
                // ya que el estado podría haber cambiado.
                if (mainViewModel.isSyncing.value && mainViewModel.isLibraryEmpty.value) {
                    canShowLoadingIndicator = true
                    loadingShownTimestamp = System.currentTimeMillis()
                }
            } else {
                // Ensure minimum display time before hiding
                if (canShowLoadingIndicator && loadingShownTimestamp > 0) {
                    val elapsed = System.currentTimeMillis() - loadingShownTimestamp
                    val remaining = minimumDisplayDuration - elapsed
                    if (remaining > 0) {
                        delay(remaining)
                    }
                }
                canShowLoadingIndicator = false
                loadingShownTimestamp = 0L
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            MainUI(playerViewModel, navController)

            // Muestra el LoadingOverlay solo si las condiciones se cumplen Y el delay ha pasado
            if (canShowLoadingIndicator) {
                LoadingOverlay(syncProgress)
            }
        }
        Trace.endSection() // End MainActivity.MainAppContent
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @androidx.annotation.OptIn(UnstableApi::class)
    @Composable
    private fun MainUI(playerViewModel: PlayerViewModel, navController: NavHostController) {
        Trace.beginSection("MainActivity.MainUI")

        val windowInfo = LocalPixelPlayWindowInfo.current
        val useNavigationRail = windowInfo.useNavigationRail
        val sheetBoundsSpec = remember(windowInfo) {
            resolveNowPlayingSheetBoundsSpec(windowInfo)
        }
        val showPlayerContentInitially by remember {
            playerViewModel.stablePlayerState
                .map { it.currentSong?.id != null }
                .distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialValue = false)
        var tabletDisplayMode by rememberSaveable {
            mutableStateOf(TabletPlayerDisplayMode.SIDE_DOCKED)
        }
        val fullScreenFractionState = animateFloatAsState(
            targetValue = if (
                !sheetBoundsSpec.supportsSideDockedMode ||
                    tabletDisplayMode == TabletPlayerDisplayMode.FULLSCREEN
            ) {
                1f
            } else {
                0f
            },
            animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
            label = "MainTabletFullScreenFraction"
        )
        val fullScreenFractionProvider = remember(fullScreenFractionState) {
            { fullScreenFractionState.value }
        }
        val isSideDockedModeActive =
            sheetBoundsSpec.supportsSideDockedMode &&
                tabletDisplayMode == TabletPlayerDisplayMode.SIDE_DOCKED &&
                showPlayerContentInitially
        val sideDockedReservedWidth =
            sheetBoundsSpec.sideDockedWidth + sheetBoundsSpec.sideDockedEndMargin
        // Keep structural window info & Wide Rail eligibility invariant across sheet expand/collapse
        // so opening or closing the Side-Docked Card never recomposes LocalPixelPlayWindowInfo,
        // never collapses the Navigation Rail mid-morph, and never tears down multi-pane screens.
        val sideDockedReservedDp = if (isSideDockedModeActive) {
            sideDockedReservedWidth.value.roundToInt()
        } else {
            0
        }
        val canKeepExpandedWideRail = windowInfo.supportsExpandableWideRail &&
            (!isSideDockedModeActive ||
                (windowInfo.widthDp - NavigationRailExpandedWidth.value.roundToInt() - sideDockedReservedDp) >= 760)
        var isRailExpanded by rememberSaveable(windowInfo.startWithExpandedWideRail) {
            mutableStateOf(windowInfo.startWithExpandedWideRail)
        }
        val effectiveRailExpanded = isRailExpanded && canKeepExpandedWideRail
        val targetRailWidthDp = when {
            !useNavigationRail -> 0
            effectiveRailExpanded -> NavigationRailExpandedWidth.value.roundToInt()
            else -> NavigationRailCollapsedWidth.value.roundToInt()
        }
        val animatedRailWidth by animateDpAsState(
            targetValue = targetRailWidthDp.dp,
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            label = "MainNavigationRailWidth"
        )

        val commonNavItems = remember {
            persistentListOf(
                BottomNavItem("Home", R.string.nav_bar_home, R.drawable.rounded_home_24, R.drawable.home_24_rounded_filled, Screen.Home),
                BottomNavItem("Search", R.string.nav_bar_search, R.drawable.rounded_search_24, R.drawable.rounded_search_24, Screen.Search),
                BottomNavItem("Library", R.string.nav_bar_library, R.drawable.rounded_library_music_24, R.drawable.round_library_music_24, Screen.Library)
            )
        }
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        var isSearchBarActive by remember { mutableStateOf(false) }

        val routesWithHiddenNavigationBar = remember {
            setOf(
                Screen.Settings.route,
                Screen.PlaylistDetail.route,
                Screen.DailyMixScreen.route,
                Screen.RecentlyPlayed.route,
                Screen.GenreDetail.route,
                Screen.AlbumDetail.route,
                Screen.ArtistDetail.route,
                Screen.DJSpace.route,
                Screen.NavBarCrRad.route,
                Screen.About.route,
                Screen.OpenSourceLicenses.route,
                Screen.Stats.route,
                Screen.EditTransition.route,
                Screen.Experimental.route,
                Screen.ArtistSettings.route,
                Screen.Equalizer.route,
                Screen.SettingsCategory.route,
                Screen.DelimiterConfig.route,
                Screen.PaletteStyle.route,
                Screen.DeviceCapabilities.route,
                Screen.EasterEgg.route,
                Screen.WordDelimiterConfig.route,
                Screen.OfflineModelHub.route
            )
        }
        val shouldHideNavigationBar by remember(currentRoute, isSearchBarActive) {
            derivedStateOf {
                if (currentRoute == Screen.Search.route && isSearchBarActive) {
                    true
                } else {
                    currentRoute?.let { route ->
                        routesWithHiddenNavigationBar.any { hiddenRoute ->
                            when {
                                hiddenRoute.contains("?") -> {
                                    val baseRoute = hiddenRoute.substringBefore("?")
                                    route == baseRoute || route.startsWith("$baseRoute?")
                                }
                                hiddenRoute.contains("{") -> {
                                    route.startsWith(hiddenRoute.substringBefore("{"))
                                }
                                else -> route == hiddenRoute
                            }
                        }
                    } ?: false
                }
            }
        }

        val navBarStyle by playerViewModel.navBarStyle.collectAsStateWithLifecycle()
        val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
        val navBarCornerRadiusRaw by playerViewModel.navBarCornerRadius.collectAsStateWithLifecycle()
        val navBarCornerRadius = sanitizeNavBarCornerRadius(navBarCornerRadiusRaw)
        val useSmoothCorners by playerViewModel.useSmoothCorners.collectAsStateWithLifecycle()
        val isMiniPlayerDismissing by playerViewModel.isMiniPlayerDismissing.collectAsStateWithLifecycle()
        val hapticsEnabled by playerViewModel.hapticsEnabled.collectAsStateWithLifecycle()
        val disableBlurAllOver by playerViewModel.disableBlurAllOver.collectAsStateWithLifecycle()
        val predictiveBackCollapseFractionState = playerViewModel.predictiveBackCollapseFraction.collectAsStateWithLifecycle()
        val rootView = LocalView.current
        val platformHapticFeedback = LocalHapticFeedback.current
        val appHapticsConfig = remember(hapticsEnabled) {
            AppHapticsConfig(enabled = hapticsEnabled)
        }
        val scopedHapticFeedback = remember(platformHapticFeedback, appHapticsConfig.enabled) {
            if (appHapticsConfig.enabled) platformHapticFeedback else NoOpHapticFeedback
        }

        val systemNavBarInset = sanitizeNavigationBarBottomInset(
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        )

        LaunchedEffect(hapticsEnabled, rootView) {
            rootView.isHapticFeedbackEnabled = hapticsEnabled
            rootView.rootView?.isHapticFeedbackEnabled = hapticsEnabled
        }

        val horizontalPadding = resolveNavBarHorizontalPadding(navBarStyle, systemNavBarInset)
        val animatedBottomBarPadding by animateDpAsState(
            targetValue = resolveNavBarBottomPadding(navBarStyle, systemNavBarInset),
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            label = "BottomBarPadding"
        )
        val bottomBarPadding = animatedBottomBarPadding
        val navBarHeight = resolveNavBarSurfaceHeight(navBarStyle, systemNavBarInset, navBarCompactMode)
        val navBarOccupiedHeight by remember(systemNavBarInset, navBarCompactMode, navBarStyle) {
            derivedStateOf { resolveNavBarOccupiedHeight(systemNavBarInset, navBarCompactMode, navBarStyle) }
        }
        val navBarVisibilityProgressState = animateFloatAsState(
            targetValue = if (shouldHideNavigationBar) 0f else 1f,
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            label = "NavBarVisibilityProgress"
        )
        val navBarVisibilityProgress by navBarVisibilityProgressState
        val visibleNavBarOccupiedHeight by remember(navBarOccupiedHeight, navBarVisibilityProgress) {
            derivedStateOf { navBarOccupiedHeight * navBarVisibilityProgress }
        }
        val miniPlayerBottomMargin by remember(useNavigationRail, systemNavBarInset, visibleNavBarOccupiedHeight) {
            derivedStateOf {
                if (useNavigationRail) {
                    systemNavBarInset + 6.dp
                } else if (visibleNavBarOccupiedHeight > systemNavBarInset) {
                    visibleNavBarOccupiedHeight
                } else {
                    systemNavBarInset
                }
            }
        }
        val shouldRenderNavigationBar by remember(useNavigationRail, shouldHideNavigationBar, navBarVisibilityProgress) {
            derivedStateOf {
                !useNavigationRail && (!shouldHideNavigationBar || navBarVisibilityProgress > 0.01f)
            }
        }
        val isNavBarEffectivelyHidden by remember(useNavigationRail, shouldHideNavigationBar, navBarVisibilityProgress) {
            derivedStateOf {
                useNavigationRail || (shouldHideNavigationBar && navBarVisibilityProgress <= 0.01f)
            }
        }

        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        LaunchedEffect(userPreferencesRepository) {
            userPreferencesRepository.clearDeprecatedPlayerSheetPreference()
        }

        CompositionLocalProvider(
            LocalAppHapticsConfig provides appHapticsConfig,
            LocalHapticFeedback provides scopedHapticFeedback
        ) {
            AppSidebarDrawer(
                drawerState = drawerState,
                selectedRoute = currentRoute ?: Screen.Home.route,
                onDestinationSelected = { destination ->
                    scope.launch { drawerState.close() }
                    when (destination) {
                        // Same top-level semantics as the bottom bar: keeps Home's saved state and
                        // never stacks a second Home on top of Settings/Equalizer.
                        DrawerDestination.Home -> navController.navigateToTopLevelSafely(Screen.Home.route)
                        DrawerDestination.Equalizer -> navController.navigateSafely(Screen.Equalizer.route)
                        DrawerDestination.Settings -> navController.navigateSafely(Screen.Settings.route)
                    }
                }
        ) {

                Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    if (shouldRenderNavigationBar) {
                        val showPlayerContentArea = showPlayerContentInitially
                        val navBarElevation = 3.dp

                        val animatedNavBarCornerRadius = animateDpAsState(
                            targetValue = navBarCornerRadius.dp,
                            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                            label = "NavBarCornerRadius"
                        )

                        val animatedDefaultTopCornerRadius = animateDpAsState(
                            targetValue = if (showPlayerContentArea && !isMiniPlayerDismissing) 10.dp else navBarCornerRadius.dp,
                            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                            label = "NavBarDefaultTopCornerRadius"
                        )

                        val density = LocalDensity.current
                        val shadowOverflowPx = remember(navBarElevation, density) {
                            with(density) { (navBarElevation * 8).toPx() }
                        }
                        val navBarHeightPx = remember(navBarHeight, density) {
                            with(density) { navBarHeight.toPx() }
                        }
                        val bottomBarPaddingPx = remember(bottomBarPadding, density) {
                            with(density) { bottomBarPadding.toPx() }
                        }
                        val navBarElevationPx = remember(navBarElevation, density) {
                            with(density) { navBarElevation.toPx() }
                        }
                        val navBarShapeCache = remember { NavBarShapeCache() }
                        val isPlayerCoveringNavBar by remember(showPlayerContentArea, playerViewModel) {
                            derivedStateOf {
                                showPlayerContentArea && playerViewModel.playerContentExpansionFraction.value >= 0.95f
                            }
                        }
                        val isNavBarHiddenOrCovered = shouldHideNavigationBar || isPlayerCoveringNavBar

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(navBarOccupiedHeight)
                                .drawWithContent {
                                    clipRect(
                                        left = -shadowOverflowPx,
                                        top = -shadowOverflowPx,
                                        right = size.width + shadowOverflowPx,
                                        bottom = size.height
                                    ) {
                                        this@drawWithContent.drawContent()
                                    }
                                }
                                .then(
                                    if (isNavBarHiddenOrCovered) {
                                        Modifier.clearAndSetSemantics { hideFromAccessibility() }
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            val onSearchIconDoubleTap = remember(playerViewModel) {
                                { playerViewModel.onSearchNavIconDoubleTapped() }
                            }

                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(
                                        start = horizontalPadding,
                                        end = horizontalPadding,
                                        bottom = bottomBarPadding
                                    )
                                    .fillMaxWidth()
                                    .height(navBarHeight)
                                    .graphicsLayer {
                                        // Slide-down hide and animated corner shape share a single RenderNode.
                                        val safeFraction = playerViewModel.playerContentExpansionFraction.value.coerceIn(0f, 1f)
                                        val expansionHide = if (showPlayerContentArea) safeFraction else 0f
                                        val routeHide = (1f - navBarVisibilityProgressState.value).coerceIn(0f, 1f)
                                        val hideFraction = maxOf(expansionHide, routeHide)
                                        translationY = (navBarHeightPx + bottomBarPaddingPx + shadowOverflowPx) * hideFraction
                                        alpha = 1f

                                        val topDp = when (navBarStyle) {
                                            NavBarStyle.DEFAULT -> animatedDefaultTopCornerRadius.value
                                            NavBarStyle.FULL_WIDTH -> lerp(animatedNavBarCornerRadius.value, 26.dp, safeFraction)
                                            else -> animatedNavBarCornerRadius.value
                                        }
                                        val bottomDp = when (navBarStyle) {
                                            NavBarStyle.FULL_WIDTH -> 0.dp
                                            else -> animatedNavBarCornerRadius.value
                                        }
                                        shape = navBarShapeCache.get(this, topDp.toPx(), bottomDp.toPx(), useSmoothCorners)
                                        clip = true
                                        shadowElevation = navBarElevationPx
                                    },
                                color = NavigationBarDefaults.containerColor
                            ) {
                                PlayerInternalNavigationBar(
                                    navController = navController,
                                    navItems = commonNavItems,
                                    currentRoute = currentRoute,
                                    navBarStyle = navBarStyle,
                                    compactMode = navBarCompactMode,
                                    bottomBarPadding = bottomBarPadding,
                                    navigationEnabled = !isNavBarHiddenOrCovered,
                                    onSearchIconDoubleTap = onSearchIconDoubleTap,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
                ) { innerPadding ->
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val density = LocalDensity.current
                        val containerWidth = this.maxWidth
                        val containerHeight = this.maxHeight
                        val screenHeightPx = remember(containerHeight, density) {
                            with(density) { containerHeight.toPx() }
                        }

                        val (collapsedStartPadding, collapsedEndPadding) = remember(
                            useNavigationRail,
                            containerWidth,
                            targetRailWidthDp,
                            windowInfo.miniPlayerMaxWidth,
                            horizontalPadding
                        ) {
                            if (useNavigationRail) {
                                val safeRailWidth = targetRailWidthDp.dp.coerceAtLeast(0.dp)
                                val contentWidth = (containerWidth - safeRailWidth).coerceAtLeast(0.dp)
                                val maxMiniWidth = if (windowInfo.miniPlayerMaxWidth != Dp.Unspecified) {
                                    windowInfo.miniPlayerMaxWidth
                                } else {
                                    contentWidth
                                }
                                val targetMiniWidth = minOf(
                                    maxMiniWidth,
                                    (contentWidth - 32.dp).coerceAtLeast(0.dp)
                                )
                                val sideMargin = ((contentWidth - targetMiniWidth) / 2f).coerceAtLeast(16.dp)
                                (safeRailWidth + sideMargin) to sideMargin
                            } else {
                                horizontalPadding to horizontalPadding
                            }
                        }

                        val routesWithHiddenMiniPlayer = remember { setOf(Screen.NavBarCrRad.route) }
                        val shouldHideMiniPlayer by remember(currentRoute) {
                            derivedStateOf { currentRoute in routesWithHiddenMiniPlayer }
                        }

                        val miniPlayerH = with(density) { MiniPlayerHeight.toPx() }
                        val totalSheetHeightWhenContentCollapsedPx = if (showPlayerContentInitially && !shouldHideMiniPlayer) miniPlayerH else 0f

                        val bottomMargin = miniPlayerBottomMargin

                        val spacerPx = with(density) { MiniPlayerBottomSpacer.toPx() }
                        val bottomMarginPx = with(density) { bottomMargin.toPx() }
                        val sheetCollapsedTargetY = calculatePlayerSheetCollapsedTargetY(
                            containerHeightPx = screenHeightPx,
                            collapsedContentHeightPx = totalSheetHeightWhenContentCollapsedPx,
                            bottomMarginPx = bottomMarginPx,
                            bottomSpacerPx = spacerPx
                        )

                        val expansionFractionProvider = remember(playerViewModel.playerContentExpansionFraction) {
                            { playerViewModel.playerContentExpansionFraction.value }
                        }
                        val sideDockedReservedWidthPx = remember(sideDockedReservedWidth, density) {
                            with(density) { sideDockedReservedWidth.toPx() }
                        }
                        val sideDockedFractionProvider = remember(
                            sheetBoundsSpec.supportsSideDockedMode,
                            showPlayerContentInitially,
                            playerViewModel.playerContentExpansionFraction,
                            fullScreenFractionProvider,
                            predictiveBackCollapseFractionState
                        ) {
                            {
                                if (!sheetBoundsSpec.supportsSideDockedMode || !showPlayerContentInitially) {
                                    0f
                                } else {
                                    val expansion = playerViewModel.playerContentExpansionFraction.value.coerceIn(0f, 1f)
                                    val predictiveBack = predictiveBackCollapseFractionState.value.coerceIn(0f, 1f)
                                    val dockedWeight = 1f - fullScreenFractionProvider().coerceIn(0f, 1f)
                                    (expansion * (1f - predictiveBack) * dockedWeight).coerceIn(0f, 1f)
                                }
                            }
                        }
                        val effectiveLeftPaneWindowInfo = remember(
                            windowInfo,
                            targetRailWidthDp,
                            sideDockedReservedDp
                        ) {
                            computeEffectiveContentWindowInfo(
                                windowInfo = windowInfo,
                                navigationRailWidthDp = targetRailWidthDp,
                                sideDockedReservedWidthDp = sideDockedReservedDp
                            )
                        }
                        val blurEffectCache = remember { BlurEffectCache() }
                        val onSearchIconDoubleTap = remember(playerViewModel) {
                            { playerViewModel.onSearchNavIconDoubleTapped() }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val expansion = expansionFractionProvider()
                                    val fullScreenFraction = fullScreenFractionProvider()
                                    val fraction = (
                                        expansion *
                                            fullScreenFraction *
                                            (1f - predictiveBackCollapseFractionState.value)
                                        ).coerceIn(0f, 1f)
                                    // When the full player sheet is completely opaque and covering the
                                    // screen (FULLSCREEN mode), skip full-screen blur and hide the
                                    // background layer to eliminate offscreen render target & Gaussian
                                    // blur GPU overhead. In SIDE_DOCKED mode (fullScreenFraction == 0f),
                                    // fraction == 0f so the left main app remains 100% sharp & interactive.
                                    if (fraction >= 0.99f) {
                                        alpha = 0f
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            renderEffect = null
                                        }
                                    } else {
                                        alpha = 1f
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            if (disableBlurAllOver || fraction <= 0.01f) {
                                                renderEffect = null
                                            } else {
                                                // Cap max blur at 36px in 4px quantized steps (9 steps total
                                                // instead of 60 steps up to 120px) for much lower GPU fill cost.
                                                val quantizedBlurPx = (fraction * 36f / 4f).roundToInt() * 4f
                                                renderEffect = blurEffectCache.get(quantizedBlurPx)
                                            }
                                        }
                                    }
                                }
                        ) {
                            val initialLaunchTab by mainViewModel.launchTab.collectAsStateWithLifecycle()
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        if (useNavigationRail) {
                                            MaterialTheme.colorScheme.surfaceContainerLow
                                        } else {
                                            MaterialTheme.colorScheme.background
                                        }
                                    )
                            ) {
                                if (useNavigationRail) {
                                    key("pixelplay_navigation_rail") {
                                        PixelPlayNavigationRail(
                                            navController = navController,
                                            currentRoute = currentRoute,
                                            isExpanded = effectiveRailExpanded,
                                            supportsExpandToggle = canKeepExpandedWideRail,
                                            onToggleExpanded = { isRailExpanded = !isRailExpanded },
                                            onSearchDoubleTap = onSearchIconDoubleTap,
                                            railWidth = animatedRailWidth
                                        )
                                    }
                                }
                                key("pixelplay_navigation_host") {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .layout { measurable, constraints ->
                                                val reservedEndPx = (sideDockedReservedWidthPx * sideDockedFractionProvider())
                                                    .roundToInt()
                                                    .coerceIn(0, constraints.maxWidth)
                                                val childMaxWidth = (constraints.maxWidth - reservedEndPx).coerceAtLeast(0)
                                                val childMinWidth = constraints.minWidth.coerceAtMost(childMaxWidth)
                                                val placeable = measurable.measure(
                                                    constraints.copy(
                                                        minWidth = childMinWidth,
                                                        maxWidth = childMaxWidth
                                                    )
                                                )
                                                layout(constraints.maxWidth, constraints.maxHeight) {
                                                    placeable.placeRelative(0, 0)
                                                }
                                            }
                                    ) {
                                        CompositionLocalProvider(
                                            LocalPixelPlayWindowInfo provides effectiveLeftPaneWindowInfo
                                        ) {
                                            AppNavigation(
                                                playerViewModel = playerViewModel,
                                                navController = navController,
                                                paddingValues = if (useNavigationRail) PaddingValues(0.dp) else innerPadding,
                                                userPreferencesRepository = userPreferencesRepository,
                                                onSearchBarActiveChange = { isSearchBarActive = it },
                                                onOpenSidebar = {
                                                    if (useNavigationRail) {
                                                        if (canKeepExpandedWideRail) {
                                                            isRailExpanded = !isRailExpanded
                                                        }
                                                    } else {
                                                        scope.launch { drawerState.open() }
                                                    }
                                                },
                                                initialLaunchTab = initialLaunchTab
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        val isFullScreenExpandedOrExpanding by remember {
                            derivedStateOf {
                                playerViewModel.playerContentExpansionFraction.value *
                                    fullScreenFractionProvider() > 0.01f
                            }
                        }
                        if (isFullScreenExpandedOrExpanding) {
                            val scrimMaxAlpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.35f else 0.6f
                            val scrimBaseColor = MaterialTheme.colorScheme.surfaceContainerLowest
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        val expansion = (
                                            expansionFractionProvider() * fullScreenFractionProvider()
                                            ).coerceIn(0f, 1f)
                                        alpha = expansion * scrimMaxAlpha
                                    }
                                    .background(scrimBaseColor)
                                    .pointerInput(Unit) {
                                        detectTapGestures {
                                            playerViewModel.collapsePlayerSheet()
                                        }
                                    }
                            )
                        }

                        UnifiedPlayerSheetV2(
                            playerViewModel = playerViewModel,
                            sheetCollapsedTargetY = sheetCollapsedTargetY,
                            collapsedStateHorizontalPadding = horizontalPadding,
                            collapsedStartHorizontalPadding = collapsedStartPadding,
                            collapsedEndHorizontalPadding = collapsedEndPadding,
                            containerWidth = containerWidth,
                            hideMiniPlayer = shouldHideMiniPlayer,
                            containerHeight = containerHeight,
                            navController = navController,
                            isNavBarHidden = isNavBarEffectivelyHidden,
                            tabletDisplayMode = tabletDisplayMode,
                            onTabletDisplayModeChange = { tabletDisplayMode = it },
                            fullScreenFractionProvider = fullScreenFractionProvider
                        )

                        val dismissUndoBarSlice by remember {
                            playerViewModel.playerUiState
                                .map { state ->
                                    DismissUndoBarSlice(
                                        isVisible = state.showDismissUndoBar,
                                        durationMillis = state.undoBarVisibleDuration
                                    )
                                }
                                .distinctUntilChanged()
                        }.collectAsStateWithLifecycle(initialValue = DismissUndoBarSlice())
                        val onUndoDismissPlaylist = remember(playerViewModel) {
                            { playerViewModel.undoDismissPlaylist() }
                        }
                        val onCloseDismissUndoBar = remember(playerViewModel) {
                            { playerViewModel.hideDismissUndoBar() }
                        }

                        AnimatedVisibility(
                            visible = dismissUndoBarSlice.isVisible,
                            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(
                                    bottom = miniPlayerBottomMargin + MiniPlayerBottomSpacer
                                )
                                .padding(start = collapsedStartPadding, end = collapsedEndPadding)
                        ) {
                            DismissUndoBar(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(MiniPlayerHeight)
                                    .padding(horizontal = if (useNavigationRail) 0.dp else 14.dp),
                                onUndo = onUndoDismissPlaylist,
                                onClose = onCloseDismissUndoBar,
                                durationMillis = dismissUndoBarSlice.durationMillis
                            )
                        }
                    }
                }
            }
        }

        Trace.endSection()
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun LoadingOverlay(syncProgress: SyncProgress) {
        // Animate progress smoothly instead of jumping in steps
        val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
            targetValue = syncProgress.progress,
            animationSpec = androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessLow
            ),
            label = "SyncProgressAnimation"
        )
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.9f))
                .clickable(enabled = false, onClick = {}),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                CircularWavyProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Preparing your library...",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                if (syncProgress.hasProgress) {
                    Spacer(modifier = Modifier.height(16.dp))
                    androidx.compose.material3.LinearWavyProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Scanned ${syncProgress.currentCount} of ${syncProgress.totalCount} songs",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }


    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onStart() {
        super.onStart()
        LogUtils.d(this, "onStart")
        playerViewModel.onMainActivityStart()
    }

    override fun onStop() {
        super.onStop()
        LogUtils.d(this, "onStop")
    }

    override fun onResume() {
        super.onResume()
    }


}

/**
 * Caches the (expensive) RenderEffect Java object so we don't allocate a new
 * blur every animation frame. The radius is quantized at the call site, so this
 * only rebuilds ~25 times across the whole expand animation instead of 60+/sec.
 */
private class BlurEffectCache {
    private var lastRadiusPx: Float = Float.NaN
    private var cached: androidx.compose.ui.graphics.RenderEffect? = null

    fun get(radiusPx: Float): androidx.compose.ui.graphics.RenderEffect? {
        if (radiusPx <= 0f) {
            lastRadiusPx = 0f
            cached = null
            return null
        }
        if (radiusPx != lastRadiusPx) {
            lastRadiusPx = radiusPx
            cached = AndroidRenderEffect
                .createBlurEffect(radiusPx, radiusPx, AndroidShader.TileMode.CLAMP)
                .asComposeRenderEffect()
        }
        return cached
    }
}

/**
 * Returns a cached Shape instance for a quantized (top, bottom) radius pair.
 * Because the instance identity is stable while the radii don't move past a
 * sub-pixel threshold, the graphics layer reuses its cached Outline between
 * frames and only re-clips when the radius actually changes.
 */
private class NavBarShapeCache {
    private var lastTopPx: Float = Float.NaN
    private var lastBottomPx: Float = Float.NaN
    private var lastSmooth: Boolean = true
    private var cached: androidx.compose.ui.graphics.Shape = RectangleShape

    fun get(
        density: androidx.compose.ui.unit.Density,
        topPx: Float,
        bottomPx: Float,
        smooth: Boolean
    ): androidx.compose.ui.graphics.Shape {
        if (smooth == lastSmooth &&
            !lastTopPx.isNaN() &&
            kotlin.math.abs(topPx - lastTopPx) < 0.5f &&
            kotlin.math.abs(bottomPx - lastBottomPx) < 0.5f
        ) {
            return cached
        }
        lastTopPx = topPx
        lastBottomPx = bottomPx
        lastSmooth = smooth
        cached = with(density) {
            DynamicSmoothCornerShape(
                useSmoothCorners = smooth,
                topRadius = topPx.toDp(),
                bottomRadius = bottomPx.toDp()
            )
        }
        return cached
    }
}

/**
 * Fixed-radius corner shape. Swaps AbsoluteSmoothCornerShape for a plain
 * RoundedCornerShape when smooth corners are disabled in settings. The radius
 * values are identical in both branches, so the animated radius behavior is
 * unchanged regardless of which delegate is active. The resulting Outline is
 * cached per (size, layoutDirection) so repeated draws are cheap.
 */
private class DynamicSmoothCornerShape(
    private val useSmoothCorners: Boolean,
    private val topRadius: androidx.compose.ui.unit.Dp,
    private val bottomRadius: androidx.compose.ui.unit.Dp
) : androidx.compose.ui.graphics.Shape {

    private var cachedSize: androidx.compose.ui.geometry.Size =
        androidx.compose.ui.geometry.Size.Unspecified
    private var cachedLayoutDirection: androidx.compose.ui.unit.LayoutDirection? = null
    private var cachedOutline: androidx.compose.ui.graphics.Outline? = null

    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density
    ): androidx.compose.ui.graphics.Outline {
        cachedOutline?.let {
            if (cachedSize == size && cachedLayoutDirection == layoutDirection) return it
        }

        val delegate: androidx.compose.ui.graphics.Shape = if (useSmoothCorners) {
            AbsoluteSmoothCornerShape(
                cornerRadiusTL = topRadius,
                smoothnessAsPercentTL = 60,
                cornerRadiusTR = topRadius,
                smoothnessAsPercentTR = 60,
                cornerRadiusBL = bottomRadius,
                smoothnessAsPercentBL = 60,
                cornerRadiusBR = bottomRadius,
                smoothnessAsPercentBR = 60
            )
        } else {
            RoundedCornerShape(
                topStart = topRadius,
                topEnd = topRadius,
                bottomEnd = bottomRadius,
                bottomStart = bottomRadius
            )
        }

        return delegate.createOutline(size, layoutDirection, density).also {
            cachedSize = size
            cachedLayoutDirection = layoutDirection
            cachedOutline = it
        }
    }
}
