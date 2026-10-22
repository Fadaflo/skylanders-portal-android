// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.features.skylanders.console

import org.dolphinemu.dolphinemu.R
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.blur
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.InputMode
import androidx.compose.foundation.focusGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import coil.request.ImageRequest
import coil.size.Precision
import android.os.Build
import android.content.res.Configuration
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import kotlin.math.roundToInt

private val Ink = PortalPalette.Background
private val Panel = PortalPalette.Surface
private val Muted = PortalPalette.Muted
private val Ice = PortalPalette.Focus
private val Shape = PortalPalette.Shape
private val LocalMenuSound = staticCompositionLocalOf<(ConsoleCue) -> Unit> { {} }

@Stable
private class HeroSwipeState {
    var position by mutableFloatStateOf(0f)
    var moving by mutableStateOf(false)
    var ready by mutableStateOf(false)
    var infoProgress by mutableFloatStateOf(0f)
    var coverBounds by mutableStateOf<Rect?>(null)
}

@Composable
internal fun ConsoleScreen(
    state: ConsoleState,
    controllerKeys: Flow<Int>,
    dispatchMenuKey: (Int) -> Unit,
    onAudioSettings: (ConsoleAudioSettings) -> Unit,
    onControllerVibration: (Boolean) -> Unit,
    onVibrationIntensity: (Float) -> Unit,
    onTestVibration: () -> Unit,
    onMenuSound: (ConsoleCue) -> Unit,
    onInfoAnimation: (Boolean) -> Unit,
    onSelectPage: (ConsolePage) -> Unit,
    onOpenAccount: () -> Unit,
    onCloseAccount: () -> Unit,
    onAccountSignIn: (String, String) -> Unit,
    onAccountSignUp: (String, String, String) -> Unit,
    onAccountSignOut: () -> Unit,
    onAccountUpdateName: (String) -> Unit,
    onAccountUpdateAvatar: (String) -> Unit,
    onAccountUpdatePassword: (String, String) -> Unit,
    onAccountRequestReset: (String) -> Unit,
    onAccountCompleteReset: (String, String, String) -> Unit,
    onCloudRefresh: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onCloudChooseLocal: () -> Unit,
    onCloudChooseRemote: () -> Unit,
    onCloudDefer: () -> Unit,
    onSelect: (ConsoleGame) -> Unit,
    onPlay: (ConsoleGame) -> Unit,
    onAddGames: () -> Unit,
    onRefresh: () -> Unit,
    onBluetooth: () -> Unit,
    onControllerSettings: () -> Unit,
    onControllerSettings2: () -> Unit,
    onSwapControllers: () -> Unit,
    onStartControllerTest: () -> Unit,
    onStopControllerTest: () -> Unit,
    onAutoController: (Boolean) -> Unit,
    onAutoGraphics: (Boolean) -> Unit,
    onSettings: () -> Unit,
    onSetup: () -> Unit,
    onCloseSetup: () -> Unit,
    onImportFiles: () -> Unit,
    onImportFolder: () -> Unit,
    onCloseImport: () -> Unit,
    onDismissImportMessage: () -> Unit,
    onAdvanced: () -> Unit
) {
    val view = LocalView.current
    val heroSwipe = remember { HeroSwipeState() }
    val figurePageState = rememberSaveableStateHolder()
    val motion = portalMotionEnabled(LocalContext.current)
    val launchProgress by animateFloatAsState(if (state.launchingGameKey != null) 1f else 0f,
        tween(if (motion) PortalPalette.LaunchDurationMs else 40, easing = PortalPalette.LaunchEasing), label = "enterPortal")
    DisposableEffect(view) {
        val original = view.isSoundEffectsEnabled
        view.isSoundEffectsEnabled = false
        onDispose { view.isSoundEffectsEnabled = original }
    }
    val inputMode = LocalInputModeManager.current
    val dispatchKey by rememberUpdatedState(dispatchMenuKey)
    val testActive by rememberUpdatedState(state.controllerTestActive)
    val pageFocus = remember { FocusRequester() }
    val accountFocus = remember { FocusRequester() }
    var settingsNavigating by remember { mutableStateOf(false) }
    LaunchedEffect(state.controllers.isNotEmpty()) {
        if (state.controllers.isNotEmpty()) inputMode.requestInputMode(InputMode.Keyboard)
    }
    LaunchedEffect(controllerKeys) {
        controllerKeys.collect { key ->
            if (testActive) return@collect
            inputMode.requestInputMode(InputMode.Keyboard)
            // Let the focus targets update before delivering the first controller action.
            withFrameNanos { }
            withFrameNanos { }
            if (settingsNavigating && key in listOf(android.view.KeyEvent.KEYCODE_ENTER,
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT)) {
                // Confirm/adjust the destination card, never the old control during its transition.
                snapshotFlow { settingsNavigating }.first { !it }
            }
            dispatchKey(key)
        }
    }
    LaunchedEffect(state.page, state.account.email) {
        if (state.account.email != null && state.page != ConsolePage.GAMES && state.page != ConsolePage.SETTINGS && inputMode.inputMode == InputMode.Keyboard) {
            withFrameNanos { }
            pageFocus.requestFocus()
        }
    }
    BackHandler(!state.accountOpen && state.page != ConsolePage.GAMES) { }
    BackHandler(state.accountOpen) { onCloseAccount() }
    BackHandler(state.controllerTestActive) { }
    BackHandler(state.launchingGameKey != null) { dispatchMenuKey(android.view.KeyEvent.KEYCODE_BACK) }
    CompositionLocalProvider(LocalMenuSound provides onMenuSound, LocalPortalMotion provides motion) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Ice, background = Ink,
        surface = Panel, onSurface = Color.White, onBackground = Color.White), typography = ConsoleTypography) {
        val selectedGame = state.games.firstOrNull { it.key == state.selectedKey } ?: consoleCatalog.first()
        Box(Modifier.fillMaxSize().background(Ink).focusRequester(pageFocus).focusGroup()) {
            val signedOut = state.account.email == null
            if (signedOut || state.page != ConsolePage.GAMES || state.accountOpen) FigureBackdrop()
            else ConsoleBackdrop(selectedGame, state.games, state.page != ConsolePage.GAMES, heroSwipe)
            Box(Modifier.fillMaxSize().graphicsLayer {
                alpha = if (state.page == ConsolePage.GAMES) heroSwipe.infoProgress else 0f
            }.background(Color.Black.copy(alpha = 0.48f)))
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                if (!signedOut && !state.controllerTestActive) ConsoleHeader(state, onSelectPage, onOpenAccount, accountFocus)
                Box(Modifier.weight(1f)) {
                    if (!signedOut && state.controllerTestActive) ControllerTestPage(state, onStopControllerTest)
                    else if (signedOut || state.accountOpen) ProfilePage(state.account, state.games,
                        state.cloudStatus, state.cloudBusy, state.cloudSaveInfo,
                        state.updateAvailable, state.updateBusy, state.updateMessage,
                        onCloudRefresh, onDownloadUpdate, onCloseAccount,
                        onAccountSignIn, onAccountSignUp, onAccountSignOut, onAccountUpdateName,
                        onAccountUpdateAvatar, onAccountUpdatePassword, onAccountRequestReset,
                        onAccountCompleteReset)
                    else when (state.page) {
                        ConsolePage.GAMES -> GamesPage(state, onSelect, onPlay, onAddGames, onRefresh,
                            heroSwipe, { accountFocus.requestFocus() })
                        ConsolePage.FIGURES -> figurePageState.SaveableStateProvider("figures") {
                            FiguresPage(state, onRefresh, accountFocus)
                        }
                        ConsolePage.CONTROLLERS -> ControllersPage(state, onBluetooth, onControllerSettings, onControllerSettings2,
                            onSwapControllers,
                            onAutoController, onStartControllerTest, accountFocus)
                        ConsolePage.SETTINGS -> SettingsPage(state, onAudioSettings, onInfoAnimation, onAutoGraphics,
                            onSettings, onAdvanced, onSetup, onAddGames, { settingsNavigating = it },
                            onControllerVibration, onTestVibration, onVibrationIntensity,
                            onOpenAccount, { accountFocus.requestFocus() })
                    }
                }
            }
            if (state.launchingGameKey != null) PortalLaunchOverlay(
                state.games.firstOrNull { it.key == state.launchingGameKey } ?: selectedGame,
                launchProgress, heroSwipe.coverBounds)
            if (!state.controllerTestActive && state.foregroundLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.TopStart).padding(16.dp).size(22.dp),
                    color = Ice, strokeWidth = 2.dp)
            }
            if (!signedOut && state.setupOpen) ConsoleSetup(state, onImportFiles, onImportFolder, onBluetooth, onCloseSetup)
            if (!signedOut && state.importOpen) AlertDialog(onDismissRequest = onCloseImport,
                title = { Text("Eigene Spiele einbinden", fontWeight = FontWeight.Black) },
                text = { Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Wähle einzelne Wii-Spieldateien oder einen Ordner mit deinen Spielen. Die Dateien bleiben an ihrem Speicherort.")
                    Text("RVZ · ISO · WBFS · WIA · GCZ · CISO\nZIP und 7z vorher entpacken.", color = Muted)
                    ConsoleButton("Dateien auswählen", Icons.Outlined.InsertDriveFile, onImportFiles, primary = true, enabled = state.ready && !state.importing)
                    ConsoleButton("Spieleordner verbinden", Icons.Outlined.FolderOpen, onImportFolder, enabled = state.ready && !state.importing)
                } }, confirmButton = { TextButton(onCloseImport) { Text("Schließen") } })
            if (!signedOut && state.importMessage != null && !state.setupOpen && !state.importOpen) AlertDialog(
                onDismissRequest = onDismissImportMessage,
                title = { Text("Spieleimport") }, text = { Text(state.importMessage) },
                confirmButton = { TextButton(onDismissImportMessage) { Text("OK") } })
            state.cloudConflictScope?.let { scope ->
                val saveInfo = state.cloudSaveInfo.firstOrNull { it.scope == scope }
                AlertDialog(onDismissRequest = {},
                    title = { Text("Zwei Spielstände gefunden") },
                    text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${saveInfo?.label ?: if (scope == "figures") "Figurensammlung" else "Spielstand"}: Dieses Gerät und die Cloud wurden beide geändert. Beide Versionen bleiben gesichert.")
                        if (saveInfo != null) Text("Dieses Gerät: ${saveTime(saveInfo.localModifiedAt)}\nCloud: ${cloudTimeLabel(saveInfo)}",
                            color = Muted)
                        Text("Welcher Stand soll aktiv sein? Die Uhrzeit allein sagt nicht, welcher Spielfortschritt weiter ist.", color = Muted)
                        TextButton(onCloudDefer) { Text("Später lokal weiterspielen") }
                    } },
                    confirmButton = { TextButton(onCloudChooseLocal, enabled = !state.cloudBusy) {
                        Text("Dieses Gerät")
                    } },
                    dismissButton = { TextButton(onCloudChooseRemote, enabled = !state.cloudBusy) {
                        Text("Cloud")
                    } })
            }
        }
    }
    }
}

