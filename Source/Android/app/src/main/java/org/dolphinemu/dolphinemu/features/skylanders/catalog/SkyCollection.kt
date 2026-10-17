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

    /**
     * Die ultimativen Figuren liegen in einem Unterordner. So bleiben sie von
     * der eigenen Sammlung getrennt — scan() liest nicht in Unterordner.
     */
    const val MAXED_FOLDER_NAME = "Ultimativ"
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

    fun maxedFolder(userDirectory: String): File =
        File(folder(userDirectory), MAXED_FOLDER_NAME)

    /** Alle bereits erschaffenen ultimativen Figuren, getrennt nach Pfad. */
    fun scanMaxed(userDirectory: String, upperPath: Boolean): List<CollectedFigure> {
        val dir = maxedFolder(userDirectory)
        if (!dir.isDirectory) return emptyList()

        val suffix = pathSuffix(upperPath)
        return dir.listFiles().orEmpty().mapNotNull { file ->
            if (!file.isFile || !file.name.endsWith(suffix, ignoreCase = true)) return@mapNotNull null
            if (file.length() < FILE_SIZE) return@mapNotNull null

            val head = ByteArray(0x20)
            try {
                file.inputStream().use { if (it.read(head) < head.size) return@use }
            } catch (e: java.io.IOException) {
                return@mapNotNull null
            }
            CollectedFigure(readShort(head, 0x10), readShort(head, 0x1C), file)
        }
    }

    fun pathSuffix(upperPath: Boolean): String =
        if (upperPath) " (oberer Pfad).sky" else " (unterer Pfad).sky"

    /** Dateiname einer ultimativen Figur. Gleicher Name heisst gleiche Datei. */
    fun maxedFileFor(userDirectory: String, figure: CatalogFigure, upperPath: Boolean): File =
        File(maxedFolder(userDirectory), safeName(figure) + pathSuffix(upperPath))

    private fun safeName(figure: CatalogFigure): String =
        figure.name.replace(Regex("[^A-Za-z0-9 ()._-]"), "_")

    /** Keep a fresh magic/adventure item, preserving other variants and all progress figures.
     * Called only after creation succeeds. Release any portal handle before deleting its file.
     */
    fun pruneItemCopies(
        userDirectory: String,
        figure: CatalogFigure,
        keep: File,
        release: (File) -> Boolean = { true }
    ): List<File> {
        if (figure.type != "Item") return emptyList()
        val copies = scan(userDirectory).filter { it.id == figure.id && it.variant == figure.variant }
        val keepPath = keep.canonicalPath
        // A failed/partial creation must never remove the existing usable item.
        if (copies.none { it.file.canonicalPath == keepPath }) return emptyList()
        return copies.filter { it.file.canonicalPath != keepPath }.mapNotNull { copy ->
            if (release(copy.file) && copy.file.delete()) null else copy.file
        }
    }

    /** Dateiname fuer eine neu erschaffene Figur, ohne vorhandene zu ueberschreiben. */
    fun newFileFor(userDirectory: String, figure: CatalogFigure): File {
        val safe = safeName(figure)
        var candidate = File(folder(userDirectory), "$safe.sky")
        var n = 2
        while (candidate.exists()) {
            candidate = File(folder(userDirectory), "$safe ($n).sky")
            n++
        }
        return candidate
    }
}
