// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.features.skylanders.console

import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.input.InputManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.InputDevice
import android.view.KeyEvent
import android.view.KeyCharacterMap
import android.view.MotionEvent
import java.io.IOException
import java.io.File
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import org.dolphinemu.dolphinemu.features.skylanders.SkylanderConfig
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
    private lateinit var accountAuth: ConsoleAuth
    private lateinit var cloud: ConsoleCloud
    private val updater by lazy { ConsoleUpdate(this) }
    private var updateJob: Job? = null
    private var pendingUpdateInstall: File? = null
    private val cloudLock = Mutex()
    private var cloudJob: Job? = null
    private var prepareLaunchJob: Job? = null
    private var accountRefreshJob: Job? = null
    private var launchJob: Job? = null
    private val heldMenuDirections = linkedMapOf<String, Int>()
    private var repeatingDirection = 0
    private var directionRepeatJob: Job? = null
    private var controllerSetupJob: Job? = null
    private var menuControllerId: Int? = null
    private var secondControllerId: Int? = null
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
        accountAuth = ConsoleAuth(this)
        cloud = ConsoleCloud(this, accountAuth)
        state = state.copy(
            account = accountAuth.currentAccount(),
            page = ConsolePage.entries.firstOrNull { it.name == savedInstanceState?.getString("console_page") }
                ?: ConsolePage.GAMES,
            selectedKey = preferences.getString("selected_game", "giants") ?: "giants",
            lastPlayedPath = preferences.getString("last_played", null),
            audio = ConsoleAudioSettings.load(preferences),
            controllerVibration = preferences.getBoolean("menu_controller_vibration", true),
            controllerVibrationIntensity = preferences.getFloat("menu_controller_vibration_intensity", 0.4f).coerceIn(0f, 1f),
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
        menuHaptics = ConsoleHaptics(state.controllerVibration, state.controllerVibrationIntensity)
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
                onVibrationIntensity = { value ->
                    val level = value.coerceIn(0f, 1f)
                    state = state.copy(controllerVibrationIntensity = level)
                    preferences.edit().putFloat("menu_controller_vibration_intensity", level).apply()
                    menuHaptics.updateIntensity(level)
                },
                onTestVibration = {
                    menuHaptics.test(preferences.getString("controller_player_1_descriptor", null)
                        ?: preferences.getString("controller_profile_descriptor", null))
                    updateHapticsStatus()
                },
                onInfoAnimation = { enabled ->
                    state = state.copy(immersiveInfo = enabled)
                    preferences.edit().putBoolean("immersive_info", enabled).apply()
                    playMenuFeedback(ConsoleCue.CONFIRM)
                },
                onSelectPage = { page ->
                    if (state.page != page || state.accountOpen) {
                        playMenuFeedback(ConsoleCue.NAVIGATE)
                        state = state.copy(page = page, accountOpen = false)
                    }
                },
                onOpenAccount = { state = state.copy(accountOpen = true) },
                onCloseAccount = { state = state.copy(accountOpen = false) },
                onAccountSignIn = { email, password -> accountAction { accountAuth.signIn(email, password) } },
                onAccountSignUp = { email, password, name ->
                    accountAction { accountAuth.signUp(email, password, name) }
                },
                onAccountSignOut = { accountAction {
                    // Finish account work before removing its session. The signed-out
                    // screen does not need to activate another Dolphin directory.
                    prepareLaunchJob?.cancelAndJoin()
                    cloudJob?.cancelAndJoin()
                    accountRefreshJob?.cancelAndJoin()
                    accountAuth.signOut()
                } },
                onAccountUpdateName = { name -> accountAction { accountAuth.updateName(name) } },
                onAccountUpdateAvatar = { avatar -> accountAction { accountAuth.updateAvatar(avatar) } },
                onAccountUpdatePassword = { current, next ->
                    accountAction { accountAuth.updatePassword(current, next) }
                },
                onAccountRequestReset = { email ->
                    accountAction { accountAuth.requestPasswordReset(email) }
                },
                onAccountCompleteReset = { email, code, password ->
                    accountAction { accountAuth.completePasswordReset(email, code, password) }
                },
                onCloudRefresh = ::startCloudSync,
                onDownloadUpdate = ::downloadUpdate,
                onCloudChooseLocal = { resolveCloudConflict(true) },
                onCloudChooseRemote = { resolveCloudConflict(false) },
                onCloudDefer = {
                    state = state.copy(cloudConflictScope = null, cloudDeferred = true,
                        cloudStatus = "Zwei Stände vorhanden · lokal weiterspielen. Später auswählen.")
                },
                onSelect = { game ->
                    if (game.key != state.selectedKey) playMenuFeedback(ConsoleCue.NAVIGATE)
                    state = state.copy(selectedKey = game.key)
                    preferences.edit().putString("selected_game", game.key).apply()
                }, onPlay = ::play, onAddGames = ::addGames,
                onRefresh = ::refresh, onBluetooth = ::openBluetooth,
                onControllerSettings = { launchSettingsActivity(MenuTag.WIIMOTE_1) },
                onControllerSettings2 = { launchSettingsActivity(MenuTag.WIIMOTE_2) },
                onSwapControllers = {
                    secondControllerId?.let { id ->
                        val second = InputDevice.getDevice(id)
                        if (second != null) {
                            preferences.edit().putInt("controller_player_1_device_id", id)
                                .putString("controller_player_1_descriptor", second.descriptor).apply()
                            synchronizeController()
                        }
                    }
                },
                onStartControllerTest = {
                    stopMenuRepeat()
                    state = state.copy(controllerTestActive = true,
                        testDeviceName = state.controllers.firstOrNull() ?: "Controller",
                        testLastInput = "Drücke eine Taste oder bewege einen Stick.",
                        testPressedKeys = emptySet(), testAxes = emptyMap())
                },
                onStopControllerTest = {
                    stopMenuRepeat()
                    state = state.copy(controllerTestActive = false,
                        testPressedKeys = emptySet(), testAxes = emptyMap())
                },
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
            startCloudSync()
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
        pendingUpdateInstall?.let { file ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()) {
                pendingUpdateInstall = null
                installUpdate(file)
            }
        }
        checkForUpdate()
        state = state.copy(cloudDeferred = false)
        state = state.copy(launchingGameKey = null)
        menuAudio.resume()
        menuHaptics.resume()
        accountRefreshJob?.cancel()
        accountRefreshJob = lifecycleScope.launch {
            while (true) {
                if (state.account.email != null && !state.account.busy) {
                    runCatching { accountAuth.refreshIfNeeded() }.onSuccess(::applyAccountOutcome)
                }
                delay(60_000)
            }
        }
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
            startCloudSync()
        }
    }

    override fun onPause() {
        cloudJob?.cancel()
        cloudJob = null
        accountRefreshJob?.cancel()
        accountRefreshJob = null
        stopMenuRepeat()
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

    private fun accountAction(action: suspend () -> AuthOutcome) {
        if (state.account.busy) return
        state = state.copy(account = state.account.copy(busy = true, error = null, message = null,
            confirmationEmail = null))
        lifecycleScope.launch {
            try {
                val previousUserId = accountAuth.currentUserId()
                applyAccountOutcome(action(), explicit = true)
                if (previousUserId != accountAuth.currentUserId()) requireProfileRestart()
                else if (accountAuth.currentUserId() != null) startCloudSync()
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                val message = when (error) {
                    is AuthHttpException -> when (error.code) {
                        "invalid_credentials" -> "E-Mail oder Passwort stimmt nicht."
                        "email_not_confirmed" -> "Bestätige zuerst deine E-Mail-Adresse."
                        "email_address_not_authorized" ->
                            "Diese Adresse kann noch keine Bestätigungs-E-Mail erhalten. Der E-Mail-Versand muss eingerichtet werden."
                        "weak_password" -> "Wähle ein stärkeres Passwort."
                        "same_password" -> "Wähle ein neues Passwort."
                        "reauthentication_needed", "invalid_password" ->
                            "Das aktuelle Passwort stimmt nicht oder muss erneut bestätigt werden."
                        "over_email_send_rate_limit", "over_request_rate_limit" ->
                            "Bitte warte einen Moment und versuche es erneut."
                        else -> if (error.status >= 500) "Der Anmeldedienst ist gerade nicht erreichbar."
                            else "Anmeldung fehlgeschlagen. Prüfe deine Angaben."
                    }
                    is IOException -> "Keine Verbindung. Deine lokalen Daten bleiben verfügbar."
                    else -> "Die Anmeldung konnte nicht abgeschlossen werden."
                }
                state = state.copy(account = state.account.copy(busy = false, error = message))
            }
        }
    }

    private fun applyAccountOutcome(outcome: AuthOutcome, explicit: Boolean = false) {
        val updated = when (outcome) {
            is AuthOutcome.SignedIn -> ConsoleAccountState(email = outcome.email, name = outcome.name,
                avatar = outcome.avatar, offline = outcome.offline,
                message = if (explicit) outcome.notice ?: "Angemeldet." else null)
            is AuthOutcome.ConfirmationRequired -> ConsoleAccountState(
                confirmationEmail = outcome.email,
                message = "Prüfe ${outcome.email} und bestätige deine Registrierung. Danach kannst du dich anmelden.")
            is AuthOutcome.Notice -> state.account.copy(busy = false, error = null,
                message = outcome.message)
            AuthOutcome.SignedOut -> ConsoleAccountState(
                message = if (explicit) "Abgemeldet. Lokale Daten bleiben auf diesem Gerät."
                    else "Deine Sitzung ist abgelaufen. Melde dich erneut an.")
        }
        state = state.copy(account = updated)
        if (outcome == AuthOutcome.SignedOut) {
            prepareLaunchJob?.cancel()
            cloudJob?.cancel()
            collectionJob?.cancel()
            playTimeJob?.cancel()
            launchJob?.cancel()
            stopMenuRepeat()
            state = state.copy(accountOpen = false, page = ConsolePage.GAMES,
                profileRestartRequired = false, figures = emptyList(), figureError = null,
                games = state.games.map { it.copy(playTimeMs = null) },
                cloudBusy = false, cloudConflictScope = null, cloudSaveInfo = emptyList(),
                cloudDeferred = false, cloudStatus = "Melde dich für den Cloud-Abgleich an.",
                setupOpen = false, importOpen = false, importMessage = null,
                controllerTestActive = false, launchingGameKey = null, refreshing = false)
        }
    }

    private fun requireProfileRestart() {
        // Keep the inactive native profile until the next login. Nothing from it
        // is shown or playable behind the signed-out screen.
        if (accountAuth.currentUserId() == null) {
            state = state.copy(profileRestartRequired = false)
            return
        }
        if (!DirectoryInitialization.areDolphinDirectoriesReady()) return
        val active = java.io.File(DirectoryInitialization.getUserDirectory()).canonicalPath
        val expected = ConsoleProfileStorage.expectedDirectory(this,
            getExternalFilesDir(null) ?: return).canonicalPath
        val wasSwitching = state.profileRestartRequired
        state = state.copy(profileRestartRequired = active != expected, accountOpen = false)
        if (active != expected && !wasSwitching) {
            startActivity(Intent(this, ConsoleProfileSwitchActivity::class.java))
        }
    }

    private fun startCloudSync() {
        if (!DirectoryInitialization.areDolphinDirectoriesReady() ||
            accountAuth.currentUserId() == null || state.profileRestartRequired ||
            cloudJob?.isActive == true) return
        cloudJob = lifecycleScope.launch { performCloudSync() }
    }

    private fun checkForUpdate() {
        if (updateJob?.isActive == true) return
        updateJob = lifecycleScope.launch {
            runCatching { updater.check() }.onSuccess { available ->
                state = state.copy(updateAvailable = available)
            }
        }
    }

    private fun downloadUpdate() {
        val update = state.updateAvailable ?: return
        if (state.updateBusy) return
        state = state.copy(updateBusy = true, updateMessage = "Update wird geladen und geprüft …")
        lifecycleScope.launch {
            try {
                val file = updater.download(update)
                state = state.copy(updateBusy = false, updateMessage = "Update bereit · Android-Installation öffnen …")
                installUpdate(file)
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                android.util.Log.w("PortalUpdate", "Update failed", error)
                state = state.copy(updateBusy = false,
                    updateMessage = "Download fehlgeschlagen. Bitte erneut versuchen.")
            }
        }
    }

    private fun installUpdate(file: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
                pendingUpdateInstall = file
                state = state.copy(updateMessage = "Erlaube einmalig die Installation aus dieser App.")
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName")))
                return
            }
            val uri = FileProvider.getUriForFile(this, "$packageName.portalupdates", file)
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,
                "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (error: Exception) {
            android.util.Log.w("PortalUpdate", "Installer unavailable", error)
            state = state.copy(updateMessage = "Installation konnte nicht geöffnet werden. Bitte erneut versuchen.")
        }
    }

    private suspend fun performCloudSync(): CloudResult = cloudLock.withLock {
        if (accountAuth.currentUserId() == null) return@withLock CloudResult("Bitte anmelden.")
        if (state.profileRestartRequired) return@withLock CloudResult("Profilwechsel nötig.")
        val connectivity = getSystemService(ConnectivityManager::class.java)
        val network = connectivity.activeNetwork
        if (network == null || connectivity.getNetworkCapabilities(network)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) != true) {
            val offline = CloudResult("Offline · Fortschritt bleibt auf diesem Gerät.")
            state = state.copy(cloudBusy = false, cloudStatus = offline.message)
            state = state.copy(cloudSaveInfo = cloud.saveInfo(false))
            return@withLock offline
        }
        state = state.copy(cloudBusy = true, cloudStatus = "Spielstände werden abgeglichen …")
        val result = try {
            when (val account = accountAuth.refreshIfNeeded()) {
                is AuthOutcome.SignedIn -> {
                    applyAccountOutcome(account)
                    if (account.offline) CloudResult("Offline · Fortschritt bleibt auf diesem Gerät.")
                    else cloud.sync()
                }
                else -> {
                    applyAccountOutcome(account)
                    CloudResult("Bitte erneut anmelden.")
                }
            }
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) {
                state = state.copy(cloudBusy = false)
                throw error
            }
            android.util.Log.w("PortalCloud", "Cloud sync deferred", error)
            CloudResult("Cloud nicht erreichbar · lokaler Fortschritt bleibt erhalten.")
        }
        state = state.copy(cloudBusy = false, cloudStatus = result.message,
            cloudConflictScope = result.conflictScope, cloudDeferred = false,
            cloudSaveInfo = cloud.saveInfo(true))
        if (result.restored) refreshCollection()
        result
    }

    private fun resolveCloudConflict(useThisDevice: Boolean) {
        val scope = state.cloudConflictScope ?: return
        if (state.cloudBusy) return
        lifecycleScope.launch {
            state = state.copy(cloudBusy = true)
            val result = try { cloudLock.withLock { cloud.resolve(scope, useThisDevice) } }
            catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                android.util.Log.w("PortalCloud", "Conflict resolution failed", error)
                CloudResult("Abgleich fehlgeschlagen. Beide Versionen bleiben erhalten.", scope)
            }
            state = state.copy(cloudBusy = false, cloudStatus = result.message,
                cloudConflictScope = result.conflictScope,
                cloudSaveInfo = cloud.saveInfo(true))
            if (result.restored) refreshCollection()
            if (result.conflictScope == null) startCloudSync()
        }
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
        if (accountAuth.currentUserId() == null) { state = state.copy(accountOpen = true); return }
        if (state.profileRestartRequired) return
        if (game.path == null) return addGames()
        if (state.cloudDeferred) return launchGame(game)
        if (accountAuth.currentUserId() != null && DirectoryInitialization.areDolphinDirectoriesReady()) {
            if (prepareLaunchJob?.isActive == true) return
            prepareLaunchJob = lifecycleScope.launch {
                val running = cloudJob
                if (running?.isActive == true) running.join()
                else performCloudSync()
                if (state.cloudConflictScope == null && !state.profileRestartRequired) launchGame(game)
            }
        } else launchGame(game)
    }

    private fun launchGame(game: ConsoleGame) {
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
        if (accountAuth.currentUserId() == null) return
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
        if (accountAuth.currentUserId() == null) return
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
                        collectionFigure(it.file.absolutePath, catalog[it.id to it.variant], false,
                            standardIds, SkylanderConfig.readUpgradePath(it.file.absolutePath))
                    }
                    // Read only: neither rename nor rewrite figure saves when browsing the collection.
                    normal + ultimate.map { file ->
                        val head = file.inputStream().use { stream -> ByteArray(32).also { stream.readFully(it) } }
                        fun short(at: Int) = (head[at].toInt() and 255) or
                            ((head[at + 1].toInt() and 255) shl 8)
                        collectionFigure(file.absolutePath, catalog[short(16) to short(28)], true,
                            standardIds, SkylanderConfig.readUpgradePath(file.absolutePath))
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
            InputDevice.getDevice(id)?.takeIf { !it.isVirtual && it.isController() &&
                it.controllerNumber > 0 }?.let { it.controllerNumber to
                    controllerFamily(it.vendorId, it.productId, it.name) }
        }.distinctBy { it.first }.map { it.second })
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
        if (accountAuth.currentUserId() == null) return
        if (!state.ready || !DirectoryInitialization.areDolphinDirectoriesReady()) return
        if (!NativeLibrary.IsUninitialized() || state.profileRestartRequired) return
        BooleanSetting.MAIN_SHOW_INPUT_OVERLAY.setBoolean(NativeConfig.LAYER_BASE, false)
        NativeConfig.save(NativeConfig.LAYER_BASE)
        if (!state.autoController) {
            state = state.copy(controllerSetup = "Manuelle Belegung aktiv. Deine Einstellungen bleiben erhalten.")
            return
        }
        val devices = InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it) }
            .filter { !it.isVirtual && it.isController() }
        val result = runCatching {
            assignConsoleControllers(preferences, devices)
        }.getOrElse { error ->
            android.util.Log.w("PortalController", "Automatic setup failed", error)
            ConsoleControllerSetup("Automatische Einrichtung nicht möglich. Bitte die Belegung manuell prüfen.")
        }
        state = state.copy(controllerSetup = result.message,
            player1Controller = result.player1, player2Controller = result.player2)
        menuControllerId = result.player1DeviceId
        secondControllerId = result.player2DeviceId
    }

    private fun rememberController(device: InputDevice?) {
        if (device != null && !device.isVirtual && device.isController()) menuHaptics.controllerInput(device)
    }

    private fun InputDevice.isController() =
        supportsSource(InputDevice.SOURCE_GAMEPAD) || supportsSource(InputDevice.SOURCE_JOYSTICK)

    override fun onInputDeviceAdded(id: Int) = updateControllers()
    override fun onInputDeviceRemoved(id: Int) {
        stopMenuRepeat()
        updateControllers()
    }
    override fun onInputDeviceChanged(id: Int) = updateControllers()

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (state.controllerTestActive) {
            if (event.action == KeyEvent.ACTION_UP && event.keyCode !in state.testPressedKeys) return true
            val pressed = state.testPressedKeys.toMutableSet()
            if (event.action == KeyEvent.ACTION_DOWN) pressed.add(event.keyCode)
            if (event.action == KeyEvent.ACTION_UP) pressed.remove(event.keyCode)
            state = state.copy(testDeviceName = event.device?.name ?: state.testDeviceName,
                testLastInput = "${KeyEvent.keyCodeToString(event.keyCode).removePrefix("KEYCODE_").replace('_', ' ')} · " +
                    if (event.action == KeyEvent.ACTION_UP) "losgelassen" else "gedrückt",
                testPressedKeys = pressed)
            return true
        }
        if (event.device?.isController() == true && menuControllerId != null && event.deviceId != menuControllerId)
            return true
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
                if (menuKey in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (event.action == KeyEvent.ACTION_UP || event.isCanceled) {
                        holdMenuDirection("${event.deviceId}:key:$menuKey", 0)
                    } else if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                        holdMenuDirection("${event.deviceId}:key:$menuKey", menuKey)
                    }
                } else if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    emitMenuKey(menuKey)
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    // Intercept before Compose: its generic-motion handler may consume joystick events.
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (state.controllerTestActive && event.isFromSource(InputDevice.SOURCE_JOYSTICK)) {
            if (event.action == MotionEvent.ACTION_MOVE) {
                val axes = event.device?.motionRanges?.filter { it.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK }
                    ?.map { it.axis }?.distinct()?.associateWith { event.getAxisValue(it) } ?: emptyMap()
                val changed = axes.entries.maxByOrNull { abs(it.value - (state.testAxes[it.key] ?: 0f)) }
                state = state.copy(testDeviceName = event.device?.name ?: state.testDeviceName,
                    testAxes = axes,
                    testLastInput = changed?.takeIf { abs(it.value - (state.testAxes[it.key] ?: 0f)) > 0.03f }
                        ?.let { "${MotionEvent.axisToString(it.key)} · ${"%.2f".format(java.util.Locale.ROOT, it.value)}" }
                        ?: state.testLastInput)
            }
            return true
        }
        if (event.isFromSource(InputDevice.SOURCE_JOYSTICK) && menuControllerId != null &&
            event.deviceId != menuControllerId) return true
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
            if (direction != 0) {
                rememberController(event.device)
                if (state.page == ConsolePage.CONTROLLERS) {
                    val label = when (direction) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> "← Links"
                        KeyEvent.KEYCODE_DPAD_RIGHT -> "Rechts →"
                        KeyEvent.KEYCODE_DPAD_UP -> "↑ Hoch"
                        else -> "↓ Runter"
                    }
                    state = state.copy(lastInput = "Linker Stick / Steuerkreuz · $label")
                }
            }
            holdMenuDirection("${event.deviceId}:stick", direction)
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    private fun emitMenuKey(key: Int) {
        window.decorView.post { navigationHaptics(key) }
        controllerKeys.trySend(key)
    }

    private fun holdMenuDirection(source: String, direction: Int) {
        if (heldMenuDirections[source] == direction ||
            (direction == 0 && source !in heldMenuDirections)) return
        heldMenuDirections.remove(source)
        if (direction != 0) heldMenuDirections[source] = direction
        val next = heldMenuDirections.values.lastOrNull() ?: 0
        if (next == repeatingDirection) return
        directionRepeatJob?.cancel()
        repeatingDirection = next
        if (next == 0) return
        emitMenuKey(next)
        directionRepeatJob = lifecycleScope.launch {
            delay(400)
            while (true) {
                emitMenuKey(next)
                delay(170)
            }
        }
    }

    private fun stopMenuRepeat() {
        directionRepeatJob?.cancel()
        directionRepeatJob = null
        heldMenuDirections.clear()
        repeatingDirection = 0
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) stopMenuRepeat()
    }

    private fun dispatchMenuKey(keyCode: Int) {
        if (state.controllerTestActive) return
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
                state = state.copy(page = pages[(state.page.ordinal + offset + pages.size) % pages.size],
                    accountOpen = false)
            }
            else -> {
                // Game swipes sound once when the selected page actually settles.
                if (state.page != ConsolePage.GAMES && keyCode in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_RIGHT) {
                    playMenuFeedback(ConsoleCue.NAVIGATE)
                }
                val now = SystemClock.uptimeMillis()
                // Compose buttons handle Enter consistently after entering keyboard input mode.
                for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
                    if (state.controllerTestActive) break
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
