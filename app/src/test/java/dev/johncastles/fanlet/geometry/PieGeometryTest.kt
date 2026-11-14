package dev.johncastles.fanlet.geometry

import dev.johncastles.fanlet.config.Edge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI

class PieGeometryTest {

    private val screen = ScreenInfo(widthPx = 1080, heightPx = 2400, density = 3f)
    private val cancel = 120f
    private val outer = 360f

    private fun layout(
        edge: Edge,
        touchY: Float = 1200f,
        slotCounts: List<Int> = listOf(4),
        on: ScreenInfo = screen,
    ) = PieGeometry.layout(on, edge, touchY, cancel, outer, slotCounts)

    // ---- Centre and clamping ------------------------------------------------------

    @Test
    fun center_sitsOnEdgeAtTouchHeight() {
        assertEquals(PixelPoint(0f, 1200f), layout(Edge.LEFT).center)
        assertEquals(PixelPoint(1080f, 1200f), layout(Edge.RIGHT).center)
    }

    @Test
    fun center_nearTop_isMovedDownToFit() {
        assertEquals(360f, layout(Edge.RIGHT, touchY = 50f).center.y)
    }

    @Test
    fun center_nearBottom_isMovedUpToFit() {
        assertEquals(2400f - 360f, layout(Edge.LEFT, touchY = 2390f).center.y)
    }

    @Test
    fun center_respectsSystemBars() {
        val withBars = screen.copy(systemBarInsets = PixelInsets(top = 100, right = 126, bottom = 150))
        val top = layout(Edge.RIGHT, touchY = 0f, on = withBars)
        assertEquals(PixelPoint(1080f - 126f, 100f + 360f), top.center)
        val bottom = layout(Edge.RIGHT, touchY = 2400f, on = withBars)
        assertEquals(2400f - 150f - 360f, bottom.center.y)
    }

    @Test
    fun center_screenTooShort_usesMiddle() {
        val short = ScreenInfo(widthPx = 2400, heightPx = 600, density = 3f)
        assertEquals(300f, layout(Edge.LEFT, touchY = 10f, on = short).center.y)
    }

    // ---- Rings --------------------------------------------------------------------

    @Test
    fun rings_shareTheSpaceEqually() {
        val five = layout(Edge.LEFT, slotCounts = listOf(1, 1, 1, 1, 1)) // depth (360 - 120) / 5 = 48
        assertEquals(Ring(120f, 168f), five.ring(0))
        assertEquals(Ring(312f, 360f), five.ring(4))
        val one = layout(Edge.LEFT, slotCounts = listOf(3))
        assertEquals(Ring(120f, 360f), one.ring(0))
    }

    // ---- Hit testing: one ring ------------------------------------------------------

    @Test
    fun oneRing_everySlotCount_bothEdges_middleOfEachSlotHitsThatSlot() {
        for (edge in Edge.entries) {
            for (count in 1..12) {
                val pie = layout(edge, slotCounts = listOf(count))
                for (index in 0 until count) {
                    val slot = SlotRef(0, index)
                    val point = PieGeometry.slotPoint(pie, slot, radius = pie.ring(0).middle)
                    assertEquals("edge=$edge count=$count index=$index", slot, PieGeometry.slotAt(pie, point.x, point.y))
                }
            }
        }
    }

    @Test
    fun slotsRunTopToBottom_onBothEdges() {
        for (edge in Edge.entries) {
            val pie = layout(edge, slotCounts = listOf(3))
            val inwardX = if (edge == Edge.LEFT) 10f else 1070f
            assertEquals(SlotRef(0, 0), PieGeometry.slotAt(pie, inwardX, 1200f - 300f))
            assertEquals(SlotRef(0, 2), PieGeometry.slotAt(pie, inwardX, 1200f + 300f))
        }
    }

