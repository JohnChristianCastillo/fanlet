package dev.johncastles.fanlet.apps

import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Label and icon of an app's launcher entry. */
class AppInfo(val label: CharSequence, val icon: Drawable) {

    /**
     * A private copy of [icon] to draw with. Drawing changes a Drawable's bounds and
     * alpha, so every user (the pie, each settings row) needs its own copy; drawing the
     * shared one is how a fading pie once left icons invisible in the settings.
     */
    fun iconCopy(resources: Resources): Drawable =
        icon.constantState?.newDrawable(resources)?.mutate() ?: icon
}

/**
 * Loads app labels and icons on a background thread and keeps them, so the pie can open
 * instantly and the settings screens can list apps. One instance per process, shared by
 * the settings screens and the overlay service.
 *
 * A package that was looked up and has no launcher entry (usually: uninstalled) is
 * remembered as missing, so the pie and the editor can mark it.
 *
 * Icons are drawn and converted on the main thread only: Drawables are not thread-safe.
 * Never draw [AppInfo.icon] directly; draw [AppInfo.iconCopy].
 *
 * @param ioScope scope for loading; should use a background dispatcher.
 */
class AppInfoCache(private val packageManager: PackageManager, private val ioScope: CoroutineScope) {

    private val cache = ConcurrentHashMap<String, AppInfo>()
    private val missing: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val changeCount = MutableStateFlow(0)

    /** Increases whenever cached data is refreshed; UI keys on it to look apps up again. */
    val changes: StateFlow<Int> = changeCount.asStateFlow()

    /** The cached info, or null if not loaded yet or missing. Never blocks. */
    fun get(packageName: String): AppInfo? = cache[packageName]

    /** True if [packageName] was looked up and has no launcher entry. */
    fun isMissing(packageName: String): Boolean = missing.contains(packageName)

    /** Starts loading every package in [packageNames] that has not been looked up yet. */
    fun prefetch(packageNames: Collection<String>) {
        val unknown = packageNames.filter { !cache.containsKey(it) && !missing.contains(it) }.distinct()
        if (unknown.isEmpty()) return
        ioScope.launch { unknown.forEach(::lookUp) }
    }

    /** The info for [packageName], loading it on a background thread if needed; null if missing. */
    suspend fun load(packageName: String): AppInfo? =
        cache[packageName] ?: withContext(Dispatchers.IO) { lookUp(packageName) }

    /** Looks [packageName] up again if it is known here (after an install, update or removal). */
    fun refresh(packageName: String) {
        if (!cache.containsKey(packageName) && !missing.contains(packageName)) return
        ioScope.launch {
            lookUp(packageName)
            changeCount.update { it + 1 }
        }
    }

    private fun lookUp(packageName: String): AppInfo? {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val activity = launchIntent?.let { packageManager.resolveActivity(it, 0) }
        if (activity == null) {
            cache.remove(packageName)
            missing.add(packageName)
            return null
        }
        val info = AppInfo(activity.loadLabel(packageManager), activity.loadIcon(packageManager))
        cache[packageName] = info
        missing.remove(packageName)
        return info
    }
}
