// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.features.skylanders.catalog

import java.io.File

/** Eine gefundene .sky-Datei mit der Figur, die darin steckt. */
data class CollectedFigure(val id: Int, val variant: Int, val file: File)

/**
 * Durchsucht den Sammlungsordner nach .sky-Dateien und liest aus jeder id und
 * variant heraus.
 *
 * Gemessen an 41 echten Dateien: die id steht als 16-Bit-Zahl (niederwertiges
 * Byte zuerst) auf Offset 0x10, die Variante auf 0x1C. Beide liegen im
 * unverschluesselten Kopfblock, es braucht also keine Krypto.
 *
 * Der Ordner liegt im Nutzerverzeichnis von Dolphin. Das ist Absicht: dort darf
 * die App ohne Androids Dateiauswahl lesen und schreiben. Ueber die Auswahl
 * geladene Dateien stuerzen ab, weil Android dabei nur Leserechte vergibt,
 * Dolphin die Datei aber zum Schreiben oeffnet.
 */
object SkyCollection {
    const val FOLDER_NAME = "Skylanders"
    private const val FILE_SIZE = 0x40 * 0x10

    fun folder(userDirectory: String): File = File(userDirectory, FOLDER_NAME)

    fun scan(userDirectory: String): List<CollectedFigure> {
        val dir = folder(userDirectory)
        if (!dir.isDirectory) {
            dir.mkdirs()
            return emptyList()
        }

        val found = ArrayList<CollectedFigure>()
        for (file in dir.listFiles().orEmpty()) {
            if (!file.isFile || !file.name.endsWith(".sky", ignoreCase = true)) continue
            if (file.length() < FILE_SIZE) continue

            val head = ByteArray(0x20)
            try {
                file.inputStream().use { if (it.read(head) < head.size) return@use }
            } catch (e: java.io.IOException) {
                continue
            }

            found.add(CollectedFigure(readShort(head, 0x10), readShort(head, 0x1C), file))
        }
        return found
    }

    private fun readShort(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

    /** Dateiname fuer eine neu erschaffene Figur, ohne vorhandene zu ueberschreiben. */
    fun newFileFor(userDirectory: String, figure: CatalogFigure): File {
        val safe = figure.name.replace(Regex("[^A-Za-z0-9 ()._-]"), "_")
        var candidate = File(folder(userDirectory), "$safe.sky")
        var n = 2
        while (candidate.exists()) {
            candidate = File(folder(userDirectory), "$safe ($n).sky")
            n++
        }
        return candidate
    }
}
