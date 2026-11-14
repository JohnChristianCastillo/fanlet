package dev.johncastles.fanlet

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import dev.johncastles.fanlet.apps.AppInfoCache
import dev.johncastles.fanlet.config.ConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** Owns the objects shared by the settings screens and the overlay service. */
class FanletApplication : Application() {

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val configRepository: ConfigRepository by lazy {
        ConfigRepository(file = File(filesDir, CONFIG_FILE_NAME), ioScope = ioScope)
    }

    val appInfoCache: AppInfoCache by lazy { AppInfoCache(packageManager, ioScope) }

    override fun onCreate() {
        super.onCreate()
        watchPackageChanges()
    }

    /**
     * Refreshes cached app info when an app is installed, updated or removed, so the pie
     * and the editor show uninstalled apps as missing. Only apps visible to Fanlet (those
     * with a launcher icon) are reported.
     */
    private fun watchPackageChanges() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val packageName = intent.data?.schemeSpecificPart ?: return
                appInfoCache.refresh(packageName)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        // Protected system broadcasts still reach a receiver that is not exported.
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private companion object {
        const val CONFIG_FILE_NAME = "config.json"
    }
}

/** The process-wide [ConfigRepository]. */
val Context.configRepository: ConfigRepository
    get() = (applicationContext as FanletApplication).configRepository

/** The process-wide [AppInfoCache]. */
val Context.appInfoCache: AppInfoCache
    get() = (applicationContext as FanletApplication).appInfoCache
