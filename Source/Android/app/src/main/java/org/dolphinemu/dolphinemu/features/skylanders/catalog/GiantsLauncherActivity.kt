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
 * Das Symbol auf dem Startbildschirm: startet Skylanders Giants unmittelbar.
 *
 * Zeigt selbst nichts an — sie wartet, bis Dolphins Ordner und die Spieleliste
 * bereit sind, sucht das Spiel und uebergibt an die EmulationActivity. Findet
 * sie es nicht, oeffnet sie die gewohnte Spieleliste, statt kommentarlos
 * nichts zu tun.
 */
class GiantsLauncherActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DirectoryInitialization.start(this)
        AfterDirectoryInitializationRunner().runWithLifecycle(this) {
            GameFileCacheManager.startLoad()
            GameFileCacheManager.getGameFiles().observe(this) { games -> onGamesReady(games) }
        }
    }

    private var started = false

    private fun onGamesReady(games: Array<GameFile>) {
        if (started) return

        val giants = games.firstOrNull { it.getTitle().contains(GAME_NAME, ignoreCase = true) }
        if (giants == null) {
            // Die Liste kommt in mehreren Schueben; erst wenn sie fertig ist und
            // das Spiel fehlt, ist es wirklich nicht da.
            if (GameFileCacheManager.isLoadingOrRescanning()) return

            Toast.makeText(this, R_MISSING, Toast.LENGTH_LONG).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        started = true
        EmulationActivity.launch(this, giants.getPath(), false)
        finish()
    }

    companion object {
        private const val GAME_NAME = "Skylanders"
        private const val R_MISSING =
            "Skylanders Giants nicht gefunden. Bitte den Spieleordner hinzufuegen."
    }
}
