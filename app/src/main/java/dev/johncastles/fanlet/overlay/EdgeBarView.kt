package dev.johncastles.fanlet.overlay

import android.content.Context
import android.view.MotionEvent
import android.view.View

/**
 * The touchable area of one edge bar. It draws nothing itself (its background colour
 * decides whether it is visible) and consumes every touch that starts inside it, so
 * touches never fall through to the app underneath.
 */
class EdgeBarView(context: Context) : View(context) {

    /** Called when a finger first touches the bar. */
    var onTouchDown: (() -> Unit)? = null

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> onTouchDown?.invoke()
            MotionEvent.ACTION_UP -> performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
