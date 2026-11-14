package dev.johncastles.fanlet.overlay

import dev.johncastles.fanlet.overlay.PieSelection.Change
import dev.johncastles.fanlet.overlay.PieSelection.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PieSelectionTest {

    // Slice 1 has a secondary app; slices 0 and 2 do not.
    private val selection = PieSelection(holdMs = 500) { slice -> slice == 1 }

    @Test
    fun startsWithNothingHighlighted() {
        assertNull(selection.result())
        assertNull(selection.onPointer(null, nowMs = 0))
    }

    @Test
    fun enteringSlices_reportsEachChangeOnce() {
        assertEquals(Change.Highlighted(0), selection.onPointer(0, nowMs = 0))
        assertNull(selection.onPointer(0, nowMs = 10))
        assertEquals(Change.Highlighted(2), selection.onPointer(2, nowMs = 20))
        assertEquals(Change.Highlighted(null), selection.onPointer(null, nowMs = 30))
        assertNull(selection.result())
    }

    @Test
    fun release_launchesHighlightedPrimary() {
        selection.onPointer(2, nowMs = 0)
        assertEquals(Result(slice = 2, secondary = false), selection.result())
    }

    @Test
    fun holding_switchesToSecondaryAfterHoldTime() {
        selection.onPointer(1, nowMs = 1000)
        assertEquals(1500L, selection.holdDeadlineMs())
        assertNull(selection.checkHold(nowMs = 1499))
        assertFalse(selection.showingSecondary)

        assertEquals(Change.SwitchedToSecondary(1), selection.checkHold(nowMs = 1500))
        assertTrue(selection.showingSecondary)
        assertEquals(Result(slice = 1, secondary = true), selection.result())
    }

    @Test
    fun switchHappensOnlyOnce() {
        selection.onPointer(1, nowMs = 0)
        selection.checkHold(nowMs = 500)
        assertNull(selection.checkHold(nowMs = 2000))
        assertNull(selection.holdDeadlineMs())
    }

    @Test
    fun movingWithinSlice_doesNotRestartHold() {
        selection.onPointer(1, nowMs = 0)
        selection.onPointer(1, nowMs = 300)
        assertEquals(Change.SwitchedToSecondary(1), selection.onPointer(1, nowMs = 500))
    }

    @Test
    fun leavingSlice_resetsToPrimary() {
        selection.onPointer(1, nowMs = 0)
        selection.checkHold(nowMs = 500)
        selection.onPointer(2, nowMs = 600)
        assertFalse(selection.showingSecondary)

        // Coming back starts a fresh hold.
        selection.onPointer(1, nowMs = 700)
        assertFalse(selection.showingSecondary)
        assertEquals(Result(slice = 1, secondary = false), selection.result())
        assertNull(selection.checkHold(nowMs = 1199))
        assertEquals(Change.SwitchedToSecondary(1), selection.checkHold(nowMs = 1200))
    }

    @Test
    fun leavingBeforeHoldTime_preventsSwitch() {
        selection.onPointer(1, nowMs = 0)
        selection.onPointer(null, nowMs = 400)
        assertNull(selection.checkHold(nowMs = 600))
        assertNull(selection.result())
    }

    @Test
    fun sliceWithoutSecondary_neverSwitches() {
        selection.onPointer(0, nowMs = 0)
        assertNull(selection.holdDeadlineMs())
        assertNull(selection.checkHold(nowMs = 10_000))
        assertEquals(Result(slice = 0, secondary = false), selection.result())
    }
}
