// SPDX-License-Identifier: GPL-2.0-or-later
package org.dolphinemu.dolphinemu.features.skylanders.console

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import org.dolphinemu.dolphinemu.model.GameFile

internal data class ConsoleImportResult(val accepted: List<String>, val rejected: List<String>)

internal fun consoleDocumentIdentity(path: String): String = runCatching {
    val uri = Uri.parse(path)
    if (uri.scheme != "content") return@runCatching path
    val parts = uri.pathSegments
    val document = parts.indexOf("document")
    val tree = parts.indexOf("tree")
    val id = when {
        document >= 0 && document + 1 < parts.size -> parts[document + 1]
        tree >= 0 && tree + 1 < parts.size -> parts.drop(tree + 1).joinToString("/")
        else -> return@runCatching path
    }
    // Dolphin's folder scanner appends names to a tree URI. Match the same document
    // selected through OpenDocument without merging distinct regions or copies.
    "${uri.authority}:$id"
}.getOrDefault(path)

internal fun importConsoleGames(context: Context, uris: List<Uri>): ConsoleImportResult {
    val accepted = mutableListOf<String>()
    val rejected = mutableListOf<String>()
    for (uri in uris.distinct()) {
        var name = "Datei"
        val valid = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) name = it.getString(0) ?: name
            }
            require(name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT) in
                setOf("iso", "rvz", "wbfs", "wia", "gcz", "ciso"))
            val game = GameFile.parse(uri.toString()) ?: error("Kein lesbares Wii-Spiel")
            require(game.getPlatform() == 2 && catalogGameForDisc(game.getGameId(), game.getTitle()) != null)
            // OpenDocument grants only this document, not arbitrary storage access.
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.isSuccess
        if (valid) accepted += uri.toString() else rejected += name
    }
    return ConsoleImportResult(accepted, rejected)
}
