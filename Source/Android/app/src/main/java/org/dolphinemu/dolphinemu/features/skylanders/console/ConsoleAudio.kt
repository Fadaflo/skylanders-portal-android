// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.features.skylanders.console

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.dolphinemu.dolphinemu.R

internal data class ConsoleAudioSettings(
    val musicEnabled: Boolean = true,
    val musicVolume: Float = 0.30f,
    val effectsEnabled: Boolean = true,
    val effectsVolume: Float = 0.35f
) {
    fun save(preferences: SharedPreferences) {
        preferences.edit().putBoolean("menu_music_enabled", musicEnabled)
            .putFloat("menu_music_volume", musicVolume.coerceIn(0f, 1f))
            .putBoolean("menu_effects_enabled", effectsEnabled)
            .putFloat("menu_effects_volume", effectsVolume.coerceIn(0f, 1f)).apply()
    }

    companion object {
        fun load(preferences: SharedPreferences) = ConsoleAudioSettings(
            preferences.getBoolean("menu_music_enabled", true),
            preferences.getFloat("menu_music_volume", 0.30f).coerceIn(0f, 1f),
            preferences.getBoolean("menu_effects_enabled", true),
            preferences.getFloat("menu_effects_volume", 0.35f).coerceIn(0f, 1f)
        )
    }
}

internal enum class ConsoleCue { NAVIGATE, CONFIRM, BACK, PORTAL }

