package com.nuvio.app

import androidx.compose.runtime.Composable
import com.nuvio.app.features.magnet.MagnetLink
import com.nuvio.app.features.magnet.MagnetScreen
import com.nuvio.app.features.magnet.buildMagnetPlayerLaunch
import com.nuvio.app.features.player.PlayerLaunchStore
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.navigation.DetailRoute
import com.nuvio.app.navigation.MagnetRoute
import com.nuvio.app.navigation.NuvioNavigator
import com.nuvio.app.navigation.PlayerRoute

@Composable
internal fun MagnetDestination(
    route: MagnetRoute,
    navController: NuvioNavigator,
) {
    val onBack = rememberGuardedPopBackStack(navController, route)
    MagnetScreen(
        url = route.url,
        onBack = onBack,
        onPlay = { resolution, target ->
            val magnet = MagnetLink.parse(route.url)
            if (magnet != null) {
                val launch = buildMagnetPlayerLaunch(
                    magnet = magnet,
                    resolution = resolution,
                    profileId = ProfileRepository.activeProfileId,
                    target = target,
                )
                val launchId = PlayerLaunchStore.put(launch)
                navController.navigate(PlayerRoute(launchId = launchId, title = launch.title))
            }
        },
        onOpenDetails = { match ->
            navController.navigate(
                DetailRoute(
                    type = match.kind.metaType,
                    id = match.metaId,
                    title = match.title,
                    initialSeasonNumber = match.seasonNumber,
                    initialEpisodeNumber = match.episodeNumber,
                ),
            )
        },
    )
}
