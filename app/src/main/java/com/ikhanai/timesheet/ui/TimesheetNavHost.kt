package com.ikhanai.timesheet.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ikhanai.timesheet.ui.dashboard.DashboardScreen
import com.ikhanai.timesheet.ui.entry.EntryScreen
import com.ikhanai.timesheet.ui.entry.EntryViewModel
import com.ikhanai.timesheet.ui.history.HistoryScreen
import com.ikhanai.timesheet.ui.projects.ProjectDetailScreen
import com.ikhanai.timesheet.ui.projects.ProjectsScreen
import com.ikhanai.timesheet.ui.settings.SettingsScreen
import java.time.LocalDate

private object Routes {
    const val DASHBOARD = "dashboard"
    const val ENTRY = "entry"
    const val ENTRY_TAB = "entry_tab"
    const val ENTRY_DAY = "entry/{${EntryViewModel.DATE_KEY}}"
    const val HISTORY = "history"
    const val PROJECTS = "projects"
    const val PROJECT = "project/{projectId}"
    const val SETTINGS = "settings"

    fun entry(date: LocalDate) = "$ENTRY/${date.toEpochDay()}"
    fun project(id: Long) = "project/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.DASHBOARD, "Dashboard", Icons.Filled.Dashboard),
    Tab(Routes.ENTRY_TAB, "Add Entry", Icons.Filled.AddCircle),
    Tab(Routes.HISTORY, "History", Icons.Filled.CalendarMonth),
    Tab(Routes.PROJECTS, "Projects", Icons.Filled.Folder),
)

@Composable
fun TimesheetNavHost(openDate: LocalDate?, onOpenDateHandled: () -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBottomBar = tabs.any { it.route == route }

    LaunchedEffect(openDate) {
        if (openDate != null) {
            nav.navigate(Routes.entry(openDate))
            onOpenDateHandled()
        }
    }

    Scaffold(
        contentWindowInsets = if (showBottomBar) WindowInsets(0) else WindowInsets.navigationBars,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { nav.switchTab(tab.route) },
                            icon = { Icon(tab.icon, null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.DASHBOARD, modifier = Modifier.padding(padding)) {
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    onOpenDay = { nav.navigate(Routes.entry(it)) },
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                )
            }
            // The "Add Entry" tab always opens today; the date can be changed on the screen.
            composable(
                Routes.ENTRY_TAB,
                arguments = listOf(
                    navArgument(EntryViewModel.DATE_KEY) {
                        type = NavType.LongType
                        defaultValue = LocalDate.now().toEpochDay()
                    },
                ),
            ) {
                EntryScreen(onBack = null)
            }
            composable(
                Routes.ENTRY_DAY,
                arguments = listOf(navArgument(EntryViewModel.DATE_KEY) { type = NavType.LongType }),
            ) {
                EntryScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.HISTORY) {
                HistoryScreen(onOpenDay = { nav.navigate(Routes.entry(it)) })
            }
            composable(Routes.PROJECTS) {
                ProjectsScreen(onOpenProject = { nav.navigate(Routes.project(it)) })
            }
            composable(
                Routes.PROJECT,
                arguments = listOf(navArgument("projectId") { type = NavType.LongType }),
            ) {
                ProjectDetailScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
