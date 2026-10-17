// SPDX-License-Identifier: GPL-2.0-or-later
package org.dolphinemu.dolphinemu.features.skylanders.console

import android.content.Context
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dolphinemu.dolphinemu.BuildConfig
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal class PortalUpdateFileProvider : FileProvider()

internal data class PortalUpdate(val version: String, val number: Int, val apkPath: String,
    val bytes: Long, val sha256: String)

internal class ConsoleUpdate(private val context: Context) {
    private val host = "https://skylanders-portal.vercel.app"

    suspend fun check(): PortalUpdate? = withContext(Dispatchers.IO) {
        val connection = URL("$host/release.json").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Cache-Control", "no-cache")
            if (connection.responseCode != 200) throw IOException("Update-Info nicht erreichbar")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val release = JSONObject(body)
            val version = release.getString("version")
            val versionPattern = Regex("^(\\d{4})\\.(\\d{2})\\.(\\d{2})\\.(\\d+)-preview$")
            val parts = versionPattern.matchEntire(version)?.groupValues?.drop(1)?.map { it.toInt() }
                ?: throw IOException("Ungültige Version")
            val number = parts.last()
            val installed = versionPattern.matchEntire(BuildConfig.SKY_PORTAL_VERSION)
                ?.groupValues?.drop(1)?.map { it.toInt() }
                ?: throw IOException("Ungültige installierte Version")
            val path = release.getString("apk")
            val bytes = release.getLong("bytes")
            val hash = release.getString("sha256").lowercase()
            if (!path.matches(Regex("/downloads/[A-Za-z0-9.-]+\\.apk")) ||
                bytes !in 1..150_000_000 || !hash.matches(Regex("[a-f0-9]{64}"))) {
                throw IOException("Ungültige Update-Datei")
            }
            if (parts.zip(installed).firstOrNull { it.first != it.second }?.let { it.first > it.second } == true)
                PortalUpdate(version, number, path, bytes, hash)
            else null
        } finally { connection.disconnect() }
    }

    suspend fun download(update: PortalUpdate): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "updates")
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Kein Speicher für Update")
        val target = File(directory, "Skylanders-Portal-${update.number}.apk")
        if (valid(target, update)) return@withContext target
        target.delete()
        val temporary = File(directory, "download.tmp")
        temporary.delete()
        val connection = URL(host + update.apkPath).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = false
            if (connection.responseCode != 200) throw IOException("Download fehlgeschlagen")
            connection.inputStream.use { input ->
                temporary.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            if (!valid(temporary, update)) throw IOException("Update-Datei konnte nicht geprüft werden")
            if (!temporary.renameTo(target)) throw IOException("Update-Datei konnte nicht gespeichert werden")
            target
        } finally {
            connection.disconnect()
            temporary.delete()
        }
    }

    private fun valid(file: File, update: PortalUpdate): Boolean {
        if (!file.isFile || file.length() != update.bytes) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val size = input.read(buffer)
                if (size < 0) break
                digest.update(buffer, 0, size)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) } == update.sha256
    }
}
