package dev.johncastles.fanlet.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Turns the edge bars back on after a reboot (`BOOT_COMPLETED`) or after Fanlet was
 * updated or reinstalled over itself (`MY_PACKAGE_REPLACED`), if the user had them on.
 * Both are protected system broadcasts, so the receiver does not need to be exported.
 *
 * Android does not deliver these to an app the user force-stopped; then the bars stay
 * off until Fanlet is opened again, which is intended.
 */
class StartupReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val start = StartupDecision.shouldStart(
            wanted = BarsWanted.get(context),
            overlayAllowed = Settings.canDrawOverlays(context),
        )
        if (start) OverlayService.start(context)
    }
}
