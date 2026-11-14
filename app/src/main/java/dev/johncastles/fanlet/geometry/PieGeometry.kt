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

/** A slot of the pie: ring [layer] (0 = innermost) and position [index] in that ring, 0 = top. */
data class SlotRef(val layer: Int, val index: Int)

/** The radial extent of one ring. */
data class Ring(val inner: Float, val outer: Float) {
    val depth: Float get() = outer - inner
    val middle: Float get() = (inner + outer) / 2
}

/**
 * A pie: concentric half rings whose straight side lies on [edge], opening toward the
 * middle of the screen. Ring i holds `slotCounts[i]` equal angular slots, numbered from the
 * top (0) to the bottom on both edges. The area inside [cancelRadius] is the cancel zone;
 * the rings share the space up to [outerRadius] equally. [outerRadius] is only the drawn
 * size: a finger beyond it still selects in the outermost ring, so a long drag works.
 */
data class PieLayout(
    val center: PixelPoint,
    val edge: Edge,
    val cancelRadius: Float,
    val outerRadius: Float,
    val slotCounts: List<Int>,
) {
    init {
        require(slotCounts.isNotEmpty() && slotCounts.all { it >= 1 }) { "every ring needs at least one slot" }
        require(cancelRadius in 0f..<outerRadius) { "need 0 <= cancelRadius < outerRadius" }
    }

    val ringCount: Int get() = slotCounts.size

    private val ringDepth: Float get() = (outerRadius - cancelRadius) / ringCount

    fun ring(layer: Int): Ring =
        Ring(inner = cancelRadius + layer * ringDepth, outer = cancelRadius + (layer + 1) * ringDepth)
}

object PieGeometry {

    /** Smallest cancel zone, so there is always a comfortable "do nothing" area. */
    const val MIN_CANCEL_RADIUS_DP = 44f

    /** How far the cancel zone reaches beyond the bar the finger starts on. */
    const val CANCEL_RADIUS_MARGIN_DP = 16f

    /** The cancel zone never takes more than this share of the pie's radius. */
    const val MAX_CANCEL_RADIUS_FRACTION = 0.6f

    /**
     * Outer radius for a pie [sizeFraction] of the screen's shorter side. The shorter side
     * keeps the size the same in both orientations; 1.0 reaches across a portrait screen.
     */
    fun outerRadius(screen: ScreenInfo, sizeFraction: Float): Float =
        sizeFraction * min(screen.widthPx, screen.heightPx)

    /**
     * Radius of the cancel zone. The finger starts on the bar, at most [barThicknessDp] from
     * the edge, so the zone reaches past the bar; otherwise a touch on a thick bar would
     * highlight a slot straight away. It stays well inside [outerRadius].
     */
    fun cancelRadius(screen: ScreenInfo, barThicknessDp: Int, outerRadius: Float): Float {
        val clearOfBar = (barThicknessDp + CANCEL_RADIUS_MARGIN_DP) * screen.density
        val wanted = max(MIN_CANCEL_RADIUS_DP * screen.density, clearOfBar)
        return min(wanted, outerRadius * MAX_CANCEL_RADIUS_FRACTION)
    }

    /**
     * Lays out a pie for a touch at height [touchY] on a bar on [edge], with one ring per
     * entry of [slotCounts]. The centre sits on that edge of the usable area (the screen
     * minus the system bars), at the touch height, moved up or down just enough for the
     * whole pie to stay inside the usable area.
     */
    fun layout(
        screen: ScreenInfo,
        edge: Edge,
        touchY: Float,
        cancelRadius: Float,
        outerRadius: Float,
        slotCounts: List<Int>,
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
        return PieLayout(PixelPoint(centerX, centerY), edge, cancelRadius, outerRadius, slotCounts)
    }

    /**
     * The slot at ([x], [y]), or null in the cancel zone (closer than the cancel radius) or
     * behind the edge. The distance from the centre picks the ring, anything beyond the
     * outer radius counting as the outermost ring; the direction picks the slot within
     * that ring. A point exactly on a ring boundary belongs to the outer ring; a point
     * exactly on the line between two slots belongs to the lower one.
     */
    fun slotAt(layout: PieLayout, x: Float, y: Float): SlotRef? {
        val dx = x - layout.center.x
        val dy = y - layout.center.y
        val distance = hypot(dx, dy)
        if (distance < layout.cancelRadius) return null

        val inward = when (layout.edge) {
            Edge.LEFT -> dx
            Edge.RIGHT -> -dx
        }
        if (inward < 0f) return null

        val ringDepth = (layout.outerRadius - layout.cancelRadius) / layout.ringCount
        val layer = ((distance - layout.cancelRadius) / ringDepth).toInt().coerceIn(0, layout.ringCount - 1)

        // 0 = straight up along the edge, PI/2 = straight into the screen, PI = straight down.
        // abs() turns -0.0 (a point exactly on the right edge) into 0.0; atan2 would
        // otherwise return -PI instead of PI for a point straight below the centre.
        val angle = atan2(abs(inward).toDouble(), (-dy).toDouble())
        val count = layout.slotCounts[layer]
        val index = (angle / (PI / count)).toInt().coerceIn(0, count - 1)
        return SlotRef(layer, index)
    }

    /**
     * Start angle of a slot for `Canvas.drawArc`/`Path.arcTo`: degrees, 0 = pointing right,
     * increasing clockwise. Use with [slotSweepDegrees].
     */
    fun slotStartDegrees(layout: PieLayout, slot: SlotRef): Float {
        val step = 180f / layout.slotCounts[slot.layer]
        return when (layout.edge) {
            Edge.LEFT -> -90f + slot.index * step
            Edge.RIGHT -> -90f - slot.index * step
        }
    }

    /** Sweep of every slot in ring [layer], in degrees; negative on the right edge (counter-clockwise). */
    fun slotSweepDegrees(layout: PieLayout, layer: Int): Float {
        val step = 180f / layout.slotCounts[layer]
        return when (layout.edge) {
            Edge.LEFT -> step
            Edge.RIGHT -> -step
        }
    }

    /** The point at [radius] from the centre, in the middle of [slot]'s angle. */
    fun slotPoint(layout: PieLayout, slot: SlotRef, radius: Float): PixelPoint {
        val angle = (slot.index + 0.5) * PI / layout.slotCounts[slot.layer]
        val inwardSign = when (layout.edge) {
            Edge.LEFT -> 1f
            Edge.RIGHT -> -1f
        }
        return PixelPoint(
            x = layout.center.x + inwardSign * (sin(angle) * radius).toFloat(),
            y = layout.center.y - (cos(angle) * radius).toFloat(),
        )
    }

    /**
     * Size of a main app icon in ring [layer]: [fraction] of the space a slot has there,
     * which is the smaller of the ring's depth and the slot's width along the ring's middle.
     */
    fun iconSize(layout: PieLayout, layer: Int, fraction: Float): Float {
        val ring = layout.ring(layer)
        val slotWidth = (PI * ring.middle / layout.slotCounts[layer]).toFloat()
        return fraction * min(ring.depth, slotWidth)
    }
}
