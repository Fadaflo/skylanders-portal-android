// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.features.skylanders.console

import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.input.InputManager
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.InputDevice
import android.view.KeyEvent
import android.view.KeyCharacterMap
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.withContext
import org.dolphinemu.dolphinemu.activities.EmulationActivity
import org.dolphinemu.dolphinemu.NativeLibrary
import org.dolphinemu.dolphinemu.features.input.model.ControllerInterface
import org.dolphinemu.dolphinemu.features.settings.model.NativeConfig
import org.dolphinemu.dolphinemu.features.settings.model.BooleanSetting
import org.dolphinemu.dolphinemu.features.settings.ui.MenuTag
import org.dolphinemu.dolphinemu.features.settings.ui.SettingsActivity
import org.dolphinemu.dolphinemu.features.skylanders.catalog.SkyCollection
import org.dolphinemu.dolphinemu.features.skylanders.catalog.SkylanderCatalog
import org.dolphinemu.dolphinemu.services.GameFileCacheManager
import org.dolphinemu.dolphinemu.model.GameFile
import org.dolphinemu.dolphinemu.ui.main.MainActivity
import org.dolphinemu.dolphinemu.ui.main.MainPresenter
import org.dolphinemu.dolphinemu.ui.main.MainView
import org.dolphinemu.dolphinemu.ui.main.ThemeProvider
import org.dolphinemu.dolphinemu.utils.AfterDirectoryInitializationRunner
import org.dolphinemu.dolphinemu.utils.Analytics
import org.dolphinemu.dolphinemu.utils.DirectoryInitialization
import org.dolphinemu.dolphinemu.utils.PermissionsHandler
import org.dolphinemu.dolphinemu.utils.StartupHandler
import kotlin.math.abs

