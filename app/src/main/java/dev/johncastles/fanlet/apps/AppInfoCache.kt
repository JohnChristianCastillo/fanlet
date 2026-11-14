package dev.johncastles.fanlet.apps

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** Label and icon of an app's launcher entry. */
class AppInfo(val label: CharSequence, val icon: Drawable)

/**
 * Loads app labels and icons on a background thread and keeps them, so the pie can open
 * instantly. [get] never blocks: it returns null for anything not loaded yet.
 *
 * @param ioScope scope for loading; should use a background dispatcher.
 */
class AppInfoCache(private val packageManager: PackageManager, private val ioScope: CoroutineScope) {

    private val cache = ConcurrentHashMap<String, AppInfo>()

    fun get(packageName: String): AppInfo? = cache[packageName]

    /** Starts loading every package in [packageNames] that is not cached yet. */
    fun prefetch(packageNames: Collection<String>) {
        val missing = packageNames.filter { !cache.containsKey(it) }.distinct()
        if (missing.isEmpty()) return
        ioScope.launch {
            for (packageName in missing) {
                load(packageName)?.let { cache[packageName] = it }
            }
        }
    }

    private fun load(packageName: String): AppInfo? {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        val activity = packageManager.resolveActivity(launchIntent, 0) ?: return null
        return AppInfo(activity.loadLabel(packageManager), activity.loadIcon(packageManager))
    }
}
