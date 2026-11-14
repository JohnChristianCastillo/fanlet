package dev.johncastles.fanlet.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.drawable.toBitmap
import dev.johncastles.fanlet.appInfoCache
import dev.johncastles.fanlet.apps.AppInfo
import dev.johncastles.fanlet.apps.AppInfoCache

/** The result of looking an app up in the shared [AppInfoCache]. */
sealed interface AppLookup {
    data object Loading : AppLookup
    data class Found(val info: AppInfo) : AppLookup

    /** No launcher entry: the app is not installed (any more). */
    data object Missing : AppLookup
}

/** Looks [packageName] up, loading it in the background; updates when apps are installed or removed. */
@Composable
fun rememberAppLookup(packageName: String): AppLookup {
    val cache = LocalContext.current.appInfoCache
    val changes by cache.changes.collectAsState()
    val lookup by produceState(initialValue = cachedLookup(cache, packageName), packageName, changes) {
        val info = cache.load(packageName)
        value = if (info != null) AppLookup.Found(info) else AppLookup.Missing
    }
    return lookup
}

private fun cachedLookup(cache: AppInfoCache, packageName: String): AppLookup {
    cache.get(packageName)?.let { return AppLookup.Found(it) }
    return if (cache.isMissing(packageName)) AppLookup.Missing else AppLookup.Loading
}

/** The name to show for an app: its label, or the package name while loading or when missing. */
fun appLabel(lookup: AppLookup, packageName: String): String = when (lookup) {
    is AppLookup.Found -> lookup.info.label.toString()
    AppLookup.Loading -> packageName
    AppLookup.Missing -> packageName
}

/** An app's icon at [size], or a plain circle while loading or when the app is missing. */
@Composable
fun AppIcon(lookup: AppLookup, size: Dp, modifier: Modifier = Modifier) {
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    // Drawables are not thread-safe, so the bitmap is made here, on the main thread.
    val bitmap = remember(lookup, sizePx) {
        (lookup as? AppLookup.Found)?.info?.icon?.toBitmap(sizePx, sizePx)?.asImageBitmap()
    }
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier.size(size))
    } else {
        Box(
            modifier = modifier
                .size(size)
                .background(MaterialTheme.colorScheme.outlineVariant, CircleShape),
        )
    }
}
