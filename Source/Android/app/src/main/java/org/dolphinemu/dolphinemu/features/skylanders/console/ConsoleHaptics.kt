// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.features.skylanders.console

import android.media.AudioAttributes
import android.os.Build
import android.os.CombinedVibration
import android.os.SystemClock
import android.os.VibrationEffect
import android.util.Log
import android.view.InputDevice
import kotlin.math.roundToInt

internal data class ConsoleHapticStatus(val available: Boolean, val description: String)

/** Only external controller motors; never use the phone/tablet vibrator service. */
internal class ConsoleHaptics(private var enabled: Boolean, initialIntensity: Float) {
    private class Endpoint(val descriptor: String, val amplitudeControl: Boolean,
        val route: String, val play: (Long, Int) -> Unit, val cancel: () -> Unit)
    private val endpoints = mutableMapOf<Int, Endpoint>()
    private var intensity = initialIntensity.coerceIn(0f, 1f)
    private var foreground = false
    private var inputDeviceId: Int? = null
    private var activeMotor: Endpoint? = null
    private var lastPulseAt = -1000L
    private var lastFailure: String? = null
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()

    fun resume() { foreground = true }
    fun pause() { foreground = false; inputDeviceId = null; cancel() }
    fun update(value: Boolean) { enabled = value; if (!value) cancel() }
    fun updateIntensity(value: Float) { intensity = value.coerceIn(0f, 1f); if (intensity == 0f) cancel() }
    fun touchInput() { inputDeviceId = null }
    fun controllerInput(device: InputDevice) { if (isController(device)) inputDeviceId = device.id }

    fun status(): ConsoleHapticStatus {
        val devices = controllers()
        val available = devices.firstOrNull { motor(it) != null }
        return when {
            available != null -> ConsoleHapticStatus(true, lastFailure ?: run {
                val control = if (motor(available)?.amplitudeControl == true) "Motorstärke regelbar"
                    else "Stärke über Impulsdauer abgestuft"
                "${available.name} · $control"
            })
            devices.isEmpty() -> ConsoleHapticStatus(false, "Verbinde einen Controller. Deine Intensitätseinstellung bleibt gespeichert.")
            else -> ConsoleHapticStatus(false, "Android meldet für diesen Controller keine Vibration. Die Intensität kannst du trotzdem voreinstellen.")
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
        if (!enabled || !foreground || intensity == 0f || !isController(device)) return
        val now = SystemClock.uptimeMillis()
        if (now - lastPulseAt < 100) return
        val target = motor(device) ?: return
        // Keep feedback short. Older on/off motors get a duration-based approximation.
        val baseDuration = if (test) 80L else if (cue == ConsoleCue.PORTAL) 44L else 40L
        val duration = if (target.amplitudeControl) baseDuration
            else (12 + baseDuration * intensity).roundToInt().toLong()
        val amplitude = (255 * intensity).roundToInt().coerceIn(1, 255)
        runCatching {
            target.play(duration, amplitude)
            activeMotor = target
            lastPulseAt = now
            lastFailure = null
            // Android may still reject the request in EventHub without throwing to the app.
            Log.d("PortalHaptics", "Requested ${if (test) "TEST" else cue.name}: device=${device.id}, route=${target.route}, intensity=$intensity, duration=${duration}ms")
        }.onFailure {
            lastFailure = "Die Vibrationsanfrage ist fehlgeschlagen. Controller neu verbinden und erneut testen."
            Log.w("PortalHaptics", "Controller feedback request failed", it)
        }
    }

    private fun cancel() { runCatching { activeMotor?.cancel?.invoke() }; activeMotor = null }
    private fun controllers(): List<InputDevice> {
        val devices = InputDevice.getDeviceIds().toList().mapNotNull(InputDevice::getDevice).filter(::isController)
        val connected = devices.map { it.id }.toSet()
        endpoints.keys.retainAll(connected)
        return devices
    }
    private fun isController(device: InputDevice) = !device.isVirtual &&
        (device.supportsSource(InputDevice.SOURCE_GAMEPAD) || device.supportsSource(InputDevice.SOURCE_JOYSTICK))

    private fun motor(device: InputDevice): Endpoint? {
        endpoints[device.id]?.takeIf { it.descriptor == device.descriptor }?.let { return it }
        val endpoint = runCatching {
            if (Build.VERSION.SDK_INT >= 31) {
                // Explicit per-motor effects use the stereo input-device path instead of
                // selecting one motor and sending an unaddressed mono effect.
                val manager = device.vibratorManager
                val ids = manager.vibratorIds.filter { manager.getVibrator(it).hasVibrator() }
                if (ids.isEmpty()) return@runCatching null
                val adjustable = ids.all { manager.getVibrator(it).hasAmplitudeControl() }
                Endpoint(device.descriptor, adjustable, "addressed-motors", { duration, amplitude ->
                    val parallel = CombinedVibration.startParallel()
                    ids.forEach { id ->
                        val gain = if (manager.getVibrator(id).hasAmplitudeControl()) amplitude else VibrationEffect.DEFAULT_AMPLITUDE
                        parallel.addVibrator(id, VibrationEffect.createOneShot(duration, gain))
                    }
                    manager.vibrate(parallel.combine())
                }, { manager.cancel() })
            } else {
                @Suppress("DEPRECATION") val vibrator = device.vibrator
                if (!vibrator.hasVibrator()) return@runCatching null
                val adjustable = Build.VERSION.SDK_INT >= 26 && vibrator.hasAmplitudeControl()
                Endpoint(device.descriptor, adjustable, "legacy-controller", { duration, amplitude ->
                    if (Build.VERSION.SDK_INT >= 26) {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(VibrationEffect.createOneShot(duration,
                            if (adjustable) amplitude else VibrationEffect.DEFAULT_AMPLITUDE), attributes)
                    } else {
                        @Suppress("DEPRECATION") vibrator.vibrate(duration, attributes)
                    }
                }, { vibrator.cancel() })
            }
        }.onFailure { Log.w("PortalHaptics", "Controller motors unavailable", it) }.getOrNull()
        if (endpoint != null) endpoints[device.id] = endpoint
        return endpoint
    }
}
