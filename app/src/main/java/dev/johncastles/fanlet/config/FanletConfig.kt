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
    /**
     * Size of a slot's main app icon as a fraction of the space the slot has (the smaller
     * of the ring's depth and the slot's width), so icons fit every ring.
     */
    val iconSizeFraction: Float = DEFAULT_ICON_SIZE_FRACTION,
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
        iconSizeFraction = iconSizeFraction.coerceInOrDefault(
            MIN_ICON_SIZE_FRACTION, MAX_ICON_SIZE_FRACTION, DEFAULT_ICON_SIZE_FRACTION,
        ),
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

        const val MIN_ICON_SIZE_FRACTION = 0.4f
        const val MAX_ICON_SIZE_FRACTION = 1.0f
        const val DEFAULT_ICON_SIZE_FRACTION = 0.7f

        const val MIN_SECONDARY_ICON_SCALE = 0.3f
        const val MAX_SECONDARY_ICON_SCALE = 1.0f
        const val DEFAULT_SECONDARY_ICON_SCALE = 0.6f
    }
}

/** One ring of the pie: its apps, in order from the top of the ring to the bottom. */
@Serializable
data class PieLayer(val slices: List<SliceConfig> = emptyList())

/** Everything the user can configure. Stored as JSON; see [ConfigCodec]. */
@Serializable
data class FanletConfig(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    /** Draw the bars in colour so they can be positioned. When false they are invisible but still touchable. */
    val showBars: Boolean = false,
    val pie: PieAppearance = PieAppearance(),
    /**
     * The pie's rings, innermost first, shared by every bar (one bar per hand). Only
     * layers with apps exist: at most [MAX_LAYERS], each with 1 to [MAX_SLOTS_PER_LAYER]
     * slots. Empty means touching a bar only vibrates.
     */
    val layers: List<PieLayer> = emptyList(),
    val bars: List<BarConfig> = defaultBars(),
) {
    /**
     * A copy with every value inside its allowed range: default bars if none are left;
     * slots without an app dropped and blank secondary apps removed; at most
     * [MAX_SLOTS_PER_LAYER] slots per layer; empty layers removed so the next one moves
     * in; at most [MAX_LAYERS] layers.
     */
    fun normalized(): FanletConfig = copy(
        pie = pie.normalized(),
        layers = layers
            .map { layer ->
                PieLayer(
                    layer.slices
                        .filter { it.primaryPackage.isNotBlank() }
                        .map { if (it.secondaryPackage.isNullOrBlank()) it.copy(secondaryPackage = null) else it }
                        .take(MAX_SLOTS_PER_LAYER)
                )
            }
            .filter { it.slices.isNotEmpty() }
            .take(MAX_LAYERS),
        bars = if (bars.isEmpty()) defaultBars() else bars.map { it.normalized() },
    )

    /** Number of apps in all layers together. */
    val slotCount: Int get() = layers.sumOf { it.slices.size }

    /** A copy with the bar that has [updated]'s id replaced by [updated]. */
    fun withBar(updated: BarConfig): FanletConfig =
        copy(bars = bars.map { if (it.id == updated.id) updated else it })

    // ---- Slot editing. Each returns a new config; out-of-range indexes change nothing.
    // [layer] is an index into [layers]; adding to index layers.size starts a new outer layer.

    /** Whether an app can be added to [layer] (or, for layers.size, as a new outer layer). */
    fun canAddSlot(layer: Int): Boolean = when {
        layer in layers.indices -> layers[layer].slices.size < MAX_SLOTS_PER_LAYER
        layer == layers.size -> layers.size < MAX_LAYERS
        else -> false
    }

    /** Adds [primaryPackage] at the end of [layer], or as a new outer layer if [layer] is layers.size. */
    fun withSlotAdded(layer: Int, primaryPackage: String): FanletConfig {
        if (!canAddSlot(layer)) return this
        val slot = SliceConfig(primaryPackage)
        return if (layer == layers.size) {
            copy(layers = layers + PieLayer(listOf(slot)))
        } else {
            withLayer(layer) { it.copy(slices = it.slices + slot) }
        }
    }

    /** Removes a slot. A layer that becomes empty is removed, so the next layer moves in. */
    fun withSlotRemoved(layer: Int, index: Int): FanletConfig {
        if (layer !in layers.indices || index !in layers[layer].slices.indices) return this
        val updated = layers.mapIndexed { i, l ->
            if (i == layer) l.copy(slices = l.slices.filterIndexed { j, _ -> j != index }) else l
        }
        return copy(layers = updated.filter { it.slices.isNotEmpty() })
    }

    /** Moves the slot at [from] so that it ends up at [to], within [layer]. */
    fun withSlotMoved(layer: Int, from: Int, to: Int): FanletConfig {
        val slices = layers.getOrNull(layer)?.slices ?: return this
        if (from !in slices.indices || to !in slices.indices || from == to) return this
        val reordered = slices.toMutableList()
        reordered.add(to, reordered.removeAt(from))
        return withLayer(layer) { it.copy(slices = reordered) }
    }

    fun withSlotPrimary(layer: Int, index: Int, packageName: String): FanletConfig =
        withSlot(layer, index) { it.copy(primaryPackage = packageName) }

    /** Sets the slot's secondary app, or removes it when [packageName] is null. */
    fun withSlotSecondary(layer: Int, index: Int, packageName: String?): FanletConfig =
        withSlot(layer, index) { it.copy(secondaryPackage = packageName) }

    private fun withLayer(layer: Int, transform: (PieLayer) -> PieLayer): FanletConfig =
        if (layer in layers.indices) {
            copy(layers = layers.mapIndexed { i, l -> if (i == layer) transform(l) else l })
        } else {
            this
        }

    private fun withSlot(layer: Int, index: Int, transform: (SliceConfig) -> SliceConfig): FanletConfig {
        val slices = layers.getOrNull(layer)?.slices ?: return this
        if (index !in slices.indices) return this
        return withLayer(layer) { l ->
            l.copy(slices = l.slices.mapIndexed { j, slot -> if (j == index) transform(slot) else slot })
        }
    }

    companion object {
        /** 1: a slice list per bar. 2: one slice list shared by all bars. 3: layers of slots. */
        const val CURRENT_SCHEMA_VERSION = 3

        const val MAX_LAYERS = 5
        const val MAX_SLOTS_PER_LAYER = 12

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
