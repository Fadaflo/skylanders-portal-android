// SPDX-License-Identifier: GPL-2.0-or-later
package org.dolphinemu.dolphinemu.features.skylanders.console

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dolphinemu.dolphinemu.BuildConfig
import org.dolphinemu.dolphinemu.NativeLibrary
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal data class CloudResult(val message: String, val conflictScope: String? = null,
    val restored: Boolean = false)
internal data class CloudSaveInfo(val scope: String, val label: String,
    val localModifiedAt: Long?, val cloudModifiedAt: Long?, val cloudPresent: Boolean?)

/** Saves figures and Wii campaign files as immutable, account-owned snapshots. */
internal class ConsoleCloud(private val context: Context, private val auth: ConsoleAuth) {
    private val settings = context.getSharedPreferences("sky_console_cloud", Context.MODE_PRIVATE)
    private val bucket = "portal-saves"
    private val root get() = File(org.dolphinemu.dolphinemu.utils.DirectoryInitialization.getUserDirectory())
    private val deviceId: String by lazy {
        settings.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            check(settings.edit().putString("device_id", it).commit())
        }
    }

    suspend fun saveInfo(online: Boolean): List<CloudSaveInfo> = withContext(Dispatchers.IO) {
        val userId = auth.currentUserId() ?: return@withContext emptyList()
        val remoteTimes = mutableMapOf<String, Long>()
        val remoteScopes = mutableSetOf<String>()
        var cloudReachable = false
        if (online) {
            try {
                val token = auth.currentAccessToken()
                if (token != null) {
                    val rows = JSONArray(request("GET", "/rest/v1/portal_heads?select=scope,updated_at", token))
                    cloudReachable = true
                    for (index in 0 until rows.length()) {
                        val row = rows.getJSONObject(index)
                        val scope = row.getString("scope")
                        if (scope != "figures" && !scope.matches(Regex("wii:[0-9A-F]{8}"))) continue
                        remoteScopes += scope
                        runCatching { parseCloudTime(row.getString("updated_at")) }.onSuccess { time ->
                            remoteTimes[scope] = time
                            settings.edit().putLong("$userId:$scope:cloud_time", time).apply()
                        }.onFailure { error -> android.util.Log.w("PortalCloud", "Cloud timestamp invalid", error) }
                    }
                }
            } catch (error: Exception) {
                android.util.Log.w("PortalCloud", "Save timestamps unavailable", error)
            }
        }
        val scopes = (localScopes() + rememberedScopes() + remoteScopes).toSortedSet()
        scopes.mapNotNull { scope ->
            val source = folder(scope)
            val local = source.takeIf { it.isDirectory }?.walkTopDown()
                ?.filter { it.isFile && (scope != "figures" || it.extension.equals("sky", true)) }
                ?.map { it.lastModified() }?.maxOrNull()?.takeIf { it > 0 }
            val cached = settings.getLong("$userId:$scope:cloud_time", 0L).takeIf { it > 0 }
            val cloud = if (cloudReachable) remoteTimes[scope] else cached
            val cloudPresent = when {
                cloudReachable -> scope in remoteScopes
                cloud != null -> true
                else -> null
            }
            if (local == null && cloudPresent != true) null
            else CloudSaveInfo(scope, scopeLabel(scope), local, cloud, cloudPresent)
        }
    }

    private fun parseCloudTime(raw: String): Long {
        // PostgREST timestamps may include six fractional digits and +00:00 offsets.
        val parts = Regex("""^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2}):(\d{2})(?:\.(\d+))?(Z|[+-]\d{2}(?::?\d{2})?)$""")
            .matchEntire(raw) ?: throw IOException("Invalid cloud timestamp")
        val g = parts.groupValues
        val date = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
            isLenient = false
            clear()
            set(g[1].toInt(), g[2].toInt() - 1, g[3].toInt(), g[4].toInt(), g[5].toInt(), g[6].toInt())
            set(Calendar.MILLISECOND, g[7].padEnd(3, '0').take(3).toIntOrNull() ?: 0)
        }
        val zone = g[8]
        val offsetMinutes = if (zone == "Z") 0 else {
            val digits = zone.drop(1).replace(":", "")
            val hours = digits.take(2).toInt()
            val minutes = digits.drop(2).ifEmpty { "0" }.toInt()
            require(hours <= 23 && minutes <= 59)
            (hours * 60 + minutes) * if (zone.startsWith('-')) -1 else 1
        }
        return date.timeInMillis - offsetMinutes * 60_000L
    }

    private fun scopeLabel(scope: String): String = when (scope.removePrefix("wii:").take(6)) {
        "535350" -> "Spyro’s Adventure"
        "534B59" -> "Giants"
        "535658" -> "Swap Force"
        "534B38" -> "Trap Team"
        else -> if (scope == "figures") "Figurensammlung" else "Wii-Spielstand"
    }

    suspend fun sync(): CloudResult = withContext(Dispatchers.IO) {
        if (!NativeLibrary.IsUninitialized()) return@withContext CloudResult("Spiel läuft · Abgleich wartet.")
        val userId = auth.currentUserId() ?: return@withContext CloudResult("Ohne Konto lokal spielen.")
        if (root.canonicalPath != ConsoleProfileStorage.expectedDirectory(context,
                context.getExternalFilesDir(null) ?: return@withContext CloudResult("Speicher nicht verfügbar.")).canonicalPath) {
            return@withContext CloudResult("Profilwechsel nötig. Öffne die App erneut.")
        }
        val token = auth.currentAccessToken() ?: return@withContext CloudResult("Bitte erneut anmelden.")
        val heads = readHeads(token)
        val scopes = (localScopes() + heads.keys + rememberedScopes()).toSortedSet()
        var uploaded = 0
        var downloaded = 0
        for (scope in scopes) {
            val archive = makeArchive(scope)
            try {
                val hasFiles = archive.second > 0
                val hash = sha256(archive.first)
                val base = settings.getString("$userId:$scope:base", null)
                val lastHash = settings.getString("$userId:$scope:hash", null)
                val remote = heads[scope]
                if (!hasFiles && lastHash == null && remote == null) continue
                if (remote == base) {
                    if (hash != lastHash && (hasFiles || lastHash != null)) {
                        val outcome = upload(scope, base, archive.first, hash, token, userId)
                        if (outcome.first == "conflict") {
                            rememberPending(userId, scope, hash, outcome.second)
                            return@withContext CloudResult("Zwei Spielstände gefunden.", scope)
                        }
                        rememberSynced(userId, scope, outcome.second, hash)
                        uploaded++
                    }
                } else if (remote != null) {
                    if (!hasFiles && lastHash == null || hash == lastHash) {
                        restore(scope, remote, token)
                        val current = makeArchive(scope)
                        try { rememberSynced(userId, scope, remote, sha256(current.first)) }
                        finally { current.first.delete() }
                        downloaded++
                    } else {
                        val pendingHash = settings.getString("$userId:$scope:pending_hash", null)
                        if (pendingHash != hash) {
                            val outcome = upload(scope, base, archive.first, hash, token, userId)
                            if (outcome.first == "current") {
                                rememberSynced(userId, scope, outcome.second, hash)
                                uploaded++
                                continue
                            }
                            rememberPending(userId, scope, hash, outcome.second)
                        }
                        return@withContext CloudResult("Zwei Spielstände gefunden.", scope)
                    }
                }
            } finally {
                archive.first.delete()
            }
        }
        CloudResult(when {
            uploaded + downloaded > 0 -> "Cloud aktuell · $uploaded hochgeladen, $downloaded geladen."
            else -> "Cloud aktuell · keine geänderten Dateiinhalte erkannt."
        }, restored = downloaded > 0)
    }

    suspend fun resolve(scope: String, useThisDevice: Boolean): CloudResult = withContext(Dispatchers.IO) {
        if (!NativeLibrary.IsUninitialized()) return@withContext CloudResult("Beende zuerst das Spiel.", scope)
        val userId = auth.currentUserId() ?: return@withContext CloudResult("Bitte anmelden.", scope)
        val token = auth.currentAccessToken() ?: return@withContext CloudResult("Bitte anmelden.", scope)
        val current = readHeads(token)[scope] ?: return@withContext CloudResult("Cloud-Stand nicht verfügbar.", scope)
        if (useThisDevice) {
            val archive = makeArchive(scope)
            try {
                val hash = sha256(archive.first)
                val outcome = upload(scope, current, archive.first, hash, token, userId)
                if (outcome.first != "current") return@withContext CloudResult("Cloud hat sich erneut geändert. Bitte noch einmal wählen.", scope)
                rememberSynced(userId, scope, outcome.second, hash)
                CloudResult("Dieser Spielstand ist jetzt in der Cloud.")
            } finally { archive.first.delete() }
        } else {
            val archive = makeArchive(scope)
            try {
                val hash = sha256(archive.first)
                if (settings.getString("$userId:$scope:pending_hash", null) != hash &&
                    (archive.second > 0 || settings.getString("$userId:$scope:hash", null) != null)) {
                    val base = settings.getString("$userId:$scope:base", null)
                    upload(scope, base, archive.first, hash, token, userId)
                }
            } finally { archive.first.delete() }
            restore(scope, current, token)
            val restored = makeArchive(scope)
            try { rememberSynced(userId, scope, current, sha256(restored.first)) }
            finally { restored.first.delete() }
            CloudResult("Cloud-Spielstand geladen. Der andere Stand bleibt als Sicherung erhalten.", restored = true)
        }
    }

    private fun localScopes(): Set<String> {
        val result = mutableSetOf("figures")
        val titles = File(root, "Wii/title/00010000")
        titles.listFiles().orEmpty().filter { it.isDirectory && it.name.matches(Regex("[0-9a-fA-F]{8}")) }
            .forEach { directory ->
                val prefix = directory.name.take(6).uppercase(java.util.Locale.ROOT)
                if (prefix in setOf("535350", "534B59", "535658", "534B38") &&
                    File(directory, "data").walkTopDown().any { it.isFile }) {
                    result += "wii:${directory.name.uppercase(java.util.Locale.ROOT)}"
                }
            }
        return result
    }

    private fun rememberedScopes(): Set<String> = settings.all.keys.mapNotNull { key ->
        key.substringAfter(":", "").substringBeforeLast(":", "").takeIf {
            it == "figures" || it.matches(Regex("wii:[0-9A-F]{8}"))
        }
    }.toSet()

    private fun folder(scope: String): File = when {
        scope == "figures" -> File(root, "Skylanders")
        scope.matches(Regex("wii:[0-9A-F]{8}")) -> File(root, "Wii/title/00010000/${scope.removePrefix("wii:")}/data")
        else -> throw IOException("Unknown save scope")
    }

    private fun makeArchive(scope: String): Pair<File, Int> {
        val archive = File.createTempFile("portal-snapshot-", ".zip", context.cacheDir)
        val source = folder(scope)
        val files = source.takeIf { it.isDirectory }?.walkTopDown()?.filter { it.isFile &&
            (scope != "figures" || it.extension.equals("sky", true)) }?.toList()
            ?.sortedBy { it.relativeTo(source).invariantSeparatorsPath }.orEmpty()
        ZipOutputStream(archive.outputStream().buffered()).use { zip ->
            fun entry(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name).apply { time = 0L })
                zip.write(bytes)
                zip.closeEntry()
            }
            entry("manifest.json", JSONObject().put("scope", scope).toString().toByteArray())
            for (file in files) {
                val name = file.relativeTo(source).invariantSeparatorsPath
                zip.putNextEntry(ZipEntry("files/$name").apply { time = 0L })
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        if (archive.length() > 20L * 1024 * 1024) {
            archive.delete()
            throw IOException("Spielstand übersteigt das Cloud-Limit")
        }
        return archive to files.size
    }

    private fun readHeads(token: String): Map<String, String> {
        val array = JSONArray(request("GET", "/rest/v1/portal_heads?select=scope,snapshot_id", token))
        return (0 until array.length()).associate { i ->
            val row = array.getJSONObject(i)
            row.getString("scope") to row.getString("snapshot_id")
        }
    }

    private fun upload(scope: String, parent: String?, archive: File, hash: String,
        token: String, userId: String): Pair<String, String> {
        val pendingKey = "$userId:$scope:upload"
        val samePending = settings.getString("${pendingKey}_hash", null) == hash &&
            settings.getString("${pendingKey}_parent", null) == (parent ?: "")
        val id = if (samePending) settings.getString("${pendingKey}_id", null)
            ?: UUID.randomUUID().toString() else UUID.randomUUID().toString()
        check(settings.edit().putString("${pendingKey}_id", id)
            .putString("${pendingKey}_hash", hash)
            .putString("${pendingKey}_parent", parent ?: "").commit())
        val key = "$userId/$id.zip"
        try {
            request("POST", "/storage/v1/object/$bucket/$key", token,
                archive, "application/zip")
        } catch (uploadError: IOException) {
            // A previous attempt may have uploaded the whole object before the reply was lost.
            val existing = File.createTempFile("portal-retry-", ".zip", context.cacheDir)
            try {
                try { requestToFile("/storage/v1/object/authenticated/$bucket/$key", token, existing) }
                catch (_: IOException) { throw uploadError }
                if (existing.length() != archive.length() || sha256(existing) != hash) throw uploadError
            } finally { existing.delete() }
        }
        val body = JSONObject().put("p_snapshot_id", id).put("p_scope", scope)
            .put("p_parent_id", parent ?: JSONObject.NULL).put("p_sha256", hash)
            .put("p_bytes", archive.length()).put("p_device_id", deviceId)
        val response = JSONObject(request("POST", "/rest/v1/rpc/publish_portal_snapshot", token,
            body.toString().toByteArray(), "application/json"))
        check(settings.edit().remove("${pendingKey}_id").remove("${pendingKey}_hash")
            .remove("${pendingKey}_parent").commit())
        return response.getString("status") to id
    }

    private fun restore(scope: String, snapshotId: String, token: String) {
        val rows = JSONArray(request("GET", "/rest/v1/portal_snapshots?select=object_key,sha256,bytes&id=eq.$snapshotId", token))
        if (rows.length() != 1) throw IOException("Cloud-Sicherung nicht gefunden")
        val row = rows.getJSONObject(0)
        val key = row.getString("object_key")
        val owner = auth.currentUserId() ?: throw IOException("Not signed in")
        if (!key.matches(Regex("${Regex.escape(owner)}/[0-9a-f-]{36}\\.zip"))) {
            throw IOException("Invalid cloud object path")
        }
        val archive = File.createTempFile("portal-download-", ".zip", context.cacheDir)
        try {
            requestToFile("/storage/v1/object/authenticated/$bucket/$key", token, archive)
            if (archive.length() != row.getLong("bytes") || sha256(archive) != row.getString("sha256")) {
                throw IOException("Cloud-Sicherung beschädigt")
            }
            replaceFolder(scope, archive)
        } finally { archive.delete() }
    }

    private fun replaceFolder(scope: String, archive: File) {
        val target = folder(scope)
        val parent = target.parentFile ?: throw IOException("Save path unavailable")
        if (!parent.isDirectory && !parent.mkdirs()) throw IOException("Save path unavailable")
        val staging = File(parent, ".${target.name}-${UUID.randomUUID()}-incoming")
        if (!staging.mkdirs()) throw IOException("Save staging unavailable")
        var total = 0L
        var entries = 0
        var manifestSeen = false
        try {
            ZipInputStream(archive.inputStream().buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries++
                    if (entries > 1024) throw IOException("Too many save files")
                    if (entry.name == "manifest.json") {
                        if (manifestSeen) throw IOException("Duplicate save manifest")
                        manifestSeen = true
                        val manifestBytes = ByteArray(1024)
                        val manifestSize = zip.read(manifestBytes)
                        if (manifestSize < 1 || zip.read() != -1) throw IOException("Invalid save manifest")
                        val manifest = String(manifestBytes, 0, manifestSize, Charsets.UTF_8)
                        if (JSONObject(manifest).getString("scope") != scope) throw IOException("Wrong save scope")
                    } else {
                        if (!entry.name.startsWith("files/") || entry.isDirectory) throw IOException("Invalid save entry")
                        val relative = entry.name.removePrefix("files/")
                        if (relative.isBlank() || relative.split('/').any { it == ".." || it == "." || it.isBlank() } ||
                            scope == "figures" && !relative.endsWith(".sky", true)) {
                            throw IOException("Invalid save filename")
                        }
                        val output = File(staging, relative)
                        if (!output.canonicalPath.startsWith(staging.canonicalPath + File.separator)) {
                            throw IOException("Invalid save path")
                        }
                        if (output.parentFile?.isDirectory != true && output.parentFile?.mkdirs() != true) {
                            throw IOException("Save folder unavailable")
                        }
                        output.outputStream().use { stream ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                total += count
                                if (total > 32L * 1024 * 1024) throw IOException("Save archive too large")
                                stream.write(buffer, 0, count)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
            if (!manifestSeen) throw IOException("Save manifest missing")
            if (!NativeLibrary.IsUninitialized()) throw IOException("Spiel läuft")
            val backupRoot = File(root, "CloudBackups/${scope.replace(':', '-')}")
            if (!backupRoot.isDirectory && !backupRoot.mkdirs()) throw IOException("Backup path unavailable")
            val backup = File(backupRoot, UUID.randomUUID().toString())
            if (target.exists() && !target.renameTo(backup)) throw IOException("Could not back up current save")
            if (!staging.renameTo(target)) {
                if (backup.exists()) backup.renameTo(target)
                throw IOException("Could not activate cloud save")
            }
        } finally { if (staging.exists()) staging.deleteRecursively() }
    }

    private fun rememberSynced(userId: String, scope: String, id: String, hash: String) {
        check(settings.edit().putString("$userId:$scope:base", id)
            .putString("$userId:$scope:hash", hash)
            .remove("$userId:$scope:pending_hash").commit())
    }

    private fun rememberPending(userId: String, scope: String, hash: String, id: String) {
        check(settings.edit().putString("$userId:$scope:pending_hash", hash)
            .putString("$userId:$scope:pending_id", id).commit())
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val size = input.read(buffer)
                if (size < 0) break
                digest.update(buffer, 0, size)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun request(method: String, path: String, token: String, file: File? = null,
        contentType: String? = null): String = request(method, path, token,
        file?.inputStream()?.use { it.readBytes() }, contentType)

    private fun request(method: String, path: String, token: String, body: ByteArray?,
        contentType: String?): String {
        val connection = (URL(BuildConfig.SKY_AUTH_URL + path).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = method
            connection.connectTimeout = 10_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("apikey", BuildConfig.SKY_AUTH_KEY)
            connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType ?: "application/octet-stream")
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw IOException("Cloud HTTP $status: ${response.take(150)}")
            return response
        } finally { connection.disconnect() }
    }

    private fun requestToFile(path: String, token: String, target: File) {
        val connection = (URL(BuildConfig.SKY_AUTH_URL + path).openConnection() as HttpURLConnection)
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("apikey", BuildConfig.SKY_AUTH_KEY)
            connection.setRequestProperty("Authorization", "Bearer $token")
            if (connection.responseCode !in 200..299) throw IOException("Cloud download failed")
            connection.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
        } finally { connection.disconnect() }
    }
}
