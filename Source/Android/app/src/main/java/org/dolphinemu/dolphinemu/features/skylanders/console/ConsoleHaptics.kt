// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.features.skylanders.console

import android.media.AudioAttributes
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.InputDevice

internal data class ConsoleHapticStatus(val available: Boolean, val description: String)

/** Menu-only feedback on an external controller. Never resolve the phone/tablet vibrator. */
internal class ConsoleHaptics(private var enabled: Boolean) {
    private var foreground = false
    private var inputDeviceId: Int? = null
    private var activeMotor: Vibrator? = null
    private var lastPulseAt = -1000L
    private var lastFailure: String? = null
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()

    fun resume() { foreground = true }
    fun pause() { foreground = false; inputDeviceId = null; cancel() }
    fun update(value: Boolean) { enabled = value; if (!value) cancel() }
    fun touchInput() { inputDeviceId = null }
    fun controllerInput(device: InputDevice) {
        if (isController(device)) inputDeviceId = device.id
    }

    fun status(): ConsoleHapticStatus {
        val devices = controllers()
        val available = devices.firstOrNull { motor(it) != null }
        return when {
            available != null -> ConsoleHapticStatus(true, lastFailure ?: "${available.name} · Vibration verfügbar")
            devices.isEmpty() -> ConsoleHapticStatus(false, "Verbinde einen PS4- oder PS5-Controller, um Vibration zu verwenden.")
            else -> ConsoleHapticStatus(false, "Android meldet für den verbundenen Controller keine Vibration. Je nach Gerät hilft eine USB-Verbindung.")
        }
    }

    fun play(cue: ConsoleCue) {
        val device = inputDeviceId?.let(InputDevice::getDevice) ?: return
        pulse(device, cue, test = false)
    }

    fun test(preferredDescriptor: String?) {
        val devices = controllers().filter { motor(it) != null }
        val device = devices.firstOrNull { it.descriptor == preferredDescriptor } ?: devices.firstOrNull() ?: return
        pulse(device, ConsoleCue.CONFIRM, test = true)
    }

    private fun pulse(device: InputDevice, cue: ConsoleCue, test: Boolean) {
        if (!enabled || !foreground || !isController(device)) return
        val now = SystemClock.uptimeMillis()
        if (!test && now - lastPulseAt < 100) return
        val target = motor(device) ?: return
        val duration = when (cue) {
            ConsoleCue.NAVIGATE, ConsoleCue.BACK -> 28L
            ConsoleCue.CONFIRM -> 28L
            ConsoleCue.PORTAL -> 36L
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) {
                val amplitude = if (target.hasAmplitudeControl()) {
                    56
                } else VibrationEffect.DEFAULT_AMPLITUDE
                @Suppress("DEPRECATION")
                target.vibrate(VibrationEffect.createOneShot(duration, amplitude), attributes)
            } else {
                @Suppress("DEPRECATION")
                target.vibrate(duration, attributes)
            }
            activeMotor = target
            lastPulseAt = now
            lastFailure = null
            Log.d("PortalHaptics", "${if (test) "Test" else cue.name}: ${device.name}, ${duration}ms, device=${device.id}")
        }.onFailure {
            lastFailure = "Der Controller meldet Vibration, der Impuls konnte aber nicht ausgelöst werden."
            Log.w("PortalHaptics", "Controller feedback unavailable", it)
        }
    }

    private fun cancel() {
        runCatching { activeMotor?.cancel() }
        activeMotor = null
    }

    private fun controllers() = InputDevice.getDeviceIds().toList().mapNotNull(InputDevice::getDevice).filter(::isController)
    private fun isController(device: InputDevice) = !device.isVirtual &&
        (device.supportsSource(InputDevice.SOURCE_GAMEPAD) || device.supportsSource(InputDevice.SOURCE_JOYSTICK))

    private fun motor(device: InputDevice): Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= 31) {
            val manager = device.vibratorManager
            manager.vibratorIds.map { manager.getVibrator(it) }.firstOrNull { it.hasVibrator() }
        } else {
            @Suppress("DEPRECATION")
            device.vibrator.takeIf { it.hasVibrator() }
        }
    }.getOrNull()
}