/** Foreground-only menu audio; independent of Dolphin's in-game mixer. Main-thread owned. */
internal class ConsoleAudio(
    private val context: Context,
    initialSettings: ConsoleAudioSettings,
    private val startPositionMs: Int = 0,
    private val onError: (String) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val pool = SoundPool.Builder().setMaxStreams(1).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val loadedSounds = mutableSetOf<Int>()
    private val navigateSound: Int
    private val confirmSound: Int
    private val portalSound: Int
    private var settings = initialSettings
    private var player: MediaPlayer? = null
    private var prepared = false
    private var foreground = false
    private var focusHeld = false
    private var released = false
    private var musicFailed = false
    private var lastCueAt = -1_000L
    private var currentStream = 0
    private var fadeGeneration = 0
    private var fadeLevel = 0f
    private var duckLevel = 1f

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (released) return@OnAudioFocusChangeListener
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                focusHeld = true
                duckLevel = 1f
                startPreparedMusic()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                duckLevel = 0.2f
                applyMusicVolume()
            }
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                focusHeld = false
                pauseMusic()
                if (currentStream != 0) pool.stop(currentStream)
            }
        }
    }
    private val focusRequest = if (Build.VERSION.SDK_INT >= 26) {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(focusListener, handler).build()
    } else null

    init {
        pool.setOnLoadCompleteListener { _, sound, status ->
            if (!released && status == 0) loadedSounds.add(sound)
        }
        navigateSound = pool.load(context, R.raw.portal_menu_swish, 1)
        confirmSound = pool.load(context, R.raw.portal_menu_confirm, 1)
        portalSound = pool.load(context, R.raw.portal_menu_enter, 1)
    }

    val positionMs: Int
        get() = if (prepared) runCatching { player?.currentPosition ?: 0 }.getOrDefault(0) else 0

    fun resume() {
        if (released) return
        foreground = true
        update(settings)
    }

    fun update(value: ConsoleAudioSettings) {
        settings = value
        if (!settings.effectsEnabled || settings.effectsVolume == 0f) {
            if (currentStream != 0) pool.stop(currentStream)
        }
        if (!foreground || !settings.musicEnabled || settings.musicVolume == 0f) {
            pauseMusic()
            abandonFocus()
            return
        }
        if (musicFailed) return
        if (!focusHeld) focusHeld = requestFocus()
        if (!focusHeld) return
        if (player == null) prepareMusic()
        else {
            applyMusicVolume()
            startPreparedMusic()
        }
    }

    fun pause() {
        foreground = false
        pauseMusic()
        if (currentStream != 0) pool.stop(currentStream)
        abandonFocus()
    }

    fun play(cue: ConsoleCue) {
        if (released || !foreground || !settings.effectsEnabled || settings.effectsVolume == 0f) return
        val now = SystemClock.uptimeMillis()
        // A selection change and a focus move may describe the same input.
        if (cue != ConsoleCue.PORTAL && now - lastCueAt < 85) return
        val sound = when (cue) {
            ConsoleCue.CONFIRM -> confirmSound
            ConsoleCue.PORTAL -> portalSound
            else -> navigateSound
        }
        if (sound !in loadedSounds) return
        lastCueAt = now
        // The short, low UI taps already have a quiet envelope. Squaring the slider
        // again makes them disappear beneath the music on small tablet speakers.
        val gain = if (cue == ConsoleCue.PORTAL) settings.effectsVolume * settings.effectsVolume
            else settings.effectsVolume
        currentStream = pool.play(sound, gain, gain, 1, 0,
            if (cue == ConsoleCue.BACK) 0.88f else 1f)
        if (currentStream == 0) onError("Der Menüklang konnte nicht abgespielt werden.")
    }

    fun release() {
        if (released) return
        pause()
        released = true
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        prepared = false
        pool.release()
    }

    private fun prepareMusic() {
        val candidate = MediaPlayer()
        player = candidate
        runCatching {
            candidate.setAudioAttributes(attributes)
            candidate.isLooping = true
            candidate.setVolume(0f, 0f)
            candidate.setOnPreparedListener {
                prepared = true
                if (startPositionMs > 0) candidate.seekTo(startPositionMs.coerceAtMost(candidate.duration - 1))
                startPreparedMusic()
            }
            candidate.setOnErrorListener { _, _, _ ->
                failMusic()
                true
            }
            context.resources.openRawResourceFd(R.raw.portal_menu_theme).use { asset ->
                candidate.setDataSource(asset.fileDescriptor, asset.startOffset, asset.length)
            }
            candidate.prepareAsync()
        }.onFailure { failMusic() }
    }

    private fun startPreparedMusic() {
        val current = player ?: return
        if (!prepared || released || !foreground || !focusHeld ||
            !settings.musicEnabled || settings.musicVolume == 0f) return
        runCatching {
            if (current.isPlaying) return
            fadeLevel = 0f
            applyMusicVolume()
            current.start()
            val generation = ++fadeGeneration
            val start = SystemClock.uptimeMillis()
            val fade = object : Runnable {
                override fun run() {
                    if (released || generation != fadeGeneration) return
                    fadeLevel = ((SystemClock.uptimeMillis() - start) / 650f).coerceIn(0f, 1f)
                    applyMusicVolume()
                    if (fadeLevel < 1f) handler.postDelayed(this, 30)
                }
            }
            handler.post(fade)
        }.onFailure { failMusic() }
    }

    private fun pauseMusic() {
        fadeGeneration++
        if (prepared) runCatching { player?.takeIf { it.isPlaying }?.pause() }
        fadeLevel = 0f
    }

    private fun applyMusicVolume() {
        val gain = settings.musicVolume * settings.musicVolume * fadeLevel * duckLevel
        if (prepared) runCatching { player?.setVolume(gain, gain) }
    }

    @Suppress("DEPRECATION")
    private fun requestFocus(): Boolean = if (Build.VERSION.SDK_INT >= 26) {
        audioManager.requestAudioFocus(focusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    } else audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC,
        AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    @Suppress("DEPRECATION")
    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= 26) audioManager.abandonAudioFocusRequest(focusRequest!!)
        else audioManager.abandonAudioFocus(focusListener)
        focusHeld = false
        duckLevel = 1f
    }

    private fun failMusic() {
        musicFailed = true
        fadeGeneration++
        prepared = false
        player?.release()
        player = null
        abandonFocus()
        onError("Die Menümusik konnte nicht geladen werden.")
    }
}