/** Own launcher; Dolphin's initialization, folder permissions and launch checks stay in charge. */
class ConsoleActivity : AppCompatActivity(), MainView, ThemeProvider,
    InputManager.InputDeviceListener {
    override var themeId = 0
    private val presenter = MainPresenter(this, this)
    private var state by mutableStateOf(ConsoleState())
    private val preferences by lazy { getSharedPreferences("skylanders_console", MODE_PRIVATE) }
    private var collectionJob: Job? = null
    private var playTimeJob: Job? = null
    private var directFileJob: Job? = null
    private var directGames: List<GameFile> = emptyList()
    private val requestGames = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) lifecycleScope.launch {
            state = state.copy(importing = true)
            val result = withContext(Dispatchers.IO) { importConsoleGames(this@ConsoleActivity, uris) }
            val stored = preferences.getStringSet("game_documents", emptySet()).orEmpty() + result.accepted
            preferences.edit().putStringSet("game_documents", stored).apply()
            val message = buildString {
                append("${result.accepted.size} Datei(en) eingebunden.")
                if (result.rejected.isNotEmpty()) append(" Nicht importiert: ${result.rejected.take(3).joinToString()}. Bitte eine lesbare Wii-Spieldatei wählen; ZIP/7z vorher entpacken.")
            }
            state = state.copy(importing = false, importMessage = message)
            reloadDirectGames()
        }
    }
    private lateinit var menuAudio: ConsoleAudio
    private lateinit var menuHaptics: ConsoleHaptics
    private var launchJob: Job? = null
    private var lastStickDirection = 0
    private var lastStickTime = 0L
    private var controllerSetupJob: Job? = null
    private var preferredController: String? = null
    private val controllerKeys = Channel<Int>(Channel.BUFFERED)
    private val controllerInput = controllerKeys.receiveAsFlow()
    private val inputManager by lazy { getSystemService(InputManager::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(8, 13, 28))
        )
        super.onCreate(savedInstanceState)
        state = state.copy(
            page = ConsolePage.entries.firstOrNull { it.name == savedInstanceState?.getString("console_page") }
                ?: ConsolePage.GAMES,
            selectedKey = preferences.getString("selected_game", "giants") ?: "giants",
            lastPlayedPath = preferences.getString("last_played", null),
            audio = ConsoleAudioSettings.load(preferences),
            controllerVibration = preferences.getBoolean("menu_controller_vibration", true),
            autoController = preferences.getBoolean("auto_controller", true),
            autoGraphics = preferences.getBoolean("auto_graphics", true),
            graphicsSetup = preferences.getString("graphics_profile", null) ?: "Automatisch · wird beim Spielstart gewählt",
            immersiveInfo = preferences.getBoolean("immersive_info", true),
            setupOpen = !preferences.getBoolean("setup_seen", false)
        )
        menuAudio = ConsoleAudio(this, state.audio,
            savedInstanceState?.getInt("menu_music_position") ?: 0) { error ->
            state = state.copy(audioError = error)
        }
        menuHaptics = ConsoleHaptics(state.controllerVibration)
        // Use our quiet UI cues instead of adding Android's default click on top.
        window.decorView.isSoundEffectsEnabled = false
        setContent {
            ConsoleScreen(state, controllerInput, ::dispatchMenuKey,
                onAudioSettings = ::updateAudio, onMenuSound = ::playMenuFeedback,
                onControllerVibration = { enabled ->
                    state = state.copy(controllerVibration = enabled)
                    preferences.edit().putBoolean("menu_controller_vibration", enabled).apply()
                    menuHaptics.update(enabled)
                    playMenuFeedback(ConsoleCue.CONFIRM)
                },
                onTestVibration = {
                    menuHaptics.test(preferredController ?: preferences.getString("controller_profile_descriptor", null))
                    updateHapticsStatus()
                },
                onInfoAnimation = { enabled ->
                    state = state.copy(immersiveInfo = enabled)
                    preferences.edit().putBoolean("immersive_info", enabled).apply()
                    playMenuFeedback(ConsoleCue.CONFIRM)
                },
                onPage = {
                    if (it != state.page) playMenuFeedback(ConsoleCue.NAVIGATE)
                    state = state.copy(page = it)
                },
                onSelect = { game ->
                    if (game.key != state.selectedKey) playMenuFeedback(ConsoleCue.NAVIGATE)
                    state = state.copy(selectedKey = game.key)
                    preferences.edit().putString("selected_game", game.key).apply()
                }, onPlay = ::play, onAddGames = ::addGames,
                onRefresh = ::refresh, onBluetooth = ::openBluetooth,
                onControllerSettings = { launchSettingsActivity(MenuTag.WIIMOTE_1) },
                onAutoController = { enabled ->
                    state = state.copy(autoController = enabled)
                    preferences.edit().putBoolean("auto_controller", enabled).apply()
                    synchronizeController()
                },
                onAutoGraphics = { enabled ->
                    state = state.copy(autoGraphics = enabled)
                    preferences.edit().putBoolean("auto_graphics", enabled).apply()
                },
                onSettings = { launchSettingsActivity(MenuTag.SETTINGS) },
                onSetup = { state = state.copy(setupOpen = true) },
                onCloseSetup = {
                    preferences.edit().putBoolean("setup_seen", true).apply()
                    state = state.copy(setupOpen = false)
                },
                onImportFiles = {
                    state = state.copy(importOpen = false, importMessage = null)
                    AfterDirectoryInitializationRunner().runWithLifecycle(this) { requestGames.launch(arrayOf("*/*")) }
                },
                onImportFolder = {
                    state = state.copy(importOpen = false, importMessage = null)
                    AfterDirectoryInitializationRunner().runWithLifecycle(this) { presenter.launchFileListActivity() }
                },
                onCloseImport = { state = state.copy(importOpen = false) },
                onDismissImportMessage = { state = state.copy(importMessage = null) },
                onAdvanced = {
                    playMenuFeedback(ConsoleCue.CONFIRM)
                    startActivity(Intent(this, MainActivity::class.java))
                })
        }
        presenter.onCreate()
        AfterDirectoryInitializationRunner().runWithLifecycle(this) {
            // The console defaults to no telemetry. Preserve an existing user's choice.
            if (!BooleanSetting.MAIN_ANALYTICS_PERMISSION_ASKED.boolean) Analytics.firstAnalyticsAdd(false)
            state = state.copy(ready = true)
            synchronizeController()
            GameFileCacheManager.startLoad()
            refreshCollection()
            reloadDirectGames()
        }
        if (savedInstanceState == null) StartupHandler.HandleInit(this)
        ControllerInterface.devicesChanged.observe(this) { scheduleControllerSetup() }
    }

    override fun onStart() {
        super.onStart()
        inputManager.registerInputDeviceListener(this, null)
        updateControllers()
    }

    override fun onResume() {
        super.onResume()
        state = state.copy(launchingGameKey = null)
        menuAudio.resume()
        menuHaptics.resume()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (DirectoryInitialization.shouldStart(this)) DirectoryInitialization.start(this)
        presenter.onResume()
        if (DirectoryInitialization.areDolphinDirectoriesReady()) {
            synchronizeController()
            refreshCollection()
            refreshPlayTimes()
            reloadDirectGames()
        }
    }

    override fun onPause() {
        launchJob?.cancel()
        state = state.copy(launchingGameKey = null)
        menuAudio.pause()
        menuHaptics.pause()
        super.onPause()
    }

    override fun onDestroy() {
        menuAudio.release()
        menuHaptics.pause()
        super.onDestroy()
    }

    private fun updateAudio(settings: ConsoleAudioSettings) {
        state = state.copy(audio = settings)
        settings.save(preferences)
        menuAudio.update(settings)
    }

    private fun playMenuFeedback(cue: ConsoleCue) {
        menuAudio.play(cue)
        // Navigation rumbles at the physical input, never at the delayed pager settlement.
        if (cue != ConsoleCue.NAVIGATE) menuHaptics.play(cue)
    }

    private fun navigationHaptics(keyCode: Int) {
        if (state.launchingGameKey == null &&
            (keyCode in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_RIGHT ||
                keyCode == KeyEvent.KEYCODE_BUTTON_L1 || keyCode == KeyEvent.KEYCODE_BUTTON_R1)) {
            menuHaptics.play(ConsoleCue.NAVIGATE)
        }
    }

    private fun updateHapticsStatus() {
        val status = menuHaptics.status()
        state = state.copy(hapticsAvailable = status.available, hapticsStatus = status.description)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) menuHaptics.touchInput()
        return super.dispatchTouchEvent(event)
    }

    override fun onStop() {
        inputManager.unregisterInputDeviceListener(this)
        super.onStop()
        if (isChangingConfigurations) MainPresenter.skipRescanningLibrary()
        else if (DirectoryInitialization.areDolphinDirectoriesReady()) {
            NativeConfig.save(NativeConfig.LAYER_BASE)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("console_page", state.page.name)
        outState.putInt("menu_music_position", menuAudio.positionMs)
        super.onSaveInstanceState(outState)
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code == PermissionsHandler.REQUEST_CODE_WRITE_PERMISSION) {
            if (results.firstOrNull() == PackageManager.PERMISSION_DENIED) {
                PermissionsHandler.setWritePermissionDenied()
            }
            DirectoryInitialization.start(this)
        }
    }

    private fun play(game: ConsoleGame) {
        val path = game.path ?: return addGames()
        if (state.launchingGameKey != null) return
        synchronizeController()
        playMenuFeedback(ConsoleCue.PORTAL)
        if (catalogGameFor("Skylanders ${game.title}") != null) {
            if (state.autoGraphics && NativeLibrary.IsUninitialized()) {
                val profile = applyConsoleGraphics(this, game)
                state = state.copy(graphicsSetup = profile)
                preferences.edit().putString("graphics_profile", profile).apply()
            }
            BooleanSetting.MAIN_EMULATE_SKYLANDER_PORTAL.setBoolean(NativeConfig.LAYER_BASE, true)
            NativeConfig.save(NativeConfig.LAYER_BASE)
        }
        preferences.edit().putString("last_played", path).apply()
        state = state.copy(lastPlayedPath = path)
        state = state.copy(launchingGameKey = game.key)
        launchJob = lifecycleScope.launch {
            delay(if (portalMotionEnabled(this@ConsoleActivity)) PortalPalette.LaunchDurationMs.toLong() else 40L)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                EmulationActivity.launch(this@ConsoleActivity, path, false)
            }
            // Launch checks can show a dialog rather than opening the emulator.
            delay(100)
            state = state.copy(launchingGameKey = null)
        }
    }

    private fun addGames() {
        playMenuFeedback(ConsoleCue.CONFIRM)
        state = state.copy(importOpen = true)
    }

    private fun refresh() {
        playMenuFeedback(ConsoleCue.CONFIRM)
        AfterDirectoryInitializationRunner().runWithLifecycle(this) {
            GameFileCacheManager.startRescan()
            refreshCollection()
            reloadDirectGames()
        }
    }

    override fun showGames() {
        val installed = (GameFileCacheManager.getGameFiles().value.orEmpty().toList() + directGames)
            .distinctBy { consoleDocumentIdentity(it.getPath()) }.map { game ->
            val path = game.getPath()
            val catalog = catalogGameForDisc(game.getGameId(), game.getTitle())
            (catalog ?: ConsoleGame(path, game.getTitle(), "Dein lokales Spiel.", "16-0.png", 0xFF9BAEFF))
                .copy(key = path, path = path)
        }
        // Retain each file as a separate entry (different regions/revisions may have separate saves).
        val missing = consoleCatalog.filter { item -> installed.none { it.title == item.title } }
        val games = (installed + missing).sortedWith(compareBy<ConsoleGame> { game ->
            consoleCatalog.indexOfFirst { it.title == game.title }.let { if (it < 0) Int.MAX_VALUE else it }
        }.thenBy { it.displayTitle }.thenBy { it.path ?: "" })
        val selected = games.firstOrNull { it.key == state.selectedKey }
            ?: games.firstOrNull { it.path == state.lastPlayedPath && it.path != null }
            ?: games.firstOrNull { it.title == "Giants" } ?: games.first()
        state = state.copy(games = games, selectedKey = selected.key)
        refreshPlayTimes()
    }

    private fun reloadDirectGames() {
        directFileJob?.cancel()
        val paths = preferences.getStringSet("game_documents", emptySet()).orEmpty().toList()
        directFileJob = lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                paths.mapNotNull { path -> runCatching { GameFile.parse(path) }.getOrNull() }
            }
            directGames = loaded
            if (loaded.size < paths.size) state = state.copy(importMessage =
                "${paths.size - loaded.size} Spieldatei(en) nicht mehr erreichbar. Bitte erneut auswählen oder den Spieleordner verbinden.")
            showGames()
        }
    }

    private fun refreshPlayTimes() {
        playTimeJob?.cancel()
        val files = (GameFileCacheManager.getGameFiles().value.orEmpty().toList() + directGames).distinctBy { it.getPath() }
        playTimeJob = lifecycleScope.launch {
            // Dolphin reads TimePlayed.ini by game ID; do not infer progress from save files.
            val times = files.associate { game ->
                game.getPath() to try { game.getTimePlayedMs() }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { null }
            }
            state = state.copy(games = state.games.map { it.copy(playTimeMs = times[it.path]) })
        }
    }

    private fun refreshCollection() {
        collectionJob?.cancel()
        collectionJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val catalog = SkylanderCatalog.getFigures(this@ConsoleActivity)
                        .associateBy { it.id to it.variant }
                    val standardIds = catalog.values.filter { (it.variant and 0xFF) < 2 }.map { it.id }.toSet()
                    val userDirectory = DirectoryInitialization.getUserDirectory()
                    val owned = SkyCollection.scan(userDirectory)
                    val ultimate = SkyCollection.maxedFolder(userDirectory).listFiles().orEmpty()
                        .filter { it.isFile && it.extension.equals("sky", true) && it.length() >= 1024 }
                    val normal = owned.map {
                        collectionFigure(it.file.absolutePath, catalog[it.id to it.variant], false, standardIds)
                    }
                    // Read only: neither rename nor rewrite figure saves when browsing the collection.
                    normal + ultimate.map { file ->
                        val head = file.inputStream().use { stream -> ByteArray(32).also { stream.readFully(it) } }
                        fun short(at: Int) = (head[at].toInt() and 255) or
                            ((head[at + 1].toInt() and 255) shl 8)
                        collectionFigure(file.absolutePath, catalog[short(16) to short(28)], true, standardIds)
                    }
                }
            }
            state = result.fold(
                onSuccess = { state.copy(figures = it.sortedBy { f -> f.name }, figureError = null) },
                onFailure = { state.copy(figureError = "Die Sammlung konnte nicht gelesen werden. Bitte erneut versuchen.") }
            )
        }
    }

    private fun java.io.InputStream.readFully(buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val count = read(buffer, offset, buffer.size - offset)
            if (count < 0) throw java.io.EOFException()
            offset += count
        }
    }

    private fun openBluetooth() {
        playMenuFeedback(ConsoleCue.CONFIRM)
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        if (intent.resolveActivity(packageManager) != null) startActivity(intent)
    }

    private fun updateControllers() {
        updateHapticsStatus()
        state = state.copy(controllers = InputDevice.getDeviceIds().toList().mapNotNull { id ->
            InputDevice.getDevice(id)?.takeIf { !it.isVirtual && it.isController() }?.name
        })
        scheduleControllerSetup()
    }

    private fun scheduleControllerSetup() {
        controllerSetupJob?.cancel()
        controllerSetupJob = lifecycleScope.launch {
            delay(250) // Native device enumeration runs on its own input thread.
            synchronizeController()
        }
    }

    private fun synchronizeController() {
        if (!state.ready || !DirectoryInitialization.areDolphinDirectoriesReady()) return
        if (!state.autoController) {
            state = state.copy(controllerSetup = "Manuelle Belegung aktiv. Deine Einstellungen bleiben erhalten.")
            return
        }
        if (!NativeLibrary.IsUninitialized()) return
        val devices = InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it) }
            .filter { !it.isVirtual && it.isController() }
        val result = runCatching {
            applyConsoleControllerProfile(preferences, devices,
                preferredController ?: preferences.getString("controller_profile_descriptor", null))
        }.getOrElse { error ->
            android.util.Log.w("PortalController", "Automatic setup failed", error)
            "Automatische Einrichtung nicht möglich. Bitte die Belegung manuell prüfen."
        }
        state = state.copy(controllerSetup = result)
    }

    private fun rememberController(device: InputDevice?) {
        if (device != null && !device.isVirtual && device.isController()) menuHaptics.controllerInput(device)
        if (device != null && !device.isVirtual && device.isController() && preferredController != device.descriptor) {
            preferredController = device.descriptor
            scheduleControllerSetup()
        }
    }

    private fun InputDevice.isController() =
        supportsSource(InputDevice.SOURCE_GAMEPAD) || supportsSource(InputDevice.SOURCE_JOYSTICK)

    override fun onInputDeviceAdded(id: Int) = updateControllers()
    override fun onInputDeviceRemoved(id: Int) = updateControllers()
    override fun onInputDeviceChanged(id: Int) = updateControllers()

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.device?.isController() == true ||
            event.keyCode == KeyEvent.KEYCODE_BUTTON_L1 || event.keyCode == KeyEvent.KEYCODE_BUTTON_R1 ||
            event.isFromSource(InputDevice.SOURCE_GAMEPAD) ||
            event.isFromSource(InputDevice.SOURCE_JOYSTICK)) {
            if (event.action == KeyEvent.ACTION_DOWN) rememberController(event.device)
            if (state.page == ConsolePage.CONTROLLERS && event.action == KeyEvent.ACTION_DOWN) {
                state = state.copy(lastInput = "${event.device?.name ?: "Controller"}: ${KeyEvent.keyCodeToString(event.keyCode).removePrefix("KEYCODE_")}")
            }
            val menuKey = when (event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER -> KeyEvent.KEYCODE_ENTER
                KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BACK
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1 -> event.keyCode
                else -> null
            }
            if (menuKey != null) {
                if (event.action == KeyEvent.ACTION_DOWN &&
                    (event.repeatCount == 0 || menuKey in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_RIGHT)) {
                    // Finish delivery of the physical input before sending a motor command.
                    // This runs on the next main-loop turn, without waiting for the pager.
                    window.decorView.post { navigationHaptics(menuKey) }
                    controllerKeys.trySend(menuKey)
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    // Intercept before Compose: its generic-motion handler may consume joystick events.
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(InputDevice.SOURCE_JOYSTICK) && event.action == MotionEvent.ACTION_MOVE) {
            val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            val x = if (abs(hatX) > 0.5f) hatX else event.getAxisValue(MotionEvent.AXIS_X)
            val y = if (abs(hatY) > 0.5f) hatY else event.getAxisValue(MotionEvent.AXIS_Y)
            val direction = when {
                abs(x) < 0.55f && abs(y) < 0.55f -> 0
                abs(x) > abs(y) -> if (x > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
                else -> if (y > 0) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP
            }
            val now = SystemClock.uptimeMillis()
            if (direction != 0 && (direction != lastStickDirection || now - lastStickTime > 240)) {
                rememberController(event.device)
                window.decorView.post { navigationHaptics(direction) }
                if (state.page == ConsolePage.CONTROLLERS) {
                    val label = when (direction) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> "← Links"
                        KeyEvent.KEYCODE_DPAD_RIGHT -> "Rechts →"
                        KeyEvent.KEYCODE_DPAD_UP -> "↑ Hoch"
                        else -> "↓ Runter"
                    }
                    state = state.copy(lastInput = "Linker Stick / Steuerkreuz · $label")
                }
                controllerKeys.trySend(direction)
                lastStickTime = now
            }
            lastStickDirection = direction
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    private fun dispatchMenuKey(keyCode: Int) {
        if ((state.setupOpen || state.importOpen) && keyCode in listOf(KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1)) return
        if (state.launchingGameKey != null) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                launchJob?.cancel()
                state = state.copy(launchingGameKey = null)
                playMenuFeedback(ConsoleCue.BACK)
            }
            return
        }
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                playMenuFeedback(ConsoleCue.BACK)
                onBackPressedDispatcher.onBackPressed()
            }
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1 -> {
                playMenuFeedback(ConsoleCue.NAVIGATE)
                val offset = if (keyCode == KeyEvent.KEYCODE_BUTTON_R1) 1 else -1
                val pages = ConsolePage.entries
                state = state.copy(page = pages[(state.page.ordinal + offset + pages.size) % pages.size])
            }
            else -> {
                // Game swipes sound once when the selected page actually settles.
                if (state.page != ConsolePage.GAMES && keyCode in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_RIGHT) {
                    playMenuFeedback(ConsoleCue.NAVIGATE)
                }
                val now = SystemClock.uptimeMillis()
                // Compose buttons handle Enter consistently after entering keyboard input mode.
                for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
                    super.dispatchKeyEvent(KeyEvent(now, now, action, keyCode, 0, 0,
                        KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD))
                }
            }
        }
    }

    override fun setVersionString(version: String) = Unit
    override fun launchSettingsActivity(menuTag: MenuTag?) {
        playMenuFeedback(ConsoleCue.CONFIRM)
        AfterDirectoryInitializationRunner().runWithLifecycle(this) { SettingsActivity.launch(this, menuTag) }
    }
    override fun setRefreshing(refreshing: Boolean) { state = state.copy(refreshing = refreshing) }
    override fun reloadGrid() = showGames()
    override fun showGridOptions() { startActivity(Intent(this, MainActivity::class.java)) }
    override fun setTheme(resId: Int) { super.setTheme(resId); themeId = resId }
}
