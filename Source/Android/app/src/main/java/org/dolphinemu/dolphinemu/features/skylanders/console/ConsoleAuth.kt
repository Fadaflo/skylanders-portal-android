// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.features.skylanders.console

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.dolphinemu.dolphinemu.BuildConfig
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class ConsoleAccountState(
    val email: String? = null,
    val name: String? = null,
    val avatar: String = "spyro",
    val busy: Boolean = false,
    val offline: Boolean = false,
    val confirmationEmail: String? = null,
    val message: String? = null,
    val error: String? = null
)

internal sealed interface AuthOutcome {
    data class SignedIn(val email: String, val name: String?, val avatar: String = "spyro",
        val offline: Boolean = false, val notice: String? = null) : AuthOutcome
    data class ConfirmationRequired(val email: String) : AuthOutcome
    data class Notice(val message: String) : AuthOutcome
    data object SignedOut : AuthOutcome
}

internal class ConsoleAuth(private val context: Context) {
    private val lock = Mutex()
    private val store = SecureSessionStore(context)
    private var session: AuthSession? = store.read()

    fun currentUserId(): String? = session?.userId

    fun currentAccessToken(): String? = session?.accessToken

    fun currentAccount(): ConsoleAccountState = session?.let {
        ConsoleAccountState(email = it.email, name = it.name, avatar = it.avatar)
    } ?: ConsoleAccountState()

    suspend fun signIn(email: String, password: String): AuthOutcome = lock.withLock {
        val response = request("/auth/v1/token?grant_type=password",
            JSONObject().put("email", email.trim()).put("password", password))
        val next = AuthSession.from(response)
        save(next)
        AuthOutcome.SignedIn(next.email, next.name, next.avatar)
    }

    suspend fun signUp(email: String, password: String, name: String): AuthOutcome = lock.withLock {
        val data = JSONObject()
        if (name.isNotBlank()) data.put("display_name", name.trim())
        val redirect = URLEncoder.encode("https://skylanders-portal.vercel.app/email-bestaetigt.html", "UTF-8")
        val response = request("/auth/v1/signup?redirect_to=$redirect", JSONObject().put("email", email.trim())
            .put("password", password).put("data", data))
        if (response.optString("access_token").isNotEmpty()) {
            val next = AuthSession.from(response)
            save(next)
            AuthOutcome.SignedIn(next.email, next.name, next.avatar)
        } else AuthOutcome.ConfirmationRequired(email.trim())
    }

    suspend fun refreshIfNeeded(force: Boolean = false): AuthOutcome = lock.withLock {
        val old = session ?: return@withLock AuthOutcome.SignedOut
        if (!force && old.expiresAtMs - System.currentTimeMillis() > 120_000L) {
            return@withLock AuthOutcome.SignedIn(old.email, old.name, old.avatar)
        }
        try {
            val response = request("/auth/v1/token?grant_type=refresh_token",
                JSONObject().put("refresh_token", old.refreshToken))
            val next = AuthSession.from(response, old)
            save(next)
            AuthOutcome.SignedIn(next.email, next.name, next.avatar)
        } catch (error: AuthHttpException) {
            if (error.status == 400 || error.status == 401 || error.status == 403) {
                save(null)
                AuthOutcome.SignedOut
            } else AuthOutcome.SignedIn(old.email, old.name, old.avatar, offline = true)
        } catch (_: IOException) {
            AuthOutcome.SignedIn(old.email, old.name, old.avatar, offline = true)
        }
    }

    suspend fun updateName(name: String): AuthOutcome = lock.withLock {
        val old = session ?: return@withLock AuthOutcome.SignedOut
        val trimmed = name.trim()
        val user = request("/auth/v1/user", JSONObject().put("data",
            JSONObject().put("display_name", trimmed)), old.accessToken, method = "PUT")
        val savedName = user.optJSONObject("user_metadata")?.optString("display_name")
            ?.takeIf { it.isNotBlank() } ?: trimmed
        val next = old.copy(name = savedName)
        save(next)
        AuthOutcome.SignedIn(next.email, next.name, next.avatar, notice = "Name gespeichert.")
    }

    suspend fun updateAvatar(avatar: String): AuthOutcome = lock.withLock {
        require(avatar in setOf("spyro", "giants", "swap", "trap"))
        val old = session ?: return@withLock AuthOutcome.SignedOut
        val user = request("/auth/v1/user", JSONObject().put("data",
            JSONObject().put("avatar_key", avatar)), old.accessToken, method = "PUT")
        val savedAvatar = user.optJSONObject("user_metadata")?.optString("avatar_key")
            ?.takeIf { it in setOf("spyro", "giants", "swap", "trap") } ?: avatar
        val next = old.copy(avatar = savedAvatar)
        save(next)
        AuthOutcome.SignedIn(next.email, next.name, next.avatar, notice = "Profilbild gespeichert.")
    }

    suspend fun updatePassword(current: String, nextPassword: String): AuthOutcome = lock.withLock {
        val old = session ?: return@withLock AuthOutcome.SignedOut
        request("/auth/v1/user", JSONObject().put("current_password", current)
            .put("password", nextPassword), old.accessToken, method = "PUT")
        AuthOutcome.SignedIn(old.email, old.name, old.avatar, notice = "Passwort geändert.")
    }

