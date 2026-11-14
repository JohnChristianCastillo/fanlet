package dev.johncastles.fanlet.overlay

import android.content.Context
import android.view.MotionEvent
import android.view.View

/**
 * The touchable area of one edge bar. It draws nothing itself (its background colour
 * decides whether it is visible) and consumes every touch that starts inside it, so
 * touches never fall through to the app underneath.
 *
 * Android keeps sending a gesture's events to the window where it started, even after
 * the finger leaves that window, so this view sees the whole drag across the pie.
 */
class EdgeBarView(context: Context) : View(context) {

    /** Receives every touch event of gestures that start on this bar. */
    var onTouch: ((MotionEvent) -> Unit)? = null

    override fun onTouchEvent(event: MotionEvent): Boolean {
        onTouch?.invoke(event)
        if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
