package org.dolphinemu.dolphinemu.features.skylanders.console

import org.junit.Assert.*
import org.junit.Test

class ConsoleControllerProfilesTest {
    private val dualSense = "Android/0/DualSense Wireless Controller"
    private val inputs = setOf("Axis 0-", "Axis 1-", "Axis 11+", "Axis 14+", "Axis 15-", "Axis 16-", "Button R2")

    @Test fun createsNunchukLayoutWithSeparatePointerAndActualControllerRumble() {
        val profile = skylandersControllerProfile(dualSense, inputs, setOf("Motor 0"), null)
        assertTrue(profile.contains("Device = $dualSense"))
        assertTrue(profile.contains("Extension = Nunchuk"))
        assertTrue(profile.contains("Nunchuk/Stick/Up = `Axis 1-`"))
        assertTrue(profile.contains("IR/Up = deadzone(`Axis 14-`, 0.15)"))
        assertTrue(profile.contains("D-Pad/Up = `Axis 16-`"))
        assertTrue(profile.contains("Rumble/Motor = `Motor 0`"))
        assertTrue(profile.contains("Buttons/Home = \n"))
    }

    @Test fun reconnectRetainsCustomBindingsAndUpdatesQualifiedDeviceReferences() {
        val saved = "[Profile]\nDevice = Android/1/Old Controller\nExtension = Nunchuk\nButtons/A = `Android/1/Old Controller:Button X`\nIR/Dead Zone = 0.25\n"
        val result = skylandersControllerProfile(dualSense, inputs, emptySet(), saved)
        assertTrue(result.contains("Buttons/A = `$dualSense:Button X`"))
        assertTrue(result.contains("IR/Dead Zone = 0.25"))
        assertFalse(result.contains("Old Controller"))
        assertFalse(result.contains("Device Sensors"))
    }

    @Test fun descriptorProfilesStayStableAndDifferentControllersDoNotCollide() {
        val ps4 = controllerProfileKey("ps4-descriptor", 0x054c, 0x09cc)
        val ps5 = controllerProfileKey("ps5-descriptor", 0x054c, 0x0ce6)
        assertNotEquals(ps4, ps5)
        assertEquals(ps5, controllerProfileKey("ps5-descriptor", 0x054c, 0x0ce6))
        assertTrue(ps5.matches(Regex("[a-f0-9]{24}")))
        assertEquals("PS5 DualSense", controllerFamily(0x054c, 0x0ce6, "Wireless Controller"))
    }

    @Test fun digitalDpadWorksWithoutHatAxes() {
        val result = skylandersControllerProfile(dualSense, emptySet(), emptySet(), null)
        assertTrue(result.contains("D-Pad/Up = `Up`"))
        assertTrue(result.contains("Rumble/Motor = \n"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesMultilineDeviceNamesInIniProfiles() {
        skylandersControllerProfile("Android/0/Gamepad\n[Other]", inputs, emptySet(), null)
    }
}
