package dev.johncastles.fanlet.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SliceEditingTest {

    private val empty = FanletConfig.DEFAULT
    private val abc = empty.withSliceAdded("a").withSliceAdded("b").withSliceAdded("c")

    private fun FanletConfig.primaries() = slices.map { it.primaryPackage }

    @Test
    fun add_appendsAtBottom() {
        assertEquals(listOf("a", "b", "c"), abc.primaries())
        assertNull(abc.slices[0].secondaryPackage)
    }

    @Test
    fun add_stopsAtMaximum() {
        var config = empty
        repeat(FanletConfig.MAX_SLICES) { config = config.withSliceAdded("app$it") }
        assertFalse(config.canAddSlice)
        assertSame(config, config.withSliceAdded("one too many"))
        assertEquals(FanletConfig.MAX_SLICES, config.slices.size)
    }

    @Test
    fun remove_dropsOnlyThatSlice() {
        assertEquals(listOf("a", "c"), abc.withSliceRemoved(1).primaries())
        assertEquals(listOf("b", "c"), abc.withSliceRemoved(0).primaries())
    }

    @Test
    fun move_upAndDown() {
        assertEquals(listOf("b", "a", "c"), abc.withSliceMoved(from = 1, to = 0).primaries())
        assertEquals(listOf("a", "c", "b"), abc.withSliceMoved(from = 1, to = 2).primaries())
        assertEquals(listOf("b", "c", "a"), abc.withSliceMoved(from = 0, to = 2).primaries())
    }

    @Test
    fun move_keepsSecondaryWithItsSlice() {
        val config = abc.withSliceSecondary(0, "x").withSliceMoved(from = 0, to = 2)
        assertEquals(SliceConfig("a", secondaryPackage = "x"), config.slices[2])
    }

    @Test
    fun outOfRangeIndexes_changeNothing() {
        assertSame(abc, abc.withSliceRemoved(3))
        assertSame(abc, abc.withSliceRemoved(-1))
        assertSame(abc, abc.withSliceMoved(from = 0, to = 3))
        assertSame(abc, abc.withSliceMoved(from = 1, to = 1))
        assertSame(abc, abc.withSlicePrimary(5, "z"))
        assertSame(abc, abc.withSliceSecondary(5, "z"))
    }

    @Test
    fun setPrimaryAndSecondary() {
        val config = abc.withSlicePrimary(1, "z").withSliceSecondary(1, "y")
        assertEquals(SliceConfig("z", secondaryPackage = "y"), config.slices[1])
        assertNull(config.withSliceSecondary(1, null).slices[1].secondaryPackage)
    }

    @Test
    fun editingSlices_leavesBarsAlone() {
        assertEquals(empty.bars, abc.withSliceMoved(0, 2).withSliceRemoved(1).bars)
    }

    @Test
    fun normalized_cleansSlices() {
        val messy = empty.copy(
            slices = listOf(
                SliceConfig(""),
                SliceConfig("a", secondaryPackage = " "),
            ) + (1..10).map { SliceConfig("app$it") },
        ).normalized()
        assertEquals(FanletConfig.MAX_SLICES, messy.slices.size)
        assertEquals(SliceConfig("a", secondaryPackage = null), messy.slices[0])
        assertTrue(messy.slices.none { it.primaryPackage.isBlank() })
    }
}