@Composable
private fun ConsoleBackdrop(game: ConsoleGame, games: List<ConsoleGame>, dimmed: Boolean, swipe: HeroSwipeState) {
    val lowerIndex by remember(games, game.key, dimmed) {
        derivedStateOf {
            if (!dimmed && swipe.ready) kotlin.math.floor(swipe.position).toInt().coerceIn(games.indices)
            else games.indexOfFirst { it.key == game.key }.coerceAtLeast(0)
        }
    }
    val upperIndex = (lowerIndex + 1).coerceAtMost(games.lastIndex)
    val blend = {
        if (dimmed || !swipe.ready) 0f else (swipe.position - lowerIndex).coerceIn(0f, 1f)
    }
    BackdropLayer(games[lowerIndex], incoming = false, blend = blend)
    if (upperIndex != lowerIndex) BackdropLayer(games[upperIndex], incoming = true, blend = blend)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (dimmed) 0.64f else 0.26f)))
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
        Color.Black.copy(alpha = 0.48f), Color.Transparent, Color.Black.copy(alpha = 0.64f)))))
}

@Composable
private fun FigureBackdrop() {
    AsyncImage(R.drawable.sky_console_elements, null,
        modifier = Modifier.fillMaxSize().scale(1.06f).blur(15.dp),
        contentScale = ContentScale.Crop)
    Box(Modifier.fillMaxSize().background(Color(0xFF070C16).copy(alpha = 0.46f)))
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
        Color(0xFF070C16).copy(alpha = 0.30f), Color.Transparent,
        Color(0xFF070C16).copy(alpha = 0.62f)))))
}

@Composable
private fun BackdropLayer(game: ConsoleGame, incoming: Boolean, blend: () -> Float) {
    val context = LocalContext.current
    val motion = LocalPortalMotion.current
    val artwork: Any = game.sceneArtwork ?: "file:///android_asset/skylanders/images/${game.artwork}"
        val request = remember(artwork) {
            ImageRequest.Builder(context).data(artwork).apply {
                // Compose blur uses RenderEffect on Android 12+. A small filtered image
                // provides the soft backdrop on older devices without a new dependency.
                if (Build.VERSION.SDK_INT < 31) size(48, 24).precision(Precision.EXACT)
            }.build()
        }
        AsyncImage(request, null, modifier = Modifier.fillMaxSize().graphicsLayer {
            val fraction = blend()
            // Soft dissolving layers drift into each other instead of changing after release.
            alpha = if (incoming) fraction * fraction * (3f - 2f * fraction) else 1f
            val drift = if (incoming) 1f - fraction else -fraction
            translationX = if (motion) drift * 12.dp.toPx() else 0f
            scaleX = 1.18f + if (motion) kotlin.math.abs(drift) * 0.025f else 0f
            scaleY = scaleX
        }.blur(40.dp),
            contentScale = ContentScale.Crop)
}

@Composable
private fun ConsoleHeader(state: ConsoleState, onSelectPage: (ConsolePage) -> Unit,
    onOpenAccount: () -> Unit,
    accountFocus: FocusRequester) {
    val configuration = LocalConfiguration.current
    val phonePortrait = configuration.smallestScreenWidthDp < 600 &&
        configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    BoxWithConstraints(Modifier.fillMaxWidth().padding(
        horizontal = if (phonePortrait) 12.dp else 24.dp, vertical = 8.dp)) {
        val compact = maxWidth < 600.dp
        val motion = LocalPortalMotion.current
        val density = LocalDensity.current
        val tabBounds = remember { mutableStateMapOf<ConsolePage, Pair<Float, Float>>() }
        val activeBounds = tabBounds[state.page]
        val lineStart by animateFloatAsState(
            activeBounds?.let { it.first + it.second * 0.23f } ?: 0f,
            if (motion) spring(stiffness = 360f, dampingRatio = 0.84f) else tween(0),
            label = "tabLineStart")
        val lineWidth by animateFloatAsState(
            activeBounds?.second?.times(0.54f) ?: 0f,
            if (motion) spring(stiffness = 360f, dampingRatio = 0.84f) else tween(0),
            label = "tabLineWidth")
        val tabRowWidth = if (phonePortrait) maxWidth - 48.dp else maxWidth
        Box(Modifier.fillMaxWidth()) {
            Row(Modifier.width(tabRowWidth),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                ConsolePage.entries.forEach { page ->
                    val active = state.page == page
                    val zoom by animateFloatAsState(if (active) 1.10f else 1f,
                        if (motion) tween(320, easing = PortalPalette.GlideEasing) else tween(0),
                        label = "tabZoom${page.name}")
                    Box(Modifier.height(52.dp)
                        .onGloballyPositioned {
                            tabBounds[page] = it.positionInParent().x to it.size.width.toFloat()
                        }
                        .focusProperties { canFocus = false }
                        .clickable(interactionSource = remember { MutableInteractionSource() },
                            indication = null) { onSelectPage(page) }
                        .padding(horizontal = if (phonePortrait) 4.dp else if (compact) 9.dp else 20.dp),
                        contentAlignment = Alignment.Center) {
                        Text(page.title, fontSize = if (phonePortrait) 11.sp else if (compact) 12.sp else 14.sp,
                            color = if (active) Color.White else Color.White.copy(alpha = 0.67f),
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.graphicsLayer { scaleX = zoom; scaleY = zoom })
                    }
                }
            }
            Box(Modifier.align(Alignment.CenterEnd).padding(end = if (phonePortrait) 0.dp else 2.dp)) {
                ProfileAvatar(state.account.avatar, state.account.name ?: state.account.email,
                    if (phonePortrait) 42.dp else 48.dp,
                    selected = state.accountOpen, onClick = onOpenAccount,
                    modifier = Modifier.focusRequester(accountFocus))
                if (state.updateAvailable != null) Box(Modifier.align(Alignment.TopEnd)
                    .offset(x = 3.dp, y = (-3).dp).size(18.dp)
                    .background(Color(0xFFFFC65C), CircleShape), contentAlignment = Alignment.Center) {
                    Text("!", color = Color(0xFF151A22), fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
            }
            if (activeBounds != null && !state.accountOpen) Box(Modifier.align(Alignment.BottomStart)
                .offset { IntOffset(lineStart.roundToInt(), 0) }
                .width(with(density) { lineWidth.toDp() }).height(2.dp)
                .background(Color.White))
        }
    }
}

@Composable
private fun GamesPage(state: ConsoleState, onSelect: (ConsoleGame) -> Unit,
    onPlay: (ConsoleGame) -> Unit, onAdd: () -> Unit, onRefresh: () -> Unit,
    heroSwipe: HeroSwipeState, onFocusAccount: () -> Unit) {
    // Keep selection above both info variants and their recyclable hero pages.
    // The visible cover is the source of truth; persisted selection is only an output.
    key(state.games.map { it.key }) {
        val initialIndex = state.games.indexOfFirst { it.key == state.selectedKey }.coerceAtLeast(0)
        val gamePager = rememberPagerState(initialPage = initialIndex, pageCount = { state.games.size })
        val scope = rememberCoroutineScope()
        val motion = LocalPortalMotion.current
        val persistSelection by rememberUpdatedState(onSelect)
        LaunchedEffect(gamePager, state.ready) {
            if (state.ready) snapshotFlow { gamePager.settledPage to gamePager.isScrollInProgress }
                .distinctUntilChanged().collect { (index, moving) ->
                    if (!moving) state.games.getOrNull(index)?.let(persistSelection)
                }
        }
        LaunchedEffect(gamePager, heroSwipe) {
            snapshotFlow { (gamePager.currentPage + gamePager.currentPageOffsetFraction) to gamePager.isScrollInProgress }
                .collect { (position, moving) ->
                    heroSwipe.position = position
                    heroSwipe.moving = moving
                    heroSwipe.ready = true
                }
        }
        val visibleGame = state.games[gamePager.currentPage.coerceIn(state.games.indices)]
        val visibleState = state.copy(selectedKey = visibleGame.key)
        val selectGame: (ConsoleGame) -> Unit = { game ->
            val index = state.games.indexOfFirst { it.key == game.key }
            if (index >= 0) scope.launch {
                gamePager.animateScrollToPage(index,
                    animationSpec = tween(if (motion) PortalPalette.GlideDurationMs else 0,
                        easing = PortalPalette.GlideEasing))
            }
        }
        if (state.immersiveInfo) {
            ImmersiveGamesPage(visibleState, selectGame, onPlay, onAdd, onRefresh, heroSwipe,
                gamePager, onFocusAccount)
        } else {
            SideEffect { heroSwipe.infoProgress = 0f }
            ClassicGamesPage(visibleState, selectGame, onPlay, onAdd, onRefresh, heroSwipe,
                gamePager, onFocusAccount)
        }
    }
}

@Composable
private fun ImmersiveGamesPage(state: ConsoleState, onSelect: (ConsoleGame) -> Unit,
    onPlay: (ConsoleGame) -> Unit, onAdd: () -> Unit, onRefresh: () -> Unit, heroSwipe: HeroSwipeState,
    gamePager: PagerState, onFocusAccount: () -> Unit) {
    val selectedIndex = state.games.indexOfFirst { it.key == state.selectedKey }.coerceAtLeast(0)
    var detailsOpen by rememberSaveable { mutableStateOf(false) }
    val pager = rememberPagerState(initialPage = if (detailsOpen) 1 else 0, pageCount = { 2 })
    val motion = LocalPortalMotion.current
    val scope = rememberCoroutineScope()
    val sound = LocalMenuSound.current
    val focus = LocalFocusManager.current
    val inputMode = LocalInputModeManager.current
    val heroFocus = remember { FocusRequester() }
    val detailsFocus = remember { FocusRequester() }
    val lifecycle = LocalLifecycleOwner.current
    val restoreFocus by rememberUpdatedState<() -> Unit> {
        if (state.ready && inputMode.inputMode == InputMode.Keyboard && !pager.isScrollInProgress) {
            if (pager.settledPage == 1) detailsFocus.requestFocus() else heroFocus.requestFocus()
        }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) scope.launch { withFrameNanos { }; restoreFocus() }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer); heroSwipe.infoProgress = 0f }
    }
    LaunchedEffect(pager) {
        snapshotFlow { (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, 1f) }
            .collect { heroSwipe.infoProgress = it }
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { page ->
            val open = page == 1
            if (detailsOpen != open) sound(if (open) ConsoleCue.NAVIGATE else ConsoleCue.BACK)
            detailsOpen = open
        }
    }
    LaunchedEffect(state.ready, pager.settledPage, inputMode.inputMode) {
        withFrameNanos { }
        restoreFocus()
    }
    fun goTo(page: Int) {
        focus.clearFocus(force = true)
        scope.launch {
            pager.animateScrollToPage(page, animationSpec = tween(
                if (motion) PortalPalette.GlideDurationMs else 0, easing = PortalPalette.GlideEasing))
            withFrameNanos { }
            restoreFocus()
        }
    }
    BackHandler(detailsOpen || pager.currentPage != 0 || pager.currentPageOffsetFraction != 0f) { goTo(0) }
    val deliberateInfoSwipe = Modifier.pointerInput(state.ready, state.launchingGameKey) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var last = down.position
            var travelX = 0f
            var travelY = 0f
            var released = false
            while (!released) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                travelX += change.position.x - last.x
                travelY += change.position.y - last.y
                last = change.position
                released = !change.pressed
            }
            if (released && state.ready && state.launchingGameKey == null &&
                -travelY > size.height * 0.12f &&
                -travelY > kotlin.math.abs(travelX) * 1.45f) {
                goTo(1)
            }
        }
    }
    VerticalPager(state = pager, beyondViewportPageCount = 1,
        overscrollEffect = null,
        // The details content scrolls independently. A draggable pager can stop between
        // pages after the inner scroll reaches its top, leaving the hero out of view.
        userScrollEnabled = false,
        flingBehavior = PagerDefaults.flingBehavior(pager,
            snapPositionalThreshold = 0.22f,
            snapAnimationSpec = tween(if (motion) PortalPalette.GlideDurationMs else 0,
                easing = PortalPalette.GlideEasing)), modifier = Modifier.fillMaxSize()) { page ->
        if (page == 0) {
            GameStage(state, selectedIndex,
                { if (!detailsOpen && !pager.isScrollInProgress) onSelect(it) },
                { if (!detailsOpen && !pager.isScrollInProgress) onPlay(it) },
                { if (!detailsOpen && !pager.isScrollInProgress) onAdd() }, onRefresh,
                { goTo(1) }, heroFocus, heroSwipe, gamePager, onFocusAccount,
                Modifier.fillMaxSize().then(deliberateInfoSwipe).graphicsLayer {
                    val progress = (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                    // Counter the pager displacement: the menu stays behind the rising info layer.
                    translationY = progress * size.height
                    alpha = 1f - progress
                    scaleX = 1f - progress * 0.02f
                    scaleY = scaleX
                    renderEffect = if (Build.VERSION.SDK_INT >= 31 && progress > 0.001f) {
                        val radius = 24.dp.toPx() * progress
                        BlurEffect(radius, radius, TileMode.Clamp)
                    } else null
                })
        } else {
            GameDetails(state.games[selectedIndex], { goTo(0) }, Modifier.fillMaxSize()
                .focusRequester(detailsFocus).onPreviewKeyEvent {
                    if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionUp) {
                        goTo(0); true
                    } else false
                }.focusable(), panel = false)
        }
    }
}

