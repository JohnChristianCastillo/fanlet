package dev.johncastles.fanlet.overlay

import dev.johncastles.fanlet.geometry.SlotRef
import dev.johncastles.fanlet.overlay.PieSelection.Change
import dev.johncastles.fanlet.overlay.PieSelection.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PieSelectionTest {

    private val plain = SlotRef(0, 0)
    private val withSecondary = SlotRef(0, 1)
    private val other = SlotRef(0, 2)
    private val outerWithSecondary = SlotRef(1, 4)

    private val selection = PieSelection(holdMs = 500) { it == withSecondary || it == outerWithSecondary }

    @Test
    fun startsWithNothingHighlighted() {
        assertNull(selection.result())
        assertNull(selection.onPointer(null, nowMs = 0))
    }

    @Test
    fun enteringSlots_reportsEachChangeOnce() {
        assertEquals(Change.Highlighted(plain), selection.onPointer(plain, nowMs = 0))
        assertNull(selection.onPointer(plain, nowMs = 10))
        assertEquals(Change.Highlighted(other), selection.onPointer(other, nowMs = 20))
        assertEquals(Change.Highlighted(null), selection.onPointer(null, nowMs = 30))
        assertNull(selection.result())
    }

    @Test
    fun sameIndexInAnotherLayer_isADifferentSlot() {
        selection.onPointer(SlotRef(0, 4), nowMs = 0)
        assertEquals(Change.Highlighted(SlotRef(1, 4)), selection.onPointer(SlotRef(1, 4), nowMs = 10))
    }

    @Test
    fun release_launchesHighlightedPrimary() {
        selection.onPointer(other, nowMs = 0)
        assertEquals(Result(other, secondary = false), selection.result())
    }

    @Test
    fun holding_switchesToSecondaryAfterHoldTime() {
        selection.onPointer(withSecondary, nowMs = 1000)
        assertEquals(1500L, selection.holdDeadlineMs())
        assertNull(selection.checkHold(nowMs = 1499))
        assertFalse(selection.showingSecondary)

        assertEquals(Change.SwitchedToSecondary(withSecondary), selection.checkHold(nowMs = 1500))
        assertTrue(selection.showingSecondary)
        assertEquals(Result(withSecondary, secondary = true), selection.result())
    }

    @Test
    fun holding_worksInOuterLayers() {
        selection.onPointer(outerWithSecondary, nowMs = 0)
        assertEquals(Change.SwitchedToSecondary(outerWithSecondary), selection.checkHold(nowMs = 500))
        assertEquals(Result(outerWithSecondary, secondary = true), selection.result())
    }

    @Test
    fun switchHappensOnlyOnce() {
        selection.onPointer(withSecondary, nowMs = 0)
        selection.checkHold(nowMs = 500)
        assertNull(selection.checkHold(nowMs = 2000))
        assertNull(selection.holdDeadlineMs())
    }

    @Test
    fun movingWithinSlot_doesNotRestartHold() {
        selection.onPointer(withSecondary, nowMs = 0)
        selection.onPointer(withSecondary, nowMs = 300)
        assertEquals(Change.SwitchedToSecondary(withSecondary), selection.onPointer(withSecondary, nowMs = 500))
    }

    @Test
    fun leavingSlot_resetsToPrimary() {
        selection.onPointer(withSecondary, nowMs = 0)
        selection.checkHold(nowMs = 500)
        selection.onPointer(other, nowMs = 600)
        assertFalse(selection.showingSecondary)

        // Coming back starts a fresh hold.
        selection.onPointer(withSecondary, nowMs = 700)
        assertFalse(selection.showingSecondary)
        assertEquals(Result(withSecondary, secondary = false), selection.result())
        assertNull(selection.checkHold(nowMs = 1199))
        assertEquals(Change.SwitchedToSecondary(withSecondary), selection.checkHold(nowMs = 1200))
    }

    @Test
    fun leavingBeforeHoldTime_preventsSwitch() {
        selection.onPointer(withSecondary, nowMs = 0)
        selection.onPointer(null, nowMs = 400)
        assertNull(selection.checkHold(nowMs = 600))
        assertNull(selection.result())
    }

    @Test
    fun slotWithoutSecondary_neverSwitches() {
        selection.onPointer(plain, nowMs = 0)
        assertNull(selection.holdDeadlineMs())
        assertNull(selection.checkHold(nowMs = 10_000))
        assertEquals(Result(plain, secondary = false), selection.result())
    }
}
