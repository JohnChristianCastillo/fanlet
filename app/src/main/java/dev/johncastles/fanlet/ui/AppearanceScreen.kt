package dev.johncastles.fanlet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.johncastles.fanlet.R
import dev.johncastles.fanlet.appInfoCache
import dev.johncastles.fanlet.config.Edge
import dev.johncastles.fanlet.config.PieAppearance
import dev.johncastles.fanlet.config.PieLayer
import dev.johncastles.fanlet.geometry.PieGeometry
import dev.johncastles.fanlet.geometry.PieLayout
import dev.johncastles.fanlet.geometry.PixelPoint
import dev.johncastles.fanlet.geometry.ScreenInfo
import dev.johncastles.fanlet.overlay.PieView

/** How the pie looks and behaves, with a live preview of the real pie above the settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    appearance: PieAppearance,
    layers: List<PieLayer>,
    barThicknessDp: Int,
    onAppearanceChanged: (PieAppearance) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.appearance_title)) }, navigationIcon = { BackButton(onBack) })
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            PiePreview(
                appearance = appearance,
                layers = layers,
                barThicknessDp = barThicknessDp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PREVIEW_HEIGHT)
                    .clipToBounds()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                PieSettingsCard(appearance = appearance, onAppearanceChanged = onAppearanceChanged)
            }
        }
    }
}

private val PREVIEW_HEIGHT = 300.dp

/**
 * The real pie, drawn at its true size as it opens from the right edge, with the user's
 * apps. A pie larger than the preview is cut off, just as a large pie reaches off screen.
 * Without apps it shows five empty slots so the size is still visible.
 */
@Composable
private fun PiePreview(
    appearance: PieAppearance,
    layers: List<PieLayer>,
    barThicknessDp: Int,
    modifier: Modifier = Modifier,
) {
    val cache = LocalContext.current.appInfoCache
    val resources = LocalResources.current
    val density = LocalDensity.current.density
    val windowSize = LocalWindowInfo.current.containerSize
    val screen = ScreenInfo(widthPx = windowSize.width, heightPx = windowSize.height, density = density)

    // Load every icon first, so the preview shows real icons and not placeholders.
    val packages = remember(layers) {
        layers.flatMap { it.slices }.flatMap { listOfNotNull(it.primaryPackage, it.secondaryPackage) }
    }
    val iconsLoaded by produceState(initialValue = false, packages) {
        packages.forEach { cache.load(it) }
        value = true
    }

    val rings: List<List<PieView.Item>> = remember(layers, iconsLoaded) {
        fun entry(packageName: String): PieView.Entry {
            val info = cache.get(packageName)
            return PieView.Entry(label = info?.label ?: packageName, icon = info?.iconCopy(resources))
        }
        if (layers.isEmpty()) {
            listOf(List(EMPTY_PREVIEW_SLOTS) { PieView.Item(PieView.Entry("", null), null) })
        } else {
            layers.map { layer ->
                layer.slices.map { slot -> PieView.Item(entry(slot.primaryPackage), slot.secondaryPackage?.let(::entry)) }
            }
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        AndroidView(
            factory = { context -> PieView(context).apply { showOpen() } },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                val outerRadius = PieGeometry.outerRadius(screen, appearance.sizeFraction)
                view.pieLayout = PieLayout(
                    center = PixelPoint(widthPx, heightPx / 2),
                    edge = Edge.RIGHT,
                    cancelRadius = PieGeometry.cancelRadius(screen, barThicknessDp, outerRadius),
                    outerRadius = outerRadius,
                    slotCounts = rings.map { it.size },
                )
                view.rings = rings
                view.iconSizeFraction = appearance.iconSizeFraction
                view.secondaryIconScale = appearance.secondaryIconScale
                view.dimBackground = appearance.dimBackground
                view.invalidate()
            },
        )
    }
}

private const val EMPTY_PREVIEW_SLOTS = 5