    suspend fun requestPasswordReset(email: String): AuthOutcome = lock.withLock {
        request("/auth/v1/recover", JSONObject().put("email", email.trim()))
        AuthOutcome.Notice("Falls das Konto existiert, kommt ein Wiederherstellungscode per E-Mail.")
    }

    suspend fun completePasswordReset(email: String, code: String, password: String): AuthOutcome = lock.withLock {
        val verified = request("/auth/v1/verify", JSONObject().put("email", email.trim())
            .put("token", code.trim()).put("type", "recovery"))
        val recovered = AuthSession.from(verified)
        request("/auth/v1/user", JSONObject().put("password", password),
            recovered.accessToken, method = "PUT")
        save(recovered)
        AuthOutcome.SignedIn(recovered.email, recovered.name, recovered.avatar,
            notice = "Passwort zurückgesetzt.")
    }

    suspend fun signOut(): AuthOutcome = lock.withLock {
        val old = session
        if (old != null) {
            try {
                request("/auth/v1/logout?scope=local", JSONObject(), old.accessToken)
            } catch (_: IOException) {
                // The local session is still removed when the network is unavailable.
            } catch (_: AuthHttpException) {
                // An expired server session must not prevent manual local sign-out.
            }
        }
        save(null)
        AuthOutcome.SignedOut
    }

    private fun save(next: AuthSession?) {
        if (next == null) store.clear() else store.write(next)
        session = next
    }

    private suspend fun request(path: String, body: JSONObject, accessToken: String? = null,
        method: String = "POST"): JSONObject =
        withContext(Dispatchers.IO) {
            val connection = (URL(BuildConfig.SKY_AUTH_URL + path).openConnection() as HttpURLConnection)
            try {
                connection.requestMethod = method
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.doOutput = true
                connection.setRequestProperty("apikey", BuildConfig.SKY_AUTH_KEY)
                connection.setRequestProperty("Content-Type", "application/json")
                if (accessToken != null) connection.setRequestProperty("Authorization", "Bearer $accessToken")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val raw = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                val result = if (raw.isBlank()) JSONObject() else runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
                if (status !in 200..299) throw AuthHttpException(status,
                    result.optString("code", result.optString("error_code")),
                    result.optString("msg", result.optString("error_description", result.optString("message"))))
                result
            } finally {
                connection.disconnect()
            }
        }
}

internal class AuthHttpException(val status: Int, val code: String, detail: String) :
    Exception(detail)

private data class AuthSession(
    val userId: String,
    val email: String,
    val name: String?,
    val avatar: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMs: Long
) {
    fun toJson() = JSONObject().put("user_id", userId).put("email", email).put("name", name)
        .put("avatar", avatar)
        .put("access_token", accessToken).put("refresh_token", refreshToken)
        .put("expires_at_ms", expiresAtMs)

    companion object {
        fun from(json: JSONObject, previous: AuthSession? = null): AuthSession {
            val user = json.optJSONObject("user")
            val access = json.getString("access_token")
            val refresh = json.getString("refresh_token")
            val id = user?.optString("id")?.takeIf { it.isNotBlank() } ?: previous?.userId
                ?: error("User ID fehlt")
            val email = user?.optString("email")?.takeIf { it.isNotBlank() } ?: previous?.email
                ?: error("E-Mail fehlt")
            val name = user?.optJSONObject("user_metadata")?.optString("display_name")
                ?.takeIf { it.isNotBlank() } ?: previous?.name
            val avatar = user?.optJSONObject("user_metadata")?.optString("avatar_key")
                ?.takeIf { it in setOf("spyro", "giants", "swap", "trap") }
                ?: previous?.avatar ?: "spyro"
            return AuthSession(id, email, name, avatar, access, refresh,
                System.currentTimeMillis() + json.optLong("expires_in", 3600L) * 1000L)
        }

        fun stored(json: JSONObject) = AuthSession(json.getString("user_id"), json.getString("email"),
            json.optString("name").takeIf { it.isNotBlank() },
            json.optString("avatar").takeIf { it in setOf("spyro", "giants", "swap", "trap") } ?: "spyro",
            json.getString("access_token"),
            json.getString("refresh_token"), json.getLong("expires_at_ms"))
    }
}

/** The refresh token is encrypted with a key that never leaves Android Keystore. */
private class SecureSessionStore(context: Context) {
    private val preferences = context.getSharedPreferences("sky_console_auth", Context.MODE_PRIVATE)
    private val alias = "sky_console_auth_session_v1"

    fun read(): AuthSession? = runCatching {
        val encoded = preferences.getString("session", null) ?: return@runCatching null
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        require(combined.size > 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, combined.copyOfRange(0, 12)))
        AuthSession.stored(JSONObject(String(cipher.doFinal(combined.copyOfRange(12, combined.size)), Charsets.UTF_8)))
    }.getOrElse {
        clear()
        null
    }

    fun write(session: AuthSession) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.iv + cipher.doFinal(session.toJson().toString().toByteArray(Charsets.UTF_8))
        check(preferences.edit().putString("session", Base64.encodeToString(bytes, Base64.NO_WRAP)).commit())
    }

    fun clear() { preferences.edit().remove("session").commit() }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return generator.generateKey()
    }
}
