// SPDX-License-Identifier: GPL-2.0-or-later
package org.dolphinemu.dolphinemu.features.skylanders.console

import android.content.Context
import java.io.File
import java.io.IOException
import java.util.UUID

/** Chooses a separate Dolphin user directory before native initialization. */
internal object ConsoleProfileStorage {
    private const val PREFERENCES = "sky_console_profiles"

    fun expectedDirectory(context: Context, legacy: File): File {
        val userId = ConsoleAuth(context).currentUserId() ?: return legacy
        val validId = runCatching { UUID.fromString(userId).toString() }.getOrNull() ?: return legacy
        return File(File(legacy, "Profiles"), validId)
    }

    fun userDirectory(context: Context, legacy: File): File {
        val destination = expectedDirectory(context, legacy)
        if (destination == legacy) return legacy
        val validId = destination.name
        val parent = File(legacy, "Profiles")
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (destination.isDirectory) {
            if (preferences.getString("legacy_owner", null) == null &&
                !preferences.edit().putString("legacy_owner", validId).commit()) {
                throw IOException("Profile ownership could not be recorded")
            }
            return destination
        }
        val claimLegacy = preferences.getString("legacy_owner", null) == null
        if (!parent.isDirectory && !parent.mkdirs()) throw IOException("Profile directory unavailable")
        val staging = File(parent, ".$validId-initializing")
        if (staging.exists() && !staging.deleteRecursively()) {
            throw IOException("Incomplete profile could not be cleared")
        }
        if (!staging.mkdirs()) throw IOException("Profile staging unavailable")

        try {
            if (claimLegacy) {
                for (name in listOf("Config", "GameSettings", "Skylanders", "Wii", "StateSaves")) {
                    copyIfPresent(File(legacy, name), File(staging, name))
                }
            }
            if (!staging.renameTo(destination)) throw IOException("Profile activation failed")
            if (claimLegacy && !preferences.edit().putString("legacy_owner", validId).commit()) {
                throw IOException("Profile ownership could not be recorded")
            }
        } catch (error: Exception) {
            // The legacy directory and any completed profile remain untouched.
            if (!destination.exists()) staging.deleteRecursively()
            throw error
        }
        return destination
    }

    private fun copyIfPresent(source: File, target: File) {
        if (!source.exists()) return
        if (!source.copyRecursively(target, overwrite = false)) {
            throw IOException("Could not copy ${source.name} into profile")
        }
    }
}
