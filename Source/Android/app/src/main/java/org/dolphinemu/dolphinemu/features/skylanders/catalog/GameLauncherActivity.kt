// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.features.skylanders.catalog

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.dolphinemu.dolphinemu.activities.EmulationActivity
import org.dolphinemu.dolphinemu.model.GameFile
import org.dolphinemu.dolphinemu.services.GameFileCacheManager
import org.dolphinemu.dolphinemu.ui.main.MainActivity
import org.dolphinemu.dolphinemu.utils.AfterDirectoryInitializationRunner
import org.dolphinemu.dolphinemu.utils.DirectoryInitialization

/**
 * Ein Symbol auf dem Startbildschirm, das unmittelbar ein bestimmtes Spiel
 * startet.
 *
 * Zeigt selbst nichts an — sie wartet, bis Dolphins Ordner und die Spieleliste
 * bereit sind, sucht das Spiel und uebergibt an die EmulationActivity. Findet
 * sie es nicht, oeffnet sie die gewohnte Spieleliste, statt kommentarlos
 * nichts zu tun.
 *
 * Je Spiel eine Unterklasse mit eigenem Symbol und eigener Beschriftung im
 * Manifest. Der Name muss genau genug sein, um die Skylanders-Spiele
 * auseinanderzuhalten — „Skylanders" allein reicht nicht mehr, sobald mehrere
 * im Ordner liegen.
 */
abstract class GameLauncherActivity : AppCompatActivity() {

    /** Teil des Spieltitels, der dieses Spiel eindeutig macht. */
    protected abstract val gameName: String

    private var started = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DirectoryInitialization.start(this)
        AfterDirectoryInitializationRunner().runWithLifecycle(this) {
            GameFileCacheManager.startLoad()
            GameFileCacheManager.getGameFiles().observe(this) { games -> onGamesReady(games) }
        }
    }

    private fun onGamesReady(games: Array<GameFile>) {
        if (started) return

        val game = games.firstOrNull { it.getTitle().contains(gameName, ignoreCase = true) }
        if (game == null) {
            // Die Liste kommt in mehreren Schueben; erst wenn sie fertig ist und
            // das Spiel fehlt, ist es wirklich nicht da.
            if (GameFileCacheManager.isLoadingOrRescanning()) return

            Toast.makeText(this, "$gameName nicht gefunden.", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        started = true
        EmulationActivity.launch(this, game.getPath(), false)
        finish()
    }
}

class GiantsLauncherActivity : GameLauncherActivity() {
    override val gameName = "Giants"
}

class TrapTeamLauncherActivity : GameLauncherActivity() {
    override val gameName = "Trap Team"
}

class SwapForceLauncherActivity : GameLauncherActivity() {
    override val gameName = "Swap Force"
}
