// SPDX-License-Identifier: GPL-2.0-or-later
package org.dolphinemu.dolphinemu.features.skylanders.console

import android.content.SharedPreferences
import android.view.InputDevice
import org.dolphinemu.dolphinemu.features.input.model.ControllerInterface
import org.dolphinemu.dolphinemu.features.input.model.MappingCommon
import org.dolphinemu.dolphinemu.features.input.model.controlleremu.EmulatedController
import org.dolphinemu.dolphinemu.features.settings.model.IntSetting
import org.dolphinemu.dolphinemu.features.settings.model.BooleanSetting
import org.dolphinemu.dolphinemu.features.settings.model.NativeConfig
import java.io.File

internal data class ConsoleControllerSetup(val message: String, val player1: String? = null,
    val player2: String? = null, val player1DeviceId: Int? = null, val player2DeviceId: Int? = null)

private data class ConnectedPad(val device: InputDevice, val qualifier: String)

/** Exact Android IDs keep two controllers with identical display names separate. */
internal fun assignConsoleControllers(preferences: SharedPreferences, devices: List<InputDevice>): ConsoleControllerSetup {
    // Console play always uses physical pads, including a fresh account without Config files.
    BooleanSetting.MAIN_SHOW_INPUT_OVERLAY.setBoolean(NativeConfig.LAYER_BASE, false)
    val available = devices.sortedBy { it.id }.mapNotNull { device ->
        val qualifier = ControllerInterface.getDeviceStringForId(device.id) ?: return@mapNotNull null
        android.util.Log.i("PortalController", "Candidate id=${device.id} name=${device.name} number=${device.controllerNumber} qualifier=$qualifier")
        val inputs = ControllerInterface.getDevice(qualifier)?.getInputs()?.map { it.getName() }?.toSet()
            ?: return@mapNotNull null
        if (!inputs.containsAll(setOf("Button A", "Button B", "Axis 0-", "Axis 1-", "Button L1", "Button R1")))
            return@mapNotNull null
        ConnectedPad(device, qualifier)
    }.distinctBy { if (it.device.controllerNumber > 0) "pad:${it.device.controllerNumber}" else it.qualifier }.toMutableList()
    if (available.isEmpty()) {
        IntSetting.WIIMOTE_2_SOURCE.setInt(NativeConfig.LAYER_BASE, 0)
        NativeConfig.save(NativeConfig.LAYER_BASE)
        return ConsoleControllerSetup(if (devices.isEmpty()) "Verbinde einen Controller über Bluetooth oder USB."
            else "Controller erkannt. Für dieses Gerät bitte die Belegung manuell prüfen.")
    }
    val chosen = mutableListOf<ConnectedPad>()
    val previous = listOf(preferences.getString("controller_player_1_descriptor", null)
        ?: preferences.getString("controller_profile_descriptor", null),
        preferences.getString("controller_player_2_descriptor", null))
    val preferredId = preferences.getInt("controller_player_1_device_id", -1)
    val exact = available.indexOfFirst { it.device.id == preferredId &&
        it.device.descriptor == previous[0] && previous[0] != null }
    val first = if (exact >= 0) exact else available.indexOfFirst {
        it.device.descriptor == previous[0] && previous[0] != null }
    chosen += available.removeAt(if (first >= 0) first else 0)
    val second = available.indexOfFirst { it.device.descriptor == previous[1] && previous[1] != null }
    if (second >= 0) chosen += available.removeAt(second)
    while (chosen.size < 2 && available.isNotEmpty()) chosen += available.removeAt(0)
    val folder = File(EmulatedController.getWiimote(0).getUserProfileDirectoryPath(), "Skylanders Portal")
        .apply { mkdirs() }
    val names = mutableListOf<String>()
    chosen.take(2).forEachIndexed { index, pad ->
        val controller = EmulatedController.getWiimote(index)
        val device = pad.device
        val key = controllerProfileKey(device.descriptor, device.vendorId, device.productId)
        val family = controllerFamily(device.vendorId, device.productId, device.name)
        val activeKey = if (index == 0) "controller_profile_active" else "controller_profile_active_2"
        val profile = File(folder, if (index == 0) "$key.ini" else "$key-player2.ini")
        val current = File(folder, "Aktuell-Spieler${index + 1}.ini")
        controller.saveProfile(current.absolutePath)
        val currentUsable = usableSkylandersProfile(current.readText())
        if (currentUsable && profile.isFile && usableSkylandersProfile(profile.readText()) && preferences.getString(activeKey, null) == key && controller.getDefaultDevice() == pad.qualifier) {
            controller.saveProfile(profile.absolutePath)
        } else {
            val backup = File(folder, if (index == 0) "Vor-Automatik.ini" else "Vor-Automatik-Spieler2.ini")
            if (!backup.exists()) controller.saveProfile(backup.absolutePath)
            preferences.getString(activeKey, null)?.let { old ->
                val previousProfile = File(folder, if (index == 0) "$old.ini" else "$old-player2.ini")
                if (currentUsable && old != key && old.matches(Regex("[a-f0-9]{24}")) && previousProfile.isFile &&
                    controller.getDefaultDevice().startsWith("Android/")) {
                    controller.saveProfile(previousProfile.absolutePath)
                }
            }
            val coreDevice = requireNotNull(ControllerInterface.getDevice(pad.qualifier))
            val inputs = coreDevice.getInputs().map { it.getName() }.toSet()
            val outputs = coreDevice.getOutputs().map { it.getName() }.toSet()
            val legacy = if (index == 0) backup.readText().takeIf {
                "Buttons/A = `Button A`" in it && "Extension = Nunchuk" in it
            } else null
            val saved = if (profile.exists()) profile.readText() else legacy
            profile.writeText(skylandersControllerProfile(pad.qualifier, inputs, outputs, saved))
            controller.loadProfile(profile.absolutePath)
            preferences.edit().putString(activeKey, key).apply()
        }
        preferences.edit().putString("controller_player_${index + 1}_descriptor", device.descriptor).apply()
        preferences.edit().putInt("controller_player_${index + 1}_number", device.controllerNumber).apply()
        names += family
        android.util.Log.i("PortalController", "Player ${index + 1}: $family -> ${pad.qualifier}")
    }
    MappingCommon.save()
    IntSetting.WIIMOTE_1_SOURCE.setInt(NativeConfig.LAYER_BASE, 1)
    IntSetting.WIIMOTE_2_SOURCE.setInt(NativeConfig.LAYER_BASE, if (names.size > 1) 1 else 0)
    NativeConfig.save(NativeConfig.LAYER_BASE)
    return ConsoleControllerSetup(if (names.size > 1) "Zwei Spieler bereit. Jeder Controller steuert nur seinen Spieler."
        else "Spieler 1 bereit. Verbinde einen zweiten Controller für Spieler 2.", names.firstOrNull(), names.getOrNull(1),
        chosen.firstOrNull()?.device?.id, chosen.getOrNull(1)?.device?.id)
}
