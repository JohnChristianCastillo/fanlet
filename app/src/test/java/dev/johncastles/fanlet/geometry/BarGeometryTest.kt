package dev.johncastles.fanlet.geometry

import dev.johncastles.fanlet.config.BarConfig
import dev.johncastles.fanlet.config.Edge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BarGeometryTest {

    // 1080 x 2400 at density 3: 20 dp = 60 px.
    private val portrait = ScreenInfo(widthPx = 1080, heightPx = 2400, density = 3f)
    private val landscape = ScreenInfo(widthPx = 2400, heightPx = 1080, density = 3f)

    private val left = BarConfig(id = "left", edge = Edge.LEFT)
    private val right = BarConfig(id = "right", edge = Edge.RIGHT)

    private fun single(screen: ScreenInfo, bar: BarConfig): PixelRect {
        val rects = BarGeometry.barRects(screen, bar)
        assertEquals("expected exactly one piece", 1, rects.size)
        return rects[0]
    }

    // ---- Edges and orientation --------------------------------------------------

    @Test
    fun rightBar_portrait_defaults() {
        // Length 0.35 * 2400 = 840, centred at 1200.
        assertEquals(PixelRect(left = 1020, top = 780, right = 1080, bottom = 1620), single(portrait, right))
    }

    @Test
    fun leftBar_portrait_defaults() {
        assertEquals(PixelRect(left = 0, top = 780, right = 60, bottom = 1620), single(portrait, left))
    }

    @Test
    fun rightBar_landscape_keepsProportions() {
        // Length 0.35 * 1080 = 378, centred at 540.
        assertEquals(PixelRect(left = 2340, top = 351, right = 2400, bottom = 729), single(landscape, right))
    }

    @Test
    fun leftBar_landscape_keepsProportions() {
        assertEquals(PixelRect(left = 0, top = 351, right = 60, bottom = 729), single(landscape, left))
    }

    @Test
    fun thickness_usesDensity() {
        val screen = portrait.copy(density = 2.625f)
        val rect = single(screen, right.copy(thicknessDp = 24)) // 63 px
        assertEquals(63, rect.width)
        assertEquals(1080, rect.right)
    }

    // ---- Length and position extremes, clamping ---------------------------------

    @Test
    fun fullLength_coversWholeEdge() {
        val rect = single(portrait, right.copy(lengthFraction = 1f))
        assertEquals(0, rect.top)
        assertEquals(2400, rect.bottom)
    }

    @Test
    fun minimumLength_isTenPercent() {
        assertEquals(240, single(portrait, right.copy(lengthFraction = 0.1f)).height)
    }

    @Test
    fun positionTop_isClampedInsideScreen() {
        val rect = single(portrait, right.copy(centerFraction = 0f))
        assertEquals(0, rect.top)
        assertEquals(840, rect.height) // moved, not shortened
    }

    @Test
    fun positionBottom_isClampedInsideScreen() {
        val rect = single(portrait, left.copy(centerFraction = 1f))
        assertEquals(2400, rect.bottom)
        assertEquals(840, rect.height)
    }

    @Test
    fun positionNearTop_isNotClampedWhenItFits() {
        // Centre at 0.25 * 2400 = 600; half length 120 fits above it.
        val rect = single(portrait, right.copy(lengthFraction = 0.1f, centerFraction = 0.25f))
        assertEquals(PixelRect(1020, 480, 1080, 720), rect)
    }

    // ---- System bars --------------------------------------------------------------

    @Test
    fun systemBars_shrinkUsableArea() {
        // Status bar 100 px on top, navigation bar 150 px at the bottom: usable height 2150.
        val screen = portrait.copy(systemBarInsets = PixelInsets(top = 100, bottom = 150))
        val rect = single(screen, right.copy(lengthFraction = 1f))
        assertEquals(100, rect.top)
        assertEquals(2250, rect.bottom)
    }

    @Test
    fun sideNavigationBar_movesRightBarInward() {
        // 3-button navigation on the right side in landscape.
        val screen = landscape.copy(systemBarInsets = PixelInsets(right = 126))
        val rect = single(screen, right)
        assertEquals(2400 - 126, rect.right)
        assertEquals(60, rect.width)
        assertEquals(PixelRect(0, 351, 60, 729), single(screen, left))
    }

    @Test
    fun usableAreaGone_returnsNothing() {
        val screen = portrait.copy(systemBarInsets = PixelInsets(top = 1200, bottom = 1200))
        assertTrue(BarGeometry.barRects(screen, right).isEmpty())
    }

    // ---- Cutouts ------------------------------------------------------------------

    @Test
    fun cutoutOnBarEdge_splitsBarAroundIt() {
        // Camera hole on the left edge in landscape, overlapping the default bar (351..729).
        val screen = landscape.copy(cutouts = listOf(PixelRect(20, 510, 80, 570)))
        val rects = BarGeometry.barRects(screen, left)
        assertEquals(listOf(PixelRect(0, 351, 60, 510), PixelRect(0, 570, 60, 729)), rects)
    }

    @Test
    fun cutoutOnOtherEdge_doesNotAffectBar() {
        val screen = landscape.copy(cutouts = listOf(PixelRect(20, 510, 80, 570)))
        assertEquals(PixelRect(2340, 351, 2400, 729), single(screen, right))
    }

    @Test
    fun cutoutAwayFromEdge_doesNotAffectBar() {
        // Same height as the bar, but further in than the bar is thick.
        val screen = landscape.copy(cutouts = listOf(PixelRect(100, 510, 160, 570)))
        assertEquals(PixelRect(0, 351, 60, 729), single(screen, left))
    }

    @Test
    fun cutoutNearBarEnd_dropsTooShortPiece() {
        // Leaves 30 px above the cutout, less than the 60 px thickness.
        val screen = landscape.copy(cutouts = listOf(PixelRect(0, 381, 60, 450)))
        assertEquals(listOf(PixelRect(0, 450, 60, 729)), BarGeometry.barRects(screen, left))
    }

    @Test
    fun cutoutCoveringWholeBar_returnsNothing() {
        val screen = landscape.copy(cutouts = listOf(PixelRect(0, 300, 80, 800)))
        assertTrue(BarGeometry.barRects(screen, left).isEmpty())
    }

    @Test
    fun overlappingCutouts_areMerged() {
        val screen = landscape.copy(
            cutouts = listOf(PixelRect(0, 500, 60, 560), PixelRect(0, 540, 60, 600)),
        )
        assertEquals(
            listOf(PixelRect(0, 351, 60, 500), PixelRect(0, 600, 60, 729)),
            BarGeometry.barRects(screen, left),
        )
    }
}
