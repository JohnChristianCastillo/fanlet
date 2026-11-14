package dev.johncastles.fanlet.config

import kotlinx.serialization.Serializable

/** The screen edge a bar sits on. */
@Serializable
enum class Edge { LEFT, RIGHT }

/** One pie slice: the app it opens, and an optional second app reached by holding still. */
@Serializable
data class SliceConfig(
    val primaryPackage: String,
    val secondaryPackage: String? = null,
)

/**
 * One activation bar. Length and position are fractions of the usable screen edge, so
 * the bar keeps its proportions when the screen rotates.
 */
@Serializable
data class BarConfig(
    val id: String,
    val edge: Edge,
    val enabled: Boolean = true,
    val thicknessDp: Int = DEFAULT_THICKNESS_DP,
    /** Bar length as a fraction of the edge, [MIN_LENGTH_FRACTION] to [MAX_LENGTH_FRACTION]. */
    val lengthFraction: Float = DEFAULT_LENGTH_FRACTION,
    /** Where the bar's centre sits along the edge: 0 = top, 1 = bottom. */
    val centerFraction: Float = DEFAULT_CENTER_FRACTION,
    val hapticsEnabled: Boolean = true,
    /** Pie slices for this bar (configured in a later version; empty for now). */
    val slices: List<SliceConfig> = emptyList(),
) {
    /** A copy with every value inside its allowed range. */
    fun normalized(): BarConfig = copy(
        thicknessDp = thicknessDp.coerceIn(MIN_THICKNESS_DP, MAX_THICKNESS_DP),
        lengthFraction = lengthFraction.coerceInOrDefault(MIN_LENGTH_FRACTION, MAX_LENGTH_FRACTION, DEFAULT_LENGTH_FRACTION),
        centerFraction = centerFraction.coerceInOrDefault(0f, 1f, DEFAULT_CENTER_FRACTION),
    )

    companion object {
        const val MIN_THICKNESS_DP = 8
        const val MAX_THICKNESS_DP = 64
        const val DEFAULT_THICKNESS_DP = 20

        const val MIN_LENGTH_FRACTION = 0.1f
        const val MAX_LENGTH_FRACTION = 1.0f
        const val DEFAULT_LENGTH_FRACTION = 0.35f

        const val DEFAULT_CENTER_FRACTION = 0.5f
    }
}

/** Everything the user can configure. Stored as JSON; see [ConfigCodec]. */
@Serializable
data class FanletConfig(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    /** Draw the bars in colour so they can be positioned. When false they are invisible but still touchable. */
    val showBars: Boolean = false,
    val bars: List<BarConfig> = defaultBars(),
) {
    /** A copy with every value inside its allowed range, and default bars if none are left. */
    fun normalized(): FanletConfig = copy(
        bars = if (bars.isEmpty()) defaultBars() else bars.map { it.normalized() },
    )

    /** A copy with the bar that has [updated]'s id replaced by [updated]. */
    fun withBar(updated: BarConfig): FanletConfig =
        copy(bars = bars.map { if (it.id == updated.id) updated else it })

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1

        val DEFAULT = FanletConfig()

        fun defaultBars(): List<BarConfig> = listOf(
            BarConfig(id = "left", edge = Edge.LEFT),
            BarConfig(id = "right", edge = Edge.RIGHT),
        )
    }
}

/** Like [coerceIn], but NaN (which coerceIn lets through) becomes [default]. */
private fun Float.coerceInOrDefault(min: Float, max: Float, default: Float): Float =
    if (isNaN()) default else coerceIn(min, max)
