// SPDX-License-Identifier: GPL-2.0-or-later
package org.dolphinemu.dolphinemu.features.skylanders.console

import android.content.SharedPreferences
import android.view.InputDevice
import org.dolphinemu.dolphinemu.features.input.model.ControllerInterface
import org.dolphinemu.dolphinemu.features.input.model.MappingCommon
import org.dolphinemu.dolphinemu.features.input.model.controlleremu.EmulatedController
import java.io.File
import java.security.MessageDigest

internal fun controllerProfileKey(descriptor: String, vendor: Int, product: Int): String =
    MessageDigest.getInstance("SHA-256").digest("$vendor:$product:$descriptor".toByteArray())
        .joinToString("") { "%02x".format(it) }.take(24)

internal fun controllerFamily(vendor: Int, product: Int, name: String): String = when {
    vendor == 0x054c && product in setOf(0x0ce6, 0x0df2) -> "PS5 DualSense"
    vendor == 0x054c && product in setOf(0x05c4, 0x09cc, 0x0ba0) -> "PS4 DualShock"
    vendor == 0x045e -> "Xbox-Controller"
    else -> name
}

internal fun skylandersControllerProfile(device: String, inputs: Set<String>, outputs: Set<String>, saved: String?): String {
    require(device.startsWith("Android/") && '\n' !in device && '\r' !in device)
    val values = linkedMapOf<String, String>()
    var profileSection = false
    saved.orEmpty().lineSequence().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.startsWith("[")) profileSection = trimmed == "[Profile]"
        else if (profileSection && '=' in trimmed) {
            values[trimmed.substringBefore('=').trim()] = trimmed.substringAfter('=').trim()
        }
    }
    if (values.isEmpty()) {
        values.putAll(linkedMapOf(
            "Extension" to "Nunchuk", "Buttons/A" to "`Button A`", "Buttons/B" to "`Button B`",
            "Buttons/1" to "`Button X`", "Buttons/2" to "`Button Y`",
            "Buttons/-" to "`Select`", "Buttons/+" to "`Start`",
            "Nunchuk/Stick/Up" to "`Axis 1-`", "Nunchuk/Stick/Down" to "`Axis 1+`",
            "Nunchuk/Stick/Left" to "`Axis 0-`", "Nunchuk/Stick/Right" to "`Axis 0+`",
            "Nunchuk/Buttons/C" to "`Button L1`", "Nunchuk/Buttons/Z" to "`Button R1`"))
        for ((direction, axis) in listOf("Up" to "16-", "Down" to "16+", "Left" to "15-", "Right" to "15+")) {
            values["D-Pad/$direction"] = if ("Axis $axis" in inputs) "`Axis $axis`" else "`$direction`"
        }
        val pointerX = if ("Axis 11+" in inputs) 11 else 0
        val pointerY = if ("Axis 14+" in inputs) 14 else 1
        values["IR/Up"] = "deadzone(`Axis $pointerY-`, 0.15)"
        values["IR/Down"] = "deadzone(`Axis $pointerY+`, 0.15)"
        values["IR/Left"] = "deadzone(`Axis $pointerX-`, 0.15)"
        values["IR/Right"] = "deadzone(`Axis $pointerX+`, 0.15)"
        values["IR/Relative Input"] = "True"
        val shake = if ("Button R2" in inputs) "`Button R2`" else "`Axis 18+`"
        listOf("X", "Y", "Z").forEach { values["Shake/$it"] = shake }
    }
    val oldDevice = values["Device"].orEmpty()
    if (oldDevice.isNotEmpty()) values.replaceAll { _, expression -> expression.replace("$oldDevice:", "$device:") }
    values["Device"] = device
    // The PS/Mode button is reserved for the app's portal overlay, not Wii Home.
    values["Buttons/Home"] = ""
    values["Rumble/Motor"] = if ("Motor 0" in outputs) "`Motor 0`" else ""
    return "[Profile]\n" + values.entries.joinToString("\n", postfix = "\n") { "${it.key} = ${it.value}" }
}

/** Called only while Dolphin is stopped; each controller retains its own editable profile. */
internal fun applyConsoleControllerProfile(preferences: SharedPreferences, devices: List<InputDevice>, preferred: String?): String {
    if (devices.isEmpty()) return "Verbinde einen Controller über Bluetooth oder USB."
    val controller = EmulatedController.getWiimote(0)
    val candidates = devices.sortedByDescending { it.descriptor == preferred }
    val nativeDevices = ControllerInterface.getAllDeviceStrings()
    for (device in candidates) {
        val qualifier = nativeDevices.firstOrNull { name ->
            name.startsWith("Android/") && name.removePrefix("Android/").substringAfter('/') == device.name &&
                ControllerInterface.getDevice(name)?.getInputs()?.map { it.getName() }?.toSet()?.let {
                    it.containsAll(setOf("Button A", "Button B", "Axis 0-", "Axis 1-", "Button L1", "Button R1"))
                } == true
        } ?: continue
        val key = controllerProfileKey(device.descriptor, device.vendorId, device.productId)
        val family = controllerFamily(device.vendorId, device.productId, device.name)
        val folder = File(controller.getUserProfileDirectoryPath(), "Skylanders Portal").apply { mkdirs() }
        val profile = File(folder, "$key.ini")
        if (preferences.getString("controller_profile_active", null) == key && controller.getDefaultDevice() == qualifier) {
            controller.saveProfile(profile.absolutePath)
            return "$family · Spieler 1 automatisch eingerichtet"
        }
        val backup = File(folder, "Vor-Automatik.ini")
        if (!backup.exists()) controller.saveProfile(backup.absolutePath)
        preferences.getString("controller_profile_active", null)?.let { previous ->
            if (previous.matches(Regex("[a-f0-9]{24}"))) controller.saveProfile(File(folder, "$previous.ini").absolutePath)
        }
        val coreDevice = requireNotNull(ControllerInterface.getDevice(qualifier))
        val inputs = coreDevice.getInputs().map { it.getName() }.toSet()
        val outputs = coreDevice.getOutputs().map { it.getName() }.toSet()
        // Migrate the existing working standard-pad bindings once; untouched defaults
        // instead get our Skylanders Wii Remote + Nunchuk layout.
        val legacy = backup.readText().takeIf { "Buttons/A = `Button A`" in it && "Extension = Nunchuk" in it }
        val saved = if (profile.exists()) profile.readText() else legacy
        profile.writeText(skylandersControllerProfile(qualifier, inputs, outputs, saved))
        controller.loadProfile(profile.absolutePath)
        MappingCommon.save()
        preferences.edit().putString("controller_profile_active", key)
            .putString("controller_profile_descriptor", device.descriptor).apply()
        android.util.Log.i("PortalController", "Player 1: $family -> $qualifier")
        return "$family · Spieler 1 automatisch eingerichtet"
    }
    return "Controller erkannt. Für dieses Gerät bitte die Belegung manuell prüfen."
}
