package de.dritteliga.stickertracker.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.dritteliga.stickertracker.ui.album.AlbumScreen
import de.dritteliga.stickertracker.ui.overview.OverviewScreen
import de.dritteliga.stickertracker.ui.scan.ScanScreen
import de.dritteliga.stickertracker.ui.settings.ClubsScreen
import de.dritteliga.stickertracker.ui.settings.SettingsScreen
import de.dritteliga.stickertracker.ui.trade.TradeScreen

object Routes {
    const val OVERVIEW = "overview"
    const val ALBUM = "album"
    const val SCAN = "scan"
    const val TRADE = "trade"
    const val SETTINGS = "settings"
    const val CLUBS = "clubs"
    fun album(filter: String? = null, clubId: Int? = null) =
        "album?filter=${filter.orEmpty()}&club=${clubId ?: -1}"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.OVERVIEW, "Übersicht", Icons.Filled.Dashboard),
    Tab(Routes.ALBUM, "Album", Icons.Filled.GridView),
    Tab(Routes.SCAN, "Scannen", Icons.Filled.CameraAlt),
    Tab(Routes.TRADE, "Tauschen", Icons.Filled.SwapHoriz),
)

@Composable
fun AppNavigation() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route?.substringBefore('?')

    fun navigateTab(route: String) {
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = { navigateTab(tab.route) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.OVERVIEW, modifier = Modifier.padding(padding)) {
            composable(Routes.OVERVIEW) {
                OverviewScreen(
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                    onOpenAlbum = { filter, club ->
                        nav.navigate(Routes.album(filter, club)) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(
                "${Routes.ALBUM}?filter={filter}&club={club}",
                arguments = listOf(
                    navArgument("filter") { type = NavType.StringType; defaultValue = "" },
                    navArgument("club") { type = NavType.IntType; defaultValue = -1 },
                ),
            ) { entry ->
                AlbumScreen(
                    initialFilter = entry.arguments?.getString("filter")?.takeIf { it.isNotEmpty() },
                    initialClub = entry.arguments?.getInt("club")?.takeIf { it >= 0 },
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.SCAN) { ScanScreen() }
            composable(Routes.TRADE) { TradeScreen() }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { nav.popBackStack() }, onOpenClubs = { nav.navigate(Routes.CLUBS) })
            }
            composable(Routes.CLUBS) { ClubsScreen(onBack = { nav.popBackStack() }) }
        }
    }
}
