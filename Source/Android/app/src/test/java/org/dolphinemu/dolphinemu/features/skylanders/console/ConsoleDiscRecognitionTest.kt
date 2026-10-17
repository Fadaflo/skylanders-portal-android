package org.dolphinemu.dolphinemu.features.skylanders.console

import org.junit.Assert.*
import org.junit.Test

class ConsoleDiscRecognitionTest {
    @Test fun discIdsIdentifyAllFourGamesWithoutRelyingOnFileNames() {
        assertEquals("spyro", catalogGameForDisc("SSPP52", "backup").keyOrNull())
        assertEquals("giants", catalogGameForDisc("SKYZ52", "backup").keyOrNull())
        assertEquals("swap", catalogGameForDisc("SVXP52", "backup").keyOrNull())
        assertEquals("trap", catalogGameForDisc("SK8E52", "backup").keyOrNull())
    }
    @Test fun trustedIdTakesPrecedenceOverMisleadingTitle() {
        assertEquals("giants", catalogGameForDisc("SKYE52", "Skylanders Trap Team").keyOrNull())
        assertEquals("trap", catalogGameForDisc("sk8p52", "Skylanders Giants").keyOrNull())
    }
    @Test fun metadataTitleFallbackAndUnknownGame() {
        assertEquals("swap", catalogGameForDisc("", "SWAP Force").keyOrNull())
        assertNull(catalogGameForDisc("RMCP01", "Mario Kart Wii"))
    }
    private fun ConsoleGame?.keyOrNull() = this?.key
}
