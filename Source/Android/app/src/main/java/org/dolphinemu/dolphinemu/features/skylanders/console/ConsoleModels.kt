// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.features.skylanders.console

import org.dolphinemu.dolphinemu.features.skylanders.catalog.CatalogFigure
import org.dolphinemu.dolphinemu.R

internal enum class ConsolePage(val title: String) {
    GAMES("Spiele"), FIGURES("Figuren"), CONTROLLERS("Controller"), SETTINGS("Einstellungen")
}

internal data class ConsoleGame(
    val key: String,
    val title: String,
    val description: String,
    val artwork: String,
    val color: Long,
    val path: String? = null,
    val sceneArtwork: Int? = null,
    val playTimeMs: Long? = null,
    // Size of the existing RVZ reference file; availability requires a configured source.
    val downloadBytes: Long? = null
) {
    val displayTitle: String
        get() = when (title) {
            "Giants", "Spyro’s Adventure", "Trap Team" -> "Skylanders $title"
            "Swap Force" -> "Skylanders SWAP Force"
            else -> title
        }
}

internal data class ConsoleFigure(
    val path: String,
    val name: String,
    val element: String,
    val image: String?,
    val ultimate: Boolean,
    val game: String = "",
    val type: String = "",
    val specialVariant: Boolean = false,
    val upgradePath: Int = -1
)

internal val figureGameLabels = linkedMapOf("SpyrosAdv" to "Spyro’s Adventure", "Giants" to "Giants",
    "SwapForce" to "SWAP Force", "TrapTeam" to "Trap Team", "Superchargers" to "SuperChargers")
internal val figureElementLabels = linkedMapOf("Magic" to "Magie", "Fire" to "Feuer", "Air" to "Luft",
    "Earth" to "Erde", "Life" to "Leben", "Water" to "Wasser", "Tech" to "Technik",
    "Undead" to "Untot", "Light" to "Licht", "Dark" to "Dunkel", "Other" to "Sonstige")

internal fun filterConsoleFigures(figures: List<ConsoleFigure>, ultimate: Boolean, query: String,
    game: String, element: String, variants: Boolean, traps: Boolean, sort: String): List<ConsoleFigure> =
    figures.filter {
        it.ultimate == ultimate && it.name.contains(query.trim(), ignoreCase = true) &&
            (game.isEmpty() || it.game == game) && (element.isEmpty() || it.element == element) &&
            (variants || !it.specialVariant) && (traps || it.type != "Trap")
    }.sortedWith(compareBy<ConsoleFigure> {
        when (sort) {
            "game" -> figureGameLabels.keys.indexOf(it.game).let { index -> if (index < 0) Int.MAX_VALUE else index }
            "element" -> figureElementLabels.keys.indexOf(it.element).let { index -> if (index < 0) Int.MAX_VALUE else index }
            else -> 0
        }
    }.thenBy { it.name.lowercase(java.util.Locale.ROOT) }.thenBy { it.path })

internal data class ConsoleState(
    val page: ConsolePage = ConsolePage.GAMES,
    val games: List<ConsoleGame> = consoleCatalog,
    val selectedKey: String = "giants",
    val lastPlayedPath: String? = null,
    val figures: List<ConsoleFigure> = emptyList(),
    val figureError: String? = null,
    val controllers: List<String> = emptyList(),
    val autoController: Boolean = true,
    val controllerSetup: String = "Verbinde einen Controller über Bluetooth oder USB.",
    val player1Controller: String? = null,
    val player2Controller: String? = null,
    val autoGraphics: Boolean = true,
    val graphicsSetup: String = "Automatisch · wird beim Spielstart gewählt",
    val lastInput: String = "Drücke eine Taste auf deinem Controller.",
    val account: ConsoleAccountState = ConsoleAccountState(),
    val accountOpen: Boolean = false,
    val profileRestartRequired: Boolean = false,
    val cloudStatus: String = "Cloud-Abgleich noch nicht gestartet.",
    val cloudBusy: Boolean = false,
    val cloudConflictScope: String? = null,
    val cloudDeferred: Boolean = false,
    val cloudSaveInfo: List<CloudSaveInfo> = emptyList(),
    val updateAvailable: PortalUpdate? = null,
    val updateBusy: Boolean = false,
    val updateMessage: String? = null,
    val controllerTestActive: Boolean = false,
    val testDeviceName: String = "Controller",
    val testLastInput: String = "Drücke eine Taste oder bewege einen Stick.",
    val testPressedKeys: Set<Int> = emptySet(),
    val testAxes: Map<Int, Float> = emptyMap(),
    val audio: ConsoleAudioSettings = ConsoleAudioSettings(),
    val audioError: String? = null,
    val controllerVibration: Boolean = true,
    val controllerVibrationIntensity: Float = 0.4f,
    val hapticsAvailable: Boolean = false,
    val hapticsStatus: String = "Verbinde einen Controller, um Vibration zu verwenden.",
    val immersiveInfo: Boolean = true,
    val launchingGameKey: String? = null,
    val setupOpen: Boolean = false,
    val importOpen: Boolean = false,
    val importing: Boolean = false,
    val importMessage: String? = null,
    val ready: Boolean = false,
    val refreshing: Boolean = false,
    val refreshRequested: Boolean = false
) {
    // Background library notifications must not leave a permanent spinner in the menu.
    val foregroundLoading: Boolean
        get() = !ready || account.busy || importing || updateBusy || (refreshRequested && refreshing)
}

