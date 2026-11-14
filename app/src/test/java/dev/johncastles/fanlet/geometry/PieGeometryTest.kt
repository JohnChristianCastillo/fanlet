package dev.johncastles.fanlet.geometry

import dev.johncastles.fanlet.config.Edge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PieGeometryTest {

    private val screen = ScreenInfo(widthPx = 1080, heightPx = 2400, density = 3f)
    private val inner = 120f
    private val outer = 360f

    private fun layout(
        edge: Edge,
        touchY: Float = 1200f,
        slices: Int = 4,
        on: ScreenInfo = screen,
    ) = PieGeometry.layout(on, edge, touchY, inner, outer, slices)

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

    // ---- Hit testing ----------------------------------------------------------------

    @Test
    fun everySliceCount_bothEdges_middleOfEachSliceHitsThatSlice() {
        for (edge in Edge.entries) {
            for (count in 2..8) {
                val pie = layout(edge, slices = count)
                for (index in 0 until count) {
                    val point = PieGeometry.slicePoint(pie, index, radius = (inner + outer) / 2)
                    assertEquals("edge=$edge count=$count index=$index", index, PieGeometry.sliceAt(pie, point.x, point.y))
                }
            }
        }
    }

    @Test
    fun slicesRunTopToBottom_onBothEdges() {
        for (edge in Edge.entries) {
            val pie = layout(edge, slices = 3)
            val inwardX = if (edge == Edge.LEFT) 10f else 1070f
            assertEquals(0, PieGeometry.sliceAt(pie, inwardX, 1200f - 300f))
            assertEquals(2, PieGeometry.sliceAt(pie, inwardX, 1200f + 300f))
        }
    }

    @Test
    fun straightAlongEdge_upIsFirstSlice_downIsLastSlice() {
        val left = layout(Edge.LEFT, slices = 5)
        assertEquals(0, PieGeometry.sliceAt(left, 0f, 1200f - 200f))
        assertEquals(4, PieGeometry.sliceAt(left, 0f, 1200f + 200f))

        val right = layout(Edge.RIGHT, slices = 5)
        assertEquals(0, PieGeometry.sliceAt(right, 1080f, 1200f - 200f))
        assertEquals(4, PieGeometry.sliceAt(right, 1080f, 1200f + 200f))
    }

    @Test
    fun lineBetweenSlices_belongsToLowerSlice() {
        // With two slices the boundary is the line straight into the screen.
        val pie = layout(Edge.RIGHT, slices = 2)
        assertEquals(1, PieGeometry.sliceAt(pie, 1080f - 200f, 1200f))
    }

    @Test
    fun cancelZone_returnsNull() {
        val pie = layout(Edge.RIGHT)
        assertNull(PieGeometry.sliceAt(pie, 1080f, 1200f)) // the centre itself
        assertNull(PieGeometry.sliceAt(pie, 1080f - 119f, 1200f))
    }

    @Test
    fun ringBoundaries_countAsInside() {
        val pie = layout(Edge.RIGHT, slices = 2)
        assertEquals(1, PieGeometry.sliceAt(pie, 1080f - inner, 1200f))
        assertEquals(1, PieGeometry.sliceAt(pie, 1080f - outer, 1200f))
    }

    @Test
    fun beyondOuterRadius_stillSelectsSliceInThatDirection() {
        val pie = layout(Edge.LEFT, slices = 2)
        assertEquals(0, PieGeometry.sliceAt(pie, 300f, 1200f - 300f)) // ~424 px out, up and in
        assertEquals(1, PieGeometry.sliceAt(pie, 1000f, 1200f + 50f)) // far across the screen
        assertEquals(1, PieGeometry.sliceAt(pie, 0f, 2390f)) // far down along the edge
    }

    @Test
    fun behindEdge_returnsNull() {
        // Only possible when the edge is inset, e.g. by a side navigation bar.
        val inset = screen.copy(systemBarInsets = PixelInsets(right = 126))
        val pie = layout(Edge.RIGHT, on = inset)
        assertNull(PieGeometry.sliceAt(pie, 1080f - 126f + 150f, 1200f))
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
    fun innerRadius_defaultIsMinimum() {
        // 20 dp bar + 16 dp margin = 36 dp < 44 dp minimum.
        assertEquals(132f, PieGeometry.innerRadius(screen, barThicknessDp = 20, outerRadius = 600f))
    }

    @Test
    fun innerRadius_reachesPastThickBar() {
        // 64 dp bar + 16 dp margin = 80 dp = 240 px, so a touch anywhere on the bar starts in the cancel zone.
        assertEquals(240f, PieGeometry.innerRadius(screen, barThicknessDp = 64, outerRadius = 1000f))
    }

    @Test
    fun innerRadius_staysInsideSmallPie() {
        assertEquals(180f, PieGeometry.innerRadius(screen, barThicknessDp = 64, outerRadius = 300f))
    }

    // ---- Drawing helpers ------------------------------------------------------------

    @Test
    fun arcAngles_coverHalfCircleFromTop() {
        val left = layout(Edge.LEFT, slices = 4)
        assertEquals(-90f, PieGeometry.sliceStartDegrees(left, 0))
        assertEquals(45f, PieGeometry.sliceSweepDegrees(left))
        assertEquals(45f, PieGeometry.sliceStartDegrees(left, 3)) // ends at 90 = straight down

        val right = layout(Edge.RIGHT, slices = 4)
        assertEquals(-90f, PieGeometry.sliceStartDegrees(right, 0))
        assertEquals(-45f, PieGeometry.sliceSweepDegrees(right))
        assertEquals(-225f, PieGeometry.sliceStartDegrees(right, 3)) // ends at -270 = straight down
    }
}
