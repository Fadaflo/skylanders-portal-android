// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.features.skylanders.console

/** Editorial facts are separate from the user's local progress. Sources checked 2026-10-16. */
internal data class ConsoleGameInfo(
    val releaseDate: String,
    val releaseScope: String,
    val excerpt: String,
    val excerptCredit: String,
    val summary: String,
    val sourceUrl: String
)

internal val consoleGameInfo = mapOf(
    "Giants" to ConsoleGameInfo(
        "17.10.2012", "Erstveröffentlichung · Activision",
        "The Giant Skylanders fought epic battles in Skylands, but were banished to Earth.",
        "Activision · Auszug aus der offiziellen Beschreibung",
        "Eine neue Bedrohung ruft die Giants zurück. Gemeinsam mit den Skylanders treten sie gegen Kaos an. " +
            "Als Portalmeister bringst du ihre gewaltigen Kräfte ins Spiel, um die Skylands zu retten.",
        "https://www.activision.com/games/skylanders/skylanders-giants"
    ),
    "Spyro’s Adventure" to ConsoleGameInfo(
        "14.10.2011", "Europa · Wii · Nintendo",
        "Platzieren Sie Action-Figuren auf dem „Portal of Power“ und sehen Sie, wie sie in der Spielwelt zum Leben erweckt werden!",
        "Nintendo / Publisher · kurzer offizieller Auszug",
        "Wähle aus über 30 Helden und nutze ihre unterschiedlichen Kräfte für die Herausforderungen des Abenteuers. " +
            "Du kannst allein spielen oder gemeinsam mit Freunden losziehen. Die Figuren behalten ihren eigenen Fortschritt.",
        "https://www.nintendo.com/de-de/Spiele/Wii/Skylanders-Spyro-s-Adventure-283102.html"
    ),
    "Swap Force" to ConsoleGameInfo(
        "18.10.2013", "Europa · Wii · Nintendo",
        "SWAP Force-Figuren kombinieren und neue Fähigkeiten nutzen.",
        "Nintendo / Publisher · kurzer offizieller Auszug",
        "Auf den Cloudbreak Islands verleiht ein magischer Vulkan den Helden die Fähigkeit, ihre Hälften zu tauschen. " +
            "Kombiniere Bewegungsarten und Kräfte, stelle dich Kaos und erkunde ein Abenteuer mit Kämpfen, Rätseln und lokalem Koop.",
        "https://www.nintendo.com/de-de/Spiele/Wii/Skylanders-SWAP-Force-820251.html"
    ),
    "Trap Team" to ConsoleGameInfo(
        "10.10.2014", "Europa · Wii · Nintendo",
        "Wechsle beliebig zwischen der Gestalt des Helden und des Bösewichts hin und her, um dir ein mächtiges Trap Team aufzubauen!",
        "Nintendo / Publisher · kurzer offizieller Auszug",
        "Kaos hat die Schurken aus dem Wolkenknacker-Knast befreit. Fange sie mit Traptanium-Fallen wieder ein und " +
            "nutze ihre Fähigkeiten auf deiner Seite. Das Abenteuer verbindet Kämpfe, Rätsel und einen lokalen Modus für zwei Spieler.",
        "https://www.nintendo.com/de-de/Spiele/Wii/Skylanders-Trap-Team-922317.html"
    )
)

internal fun playTimeLabel(game: ConsoleGame): String {
    if (game.path == null) return "Nicht installiert"
    val time = game.playTimeMs ?: return "Noch nicht erfasst"
    if (time <= 0) return "Noch keine Spielzeit"
    val minutes = time / 60_000
    return when {
        minutes < 1 -> "Unter 1 Min."
        minutes < 60 -> "$minutes Min."
        else -> "${minutes / 60} Std. ${minutes % 60} Min."
    }
}
