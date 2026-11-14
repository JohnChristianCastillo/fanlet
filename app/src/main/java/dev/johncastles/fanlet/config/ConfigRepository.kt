package dev.johncastles.fanlet.config

import android.util.AtomicFile
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

/**
 * Holds the current [FanletConfig] and stores it as JSON in [file].
 *
 * There is one instance per process (see `FanletApplication`), shared by the settings
 * screen and the overlay service, so a change made in one shows up in the other at once.
 *
 * @param ioScope scope for file writes; should use a background dispatcher.
 */
class ConfigRepository(file: File, ioScope: CoroutineScope) {

    private val atomicFile = AtomicFile(file)

    // Loaded synchronously: the file is a few hundred bytes, and starting from the stored
    // value avoids briefly showing default bars or overwriting the file before it is read.
    private val state = MutableStateFlow(load())

    /** The current config. Updates immediately on every [update]. */
    val config: StateFlow<FanletConfig> = state.asStateFlow()

    init {
        // Save after changes settle, so dragging a slider does not write the file on every
        // frame. collectLatest cancels the pending delay when a newer value arrives.
        ioScope.launch {
            state.drop(1).collectLatest { latest ->
                delay(SAVE_DELAY_MS)
                write(latest)
            }
        }
    }

    /** Applies [transform] to the current config. Out-of-range values are clamped. */
    fun update(transform: (FanletConfig) -> FanletConfig) {
        state.update { current -> transform(current).normalized() }
    }

    private fun load(): FanletConfig {
        if (!atomicFile.baseFile.exists()) return FanletConfig.DEFAULT
        return try {
            ConfigCodec.decode(atomicFile.readFully().decodeToString())
                ?: FanletConfig.DEFAULT.also { Log.w(TAG, "Stored config is invalid; using defaults") }
        } catch (e: IOException) {
            Log.w(TAG, "Could not read config; using defaults", e)
            FanletConfig.DEFAULT
        }
    }

    private fun write(config: FanletConfig) {
        val stream = try {
            atomicFile.startWrite()
        } catch (e: IOException) {
            Log.w(TAG, "Could not save config", e)
            return
        }
        try {
            stream.write(ConfigCodec.encode(config).encodeToByteArray())
            atomicFile.finishWrite(stream)
        } catch (e: IOException) {
            atomicFile.failWrite(stream)
            Log.w(TAG, "Could not save config", e)
        }
    }

    private companion object {
        const val TAG = "ConfigRepository"
        const val SAVE_DELAY_MS = 300L
    }
}
