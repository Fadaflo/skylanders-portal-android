package org.dolphinemu.dolphinemu.features.skylanders.console

import org.junit.Assert.*
import org.junit.Test

class ConsoleGraphicsTest {
    @Test fun snapdragonDoesNotSelectThePreviouslyFlickeringVulkanBackend() {
        val plan = consoleGraphicsPlan("qcom SM8250", false, true)
        assertEquals("OGL", plan.backend)
        assertEquals(0, plan.shaders)
        assertFalse(plan.fastDepth)
        assertEquals(2, plan.resolution)
    }
    @Test fun maliRequiresVulkanSupportBeforeSelectingIt() {
        assertEquals("Vulkan", consoleGraphicsPlan("exynos", false, true).backend)
        assertEquals("OGL", consoleGraphicsPlan("exynos", false, false).backend)
    }
    @Test fun unknownAndLowMemoryHardwareUseConservativeShaders() {
        assertEquals(0, consoleGraphicsPlan("unknown", false, true).shaders)
        assertEquals(0, consoleGraphicsPlan("qcom", true, true).shaders)
        assertEquals(1, consoleGraphicsPlan("qcom SM8250", true, true).resolution)
        assertEquals(1, consoleGraphicsPlan("qcom SM7250", false, true).resolution)
        assertEquals(1, consoleGraphicsPlan("unknown", false, true).resolution)
    }
}
