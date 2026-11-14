package dev.johncastles.fanlet.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Editing the pie's layers and slots (pure config operations). */
class SliceEditingTest {

    private val empty = FanletConfig.DEFAULT

    // Layer 0: a, b, c. Layer 1: x.
    private val config = empty
        .withSlotAdded(0, "a").withSlotAdded(0, "b").withSlotAdded(0, "c")
        .withSlotAdded(1, "x")

    private fun FanletConfig.primaries(layer: Int) = layers[layer].slices.map { it.primaryPackage }

    @Test
    fun addingToNextIndex_startsNewOuterLayer() {
        assertEquals(2, config.layers.size)
        assertEquals(listOf("a", "b", "c"), config.primaries(0))
        assertEquals(listOf("x"), config.primaries(1))
        assertNull(config.layers[0].slices[0].secondaryPackage)
    }

    @Test
    fun cannotSkipALayer() {
        assertFalse(empty.canAddSlot(1))
        assertSame(empty, empty.withSlotAdded(1, "a"))
        assertSame(config, config.withSlotAdded(3, "z"))
    }

    @Test
    fun add_stopsAtMaximumPerLayer() {
        var full = empty
        repeat(FanletConfig.MAX_SLOTS_PER_LAYER) { full = full.withSlotAdded(0, "app$it") }
        assertFalse(full.canAddSlot(0))
        assertSame(full, full.withSlotAdded(0, "one too many"))
        assertEquals(FanletConfig.MAX_SLOTS_PER_LAYER, full.layers[0].slices.size)
        assertTrue(full.canAddSlot(1)) // a full layer does not stop the next one
    }

    @Test
    fun add_stopsAtMaximumLayers() {
        var many = empty
        repeat(FanletConfig.MAX_LAYERS) { layer -> many = many.withSlotAdded(layer, "app$layer") }
        assertEquals(FanletConfig.MAX_LAYERS, many.layers.size)
        assertFalse(many.canAddSlot(FanletConfig.MAX_LAYERS))
        assertSame(many, many.withSlotAdded(FanletConfig.MAX_LAYERS, "one too many"))
    }

    @Test
    fun remove_dropsOnlyThatSlot() {
        assertEquals(listOf("a", "c"), config.withSlotRemoved(0, 1).primaries(0))
        assertEquals(listOf("x"), config.withSlotRemoved(0, 1).primaries(1))
    }

    @Test
    fun removingLastSlotOfLayer_removesLayer_andNextMovesIn() {
        val withoutInner = config.withSlotRemoved(0, 0).withSlotRemoved(0, 0).withSlotRemoved(0, 0)
        assertEquals(1, withoutInner.layers.size)
        assertEquals(listOf("x"), withoutInner.primaries(0))
    }

    @Test
    fun move_upAndDown_withinLayer() {
        assertEquals(listOf("b", "a", "c"), config.withSlotMoved(0, from = 1, to = 0).primaries(0))
        assertEquals(listOf("a", "c", "b"), config.withSlotMoved(0, from = 1, to = 2).primaries(0))
        assertEquals(listOf("b", "c", "a"), config.withSlotMoved(0, from = 0, to = 2).primaries(0))
    }

    @Test
    fun move_keepsSecondaryWithItsSlot() {
        val moved = config.withSlotSecondary(0, 0, "s").withSlotMoved(0, from = 0, to = 2)
        assertEquals(SliceConfig("a", secondaryPackage = "s"), moved.layers[0].slices[2])
    }

    @Test
    fun outOfRangeIndexes_changeNothing() {
        assertSame(config, config.withSlotRemoved(0, 3))
        assertSame(config, config.withSlotRemoved(2, 0))
        assertSame(config, config.withSlotRemoved(-1, 0))
        assertSame(config, config.withSlotMoved(0, from = 0, to = 3))
        assertSame(config, config.withSlotMoved(0, from = 1, to = 1))
        assertSame(config, config.withSlotMoved(5, from = 0, to = 1))
        assertSame(config, config.withSlotPrimary(0, 5, "z"))
        assertSame(config, config.withSlotSecondary(4, 0, "z"))
    }

    @Test
    fun setPrimaryAndSecondary() {
        val edited = config.withSlotPrimary(1, 0, "z").withSlotSecondary(1, 0, "y")
        assertEquals(SliceConfig("z", secondaryPackage = "y"), edited.layers[1].slices[0])
        assertNull(edited.withSlotSecondary(1, 0, null).layers[1].slices[0].secondaryPackage)
        assertEquals(config.layers[0], edited.layers[0])
    }

    @Test
    fun slotCount_countsAllLayers() {
        assertEquals(4, config.slotCount)
        assertEquals(0, empty.slotCount)
    }

    @Test
    fun editingSlots_leavesBarsAlone() {
        assertEquals(empty.bars, config.withSlotMoved(0, 0, 2).withSlotRemoved(1, 0).bars)
    }

    @Test
    fun normalized_cleansLayers() {
        val messy = empty.copy(
            layers = listOf(
                PieLayer(listOf(SliceConfig(""), SliceConfig(" "))), // only blank slots: removed
                PieLayer(
                    listOf(SliceConfig("a", secondaryPackage = " ")) + (1..20).map { SliceConfig("app$it") }
                ),
            ) + (1..10).map { PieLayer(listOf(SliceConfig("extra$it"))) },
        ).normalized()
        assertEquals(FanletConfig.MAX_LAYERS, messy.layers.size)
        assertEquals(FanletConfig.MAX_SLOTS_PER_LAYER, messy.layers[0].slices.size)
        assertEquals(SliceConfig("a", secondaryPackage = null), messy.layers[0].slices[0])
        assertTrue(messy.layers.all { layer -> layer.slices.none { it.primaryPackage.isBlank() } })
    }
}
