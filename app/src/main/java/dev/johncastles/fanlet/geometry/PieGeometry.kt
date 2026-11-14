package dev.johncastles.fanlet.geometry

import dev.johncastles.fanlet.config.Edge
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** A point in screen pixels. */
data class PixelPoint(val x: Float, val y: Float)

/**
 * A pie: a half ring whose straight side lies on [edge], opening toward the middle of the
 * screen. Slices are equal angular segments numbered from the top (0) to the bottom
 * (`sliceCount - 1`), on both edges. The area inside [innerRadius] is the cancel zone.
 * [outerRadius] is only the drawn size: a finger beyond it still selects the slice in its
 * direction, so a long drag works.
 */
data class PieLayout(
    val center: PixelPoint,
    val edge: Edge,
    val innerRadius: Float,
    val outerRadius: Float,
    val sliceCount: Int,
) {
    init {
        require(sliceCount >= 1) { "sliceCount must be at least 1" }
        require(innerRadius in 0f..<outerRadius) { "need 0 <= innerRadius < outerRadius" }
    }
}

object PieGeometry {

    /** Smallest cancel zone, so there is always a comfortable "do nothing" area. */
    const val MIN_INNER_RADIUS_DP = 44f

    /** How far the cancel zone reaches beyond the bar the finger starts on. */
    const val INNER_RADIUS_MARGIN_DP = 16f

    /** The cancel zone never takes more than this share of the pie's radius. */
    const val MAX_INNER_RADIUS_FRACTION = 0.6f

    /**
     * Outer radius for a pie [sizeFraction] of the screen's shorter side. The shorter side
     * keeps the size the same in both orientations; 1.0 reaches across a portrait screen.
     */
    fun outerRadius(screen: ScreenInfo, sizeFraction: Float): Float =
        sizeFraction * min(screen.widthPx, screen.heightPx)

    /**
     * Radius of the cancel zone. The finger starts on the bar, at most [barThicknessDp] from
     * the edge, so the zone reaches past the bar; otherwise a touch on a thick bar would
     * highlight a slice straight away. It stays well inside [outerRadius].
     */
    fun innerRadius(screen: ScreenInfo, barThicknessDp: Int, outerRadius: Float): Float {
        val clearOfBar = (barThicknessDp + INNER_RADIUS_MARGIN_DP) * screen.density
        val wanted = max(MIN_INNER_RADIUS_DP * screen.density, clearOfBar)
        return min(wanted, outerRadius * MAX_INNER_RADIUS_FRACTION)
    }

    /**
     * Lays out a pie for a touch at height [touchY] on a bar on [edge]. The centre sits on
     * that edge of the usable area (the screen minus the system bars), at the touch height,
     * moved up or down just enough for the whole pie to stay inside the usable area.
     */
    fun layout(
        screen: ScreenInfo,
        edge: Edge,
        touchY: Float,
        innerRadius: Float,
        outerRadius: Float,
        sliceCount: Int,
    ): PieLayout {
        val insets = screen.systemBarInsets
        val areaTop = insets.top.toFloat()
        val areaBottom = (screen.heightPx - insets.bottom).toFloat()
        val minY = areaTop + outerRadius
        val maxY = areaBottom - outerRadius
        val centerY = if (minY <= maxY) touchY.coerceIn(minY, maxY) else (areaTop + areaBottom) / 2f
        val centerX = when (edge) {
            Edge.LEFT -> insets.left.toFloat()
            Edge.RIGHT -> (screen.widthPx - insets.right).toFloat()
        }
        return PieLayout(PixelPoint(centerX, centerY), edge, innerRadius, outerRadius, sliceCount)
    }

    /**
     * The slice in the direction of ([x], [y]) from the centre, or null when the point is in
     * the cancel zone (closer than the inner radius) or behind the edge. Points beyond the
     * outer radius still select the slice in their direction. A point exactly on the inner
     * radius selects; a point exactly on the line between two slices belongs to the lower one.
     */
    fun sliceAt(layout: PieLayout, x: Float, y: Float): Int? {
        val dx = x - layout.center.x
        val dy = y - layout.center.y
        if (hypot(dx, dy) < layout.innerRadius) return null

        val inward = when (layout.edge) {
            Edge.LEFT -> dx
            Edge.RIGHT -> -dx
        }
        if (inward < 0f) return null

        // 0 = straight up along the edge, PI/2 = straight into the screen, PI = straight down.
        // abs() turns -0.0 (a point exactly on the right edge) into 0.0; atan2 would
        // otherwise return -PI instead of PI for a point straight below the centre.
        val angle = atan2(abs(inward).toDouble(), (-dy).toDouble())
        val index = (angle / (PI / layout.sliceCount)).toInt()
        return index.coerceIn(0, layout.sliceCount - 1)
    }

    /**
     * Start angle of slice [index] for `Canvas.drawArc`/`Path.arcTo`: degrees, 0 = pointing
     * right, increasing clockwise. Use with [sliceSweepDegrees].
     */
    fun sliceStartDegrees(layout: PieLayout, index: Int): Float {
        val step = 180f / layout.sliceCount
        return when (layout.edge) {
            Edge.LEFT -> -90f + index * step
            Edge.RIGHT -> -90f - index * step
        }
    }

    /** Sweep of every slice in degrees; negative on the right edge, where slices run counter-clockwise. */
    fun sliceSweepDegrees(layout: PieLayout): Float {
        val step = 180f / layout.sliceCount
        return when (layout.edge) {
            Edge.LEFT -> step
            Edge.RIGHT -> -step
        }
    }

    /** The point at [radius] from the centre, in the middle of slice [index]. */
    fun slicePoint(layout: PieLayout, index: Int, radius: Float): PixelPoint {
        val angle = (index + 0.5) * PI / layout.sliceCount
        val inwardSign = when (layout.edge) {
            Edge.LEFT -> 1f
            Edge.RIGHT -> -1f
        }
        return PixelPoint(
            x = layout.center.x + inwardSign * (sin(angle) * radius).toFloat(),
            y = layout.center.y - (cos(angle) * radius).toFloat(),
        )
    }
}
