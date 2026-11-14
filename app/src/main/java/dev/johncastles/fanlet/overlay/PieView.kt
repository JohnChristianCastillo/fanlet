package dev.johncastles.fanlet.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.withScale
import dev.johncastles.fanlet.config.Edge
import dev.johncastles.fanlet.geometry.PieGeometry
import dev.johncastles.fanlet.geometry.PieLayout
import dev.johncastles.fanlet.geometry.PixelPoint
import dev.johncastles.fanlet.geometry.SlotRef
import kotlin.math.roundToInt

/**
 * Draws the pie. Display only: it never receives touches (its window is not touchable).
 * The bar window that started the gesture feeds it the highlighted slot.
 *
 * The view fills the whole screen, so its coordinates are screen coordinates.
 */
class PieView(context: Context) : View(context) {

    /** What one slot shows: an app's label and icon (null while the icon is loading). */
    class Entry(val label: CharSequence, val icon: Drawable?)

    class Item(val primary: Entry, val secondary: Entry?)

    var pieLayout: PieLayout? = null

    /** One list per ring, innermost first, matching the layout's slot counts. */
    var rings: List<List<Item>> = emptyList()
    var dimBackground: Boolean = true

    /** Main icon size as a fraction of the space a slot has (see [PieGeometry.iconSize]). */
    var iconSizeFraction: Float = 0.7f

    /** Size of a secondary app's icon relative to the primary icon in the same slot. */
    var secondaryIconScale: Float = 0.6f

    var highlightedSlot: SlotRef? = null
        set(value) {
            field = value
            invalidate()
        }
    var showingSecondary: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    /** 0 = closed, 1 = fully open. Drives the open and close animation. */
    private var progress = 0f
    private var animator: ValueAnimator? = null

    private val density = resources.displayMetrics.density
    private val colors = if (isNightMode(context)) DARK else LIGHT

