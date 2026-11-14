package dev.johncastles.fanlet.overlay

import dev.johncastles.fanlet.geometry.SlotRef

/**
 * Which pie slot is highlighted, and whether it has switched to its secondary app.
 *
 * Holding the finger on one slot for [holdMs] switches that slot to its secondary app,
 * if it has one. Leaving the slot resets it to its primary app. Pure Kotlin: the caller
 * passes the current time (in milliseconds, any monotonic clock), so tests use fake times.
 *
 * @param hasSecondary whether the given slot has a secondary app.
 */
class PieSelection(
    private val holdMs: Long,
    private val hasSecondary: (slot: SlotRef) -> Boolean,
) {

    sealed interface Change {
        /** The highlighted slot changed; null means nothing is highlighted. */
        data class Highlighted(val slot: SlotRef?) : Change

        /** The finger stayed on [slot] long enough; it now shows its secondary app. */
        data class SwitchedToSecondary(val slot: SlotRef) : Change
    }

    /** What releasing the finger now would launch. */
    data class Result(val slot: SlotRef, val secondary: Boolean)

    var highlightedSlot: SlotRef? = null
        private set

    var showingSecondary: Boolean = false
        private set

    private var highlightedSinceMs = 0L

    /** Reports the slot under the finger (null if none). Returns what changed, if anything. */
    fun onPointer(slot: SlotRef?, nowMs: Long): Change? {
        if (slot == highlightedSlot) return checkHold(nowMs)
        highlightedSlot = slot
        showingSecondary = false
        highlightedSinceMs = nowMs
        return Change.Highlighted(slot)
    }

    /** Call when time passes without pointer movement (from a timer set to [holdDeadlineMs]). */
    fun checkHold(nowMs: Long): Change? {
        val slot = highlightedSlot ?: return null
        val deadline = holdDeadlineMs() ?: return null
        if (nowMs < deadline) return null
        showingSecondary = true
        return Change.SwitchedToSecondary(slot)
    }

    /** When the switch to the secondary app is due, or null if no switch is pending. */
    fun holdDeadlineMs(): Long? {
        val slot = highlightedSlot ?: return null
        if (showingSecondary || !hasSecondary(slot)) return null
        return highlightedSinceMs + holdMs
    }

    /** The slot a release would launch and whether it is the secondary app; null cancels. */
    fun result(): Result? = highlightedSlot?.let { Result(it, showingSecondary) }
}
