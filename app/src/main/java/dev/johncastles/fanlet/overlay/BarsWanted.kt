package dev.johncastles.fanlet.overlay

import android.content.Context
import androidx.core.content.edit

/**
 * Whether the user wants the edge bars on, remembered on the device so they can come
 * back after a reboot or update. Kept in private preferences, not in the exported
 * config: it describes this phone's state, not the user's settings.
 */
object BarsWanted {

    private const val PREFERENCES = "service"
    private const val KEY_WANTED = "bars_wanted"

    fun get(context: Context): Boolean =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getBoolean(KEY_WANTED, false)

    fun set(context: Context, wanted: Boolean) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit { putBoolean(KEY_WANTED, wanted) }
    }
}
