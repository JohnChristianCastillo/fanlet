package dev.johncastles.fanlet.overlay

/**
 * Which pie slice is highlighted, and whether it has switched to its secondary app.
 *
 * Holding the finger on one slice for [holdMs] switches that slice to its secondary app,
 * if it has one. Leaving the slice resets it to its primary app. Pure Kotlin: the caller
 * passes the current time (in milliseconds, any monotonic clock), so tests use fake times.
 *
 * @param hasSecondary whether the slice with the given index has a secondary app.
 */
class PieSelection(
    private val holdMs: Long,
    private val hasSecondary: (slice: Int) -> Boolean,
) {

    sealed interface Change {
        /** The highlighted slice changed; null means nothing is highlighted. */
        data class Highlighted(val slice: Int?) : Change

        /** The finger stayed on [slice] long enough; it now shows its secondary app. */
        data class SwitchedToSecondary(val slice: Int) : Change
    }

    /** What releasing the finger now would launch. */
    data class Result(val slice: Int, val secondary: Boolean)

    var highlightedSlice: Int? = null
        private set

    var showingSecondary: Boolean = false
        private set

    private var highlightedSinceMs = 0L

    /** Reports the slice under the finger (null if none). Returns what changed, if anything. */
    fun onPointer(slice: Int?, nowMs: Long): Change? {
        if (slice == highlightedSlice) return checkHold(nowMs)
        highlightedSlice = slice
        showingSecondary = false
        highlightedSinceMs = nowMs
        return Change.Highlighted(slice)
    }

    /** Call when time passes without pointer movement (from a timer set to [holdDeadlineMs]). */
    fun checkHold(nowMs: Long): Change? {
        val slice = highlightedSlice ?: return null
        val deadline = holdDeadlineMs() ?: return null
        if (nowMs < deadline) return null
        showingSecondary = true
        return Change.SwitchedToSecondary(slice)
    }

    /** When the switch to the secondary app is due, or null if no switch is pending. */
    fun holdDeadlineMs(): Long? {
        val slice = highlightedSlice ?: return null
        if (showingSecondary || !hasSecondary(slice)) return null
        return highlightedSinceMs + holdMs
    }

    /** The slice a release would launch and whether it is the secondary app; null cancels. */
    fun result(): Result? = highlightedSlice?.let { Result(it, showingSecondary) }
}
