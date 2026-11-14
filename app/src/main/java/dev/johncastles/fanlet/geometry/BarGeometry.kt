package dev.johncastles.fanlet.geometry

import dev.johncastles.fanlet.config.BarConfig
import dev.johncastles.fanlet.config.Edge
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A rectangle in screen pixels; [right] and [bottom] are exclusive. */
data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    fun intersects(other: PixelRect): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom
}

/** Distances in pixels from each screen edge. */
data class PixelInsets(val left: Int = 0, val top: Int = 0, val right: Int = 0, val bottom: Int = 0) {
    companion object {
        val NONE = PixelInsets()
    }
}

/**
 * What the bar geometry needs to know about the screen in its current rotation.
 * Width and height already reflect the rotation, so landscape simply has width > height.
 */
data class ScreenInfo(
    val widthPx: Int,
    val heightPx: Int,
    /** Pixels per dp. */
    val density: Float,
    /** Space taken by the status and navigation bars. Edge bars stay outside it, where they can be touched. */
    val systemBarInsets: PixelInsets = PixelInsets.NONE,
    /** Camera holes and notches. Edge bars leave a gap where they would overlap one. */
    val cutouts: List<PixelRect> = emptyList(),
)

object BarGeometry {

    /**
     * Where [bar] goes on [screen], as one or more rectangles.
     *
     * The bar sits against its edge of the usable area (the screen minus the system bars).
     * Its length and centre are fractions of that area's height; if the bar would stick out
     * at the top or bottom, it is moved back inside rather than shortened. Where a display
     * cutout overlaps the bar, the bar is split around it; pieces shorter than the bar is
     * thick are dropped. Returns an empty list if nothing usable is left.
     */
    fun barRects(screen: ScreenInfo, bar: BarConfig): List<PixelRect> {
        val insets = screen.systemBarInsets
        val areaLeft = insets.left
        val areaRight = screen.widthPx - insets.right
        val areaTop = insets.top
        val areaBottom = screen.heightPx - insets.bottom
        val areaWidth = areaRight - areaLeft
        val areaHeight = areaBottom - areaTop
        if (areaWidth <= 0 || areaHeight <= 0) return emptyList()

        val thickness = (bar.thicknessDp * screen.density).roundToInt()
            .coerceIn(1, max(1, areaWidth / 2))
        val length = (bar.lengthFraction * areaHeight).roundToInt().coerceIn(1, areaHeight)

        val center = areaTop + bar.centerFraction * areaHeight
        val top = (center - length / 2f).roundToInt().coerceIn(areaTop, areaBottom - length)

        val left = when (bar.edge) {
            Edge.LEFT -> areaLeft
            Edge.RIGHT -> areaRight - thickness
        }
        val full = PixelRect(left, top, left + thickness, top + length)
        return splitAroundCutouts(full, screen.cutouts, minLength = thickness)
    }

    /** The parts of the vertical bar [full] that no cutout overlaps, top to bottom. */
    private fun splitAroundCutouts(full: PixelRect, cutouts: List<PixelRect>, minLength: Int): List<PixelRect> {
        val blocked = cutouts
            .filter { it.intersects(full) }
            .map { max(it.top, full.top) to min(it.bottom, full.bottom) }
            .sortedBy { it.first }

        val pieces = mutableListOf<PixelRect>()
        var freeFrom = full.top
        for ((blockedTop, blockedBottom) in blocked) {
            if (blockedTop > freeFrom) pieces += full.copy(top = freeFrom, bottom = blockedTop)
            freeFrom = max(freeFrom, blockedBottom)
        }
        if (freeFrom < full.bottom) pieces += full.copy(top = freeFrom)

        return pieces.filter { it.height >= minLength }
    }
}
