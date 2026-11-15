package dev.johncastles.fanlet.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupDecisionTest {

    @Test
    fun barsWanted_andOverlayAllowed_start() {
        assertTrue(StartupDecision.shouldStart(wanted = true, overlayAllowed = true))
    }

    @Test
    fun barsSwitchedOff_doNotStart() {
        assertFalse(StartupDecision.shouldStart(wanted = false, overlayAllowed = true))
    }

    @Test
    fun overlayPermissionRevoked_doesNotStart() {
        assertFalse(StartupDecision.shouldStart(wanted = true, overlayAllowed = false))
    }

    @Test
    fun neither_doesNotStart() {
        assertFalse(StartupDecision.shouldStart(wanted = false, overlayAllowed = false))
    }
}
