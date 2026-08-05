package com.vitalypr.daylog.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.res.stringResource
import com.vitalypr.daylog.R
import com.vitalypr.daylog.ui.theme.DayLogTheme
import com.vitalypr.daylog.ui.today.TodayScreen

private enum class Tab(val labelRes: Int) {
    TODAY(R.string.tab_today),
    HISTORY(R.string.tab_history),
    STATS(R.string.tab_stats),
    SETTINGS(R.string.tab_settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayLogRoot() {
    DayLogTheme {
        var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = {
                                val icon = when (t) {
                                    Tab.TODAY -> Icons.Default.Home
                                    Tab.HISTORY -> Icons.Default.DateRange
                                    Tab.STATS -> Icons.Default.Info
                                    Tab.SETTINGS -> Icons.Default.Settings
                                }
                                Icon(icon, contentDescription = stringResource(t.labelRes))
                            },
                            label = { Text(stringResource(t.labelRes)) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (tab) {
                    Tab.TODAY -> TodayScreen()
                    // Phase 5/7 replace these placeholders with the real screens.
                    else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.coming_soon), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
