package dev.johncastles.fanlet.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import dev.johncastles.fanlet.R
import dev.johncastles.fanlet.apps.AppInfoCache
import dev.johncastles.fanlet.config.BarConfig
import dev.johncastles.fanlet.config.PieAppearance
import dev.johncastles.fanlet.config.PieLayer
import dev.johncastles.fanlet.geometry.PieGeometry
import dev.johncastles.fanlet.geometry.PieLayout
import dev.johncastles.fanlet.geometry.ScreenInfo

/**
 * Runs one pie gesture at a time: shows the pie when a bar is pressed, follows the finger
 * (reported by the bar window, which keeps the whole touch stream), and launches the
 * chosen app on release.
 *
 * The pie lives in its own full-screen overlay window that is display-only: not touchable
 * and not focusable, so it never takes touches from anything.
 *
 * All methods must be called on the main thread.
 */
class PieController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val haptics: Haptics,
    private val appInfo: AppInfoCache,
    private val launchApp: (packageName: String) -> Unit,
) {

    private class Session(
        val layout: PieLayout,
        val layers: List<PieLayer>,
        val selection: PieSelection,
        val hapticsEnabled: Boolean,
        val view: PieView,
    )

    private val handler = Handler(Looper.getMainLooper())
    private var session: Session? = null

    /** Pie windows on screen: the active one plus any still animating closed. */
    private val pieViews = mutableListOf<PieView>()

    private val holdCheck = Runnable {
        val current = session ?: return@Runnable
        apply(current, current.selection.checkHold(SystemClock.uptimeMillis()))
    }

    /** True while a gesture is in progress. */
    val isActive: Boolean get() = session != null

    /**
     * Starts a gesture: a finger went down on [bar] at screen position ([x], [y]).
     * Does nothing if there are no [layers] (the config keeps only layers with apps).
     *
     * @param timeMs event time on the [SystemClock.uptimeMillis] clock.
     */
    fun start(
        bar: BarConfig,
        layers: List<PieLayer>,
        appearance: PieAppearance,
        screen: ScreenInfo,
        x: Float,
        y: Float,
        timeMs: Long,
    ) {
        dismiss()
        if (layers.isEmpty() || layers.any { it.slices.isEmpty() }) return

        val outerRadius = PieGeometry.outerRadius(screen, appearance.sizeFraction)
        val layout = PieGeometry.layout(
            screen = screen,
            edge = bar.edge,
            touchY = y,
            cancelRadius = PieGeometry.cancelRadius(screen, bar.thicknessDp, outerRadius),
            outerRadius = outerRadius,
            slotCounts = layers.map { it.slices.size },
        )
        val view = PieView(context).apply {
            pieLayout = layout
            iconSizeFraction = appearance.iconSizeFraction
            secondaryIconScale = appearance.secondaryIconScale
            dimBackground = appearance.dimBackground
            rings = layers.map { layer ->
                layer.slices.map { slot ->
                    PieView.Item(
                        primary = entryFor(slot.primaryPackage),
                        secondary = slot.secondaryPackage?.let(::entryFor),
                    )
                }
            }
        }
        try {
            windowManager.addView(view, pieWindowParams(screen))
        } catch (e: WindowManager.BadTokenException) {
            Log.w(TAG, "Pie window refused", e)
            return
        } catch (e: SecurityException) {
            Log.w(TAG, "Pie window refused", e)
            return
        }
        pieViews += view
        view.animateOpen()

        session = Session(
            layout = layout,
            layers = layers,
            selection = PieSelection(appearance.holdMs.toLong()) { slot ->
                layers[slot.layer].slices[slot.index].secondaryPackage != null
            },
            hapticsEnabled = bar.hapticsEnabled,
            view = view,
        )
        move(x, y, timeMs)
    }

    /** The finger moved to screen position ([x], [y]). */
    fun move(x: Float, y: Float, timeMs: Long) {
        val current = session ?: return
        val slot = PieGeometry.slotAt(current.layout, x, y)
        apply(current, current.selection.onPointer(slot, timeMs))
        scheduleHoldCheck(current)
    }

    /** The finger lifted at ([x], [y]): launch the shown app, or nothing in the cancel zone. */
    fun release(x: Float, y: Float, timeMs: Long) {
        val current = session ?: return
        move(x, y, timeMs)
        val result = current.selection.result()
        close()
        if (result != null) {
            val slot = current.layers[result.slot.layer].slices[result.slot.index]
            val packageName = if (result.secondary) slot.secondaryPackage else slot.primaryPackage
            packageName?.let(launchApp)
        }
    }

    /** The gesture was cancelled by the system: close without launching. */
    fun cancel() {
        if (session != null) close()
    }

    /** Removes every pie window at once (service stopping or pausing). */
    fun dismiss() {
        handler.removeCallbacks(holdCheck)
        session = null
        removeAllPies()
    }

    private fun apply(current: Session, change: PieSelection.Change?) {
        when (change) {
            null -> Unit
            is PieSelection.Change.Highlighted -> {
                current.view.showingSecondary = false
                current.view.highlightedSlot = change.slot
                if (change.slot != null && current.hapticsEnabled) haptics.sliceChanged()
            }
            is PieSelection.Change.SwitchedToSecondary -> {
                current.view.showingSecondary = true
                if (current.hapticsEnabled) haptics.switchedToSecondary()
            }
        }
    }

    /** Arms a timer for the hold switch, so it fires even when the finger is perfectly still. */
    private fun scheduleHoldCheck(current: Session) {
        handler.removeCallbacks(holdCheck)
        val deadline = current.selection.holdDeadlineMs() ?: return
        handler.postAtTime(holdCheck, deadline)
    }

    private fun close() {
        handler.removeCallbacks(holdCheck)
        val view = session?.view ?: return
        session = null
        view.animateClose { removePie(view) }
    }

    private fun removePie(view: PieView) {
        if (!pieViews.remove(view)) return
        view.stopAnimation()
        windowManager.removeView(view)
    }

    private fun removeAllPies() {
        pieViews.toList().forEach(::removePie)
    }

    /**
     * What a slice shows for [packageName]. An uninstalled app keeps its slice but shows a
     * placeholder; releasing on it does nothing, because it has no launch intent.
     */
    private fun entryFor(packageName: String): PieView.Entry {
        if (appInfo.isMissing(packageName)) {
            return PieView.Entry(label = context.getString(R.string.app_not_installed), icon = null)
        }
        val info = appInfo.get(packageName)
        return PieView.Entry(label = info?.label ?: packageName, icon = info?.icon)
    }

    @SuppressLint("RtlHardcoded") // Absolute screen position, not layout-direction relative.
    private fun pieWindowParams(screen: ScreenInfo): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            screen.widthPx,
            screen.heightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = 0
            y = 0
            title = "Fanlet pie"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                fitInsetsTypes = 0
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

    private companion object {
        const val TAG = "PieController"
    }
}
