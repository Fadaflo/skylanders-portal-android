// SPDX-License-Identifier: GPL-2.0-or-later
package org.dolphinemu.dolphinemu.features.skylanders.console

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import org.dolphinemu.dolphinemu.features.settings.model.NativeConfig
import org.dolphinemu.dolphinemu.features.settings.model.Settings
import org.dolphinemu.dolphinemu.utils.DirectoryInitialization
import java.io.File

internal data class ConsoleGraphicsPlan(val backend: String, val shaders: Int, val fastDepth: Boolean,
    val resolution: Int = 1, val label: String)

internal fun consoleGraphicsPlan(hardware: String, lowMemory: Boolean, vulkan: Boolean): ConsoleGraphicsPlan {
    val name = hardware.lowercase(java.util.Locale.ROOT)
    // 2x restores detail on known high-end chips; do not upscale every Snapdragon phone.
    val highEndSnapdragon = Regex("\\b(sm8150|sm8250|sm8350|sm8450|sm8475|sm8550|sm8650|sm8750|kona)\\b")
        .containsMatchIn(name)
    return when {
        listOf("qcom", "qualcomm", "snapdragon", "sm8", "sm7", "msm").any { it in name } ->
            // Some shipped Adreno drivers crash inside libGLESv2_adreno on async compilation.
            ConsoleGraphicsPlan("OGL", 0, false, resolution = if (!lowMemory && highEndSnapdragon) 2 else 1,
                label = if (!lowMemory && highEndSnapdragon) "Automatisch · Snapdragon · 2×" else "Automatisch · Snapdragon")
        vulkan && listOf("exynos", "s5e", "mediatek", "mt6", "mt8", "tensor", "gs1", "gs2", "zuma").any { it in name } ->
            ConsoleGraphicsPlan("Vulkan", if (lowMemory) 0 else 2, true, label = "Automatisch · Vulkan")
        else -> ConsoleGraphicsPlan("OGL", 0, false, label = "Automatisch · Kompatibilität")
    }
}

internal fun applyConsoleGraphics(context: Context, game: ConsoleGame): String {
    val activityManager = context.getSystemService(ActivityManager::class.java)
    val hardware = listOf(Build.HARDWARE, Build.BOARD,
        if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else "").joinToString(" ")
    val plan = consoleGraphicsPlan(hardware, activityManager.isLowRamDevice,
        context.packageManager.hasSystemFeature("android.hardware.vulkan.level"))
    // Spyro's Adventure needs a lighter rendering path than Giants on the same tablet.
    // Keep the hardware backend choice, but render this game's bloom at native size.
    val spyro = game.key == "spyro"
    val resolution = if (spyro) 1 else plan.resolution
    val directory = File(DirectoryInitialization.getUserDirectory(), "Config")
    val backup = File(directory, "Profiles/PortalGraphics/Vor-Automatik.ini")
    val original = File(directory, "GFX.ini")
    if (!backup.exists() && original.exists()) {
        backup.parentFile?.mkdirs()
        original.copyTo(backup)
    }
    val layer = NativeConfig.LAYER_BASE
    fun bool(section: String, key: String, value: Boolean) = NativeConfig.setBoolean(layer, Settings.FILE_GFX, section, key, value)
    fun int(section: String, key: String, value: Int) = NativeConfig.setInt(layer, Settings.FILE_GFX, section, key, value)
    NativeConfig.setString(layer, Settings.FILE_DOLPHIN, "Core", "GFXBackend", plan.backend)
    int("Settings", "ShaderCompilationMode", plan.shaders)
    int("Settings", "ShaderCompilerThreads", if (plan.backend == "OGL") 0 else 1)
    int("Settings", "ShaderPrecompilerThreads", if (plan.backend == "OGL") 0 else 1)
    int("Settings", "InternalResolution", resolution)
    int("Settings", "MSAA", 1)
    int("Settings", "SafeTextureCacheColorSamples", 128)
    bool("Settings", "SSAA", false)
    bool("Settings", "FastDepthCalc", plan.fastDepth)
    bool("Settings", "WaitForShadersBeforeStarting", false)
    bool("Settings", "ShaderCache", true)
    int("Enhancements", "MaxAnisotropy", -1)
    int("Enhancements", "ForceTextureFiltering", 0)
    bool("Hacks", "EFBAccessEnable", true)
    bool("Hacks", "EFBEmulateFormatChanges", true)
    bool("Hacks", "VISkip", false)
    bool("Hacks", "ImmediateXFBEnable", false)
    bool("Hacks", "EFBScaledCopy", game.key != "giants" && !spyro)
    NativeConfig.save(layer)
    android.util.Log.i("PortalGraphics", "${game.title}: ${plan.backend}, shaders=${plan.shaders}, depth=${plan.fastDepth}, scale=$resolution")
    return if (spyro) "Automatisch · Spyro’s Adventure · Leistung · 1×" else plan.label
}
