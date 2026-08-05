package com.vitalypr.daylog.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vitalypr.daylog.R
import com.vitalypr.daylog.ui.history.HistoryScreen
import com.vitalypr.daylog.ui.stats.StatsScreen
import com.vitalypr.daylog.ui.theme.DayLogTheme
import com.vitalypr.daylog.ui.today.TodayScreen

private data class TopDestination(val route: String, val labelRes: Int, val icon: ImageVector)

private val topDestinations = listOf(
    TopDestination("today", R.string.tab_today, Icons.Default.Home),
    TopDestination("history", R.string.tab_history, Icons.Default.DateRange),
    TopDestination("stats", R.string.tab_stats, Icons.Default.Info),
    TopDestination("settings", R.string.tab_settings, Icons.Default.Settings),
)

@Composable
fun DayLogRoot() {
    DayLogTheme {
        val navController = rememberNavController()
        val backStack by navController.currentBackStackEntryAsState()
        val currentRoute = backStack?.destination?.route

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar {
                    topDestinations.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = stringResource(dest.labelRes)) },
                            label = { Text(stringResource(dest.labelRes)) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                NavHost(navController, startDestination = "today") {
                    composable("today") { TodayScreen() }
                    composable("history") {
                        HistoryScreen(onOpenDay = { date -> navController.navigate("day/$date") })
                    }
                    // Day editor: same screen/ViewModel bound to a past date via the nav arg.
                    composable("day/{date}") { TodayScreen() }
                    composable("stats") { StatsScreen() }
                    composable("settings") { com.vitalypr.daylog.ui.settings.SettingsScreen() }
                }
            }
        }
    }
}
