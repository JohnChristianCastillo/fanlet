package dev.johncastles.fanlet.apps

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build

/** An app that has a launcher icon. */
data class LaunchableApp(val packageName: String, val label: String)

object LaunchableApps {

    /**
     * Every app with a launcher icon, sorted by label. Visible to Fanlet through the
     * `<queries>` element in the manifest (launcher intents only), not QUERY_ALL_PACKAGES.
     * Talks to the package manager: call it off the main thread.
     */
    fun query(packageManager: PackageManager): List<LaunchableApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }
        return activities
            .map { LaunchableApp(it.activityInfo.packageName, it.loadLabel(packageManager).toString()) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}
