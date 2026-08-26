package me.rerere.readstack.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import me.rerere.readstack.R
import me.rerere.readstack.ui.browse.BrowseScreen
import me.rerere.readstack.ui.detail.DetailScreen
import me.rerere.readstack.ui.library.LibraryScreen
import me.rerere.readstack.ui.reader.ReaderScreen
import me.rerere.readstack.ui.search.SearchScreen
import me.rerere.readstack.ui.settings.SettingsScreen
import kotlin.reflect.KClass

private data class TopLevelTab(
    val route: Route,
    val routeClass: KClass<out Route>,
    val labelRes: Int,
    val icon: ImageVector,
    val iconSelected: ImageVector,
)

private val tabs = listOf(
    TopLevelTab(Route.Library, Route.Library::class, R.string.nav_library, Icons.Outlined.AutoStories, Icons.Rounded.AutoStories),
    TopLevelTab(Route.Browse,  Route.Browse::class,  R.string.nav_browse,  Icons.Outlined.Explore,     Icons.Rounded.Explore),
    TopLevelTab(Route.Search,  Route.Search::class,  R.string.nav_search,  Icons.Outlined.Search,      Icons.Rounded.Search),
    TopLevelTab(Route.Settings, Route.Settings::class, R.string.nav_settings, Icons.Outlined.Settings, Icons.Rounded.Settings),
)

@Composable
fun ReadStackApp() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination

    val suiteType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(
        currentWindowAdaptiveInfo()
    )

    NavigationSuiteScaffold(
        layoutType = suiteType,
        navigationSuiteItems = {
            tabs.forEach { tab ->
                val selected = current?.isOn(tab.routeClass) == true
                item(
                    selected = selected,
                    onClick = { nav.navigateTopLevel(tab.route) },
                    icon = { Icon(if (selected) tab.iconSelected else tab.icon, null) },
                    label = { Text(stringResource(tab.labelRes)) },
                )
            }
        },
    ) {
        NavHost(
            navController = nav,
            startDestination = Route.Library,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable<Route.Library> { LibraryScreen(nav) }
            composable<Route.Browse> { BrowseScreen(nav) }
            composable<Route.Search> { SearchScreen(nav) }
            composable<Route.Settings> { SettingsScreen(nav) }
            composable<Route.SourceDetail> { entry ->
                val args = entry.toRoute<Route.SourceDetail>()
                BrowseScreen(nav, sourceOverride = args.source)
            }
            composable<Route.Detail> { entry ->
                val args = entry.toRoute<Route.Detail>()
                DetailScreen(nav, args)
            }
            composable<Route.Reader> { entry ->
                val args = entry.toRoute<Route.Reader>()
                ReaderScreen(nav, args)
            }
        }
    }
}

private fun NavDestination.isOn(route: KClass<out Route>): Boolean =
    hierarchy.any { hasRoute(route) }

private fun NavHostController.navigateTopLevel(route: Route) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