@Composable
private fun ClassicGamesPage(state: ConsoleState, onSelect: (ConsoleGame) -> Unit,
    onPlay: (ConsoleGame) -> Unit, onAdd: () -> Unit, onRefresh: () -> Unit, heroSwipe: HeroSwipeState,
    gamePager: PagerState, onFocusAccount: () -> Unit) {
    val selectedIndex = state.games.indexOfFirst { it.key == state.selectedKey }.coerceAtLeast(0)
    val game = state.games[selectedIndex]
    val scroll = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val menuSound = LocalMenuSound.current
    val focusManager = LocalFocusManager.current
    val inputMode = LocalInputModeManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val heroFocus = remember { FocusRequester() }
    val detailsFocus = remember { FocusRequester() }
    var detailsOpen by rememberSaveable { mutableStateOf(false) }
    val restoreSectionFocus by rememberUpdatedState<() -> Unit> {
        if (state.ready) {
            if (state.controllers.isNotEmpty()) inputMode.requestInputMode(InputMode.Keyboard)
            if (detailsOpen) detailsFocus.requestFocus() else heroFocus.requestFocus()
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) scope.launch {
                withFrameNanos { }
                restoreSectionFocus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(inputMode.inputMode) {
        if (inputMode.inputMode == InputMode.Keyboard) {
            withFrameNanos { }
            restoreSectionFocus()
        }
    }
    val openDetails: () -> Unit = {
        menuSound(ConsoleCue.NAVIGATE)
        detailsOpen = true
        focusManager.clearFocus(force = true)
        scope.launch { scroll.animateScrollToItem(1); detailsFocus.requestFocus() }
    }
    val closeDetails: () -> Unit = {
        menuSound(ConsoleCue.BACK)
        detailsOpen = false
        // Release the old section before scrolling; its focused child can otherwise
        // bring itself back into view and interrupt the return animation on devices.
        focusManager.clearFocus(force = true)
        scope.launch { scroll.animateScrollToItem(0); heroFocus.requestFocus() }
    }
    BackHandler(detailsOpen || scroll.firstVisibleItemIndex > 0 || scroll.firstVisibleItemScrollOffset > 0) { closeDetails() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewport = maxHeight
        // Restore the section, not an old pixel offset from a different orientation/density.
        // Request focus here once; requesting it inside a recycled hero item would pull
        // the user back up while they are scrolling through the game information.
        LaunchedEffect(viewport, maxWidth, state.ready) {
            if (state.ready) {
                scroll.scrollToItem(if (detailsOpen) 1 else 0)
                withFrameNanos { }
                if (detailsOpen) detailsFocus.requestFocus() else heroFocus.requestFocus()
            }
        }
        LazyColumn(state = scroll, modifier = Modifier.fillMaxSize()) {
            item(key = "hero") {
                GameStage(state, selectedIndex, onSelect, onPlay, onAdd, onRefresh,
                    openDetails, heroFocus, heroSwipe, gamePager, onFocusAccount,
                    Modifier.fillMaxWidth().height(viewport))
            }
            item(key = "details") {
                GameDetails(game, closeDetails, Modifier.fillMaxWidth().heightIn(min = viewport)
                    .focusRequester(detailsFocus).onPreviewKeyEvent {
                        if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionUp) {
                            closeDetails(); true
                        } else false
                    }.focusable())
            }
        }
    }
}

@Composable
private fun GameStage(state: ConsoleState, selectedIndex: Int, onSelect: (ConsoleGame) -> Unit,
    onPlay: (ConsoleGame) -> Unit, onAdd: () -> Unit, onRefresh: () -> Unit,
    onDetails: () -> Unit, heroFocus: FocusRequester, heroSwipe: HeroSwipeState,
    pager: PagerState, onFocusAccount: () -> Unit, modifier: Modifier) {
    val game = state.games[selectedIndex]
    val motion = LocalPortalMotion.current
    val titlePosition = animateFloatAsState(
        if (heroSwipe.ready) heroSwipe.position else selectedIndex.toFloat(),
        if (motion) spring(dampingRatio = 1f, stiffness = 170f) else tween(0), label = "stickyTitle")
    val actionsAlpha = animateFloatAsState(if (heroSwipe.moving) 0f else 1f,
        tween(if (motion) { if (heroSwipe.moving) 160 else 340 } else 40,
            easing = PortalPalette.GlideEasing), label = "swipeActions")
    BoxWithConstraints(modifier.onPreviewKeyEvent {
        if (it.type != KeyEventType.KeyDown) false
        else when (it.key) {
            Key.DirectionUp -> { onFocusAccount(); true }
            Key.DirectionDown -> { onDetails(); true }
            Key.DirectionLeft -> { onSelect(state.games[(selectedIndex - 1).coerceAtLeast(0)]); true }
            Key.DirectionRight -> { onSelect(state.games[(selectedIndex + 1).coerceAtMost(state.games.lastIndex)]); true }
            else -> false
        }
    }) {
        val phone = LocalConfiguration.current.smallestScreenWidthDp < 600
        val phoneLandscape = phone && maxWidth > maxHeight
        val phonePortrait = phone && !phoneLandscape
        val wide = maxWidth >= 650.dp
        val short = maxHeight < 350.dp
        val gutter = if (wide) 32.dp else 20.dp
        val cardSize = if (phoneLandscape) (maxHeight * 0.72f).coerceIn(170.dp, 300.dp)
            else if (phonePortrait) (maxWidth * 0.64f).coerceIn(190.dp, 300.dp)
            else if (short) (maxHeight * 0.62f).coerceIn(140.dp, 220.dp)
            else if (wide) (maxWidth * 0.34f).coerceIn(320.dp, 440.dp)
            else (maxWidth * 0.82f).coerceIn(220.dp, 360.dp)
        val cardHeight = cardSize / 1.6f
        val stripTop = if (phoneLandscape) (maxHeight * 0.35f - cardHeight / 2).coerceAtLeast(0.dp)
            else if (short) 4.dp else maxHeight * 0.40f - cardHeight / 2
        val centerPadding = ((maxWidth - cardSize) / 2).coerceAtLeast(0.dp)
        val titleSize = if (phoneLandscape) 30f else if (short) 26f else if (wide) 54f else 32f
        HorizontalPager(state = pager, pageSize = PageSize.Fixed(cardSize),
            overscrollEffect = null,
            flingBehavior = PagerDefaults.flingBehavior(pager,
                snapAnimationSpec = tween(if (motion) PortalPalette.GlideDurationMs else 0,
                    easing = PortalPalette.GlideEasing)),
            userScrollEnabled = state.ready && state.launchingGameKey == null,
            beyondViewportPageCount = 1,
            contentPadding = PaddingValues(horizontal = centerPadding),
            pageSpacing = if (phonePortrait) 6.dp else if (wide) 14.dp else 10.dp,
            verticalAlignment = Alignment.CenterVertically,
            key = { state.games[it].key },
            modifier = Modifier.offset(y = stripTop).fillMaxWidth().height(cardHeight + 24.dp)) { index ->
                val item = state.games[index]
                SceneTile(item, index == pager.currentPage, cardSize, state.ready, Modifier,
                    prominence = {
                        (1f - kotlin.math.abs(pager.currentPage - index + pager.currentPageOffsetFraction))
                            .coerceIn(0f, 1f)
                    },
                    launching = state.launchingGameKey == item.key,
                    onBounds = { heroSwipe.coverBounds = it },
                    onClick = {
                        if (!heroSwipe.moving) {
                            if (item.key == game.key && item.path != null) onPlay(item) else onSelect(item)
                        }
                    })
        }
        Column(Modifier.offset(y = stripTop + cardHeight +
            if (phoneLandscape) 14.dp else if (short) 18.dp else 30.dp)
            .fillMaxWidth().padding(horizontal = gutter), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth()) {
                state.games.forEachIndexed { index, item ->
                    Text(item.displayTitle, color = Color.White, fontWeight = FontWeight.Black,
                        fontSize = titleSize.sp, lineHeight = (titleSize * 1.08f).sp, letterSpacing = (-0.8).sp,
                        textAlign = TextAlign.Center, maxLines = if (short) 1 else 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().graphicsLayer {
                            val distance = index - titlePosition.value
                            translationX = if (motion) distance * size.width * 0.22f else 0f
                            // Keep long game names from overlapping halfway through a swipe.
                            val visibility = (1f - kotlin.math.abs(distance) * 2f).coerceIn(0f, 1f)
                            alpha = visibility * visibility * (3f - 2f * visibility) * actionsAlpha.value
                        })
                }
            }
            Spacer(Modifier.height(if (short) 6.dp else 12.dp))
            Row(Modifier.graphicsLayer {
                alpha = actionsAlpha.value
                translationY = if (motion) (1f - actionsAlpha.value) * 4.dp.toPx() else 0f
            }, verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (phone) 10.dp else 0.dp)) {
                ScenePlayButton(game, state.ready,
                    { if (!heroSwipe.moving && actionsAlpha.value > 0.95f) onPlay(game) },
                    { if (!heroSwipe.moving && actionsAlpha.value > 0.95f) onAdd() },
                    short, Modifier.focusRequester(heroFocus))
                if (phone) IconButton(onClick = onDetails, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.KeyboardDoubleArrowUp, "Spielinfos öffnen",
                        tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .padding(start = gutter, end = gutter, top = 10.dp, bottom = if (wide) 10.dp else 58.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Crossfade(selectedIndex + 1, modifier = Modifier.width(52.dp),
                animationSpec = tween(if (motion) 240 else 0), label = "gameNumber") { number ->
                Text("${number.toString().padStart(2, '0')} / ${state.games.size.toString().padStart(2, '0')}",
                    color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            if (wide) {
                Spacer(Modifier.width(18.dp))
                LinearProgressIndicator(progress = {
                    ((if (heroSwipe.ready) heroSwipe.position else selectedIndex.toFloat()) + 1f)
                        .div(state.games.size).coerceIn(0f, 1f)
                },
                    modifier = Modifier.width(120.dp).height(1.dp),
                    color = Color.White, trackColor = Color.White.copy(alpha = 0.2f))
            }
            Spacer(Modifier.weight(1f))
            IconButton(onAdd, enabled = state.ready) { Icon(Icons.Outlined.FolderOpen, "Spieleordner hinzufügen", tint = Color.White) }
            if (wide) IconButton(onRefresh, enabled = state.ready && !state.refreshing) { Icon(Icons.Outlined.Refresh, "Spiele aktualisieren", tint = Color.White) }
        }
        if (!phone) TextButton(onDetails, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp)
            .semantics { contentDescription = "Nach oben wischen für Spielinfos" },
            colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.85f))) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.KeyboardDoubleArrowUp, null, modifier = Modifier.size(24.dp))
                Text("Spielinfos", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ScenePlayButton(game: ConsoleGame, ready: Boolean, onPlay: () -> Unit, onAdd: () -> Unit,
    compact: Boolean, modifier: Modifier = Modifier) {
    var downloadOpen by remember(game.key) { mutableStateOf(false) }
    if (downloadOpen) AlertDialog(onDismissRequest = { downloadOpen = false },
        title = { Text("${game.displayTitle} herunterladen", fontWeight = FontWeight.Black) },
        text = { Text("Für dieses Spiel gibt es in der App noch keine Downloadquelle. Du kannst deine eigene Wii-Spieldatei auswählen oder einen Spieleordner verbinden. Die App erkennt das Spiel automatisch.") },
        confirmButton = { TextButton({ downloadOpen = false; onAdd() }) { Text("Eigene Datei einbinden") } },
        dismissButton = { TextButton({ downloadOpen = false }) { Text("Schließen") } })
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(if (game.path != null) onPlay else { { downloadOpen = true } }, enabled = ready, modifier = modifier.heightIn(min = 44.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF15100B)),
            shape = CircleShape, contentPadding = PaddingValues(horizontal = 32.dp, vertical = if (compact) 6.dp else 10.dp)) {
            Icon(if (game.path == null) Icons.Outlined.Download else Icons.Outlined.PlayArrow, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (game.path == null) "Spiel herunterladen" else "Starten", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        if (!compact) Text(if (game.path != null) "Wii · Auf diesem Gerät" else
            game.downloadBytes?.let { "${String.format(java.util.Locale.GERMANY, "%.2f", it / 1_000_000_000.0)} GB · Downloadquelle folgt" }
                ?: "Dateigröße und Downloadquelle folgen",
            color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp,
            modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun GameDetails(game: ConsoleGame, onBack: () -> Unit, modifier: Modifier = Modifier, panel: Boolean = true) {
    val info = consoleGameInfo[game.title]
    val uriHandler = LocalUriHandler.current
    val menuSound = LocalMenuSound.current
    val infoScroll = rememberScrollState()
    val returnSwipe = if (panel) Modifier else Modifier.pointerInput(onBack) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            // A swipe that starts within the description should scroll it to the top first.
            if (infoScroll.value > 0) return@awaitEachGesture
            var last = down.position
            var travelX = 0f
            var travelY = 0f
            var released = false
            while (!released) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                travelX += change.position.x - last.x
                travelY += change.position.y - last.y
                last = change.position
                released = !change.pressed
            }
            if (released && travelY > size.height * 0.12f &&
                travelY > kotlin.math.abs(travelX) * 1.45f) onBack()
        }
    }
    Box(modifier.then(returnSwipe)
        .then(if (panel) Modifier.background(Color(0xFF100E13).copy(alpha = 0.88f)) else Modifier),
        contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 1000.dp).fillMaxWidth()
            .then(if (panel) Modifier else Modifier.verticalScroll(infoScroll, overscrollEffect = null))
            .padding(horizontal = 28.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)) {
            TextButton(onBack, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                Icon(Icons.Outlined.KeyboardArrowUp, null)
                Text("Zur Spieleauswahl", fontWeight = FontWeight.Bold)
            }
            Text(game.displayTitle, color = Color.White, fontWeight = FontWeight.Black, fontSize = 36.sp, lineHeight = 40.sp)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth > 600.dp) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                        GameFact("Deine Spielzeit", playTimeLabel(game), "Von Dolphin · auf diesem Gerät", Modifier.weight(1f))
                        GameFact("Veröffentlicht", info?.releaseDate ?: "Nicht bekannt", info?.releaseScope ?: "Keine geprüfte Angabe", Modifier.weight(1f))
                        GameFact("Publisher", if (info != null) "Activision" else "Nicht bekannt", "", Modifier.weight(1f))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                        GameFact("Deine Spielzeit", playTimeLabel(game), "Von Dolphin · auf diesem Gerät")
                        GameFact("Veröffentlicht", info?.releaseDate ?: "Nicht bekannt", info?.releaseScope ?: "Keine geprüfte Angabe")
                    }
                }
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
            if (info != null) {
                Text("Über das Spiel", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text(info.summary, color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp, lineHeight = 26.sp)
                Text("„${info.excerpt}“", color = Color.White, fontSize = 18.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
                Text(info.excerptCredit, color = Muted, fontSize = 12.sp)
                TextButton({ menuSound(ConsoleCue.CONFIRM); uriHandler.openUri(info.sourceUrl) }) {
                    Text("Vollständige offizielle Beschreibung", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Outlined.OpenInNew, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            } else Text("Für dieses Spiel ist noch keine geprüfte Beschreibung hinterlegt.", color = Muted)
        }
    }
}

@Composable
private fun GameFact(label: String, value: String, caption: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
        if (caption.isNotEmpty()) Text(caption, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun SceneTile(game: ConsoleGame, active: Boolean, size: androidx.compose.ui.unit.Dp,
    ready: Boolean, modifier: Modifier, prominence: () -> Float, launching: Boolean,
    onBounds: (Rect) -> Unit, onClick: () -> Unit) {
    val art: Any = game.sceneArtwork ?: "file:///android_asset/skylanders/images/${game.artwork}"
    val tileShape = RoundedCornerShape(2.dp)
    val motion = LocalPortalMotion.current
    val breathingPhase: State<Float> = if (motion && active) {
        rememberInfiniteTransition(label = "gameTileBreathing").animateFloat(
            0f, (2f * Math.PI).toFloat(),
            infiniteRepeatable(tween(7200, easing = LinearEasing), RepeatMode.Restart),
            label = "gameTileBreath")
    } else remember { mutableFloatStateOf(0f) }
    Box(modifier.width(size).height(size / 1.6f + 20.dp).padding(12.dp).zIndex(if (active) 1f else 0f)
        .graphicsLayer {
            val breath = if (motion && active) (1f - kotlin.math.cos(breathingPhase.value)) * 0.5f else 0f
            scaleX = (0.84f + 0.16f * prominence()) * (1f + breath * 0.035f)
            scaleY = scaleX
        }
        .shadow(
            elevation = if (active) 30.dp else 17.dp,
            shape = tileShape,
            ambientColor = if (active) Color(0xFFD9F2FF).copy(alpha = 0.82f)
                else Color(game.color).copy(alpha = 0.30f),
            spotColor = if (active) Color.White.copy(alpha = 0.90f)
                else Color(game.color).copy(alpha = 0.45f)
        )) {
        PortalSurface(Modifier.fillMaxSize().semantics { selected = active }
            .onGloballyPositioned { if (active && !launching) onBounds(it.boundsInRoot()) },
            accent = Color.White, selected = active, enabled = ready && !launching,
            shadow = false,
            shape = tileShape, onClick = onClick) {
            Box(Modifier.fillMaxSize().background(Color(game.color).copy(alpha = 0.07f)))
            AsyncImage(art, game.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (!active) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.12f)))
        }
    }
}

@Composable
private fun FiguresPage(state: ConsoleState, onRefresh: () -> Unit,
    accountFocus: FocusRequester) {
    val searchFocus = remember { FocusRequester() }
    val collectionFocus = remember { FocusRequester() }
    val filterFocus = remember { FocusRequester() }
    var query by rememberSaveable { mutableStateOf("") }
    var ultimate by rememberSaveable { mutableStateOf(false) }
    var gameFilter by rememberSaveable { mutableStateOf("") }
    var elementFilter by rememberSaveable { mutableStateOf("") }
    var showVariants by rememberSaveable { mutableStateOf(true) }
    var showTraps by rememberSaveable { mutableStateOf(true) }
    var sort by rememberSaveable { mutableStateOf("game") }
    val filterColors = FilterChipDefaults.filterChipColors(
        containerColor = Color.White.copy(alpha = 0.04f), labelColor = Muted,
        iconColor = Muted, selectedContainerColor = Color.White.copy(alpha = 0.18f),
        selectedLabelColor = Color.White, selectedLeadingIconColor = Color.White)
    val sound = LocalMenuSound.current
    val figures = remember(state.figures, query, ultimate, gameFilter, elementFilter, showVariants, showTraps, sort) {
        filterConsoleFigures(state.figures, ultimate, query, gameFilter, elementFilter, showVariants, showTraps, sort)
    }
    LazyVerticalGrid(columns = GridCells.Adaptive(190.dp), contentPadding = PaddingValues(28.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PortalIntro("Deine Sammlung", "Dein Portal. Deine Helden.", "${state.figures.size} Figuren · Bereit für dein nächstes Abenteuer.")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ConsoleButton("Sammlung (${state.figures.count { !it.ultimate }})", Icons.Outlined.AutoAwesome,
                        { ultimate = false }, primary = !ultimate,
                        modifier = Modifier.focusRequester(collectionFocus).focusProperties {
                            up = accountFocus; down = searchFocus
                        },
                        shape = RoundedCornerShape(2.dp))
                    ConsoleButton("Ultimative (${state.figures.count { it.ultimate }})", Icons.Outlined.StarOutline,
                        { ultimate = true }, primary = ultimate,
                        modifier = Modifier.focusProperties { up = accountFocus; down = searchFocus },
                        shape = RoundedCornerShape(2.dp))
                }
                FigureSearchField(query, searchFocus, collectionFocus, filterFocus) { query = it }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FigureFilterMenu("Spiel", gameFilter, listOf("" to "Alle Spiele") + figureGameLabels.toList(),
                        Modifier.focusRequester(filterFocus).focusProperties { up = searchFocus }) { gameFilter = it }
                    FigureFilterMenu("Element", elementFilter, listOf("" to "Alle Elemente") + figureElementLabels.toList(),
                        Modifier.focusProperties { up = searchFocus }, elementIcons = true) { elementFilter = it }
                    FigureFilterMenu("Sortieren", sort, listOf("game" to "Spiel", "element" to "Element", "name" to "Name"),
                        Modifier.focusProperties { up = searchFocus }) { sort = it }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(showVariants, { sound(ConsoleCue.CONFIRM); showVariants = !showVariants },
                        colors = filterColors, shape = RoundedCornerShape(2.dp),
                        label = { Text("Varianten") }, leadingIcon = {
                            Icon(if (showVariants) Icons.Outlined.Check else Icons.Outlined.Add, null, Modifier.size(18.dp))
                        })
                    FilterChip(showTraps, { sound(ConsoleCue.CONFIRM); showTraps = !showTraps },
                        colors = filterColors, shape = RoundedCornerShape(2.dp),
                        label = { Text("Fallen") }, leadingIcon = {
                            Icon(if (showTraps) Icons.Outlined.Check else Icons.Outlined.Add, null, Modifier.size(18.dp))
                        })
                    Text("${figures.size} angezeigt", color = Muted, fontSize = 12.sp)
                    if (query.isNotBlank() || gameFilter.isNotEmpty() || elementFilter.isNotEmpty() || !showVariants || !showTraps) {
                        TextButton({
                            query = ""; gameFilter = ""; elementFilter = ""; showVariants = true; showTraps = true
                            sound(ConsoleCue.BACK)
                        }) { Text("Zurücksetzen") }
                    }
                }
                Text("Figuren auflegen und neue beschwören kannst du im Figurenmenü während des Spiels.",
                    color = Muted, fontSize = 13.sp)
            }
        }
        if (state.figureError != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                InfoCard("Sammlung nicht verfügbar", state.figureError) {
                    ConsoleButton("Erneut versuchen", Icons.Outlined.Refresh, onRefresh, enabled = state.ready)
                }
            }
        } else if (figures.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                val hasFigures = state.figures.any { it.ultimate == ultimate }
                InfoCard(if (hasFigures) "Keine passende Figur" else "Dein Abenteuer beginnt hier",
                    if (hasFigures) "Passe die Suche oder deine Filter an."
                    else "Wechsle mit L1 zu Spiele und öffne dort das Figurenmenü. Dort kannst du deine erste Figur beschwören.")
            }
        }
        items(figures, key = { it.path }) { figure ->
            var focused by remember { mutableStateOf(false) }
            FigureCharacterCard(figure, focused) { focused = it }
        }
    }
}

