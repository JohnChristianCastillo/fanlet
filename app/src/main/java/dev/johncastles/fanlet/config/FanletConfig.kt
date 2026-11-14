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

/** How the pie looks. Shared by all bars. */
@Serializable
data class PieAppearance(
    /** Outer radius as a fraction of the screen's shorter side; 1.0 reaches across a portrait screen. */
    val sizeFraction: Float = DEFAULT_SIZE_FRACTION,
    /** Size of a slice's main app icon. Icons shrink when a slice is too small for them. */
    val iconSizeDp: Int = DEFAULT_ICON_SIZE_DP,
    /** Size of a slice's secondary app icon relative to its main app icon. */
    val secondaryIconScale: Float = DEFAULT_SECONDARY_ICON_SCALE,
    /** Darken the rest of the screen while the pie is open. */
    val dimBackground: Boolean = true,
    /** How long the finger must stay on a slice to switch to its secondary app. */
    val holdMs: Int = DEFAULT_HOLD_MS,
) {
    /** A copy with every value inside its allowed range. */
    fun normalized(): PieAppearance = copy(
        sizeFraction = sizeFraction.coerceInOrDefault(MIN_SIZE_FRACTION, MAX_SIZE_FRACTION, DEFAULT_SIZE_FRACTION),
        iconSizeDp = iconSizeDp.coerceIn(MIN_ICON_SIZE_DP, MAX_ICON_SIZE_DP),
        secondaryIconScale = secondaryIconScale.coerceInOrDefault(
            MIN_SECONDARY_ICON_SCALE, MAX_SECONDARY_ICON_SCALE, DEFAULT_SECONDARY_ICON_SCALE,
        ),
        holdMs = holdMs.coerceIn(MIN_HOLD_MS, MAX_HOLD_MS),
    )

    companion object {
        const val MIN_HOLD_MS = 250
        const val MAX_HOLD_MS = 1000
        const val DEFAULT_HOLD_MS = 500

        const val MIN_SIZE_FRACTION = 0.2f
        const val MAX_SIZE_FRACTION = 1.0f
        const val DEFAULT_SIZE_FRACTION = 0.35f

        const val MIN_ICON_SIZE_DP = 24
        const val MAX_ICON_SIZE_DP = 96
        const val DEFAULT_ICON_SIZE_DP = 40

        const val MIN_SECONDARY_ICON_SCALE = 0.3f
        const val MAX_SECONDARY_ICON_SCALE = 1.0f
        const val DEFAULT_SECONDARY_ICON_SCALE = 0.6f
    }
}

/** Everything the user can configure. Stored as JSON; see [ConfigCodec]. */
@Serializable
data class FanletConfig(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    /** Draw the bars in colour so they can be positioned. When false they are invisible but still touchable. */
    val showBars: Boolean = false,
    val pie: PieAppearance = PieAppearance(),
    /**
     * The pie's apps, top slice first, shared by every bar (one bar per hand). At most
     * [MAX_SLICES]; empty means touching a bar only vibrates.
     */
    val slices: List<SliceConfig> = emptyList(),
    val bars: List<BarConfig> = defaultBars(),
) {
    /**
     * A copy with every value inside its allowed range: default bars if none are left,
     * slices without an app dropped, blank secondary apps removed, at most [MAX_SLICES].
     */
    fun normalized(): FanletConfig = copy(
        pie = pie.normalized(),
        slices = slices
            .filter { it.primaryPackage.isNotBlank() }
            .map { if (it.secondaryPackage.isNullOrBlank()) it.copy(secondaryPackage = null) else it }
            .take(MAX_SLICES),
        bars = if (bars.isEmpty()) defaultBars() else bars.map { it.normalized() },
    )

    /** A copy with the bar that has [updated]'s id replaced by [updated]. */
    fun withBar(updated: BarConfig): FanletConfig =
        copy(bars = bars.map { if (it.id == updated.id) updated else it })

    // ---- Slice editing. Each returns a new config; out-of-range indexes change nothing.

    val canAddSlice: Boolean get() = slices.size < MAX_SLICES

    /** Adds a slice for [primaryPackage] at the bottom of the pie, if there is room. */
    fun withSliceAdded(primaryPackage: String): FanletConfig =
        if (canAddSlice) copy(slices = slices + SliceConfig(primaryPackage)) else this

    fun withSliceRemoved(index: Int): FanletConfig =
        if (index in slices.indices) copy(slices = slices.filterIndexed { i, _ -> i != index }) else this

    /** Moves the slice at [from] so that it ends up at [to]. */
    fun withSliceMoved(from: Int, to: Int): FanletConfig {
        if (from !in slices.indices || to !in slices.indices || from == to) return this
        val reordered = slices.toMutableList()
        reordered.add(to, reordered.removeAt(from))
        return copy(slices = reordered)
    }

    fun withSlicePrimary(index: Int, packageName: String): FanletConfig =
        withSlice(index) { it.copy(primaryPackage = packageName) }

    /** Sets the slice's secondary app, or removes it when [packageName] is null. */
    fun withSliceSecondary(index: Int, packageName: String?): FanletConfig =
        withSlice(index) { it.copy(secondaryPackage = packageName) }

    private fun withSlice(index: Int, transform: (SliceConfig) -> SliceConfig): FanletConfig =
        if (index in slices.indices) {
            copy(slices = slices.mapIndexed { i, slice -> if (i == index) transform(slice) else slice })
        } else {
            this
        }

    companion object {
        /** 1: a slice list per bar. 2: one slice list shared by all bars. */
        const val CURRENT_SCHEMA_VERSION = 2

        const val MAX_SLICES = 8

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