    @Test
    fun straightAlongEdge_upIsFirstSlot_downIsLastSlot() {
        val left = layout(Edge.LEFT, slotCounts = listOf(5))
        assertEquals(SlotRef(0, 0), PieGeometry.slotAt(left, 0f, 1200f - 200f))
        assertEquals(SlotRef(0, 4), PieGeometry.slotAt(left, 0f, 1200f + 200f))

        val right = layout(Edge.RIGHT, slotCounts = listOf(5))
        assertEquals(SlotRef(0, 0), PieGeometry.slotAt(right, 1080f, 1200f - 200f))
        assertEquals(SlotRef(0, 4), PieGeometry.slotAt(right, 1080f, 1200f + 200f))
    }

    @Test
    fun lineBetweenSlots_belongsToLowerSlot() {
        // With two slots the boundary is the line straight into the screen.
        val pie = layout(Edge.RIGHT, slotCounts = listOf(2))
        assertEquals(SlotRef(0, 1), PieGeometry.slotAt(pie, 1080f - 200f, 1200f))
    }

    @Test
    fun cancelZone_returnsNull() {
        val pie = layout(Edge.RIGHT)
        assertNull(PieGeometry.slotAt(pie, 1080f, 1200f)) // the centre itself
        assertNull(PieGeometry.slotAt(pie, 1080f - 119f, 1200f))
        assertEquals(SlotRef(0, 2), PieGeometry.slotAt(pie, 1080f - cancel, 1200f)) // exactly on the cancel radius
    }

    @Test
    fun beyondOuterRadius_stillSelectsInThatDirection() {
        val pie = layout(Edge.LEFT, slotCounts = listOf(2))
        assertEquals(SlotRef(0, 0), PieGeometry.slotAt(pie, 300f, 1200f - 300f)) // ~424 px out
        assertEquals(SlotRef(0, 1), PieGeometry.slotAt(pie, 1000f, 1200f + 50f)) // across the screen
    }

    @Test
    fun behindEdge_returnsNull() {
        // Only possible when the edge is inset, e.g. by a side navigation bar.
        val inset = screen.copy(systemBarInsets = PixelInsets(right = 126))
        val pie = layout(Edge.RIGHT, on = inset)
        assertNull(PieGeometry.slotAt(pie, 1080f - 126f + 150f, 1200f))
    }

    // ---- Hit testing: several rings ---------------------------------------------------

    @Test
    fun severalRings_distancePicksRing_directionPicksSlotWithinIt() {
        // 2 apps on layer 1, 10 on layer 2 (rings 120..240 and 240..360).
        for (edge in Edge.entries) {
            val pie = layout(edge, slotCounts = listOf(2, 10))
            for (layer in 0..1) {
                for (index in 0 until pie.slotCounts[layer]) {
                    val slot = SlotRef(layer, index)
                    val point = PieGeometry.slotPoint(pie, slot, radius = pie.ring(layer).middle)
                    assertEquals("edge=$edge slot=$slot", slot, PieGeometry.slotAt(pie, point.x, point.y))
                }
            }
        }
    }

    @Test
    fun everyRingCountUpToFive_hitsEverySlot() {
        val counts = listOf(3, 12, 1, 7, 5)
        for (rings in 1..5) {
            val pie = layout(Edge.RIGHT, slotCounts = counts.take(rings))
            for (layer in 0 until rings) {
                for (index in 0 until counts[layer]) {
                    val slot = SlotRef(layer, index)
                    val point = PieGeometry.slotPoint(pie, slot, radius = pie.ring(layer).middle)
                    assertEquals("rings=$rings slot=$slot", slot, PieGeometry.slotAt(pie, point.x, point.y))
                }
            }
        }
    }

    @Test
    fun ringBoundary_belongsToOuterRing() {
        val pie = layout(Edge.RIGHT, slotCounts = listOf(2, 10))
        // Exactly 240 px in: the boundary between the rings. One pixel below straight-in, so the
        // angle is clearly inside slot 5 of 10 (straight in is itself a line between slots).
        assertEquals(SlotRef(1, 5), PieGeometry.slotAt(pie, 1080f - 240f, 1201f))
        assertEquals(SlotRef(0, 1), PieGeometry.slotAt(pie, 1080f - 238f, 1201f))
    }

