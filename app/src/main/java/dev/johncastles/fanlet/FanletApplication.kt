package dev.johncastles.fanlet

import android.app.Application
import android.content.Context
import dev.johncastles.fanlet.config.ConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** Owns the objects shared by the settings screen and the overlay service. */
class FanletApplication : Application() {

    val configRepository: ConfigRepository by lazy {
        ConfigRepository(
            file = File(filesDir, CONFIG_FILE_NAME),
            ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )
    }

    private companion object {
        const val CONFIG_FILE_NAME = "config.json"
    }
}

/** The process-wide [ConfigRepository]. */
val Context.configRepository: ConfigRepository
    get() = (applicationContext as FanletApplication).configRepository
