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
        if (!start) return

        // After a reboot Samsung can delay the normal start by minutes, so offer a one-tap
        // start straight away. It disappears by itself as soon as the bars are on, whether
        // the user tapped it or the delayed start arrived first: the service cancels it
        // when it starts. Posted before the start so the service's cancel always comes after.
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) OverlayNotifications.showRestart(context)
        OverlayService.start(context)
    }
}