    @Test
    fun beyondOuterRadius_selectsInOutermostRing() {
        val pie = layout(Edge.LEFT, slotCounts = listOf(2, 10))
        assertEquals(SlotRef(1, 5), PieGeometry.slotAt(pie, 900f, 1210f))
    }

    // ---- Sizes ----------------------------------------------------------------------

    @Test
    fun outerRadius_isFractionOfShorterSide_inBothOrientations() {
        val landscape = ScreenInfo(widthPx = 2400, heightPx = 1080, density = 3f)
        assertEquals(1080f, PieGeometry.outerRadius(screen, 1f)) // reaches across portrait
        assertEquals(1080f, PieGeometry.outerRadius(landscape, 1f))
        assertEquals(378f, PieGeometry.outerRadius(screen, 0.35f), 0.01f)
    }

    @Test
    fun cancelRadius_defaultIsMinimum() {
        // 20 dp bar + 16 dp margin = 36 dp < 44 dp minimum.
        assertEquals(132f, PieGeometry.cancelRadius(screen, barThicknessDp = 20, outerRadius = 600f))
    }

    @Test
    fun cancelRadius_reachesPastThickBar() {
        // 64 dp bar + 16 dp margin = 80 dp = 240 px, so a touch anywhere on the bar starts in the cancel zone.
        assertEquals(240f, PieGeometry.cancelRadius(screen, barThicknessDp = 64, outerRadius = 1000f))
    }

    @Test
    fun cancelRadius_staysInsideSmallPie() {
        assertEquals(180f, PieGeometry.cancelRadius(screen, barThicknessDp = 64, outerRadius = 300f))
    }

    @Test
    fun iconSize_isFractionOfTheSmallerOfDepthAndSlotWidth() {
        // One ring 120..360 with 5 slots: depth 240, width pi * 240 / 5 = 150.8 -> width limits.
        val one = layout(Edge.LEFT, slotCounts = listOf(5))
        assertEquals((PI * 240 / 5 * 0.7).toFloat(), PieGeometry.iconSize(one, 0, 0.7f), 0.01f)

        // Rings 120..240 (2 slots) and 240..360 (10 slots).
        val two = layout(Edge.LEFT, slotCounts = listOf(2, 10))
        assertEquals(120f, PieGeometry.iconSize(two, 0, 1f), 0.01f) // depth 120 < width 282.7
        assertEquals((PI * 300 / 10 * 0.5).toFloat(), PieGeometry.iconSize(two, 1, 0.5f), 0.01f) // width 94.2 < depth 120
    }

    // ---- Drawing helpers ------------------------------------------------------------

    @Test
    fun arcAngles_coverHalfCircleFromTop_perRing() {
        val left = layout(Edge.LEFT, slotCounts = listOf(4, 2))
        assertEquals(-90f, PieGeometry.slotStartDegrees(left, SlotRef(0, 0)))
        assertEquals(45f, PieGeometry.slotSweepDegrees(left, 0))
        assertEquals(45f, PieGeometry.slotStartDegrees(left, SlotRef(0, 3))) // ends at 90 = straight down
        assertEquals(90f, PieGeometry.slotSweepDegrees(left, 1))
        assertEquals(0f, PieGeometry.slotStartDegrees(left, SlotRef(1, 1)))

        val right = layout(Edge.RIGHT, slotCounts = listOf(4))
        assertEquals(-90f, PieGeometry.slotStartDegrees(right, SlotRef(0, 0)))
        assertEquals(-45f, PieGeometry.slotSweepDegrees(right, 0))
        assertEquals(-225f, PieGeometry.slotStartDegrees(right, SlotRef(0, 3))) // ends at -270 = straight down
    }
}