private fun figureAccent(element: String): Color = when (element) {
    "Magic" -> Color(0xFFC968F1)
    "Fire" -> Color(0xFFFF6E56)
    "Air" -> Color(0xFF8CDFFF)
    "Earth" -> Color(0xFFE3B66C)
    "Life" -> Color(0xFF74DF8C)
    "Water" -> Color(0xFF59D6FF)
    "Tech" -> Color(0xFFFFCE62)
    "Undead" -> Color(0xFFBBA0D8)
    "Light" -> Color(0xFFFFEC9B)
    "Dark" -> Color(0xFF9690ED)
    else -> Ice
}

@Composable
private fun FigureCharacterCard(figure: ConsoleFigure, focused: Boolean, onFocus: (Boolean) -> Unit) {
    val accent = figureAccent(figure.element)
    val artwork = figure.image?.let { "file:///android_asset/skylanders/images/$it" }
    PortalSurface(Modifier.fillMaxWidth().onFocusChanged { onFocus(it.isFocused) }
        .focusable().semantics { contentDescription = figure.name },
        accent = accent, selected = focused, breathe = true, shape = RoundedCornerShape(2.dp)) {
        Column {
            Box(Modifier.fillMaxWidth().height(178.dp)
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.22f),
                    Color(0xFF101724).copy(alpha = 0.75f), Color(0xFF090D16))))) {
                Box(Modifier.align(Alignment.TopStart).width(48.dp).height(3.dp).background(accent))
                Text((figureElementLabels[figure.element] ?: figure.element).uppercase(),
                    color = accent, fontSize = 10.sp, fontWeight = FontWeight.Black,
                    letterSpacing = 1.4.sp,
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 14.dp))
                if (artwork != null) AsyncImage(artwork, null,
                    modifier = Modifier.fillMaxSize().padding(start = 8.dp, end = 8.dp, top = 28.dp),
                    contentScale = ContentScale.Fit)
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(38.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent,
                        Color(0xFF090D16).copy(alpha = 0.78f)))))
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(figure.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp,
                    lineHeight = 19.sp, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text((figureGameLabels[figure.game] ?: "Spiel unbekannt").uppercase(),
                    color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 0.7.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (figure.type in setOf("Skylander", "Giant", "Swapper", "TrapMaster", "Mini")) {
                    Text(when (figure.upgradePath) {
                        1 -> "Oberer Pfad"
                        2 -> "Unterer Pfad"
                        0 -> "Noch kein Pfad gewählt"
                        else -> "Pfad nicht lesbar"
                    }, color = if (figure.upgradePath > 0) accent else Muted,
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (focused) {
            Box(Modifier.matchParentSize().border(3.dp, Color.White, RoundedCornerShape(2.dp)))
        }
    }
}

@Composable
private fun FigureElementIcon(element: String, size: androidx.compose.ui.unit.Dp = 22.dp) {
    if (element.isEmpty()) {
        Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(size), tint = Ice)
    } else if (element in figureElementLabels && element != "Other") {
        AsyncImage("file:///android_asset/skylanders/elements/$element.png", null,
            modifier = Modifier.size(size), contentScale = ContentScale.Fit)
    } else {
        Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(size), tint = Ice)
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun FigureSearchField(query: String, navigationFocus: FocusRequester,
    previousFocus: FocusRequester, nextFocus: FocusRequester, onQueryChange: (String) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val editorFocus = remember { FocusRequester() }
    var editing by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(false) }
    val imeVisible = WindowInsets.isImeVisible

    fun finishEditing() {
        keyboard?.hide()
        editing = false
        scope.launch {
            withFrameNanos { }
            navigationFocus.requestFocus()
        }
    }

    LaunchedEffect(editing) {
        if (editing) {
            editorFocus.requestFocus()
            keyboard?.show()
        }
    }
    var keyboardWasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible, editing) {
        if (editing && imeVisible) keyboardWasVisible = true
        if (editing && keyboardWasVisible && !imeVisible) {
            keyboardWasVisible = false
            finishEditing()
        }
        if (!editing) keyboardWasVisible = false
    }
    BackHandler(editing) { finishEditing() }

    if (editing) {
        OutlinedTextField(query, onQueryChange, label = { Text("Figur suchen") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(showKeyboardOnFocus = false, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { finishEditing() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.White, unfocusedBorderColor = Color.White.copy(alpha = 0.48f)),
            modifier = Modifier.fillMaxWidth().focusRequester(editorFocus),
            shape = RoundedCornerShape(2.dp))
    } else {
        OutlinedButton(onClick = { editing = true },
            modifier = Modifier.fillMaxWidth().height(64.dp).focusRequester(navigationFocus)
                .focusProperties { up = previousFocus; down = nextFocus }
                .onFocusChanged { selected = it.isFocused },
            shape = RoundedCornerShape(2.dp),
            border = BorderStroke(if (selected) 2.dp else 1.dp,
                if (selected) Color.White else Color.White.copy(alpha = 0.48f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White,
                containerColor = if (selected) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.18f)),
            contentPadding = PaddingValues(horizontal = 16.dp)) {
            Icon(Icons.Outlined.Search, null, Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(query.ifBlank { "Figur suchen" }, modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start, color = if (query.isBlank()) Muted else Color.White)
        }
    }
}

@Composable
private fun FigureFilterMenu(label: String, value: String, options: List<Pair<String, String>>,
    modifier: Modifier = Modifier, elementIcons: Boolean = false, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var focusedOption by remember { mutableStateOf<String?>(null) }
    val sound = LocalMenuSound.current
    Box {
        OutlinedButton({ focusedOption = value; open = true; sound(ConsoleCue.NAVIGATE) },
            modifier = modifier.onFocusChanged { focused = it.isFocused },
            shape = RoundedCornerShape(2.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White,
                containerColor = if (focused) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.18f)),
            border = BorderStroke(if (focused) 2.dp else 1.dp,
                if (focused) Color.White else Color.White.copy(alpha = 0.48f))) {
            if (elementIcons) {
                FigureElementIcon(value)
                Spacer(Modifier.width(8.dp))
            }
            Text("$label: ${options.firstOrNull { it.first == value }?.second ?: value}")
            Icon(Icons.Outlined.ExpandMore, null, Modifier.size(20.dp))
        }
        DropdownMenu(open, { open = false; focusedOption = null },
            modifier = Modifier.widthIn(min = 220.dp).heightIn(max = 420.dp)
                .background(Color(0xFF121A29), RoundedCornerShape(2.dp))
                .border(1.dp, Color.White.copy(alpha = 0.38f), RoundedCornerShape(2.dp))) {
            options.forEach { (key, title) ->
                DropdownMenuItem(text = { Text(title) }, modifier = Modifier
                    .onFocusChanged { if (it.isFocused) focusedOption = key }
                    .background(if (focusedOption == key) Ice.copy(alpha = 0.25f) else Color.Transparent),
                    leadingIcon = if (elementIcons) { { FigureElementIcon(key) } } else null,
                    onClick = {
                    onSelect(key); open = false; sound(ConsoleCue.CONFIRM)
                }, trailingIcon = { if (key == value) Icon(Icons.Outlined.Check, null) })
            }
        }
    }
}

@Composable
private fun ControllersPage(state: ConsoleState, onBluetooth: () -> Unit, onMapping: () -> Unit,
    onMapping2: () -> Unit, onSwapControllers: () -> Unit,
    onAutoController: (Boolean) -> Unit, onStartTest: () -> Unit,
    accountFocus: FocusRequester) {
    LazyVerticalGrid(columns = GridCells.Adaptive(500.dp), contentPadding = PaddingValues(28.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            PortalIntro("Controller", "Alles unter Kontrolle.", "Verbinden. Testen. Losspielen.")
        }
        item {
            InfoCard(if (state.controllers.isEmpty()) "Noch kein Controller verbunden" else "Verbunden mit deinem Gerät",
                state.controllers.joinToString("\n").ifEmpty { "Verbinde deinen Controller über Bluetooth oder USB." },
                angular = true) {
                ConsoleButton("Bluetooth öffnen", Icons.Outlined.Bluetooth, onBluetooth, primary = true,
                    modifier = Modifier.focusProperties { up = accountFocus },
                    shape = RoundedCornerShape(2.dp))
            }
        }
        item {
            InfoCard("Tastentest", "Teste alle Tasten, Sticks und Trigger in einem eigenen Bildschirm.",
                angular = true) {
                ConsoleButton("Test starten", Icons.Outlined.PlayArrow, onStartTest,
                    shape = RoundedCornerShape(2.dp))
            }
        }
        item {
            InfoCard("Automatische Belegung", state.controllerSetup, angular = true) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Controller automatisch einrichten", color = Color.White, modifier = Modifier.weight(1f))
                    SettingsSwitch(state.autoController, onAutoController, "Controller automatisch einrichten")
                }
                Text("Ein eigenes Profil pro Controller. Bereits angepasste Profile werden wiederverwendet.",
                    color = Muted, fontSize = 12.sp)
            }
        }
        item {
            InfoCard("Spieler 1", state.player1Controller ?: "Noch kein Controller zugewiesen.",
                angular = true) {
                ConsoleButton("Belegung Spieler 1", Icons.Outlined.Gamepad, onMapping, enabled = state.ready,
                    shape = RoundedCornerShape(2.dp))
            }
        }
        item {
            InfoCard("Spieler 2", state.player2Controller ?: "Verbinde einen zweiten Controller vor dem Spielstart.",
                angular = true) {
                ConsoleButton("Belegung Spieler 2", Icons.Outlined.Gamepad, onMapping2, enabled = state.ready,
                    shape = RoundedCornerShape(2.dp))
                if (state.player2Controller != null) {
                    ConsoleButton("Spieler 1 und 2 tauschen", Icons.Outlined.SwapHoriz, onSwapControllers,
                        shape = RoundedCornerShape(2.dp))
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("Menü: Steuerkreuz oder linker Stick · A / ✕ bestätigen · B / ○ zurück · L1 / R1 Bereiche wechseln",
                color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ControllerTestPage(state: ConsoleState, onStop: () -> Unit) {
    LazyVerticalGrid(columns = GridCells.Adaptive(500.dp), contentPadding = PaddingValues(28.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Box(Modifier.weight(1f)) {
                    PortalIntro("CONTROLLER · TASTENTEST", "Teste deine Steuerung.",
                        "Alle Controller-Eingaben bleiben hier. Tippe oder klicke auf „Test beenden“, um zurückzugehen.")
                }
                ConsoleButton("Test beenden", Icons.Outlined.Close, onStop, primary = true,
                    shape = RoundedCornerShape(2.dp))
            }
        }
        item {
            InfoCard("Letzte Eingabe", state.testLastInput, angular = true) {
                Text(state.testDeviceName, color = Ice, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        item {
            val buttons = state.testPressedKeys.sorted().map { code ->
                android.view.KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_").replace('_', ' ')
            }
            InfoCard("Gedrückte Tasten", if (buttons.isEmpty()) "Noch keine Taste gedrückt."
                else buttons.joinToString("  ·  "), angular = true)
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            PortalSurface(Modifier.fillMaxWidth(), shadow = false, focusWholeSurface = false,
                shape = RoundedCornerShape(2.dp)) {
                Column(Modifier.fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    PortalCardTitle("Sticks, Steuerkreuz und Trigger")
                    Text("Bewege die Sticks und drücke die Trigger. Die Werte reagieren live auf deinen Controller.",
                        color = Muted, fontSize = 14.sp)
                    if (state.testAxes.isEmpty()) {
                        Text("Noch keine Achsbewegung erkannt.", color = Color.White, fontSize = 14.sp)
                    } else {
                        state.testAxes.toSortedMap().forEach { (axis, value) ->
                            val label = android.view.MotionEvent.axisToString(axis).removePrefix("AXIS_").replace('_', ' ')
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(label, color = Color.White, fontSize = 13.sp,
                                    modifier = Modifier.width(112.dp))
                                BoxWithConstraints(Modifier.weight(1f).height(10.dp)
                                    .background(Color.White.copy(alpha = 0.13f))) {
                                    val trigger = axis in listOf(android.view.MotionEvent.AXIS_LTRIGGER,
                                        android.view.MotionEvent.AXIS_RTRIGGER, android.view.MotionEvent.AXIS_BRAKE,
                                        android.view.MotionEvent.AXIS_GAS)
                                    val position = (if (trigger) value else (value + 1f) / 2f).coerceIn(0f, 1f)
                                    Box(Modifier.offset(x = (maxWidth - 7.dp) * position).width(7.dp)
                                        .fillMaxHeight().background(Ice))
                                }
                                Text("%.2f".format(java.util.Locale.ROOT, value), color = Ice,
                                    fontSize = 13.sp, modifier = Modifier.width(48.dp), textAlign = TextAlign.End)
                            }
                        }
                    }
                }
                Box(Modifier.align(Alignment.TopStart).width(66.dp).height(3.dp).background(Ice))
            }
        }
    }
}

@Composable
private fun SettingsPage(state: ConsoleState, onAudioSettings: (ConsoleAudioSettings) -> Unit,
    onInfoAnimation: (Boolean) -> Unit,
    onAutoGraphics: (Boolean) -> Unit,
    onSettings: () -> Unit, onAdvanced: () -> Unit, onSetup: () -> Unit, onAddGames: () -> Unit,
    onNavigationChanged: (Boolean) -> Unit, onControllerVibration: (Boolean) -> Unit,
    onTestVibration: () -> Unit, onVibrationIntensity: (Float) -> Unit,
    onOpenAccount: () -> Unit, onFocusAccount: () -> Unit) {
    val sound = LocalMenuSound.current
    val inputMode = LocalInputModeManager.current
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val titles = listOf("Bibliothek und Einrichtung", "Grafik", "Spielinfos", "Menümusik",
        "Menüklänge", "Controller-Vibration", "Profil", "Bild, Ton und Spielsteuerung", "Erweiterte Verwaltung")
    val targets = remember { List(titles.size) { FocusRequester() } }
    var selectedCard by rememberSaveable { mutableIntStateOf(0) }
    var navigationJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val onFocused: (Int) -> Unit = { if (navigationJob?.isActive != true) selectedCard = it }
    DisposableEffect(Unit) { onDispose { onNavigationChanged(false) } }
    LaunchedEffect(inputMode.inputMode) {
        if (inputMode.inputMode == InputMode.Keyboard) {
            onNavigationChanged(true)
            withFrameNanos { }
            list.scrollToItem(if (selectedCard == 0) 0 else selectedCard + 1)
            withFrameNanos { }
            targets[selectedCard].requestFocus()
            onNavigationChanged(false)
        }
    }
    LazyColumn(state = list, modifier = Modifier.onPreviewKeyEvent { event ->
        val step = when (event.key) {
            Key.DirectionUp -> -1
            Key.DirectionDown -> 1
            else -> 0
        }
        if (step == 0) false else if (step < 0 && selectedCard == 0 &&
            list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0) {
            if (event.type == KeyEventType.KeyDown) onFocusAccount()
            true
        } else {
            if (event.type == KeyEventType.KeyDown) {
                val next = (selectedCard + step).coerceIn(titles.indices)
                if (next != selectedCard || (next == 0 &&
                        (list.firstVisibleItemIndex != 0 || list.firstVisibleItemScrollOffset != 0))) {
                    selectedCard = next
                    navigationJob?.cancel()
                    onNavigationChanged(true)
                    navigationJob = scope.launch {
                        // Align the complete card before focusing a control inside it.
                        // The last card stays fully visible even when the list cannot align its top.
                        try {
                            list.animateScrollToItem(if (next == 0) 0 else next + 1)
                            withFrameNanos { }
                            targets[next].requestFocus()
                        } finally {
                            if (selectedCard == next) onNavigationChanged(false)
                        }
                    }
                }
            }
            true // Clamp both ends: never let focus wrap into the tabs.
        }
    }, contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "intro") { PortalIntro("Einstellungen", "Deine Konsole.", "Dein Klang. Deine Steuerung. Dein Abenteuer.") }
        item(key = "library") {
            SettingsCardFrame(titles[0], targets[0], { onFocused(0) }) {
                InfoCard(titles[0], "${state.games.count { it.path != null }} Spiele auf diesem Gerät · Figuren und Spielstände bleiben lokal gespeichert.", angular = true) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ConsoleButton("Eigene Spiele einbinden", Icons.Outlined.FolderOpen, onAddGames, primary = true,
                            enabled = state.ready && !state.importing, shape = RoundedCornerShape(2.dp))
                        ConsoleButton("Einrichtung öffnen", Icons.Outlined.CheckCircle, onSetup,
                            shape = RoundedCornerShape(2.dp))
                    }
                }
            }
        }
        item(key = "graphics") {
            SettingsCardFrame(titles[1], targets[1], { onFocused(1) }) {
                InfoCard(titles[1], if (state.autoGraphics) state.graphicsSetup else "Deine eigenen Grafikeinstellungen sind aktiv.", angular = true) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Grafik automatisch optimieren", color = Color.White, modifier = Modifier.weight(1f))
                        SettingsSwitch(state.autoGraphics, onAutoGraphics, "Grafik automatisch optimieren")
                    }
                    Text("Passende Auflösung und Einstellungen werden beim Spielstart für dein Gerät gewählt.", color = Muted, fontSize = 12.sp)
                }
            }
        }
        item(key = "information") {
            SettingsCardFrame(titles[2], targets[2], { onFocused(2) }) {
                InfoCard(titles[2], if (state.immersiveInfo)
                    "Immersiv: zwei einrastende Ansichten mit Unschärfe und weichem Übergang."
                    else "Klassisch: frei zwischen Spielemenü und Spielinfos scrollen.", angular = true) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Immersive Spielinfos", color = Color.White, modifier = Modifier.weight(1f))
                        SettingsSwitch(state.immersiveInfo, onInfoAnimation, "Immersive Spielinfos")
                    }
                }
            }
        }
        item(key = "music") {
            SettingsCardFrame(titles[3], targets[3], { onFocused(3) }) {
                AudioControl(titles[3], "Skylanders Main Theme · Hans Zimmer & Lorne Balfe",
                    state.audio.musicEnabled, state.audio.musicVolume,
                    { onAudioSettings(state.audio.copy(musicEnabled = it)); sound(ConsoleCue.CONFIRM) },
                    { onAudioSettings(state.audio.copy(musicVolume = it)) })
            }
        }
        item(key = "effects") {
            SettingsCardFrame(titles[4], targets[4], { onFocused(4) }) {
                AudioControl(titles[4], "Ein kurzer, dumpfer Ton beim Navigieren und Bestätigen.",
                    state.audio.effectsEnabled, state.audio.effectsVolume,
                    { onAudioSettings(state.audio.copy(effectsEnabled = it)); sound(ConsoleCue.CONFIRM) },
                    { onAudioSettings(state.audio.copy(effectsVolume = it)) }, previewSound = true) {
                    if (state.audioError != null) Text(state.audioError, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        item(key = "haptics") {
            SettingsCardFrame(titles[5], targets[5], { onFocused(5) }) {
                HapticsControl(state, onControllerVibration, onTestVibration, onVibrationIntensity)
            }
        }
        item(key = "profile") {
            SettingsCardFrame(titles[6], targets[6], { onFocused(6) }) {
                InfoCard(titles[6], state.account.email?.let { "Angemeldet als $it" }
                    ?: "Melde dich an oder erstelle ein Konto. Figuren und Spielstände bleiben vorerst lokal auf diesem Gerät.", angular = true) {
                    ConsoleButton("Profil öffnen", Icons.Outlined.PersonOutline, onOpenAccount,
                        shape = RoundedCornerShape(2.dp))
                }
            }
        }
        item(key = "emulator") {
            SettingsCardFrame(titles[7], targets[7], { onFocused(7) }) {
                InfoCard(titles[7], "Öffne die vorhandenen Einstellungen des Emulators.", angular = true) {
                    ConsoleButton("Einstellungen öffnen", Icons.Outlined.Settings, onSettings, primary = true,
                        shape = RoundedCornerShape(2.dp))
                }
            }
        }
        item(key = "advanced") {
            SettingsCardFrame(titles[8], targets[8], { onFocused(8) }) {
                InfoCard(titles[8], "Die bisherige Dolphin-Oberfläche bleibt für zusätzliche Werkzeuge erreichbar.", angular = true) {
                    ConsoleButton("Dolphin-Werkzeuge", Icons.Outlined.Tune, onAdvanced,
                        shape = RoundedCornerShape(2.dp))
                }
            }
        }
    }
}

private val LocalSettingsCardFocused = compositionLocalOf { false }

@Composable
private fun SettingsCardFrame(title: String, target: FocusRequester, onFocused: () -> Unit,
    passive: Boolean = false, content: @Composable () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val highlighted = focused && LocalInputModeManager.current.inputMode == InputMode.Keyboard
    Box(Modifier.fillMaxWidth().focusRequester(target).onFocusChanged {
        focused = it.hasFocus
        if (it.hasFocus) onFocused()
    }.focusGroup().then(if (passive) Modifier.focusable() else Modifier)
        .semantics { contentDescription = "Einstellung: $title" }) {
        CompositionLocalProvider(LocalSettingsCardFocused provides highlighted) { content() }
        if (highlighted) {
            Box(Modifier.matchParentSize().border(3.dp, Color.White, RoundedCornerShape(2.dp)))
        }
    }
}

@Composable
private fun SettingsSwitch(checked: Boolean, onChecked: (Boolean) -> Unit, label: String,
    emphasized: Boolean = false, keyboardFocus: Boolean = true) {
    var focused by remember { mutableStateOf(false) }
    val keyboard = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    val highlighted = emphasized || (keyboard && (focused || LocalSettingsCardFocused.current))
    Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            Switch(checked, onChecked, modifier = Modifier.onFocusChanged { focused = it.isFocused }
                .focusProperties { canFocus = keyboardFocus }
                .border(2.dp, if (highlighted) Ice else Color.Transparent, RoundedCornerShape(50))
                .padding(4.dp).semantics { contentDescription = label })
        }
    }
}

@Composable
private fun HapticsControl(state: ConsoleState, onEnabled: (Boolean) -> Unit, onTest: () -> Unit,
    onIntensity: (Float) -> Unit) {
    PortalSurface(Modifier.fillMaxWidth()
        .onPreviewKeyEvent { event ->
            when (event.key) {
                Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                    if (event.type == KeyEventType.KeyDown) onEnabled(!state.controllerVibration)
                    true
                }
                Key.DirectionLeft, Key.DirectionRight -> {
                    if (event.type == KeyEventType.KeyDown) {
                        val delta = if (event.key == Key.DirectionRight) 1 else -1
                        onIntensity(((state.controllerVibrationIntensity * 20).roundToInt() + delta).coerceIn(0, 20) / 20f)
                    }
                    true
                }
                else -> if (event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_BUTTON_X) {
                    if (event.type == KeyEventType.KeyDown && state.controllerVibration && state.hapticsAvailable) onTest()
                    true
                } else false
            }
        }.focusable().semantics { contentDescription = "Controller-Vibration: Bestätigen schaltet ein oder aus, links und rechts ändern die Intensität" },
        shadow = false, focusWholeSurface = false, shape = RoundedCornerShape(2.dp)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PortalCardTitle("Controller-Vibration", Modifier.weight(1f))
                SettingsSwitch(state.controllerVibration, onEnabled, "Controller-Vibration",
                    keyboardFocus = false)
            }
            Text(state.hapticsStatus, color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Slider(state.controllerVibrationIntensity, onIntensity, steps = 19,
                    modifier = Modifier.weight(1f).focusProperties { canFocus = false }
                        .semantics { contentDescription = "Vibrationsintensität" })
                Text("${(state.controllerVibrationIntensity * 100).roundToInt()} %", color = Color.White,
                    fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp))
            }
            Text("X schaltet ein/aus · Links/rechts ändert die Intensität · □ testet. Bleibt auch nach einem Controllerwechsel gespeichert.", color = Muted, fontSize = 12.sp)
            ConsoleButton("Vibration testen · □ / X", Icons.Outlined.Vibration, onTest,
                enabled = state.controllerVibration && state.hapticsAvailable && state.controllerVibrationIntensity > 0f,
                modifier = Modifier.focusProperties { canFocus = false }, shape = RoundedCornerShape(2.dp))
            Text("Falls nichts spürbar ist: Controller neu verbinden. Manche Android-Treiber melden Motoren, obwohl sie die Ausgabe ablehnen.", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun AudioControl(title: String, description: String, enabled: Boolean, volume: Float,
    onEnabled: (Boolean) -> Unit, onVolume: (Float) -> Unit, previewSound: Boolean = false,
    extra: @Composable () -> Unit = {}) {
    val sound = LocalMenuSound.current
    // One controller stop per audio card. Touch and accessibility keep the individual controls.
    PortalSurface(Modifier.fillMaxWidth().onPreviewKeyEvent { event ->
        when (event.key) {
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                if (event.type == KeyEventType.KeyDown) onEnabled(!enabled)
                true
            }
            Key.DirectionLeft, Key.DirectionRight -> {
                if (event.type == KeyEventType.KeyDown) {
                    if (enabled) {
                        val delta = if (event.key == Key.DirectionRight) 1 else -1
                        onVolume(((volume * 20).roundToInt() + delta).coerceIn(0, 20) / 20f)
                    }
                }
                true
            }
            else -> if (previewSound && event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_BUTTON_X) {
                if (event.type == KeyEventType.KeyDown) sound(ConsoleCue.CONFIRM)
                true
            } else false
        }
    }.focusable().semantics { contentDescription = "$title: Bestätigen schaltet ein oder aus, links und rechts ändern die Lautstärke" },
        shadow = false, focusWholeSurface = false, shape = RoundedCornerShape(2.dp)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PortalCardTitle(title, Modifier.weight(1f))
                SettingsSwitch(enabled, onEnabled, title,
                    keyboardFocus = false)
            }
            Text(description, color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Slider(volume, onVolume, enabled = enabled, steps = 19,
                    modifier = Modifier.weight(1f).focusProperties { canFocus = false }
                        .padding(horizontal = 8.dp).semantics { contentDescription = "Lautstärke $title" })
                Text(if (enabled) "${(volume * 100).roundToInt()} %" else "Aus", color = Color.White,
                    fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp))
            }
            if (previewSound) ConsoleButton("Klang testen · □ / X", Icons.Outlined.VolumeUp, {},
                enabled = enabled && volume > 0f, modifier = Modifier.focusProperties { canFocus = false },
                shape = RoundedCornerShape(2.dp))
            extra()
        }
    }
}
@Composable
private fun PageTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, fontSize = 14.sp)
    }
}