internal val consoleCatalog = listOf(
    ConsoleGame("spyro", "Spyro’s Adventure", "Hier beginnt die Reise.\nEntdecke die Magie der Skylands.", "16-0.png", 0xFFBD9BFF,
        sceneArtwork = R.drawable.sky_console_spyro),
    ConsoleGame("giants", "Giants", "Große Helden. Ein neues Abenteuer.\nErwecke deine Skylander zum Leben.", "112-0.png", 0xFFFFC56A,
        sceneArtwork = R.drawable.sky_console_giants, downloadBytes = 4152102704L),
    ConsoleGame("swap", "Swap Force", "Zwei Hälften. Neue Möglichkeiten.\nStelle dein eigenes Heldenteam zusammen.", "2000-0.png", 0xFFFFC978,
        sceneArtwork = R.drawable.sky_console_swap, downloadBytes = 4029226016L),
    ConsoleGame("trap", "Trap Team", "Fange die Schurken.\nBeschütze die Skylands mit deinem Team.", "462-0.png", 0xFF80CFFF,
        sceneArtwork = R.drawable.sky_console_trap, downloadBytes = 4017900028L)
)

internal fun catalogGameFor(title: String): ConsoleGame? {
    val normalized = title.lowercase().replace('’', '\'').replace("™", "").replace("®", "").trim()
    // Some Wii disc metadata contains only "SWAP Force", without the franchise name.
    if (normalized.filter { it.isLetterOrDigit() } == "swapforce") return consoleCatalog.first { it.key == "swap" }
    if (!normalized.contains("skylanders")) return null
    return when {
        normalized.contains("giants") -> consoleCatalog.first { it.key == "giants" }
        normalized.contains("spyro") -> consoleCatalog.first { it.key == "spyro" }
        normalized.contains("swap") -> consoleCatalog.first { it.key == "swap" }
        normalized.contains("trap") -> consoleCatalog.first { it.key == "trap" }
        else -> null
    }
}

// Disc metadata remains reliable even if the user renames an ISO/RVZ file.
internal fun catalogGameForDisc(id: String, title: String): ConsoleGame? {
    val key = when (id.uppercase(java.util.Locale.ROOT).take(3)) {
        "SSP" -> "spyro"
        "SKY" -> "giants"
        "SVX" -> "swap"
        "SK8" -> "trap"
        else -> null
    }
    return consoleCatalog.firstOrNull { it.key == key } ?: catalogGameFor(title)
}

internal fun collectionFigure(path: String, figure: CatalogFigure?, ultimate: Boolean,
    standardIds: Set<Int> = emptySet(), upgradePath: Int = -1) =
    ConsoleFigure(path, figure?.name ?: java.io.File(path).nameWithoutExtension,
        figure?.element ?: "Other", figure?.image, ultimate, figure?.game ?: "", figure?.type ?: "",
        figure != null && (figure.variant and 0xFF) >= 2 && figure.id in standardIds, upgradePath)