    // Allocated once; onDraw runs every frame of the animation.
    private val slicePath = Path()
    private val outerOval = RectF()
    private val innerOval = RectF()
    private val labelBackground = RectF()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, LABEL_TEXT_SP, resources.displayMetrics)
    }

    fun animateOpen() = animateTo(target = 1f, durationMs = OPEN_MS, onEnd = null)

    /** Animates closed, then calls [onEnd] (for example to remove the window). */
    fun animateClose(onEnd: () -> Unit) = animateTo(target = 0f, durationMs = CLOSE_MS, onEnd = onEnd)

    /** Stops any animation without calling its end action. */
    fun stopAnimation() {
        animator?.removeAllListeners()
        animator?.cancel()
        animator = null
    }

    private fun animateTo(target: Float, durationMs: Long, onEnd: (() -> Unit)?) {
        stopAnimation()
        animator = ValueAnimator.ofFloat(progress, target).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            if (onEnd != null) {
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) = onEnd()
                })
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val pie = pieLayout ?: return
        if (progress <= 0f) return
        val alpha = (progress * 255).roundToInt()

        if (dimBackground) {
            canvas.drawColor(withAlpha(colors.scrim, progress))
        }

        val scale = 0.85f + 0.15f * progress
        canvas.withScale(scale, scale, pie.center.x, pie.center.y) {
            drawSlices(this, pie, alpha)
            drawIcons(this, pie, alpha)
            drawLabel(this, pie, alpha)
        }
    }

    private fun isHighlighted(layer: Int, index: Int): Boolean {
        val slot = highlightedSlot ?: return false
        return slot.layer == layer && slot.index == index
    }

    /** Every ring, each with its own slot division, separated by a thin gap. */
    private fun drawSlices(canvas: Canvas, pie: PieLayout, alpha: Int) {
        val c = pie.center
        val ringGap = RING_GAP_DP * density
        for (layer in 0 until pie.ringCount) {
            val ring = pie.ring(layer)
            val inner = if (layer == 0) ring.inner else ring.inner + ringGap / 2
            val outer = if (layer == pie.ringCount - 1) ring.outer else ring.outer - ringGap / 2
            outerOval.set(c.x - outer, c.y - outer, c.x + outer, c.y + outer)
            innerOval.set(c.x - inner, c.y - inner, c.x + inner, c.y + inner)

            val sweep = PieGeometry.slotSweepDegrees(pie, layer)
            val gap = if (sweep > 0) SLICE_GAP_DEGREES else -SLICE_GAP_DEGREES
            for (index in 0 until pie.slotCounts[layer]) {
                val start = PieGeometry.slotStartDegrees(pie, SlotRef(layer, index)) + gap / 2
                val visibleSweep = sweep - gap
                slicePath.reset()
                slicePath.arcTo(outerOval, start, visibleSweep)
                slicePath.arcTo(innerOval, start + visibleSweep, -visibleSweep)
                slicePath.close()

                fillPaint.color = when {
                    !isHighlighted(layer, index) -> colors.slice
                    showingSecondary -> colors.secondaryHighlight
                    else -> colors.highlight
                }
                fillPaint.alpha = (Color.alpha(fillPaint.color) * alpha) / 255
                canvas.drawPath(slicePath, fillPaint)
            }
        }
    }

    /**
     * One icon per slot, or two when the slot has a secondary app: the app a release
     * would launch large, the other one smaller, one behind the other from the centre
     * outward. A hold swaps which of the two is large.
     */
    private fun drawIcons(canvas: Canvas, pie: PieLayout, alpha: Int) {
        val gap = ICON_GAP_DP * density
        for ((layer, items) in rings.withIndex().take(pie.ringCount)) {
            val ring = pie.ring(layer)
            val baseSize = PieGeometry.iconSize(pie, layer, iconSizeFraction)

            for ((index, item) in items.withIndex().take(pie.slotCounts[layer])) {
                val slot = SlotRef(layer, index)
                val secondary = item.secondary
                if (secondary == null) {
                    drawIcon(canvas, item.primary.icon, PieGeometry.slotPoint(pie, slot, ring.middle), baseSize, alpha)
                    continue
                }

                val swapped = isHighlighted(layer, index) && showingSecondary
                val large = if (swapped) secondary else item.primary
                val small = if (swapped) item.primary else secondary

                // Both icons and the gap between them must fit across the ring.
                var largeSize = baseSize
                var smallSize = baseSize * secondaryIconScale
                val available = ring.depth * 0.95f - gap
                if (largeSize + smallSize > available) {
                    val shrink = available / (largeSize + smallSize)
                    largeSize *= shrink
                    smallSize *= shrink
                }
                val start = ring.middle - (largeSize + gap + smallSize) / 2
                drawIcon(canvas, large.icon, PieGeometry.slotPoint(pie, slot, start + largeSize / 2), largeSize, alpha)
                drawIcon(
                    canvas, small.icon,
                    PieGeometry.slotPoint(pie, slot, start + largeSize + gap + smallSize / 2), smallSize, alpha,
                )
            }
        }
    }

    /** Draws [icon] centred on [center], or a placeholder circle while it is still loading. */
    private fun drawIcon(canvas: Canvas, icon: Drawable?, center: PixelPoint, size: Float, alpha: Int) {
        val half = size / 2
        if (icon != null) {
            icon.setBounds(
                (center.x - half).roundToInt(), (center.y - half).roundToInt(),
                (center.x + half).roundToInt(), (center.y + half).roundToInt(),
            )
            icon.alpha = alpha
            icon.draw(canvas)
        } else {
            fillPaint.color = colors.placeholder
            fillPaint.alpha = alpha
            canvas.drawCircle(center.x, center.y, half * 0.8f, fillPaint)
        }
    }

    /** The highlighted app's name, in a pill just beyond the pie's outer edge. */
    private fun drawLabel(canvas: Canvas, pie: PieLayout, alpha: Int) {
        val slot = highlightedSlot ?: return
        val item = rings.getOrNull(slot.layer)?.getOrNull(slot.index) ?: return
        val entry = if (showingSecondary) item.secondary ?: item.primary else item.primary
        val text = entry.label.toString()

        val padding = LABEL_PADDING_DP * density
        val textWidth = labelPaint.measureText(text)
        val metrics = labelPaint.fontMetrics
        val textHeight = metrics.descent - metrics.ascent
        val gapFromPie = LABEL_GAP_DP * density

        val labelWidth = textWidth + 2 * padding
        val beyondPie = when (pie.edge) {
            Edge.LEFT -> pie.center.x + pie.outerRadius + gapFromPie
            Edge.RIGHT -> pie.center.x - pie.outerRadius - gapFromPie - labelWidth
        }
        // A large pie leaves no room beside it; then the label sits on top of the pie.
        val margin = LABEL_GAP_DP * density
        val left = beyondPie.coerceIn(margin, maxOf(margin, width - labelWidth - margin))
        val top = pie.center.y - textHeight / 2 - padding
        labelBackground.set(left, top, left + textWidth + 2 * padding, top + textHeight + 2 * padding)

        fillPaint.color = colors.labelBackground
        fillPaint.alpha = (Color.alpha(colors.labelBackground) * alpha) / 255
        val corner = labelBackground.height() / 2
        canvas.drawRoundRect(labelBackground, corner, corner, fillPaint)

        labelPaint.color = colors.labelText
        labelPaint.alpha = alpha
        canvas.drawText(text, left + padding, top + padding - metrics.ascent, labelPaint)
    }

    private class Palette(
        val scrim: Int,
        val slice: Int,
        val highlight: Int,
        val secondaryHighlight: Int,
        val placeholder: Int,
        val labelBackground: Int,
        val labelText: Int,
    )

    private companion object {
        const val OPEN_MS = 120L
        const val CLOSE_MS = 100L

        const val SLICE_GAP_DEGREES = 2f
        const val RING_GAP_DP = 2f
        const val LABEL_TEXT_SP = 15f
        const val LABEL_PADDING_DP = 10f
        const val LABEL_GAP_DP = 12f
        const val ICON_GAP_DP = 4f

        val DARK = Palette(
            scrim = 0x80000000.toInt(),
            slice = 0xE6303134.toInt(),
            highlight = 0xFF00838F.toInt(),
            secondaryHighlight = 0xFFFFA000.toInt(),
            placeholder = 0xFF5F6368.toInt(),
            labelBackground = 0xE6202124.toInt(),
            labelText = 0xFFFFFFFF.toInt(),
        )

        val LIGHT = Palette(
            scrim = 0x52000000,
            slice = 0xF2FFFFFF.toInt(),
            highlight = 0xFF80DEEA.toInt(),
            secondaryHighlight = 0xFFFFCA28.toInt(),
            placeholder = 0xFFBDC1C6.toInt(),
            labelBackground = 0xF2FFFFFF.toInt(),
            labelText = 0xFF202124.toInt(),
        )

        fun isNightMode(context: Context): Boolean =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        fun withAlpha(color: Int, fraction: Float): Int =
            Color.argb((Color.alpha(color) * fraction).roundToInt(), Color.red(color), Color.green(color), Color.blue(color))
    }
}