@Composable
private fun InfoCard(title: String, text: String, angular: Boolean = false,
    action: @Composable () -> Unit = {}) {
    PortalSurface(Modifier.fillMaxWidth(), shadow = false, focusWholeSurface = false,
        shape = if (angular) RoundedCornerShape(2.dp) else Shape) {
    Column(Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PortalCardTitle(title)
        Text(text, color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
        action()
    }
    if (angular) Box(Modifier.align(Alignment.TopStart).width(66.dp).height(3.dp).background(Ice))
    }
}

@Composable
private fun PortalCardTitle(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier = modifier, color = Color.White, fontSize = 22.sp,
        lineHeight = 28.sp, fontWeight = FontWeight.Black)
}

@Composable
private fun ConsoleButton(label: String, icon: ImageVector, onClick: () -> Unit,
    primary: Boolean = false, enabled: Boolean = true, modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = CircleShape) {
    var focused by remember { mutableStateOf(false) }
    val menuSound = LocalMenuSound.current
    Button({ menuSound(ConsoleCue.CONFIRM); onClick() }, modifier.onFocusChanged { focused = it.isFocused }.heightIn(min = 48.dp),
        enabled = enabled, shape = shape,
        border = BorderStroke(if (focused) 3.dp else 1.dp,
            if (focused) Ice else Color.White.copy(alpha = 0.1f)),
        colors = ButtonDefaults.buttonColors(containerColor = if (primary) Color.White else Color.White.copy(alpha = 0.08f),
            contentColor = if (primary) Ink else Color.White), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Icon(icon, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ConsoleSetup(state: ConsoleState, onFiles: () -> Unit, onFolder: () -> Unit,
    onBluetooth: () -> Unit, onDone: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDone,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(16.dp).widthIn(max = 760.dp).fillMaxWidth().fillMaxHeight(0.94f),
            shape = Shape, color = Ink, border = BorderStroke(1.dp, Color.White.copy(alpha = 0.24f))) {
            Column(Modifier.padding(22.dp)) {
                Text("Dein Portal. Dein Abenteuer.", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                Text("Alles für deinen ersten Start an einem Ort.", color = Muted, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item { InfoCard("01 · Deine Spiele", if (!state.ready || state.refreshing || state.importing)
                        "Deine Bibliothek wird vorbereitet …" else "${state.games.count { it.path != null }} Spiel(e) bereit. Wähle weitere Dateien oder einen Spieleordner.") {
                        ConsoleButton("Dateien auswählen", Icons.Outlined.InsertDriveFile, onFiles, primary = true,
                            enabled = state.ready && !state.importing)
                        ConsoleButton("Spieleordner verbinden", Icons.Outlined.FolderOpen, onFolder,
                            enabled = state.ready && !state.importing)
                        Text("Eigene Wii-Spiele als RVZ, ISO oder WBFS; außerdem WIA, GCZ und CISO. ZIP/7z vorher entpacken. Es wird keine zweite Kopie angelegt.", color = Muted, fontSize = 12.sp)
                        if (state.importMessage != null) Text(state.importMessage, color = Color.White)
                        state.games.filter { it.path != null }.forEach {
                            Text("✓ ${it.displayTitle}", color = Ice, fontSize = 13.sp)
                        }
                    } }
                    item { InfoCard("02 · Dein Controller", state.controllers.joinToString("\n").ifEmpty {
                        "Verbinde deinen PS4- oder PS5-Controller über Bluetooth oder USB. Du kannst das auch später erledigen."
                    }) {
                        Text(state.controllerSetup, color = Muted, fontSize = 12.sp)
                        ConsoleButton("Bluetooth öffnen", Icons.Outlined.Bluetooth, onBluetooth)
                    } }
                    item { InfoCard("03 · Dein Fortschritt", "Figuren und Spielstände bleiben auf diesem Gerät erhalten. Vorhandene Daten werden weiterverwendet.") {
                        Text("Das Figurenmenü im Spiel öffnest du mit der PS-Taste. Grafik und Controllerbelegung werden automatisch eingerichtet. Zum Spielen ist kein Konto nötig.", color = Muted, fontSize = 13.sp)
                        Text("Offline spielen ist direkt möglich. Optionale Wii-Internetfunktionen benötigen eine separate Wii-Netzwerkeinrichtung.", color = Muted, fontSize = 12.sp)
                        Text("L1 / R1: Bereiche wechseln · ✕: bestätigen · ○: zurück", color = Ice, fontSize = 12.sp)
                    } }
                }
                Spacer(Modifier.height(14.dp))
                ConsoleButton("Zum Menü", Icons.Outlined.ArrowForward, onDone, primary = true,
                    modifier = Modifier.align(Alignment.End))
            }
        }
    }
}
