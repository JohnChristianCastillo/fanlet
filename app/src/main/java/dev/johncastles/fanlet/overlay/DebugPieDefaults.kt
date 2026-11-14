package dev.johncastles.fanlet.overlay

import dev.johncastles.fanlet.apps.LaunchableApp
import dev.johncastles.fanlet.config.SliceConfig

/**
 * TEMPORARY DEBUG DEFAULT. Remove once slices can be configured in the settings screen.
 *
 * Gives bars without configured slices a pie to try out: the first [PRIMARY_COUNT]
 * launchable apps in alphabetical order, with the next app as the secondary app of the
 * top slice.
 */
object DebugPieDefaults {

    private const val PRIMARY_COUNT = 5

    fun slices(apps: List<LaunchableApp>, ownPackageName: String): List<SliceConfig> {
        val candidates = apps.filter { it.packageName != ownPackageName }
        val secondary = candidates.getOrNull(PRIMARY_COUNT)?.packageName
        return candidates.take(PRIMARY_COUNT).mapIndexed { index, app ->
            SliceConfig(
                primaryPackage = app.packageName,
                secondaryPackage = if (index == 0) secondary else null,
            )
        }
    }
}
