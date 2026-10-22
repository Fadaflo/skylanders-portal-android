package org.dolphinemu.dolphinemu.features.skylanders.console

import org.junit.Assert.*
import org.junit.Test

class ConsoleLoadingTest {
    @Test fun idleLoginAndMenuHaveNoSpinnerEvenDuringBackgroundLibraryNotifications() {
        val idle = ConsoleState(ready = true)
        assertFalse(idle.foregroundLoading)
        assertFalse(idle.copy(refreshing = true).foregroundLoading)
        assertFalse(idle.copy(account = ConsoleAccountState(email = "player@example.com"),
            refreshing = true).foregroundLoading)
    }

    @Test funForegroundActionsShowSpinnerOnlyUntilTheirOperationFinishes() {
        val idle = ConsoleState(ready = true)
        assertTrue(idle.copy(account = ConsoleAccountState(busy = true)).foregroundLoading)
        assertTrue(idle.copy(importing = true).foregroundLoading)
        assertTrue(idle.copy(updateBusy = true).foregroundLoading)
        assertTrue(idle.copy(refreshing = true, refreshRequested = true).foregroundLoading)
        assertFalse(idle.copy(refreshing = false, refreshRequested = true).foregroundLoading)
        assertTrue(idle.copy(ready = false).foregroundLoading)
    }
}
