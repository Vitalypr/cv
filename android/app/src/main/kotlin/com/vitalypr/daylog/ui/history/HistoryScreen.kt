package com.vitalypr.daylog.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitalypr.daylog.R
import com.vitalypr.daylog.domain.time.formatDate
import com.vitalypr.daylog.domain.time.formatDuration
import com.vitalypr.daylog.domain.time.hebrewDayName
import com.vitalypr.daylog.domain.time.hebrewMonthName
import com.vitalypr.daylog.ui.components.SectionCard
import com.vitalypr.daylog.ui.components.StatusBadge
import com.vitalypr.daylog.ui.theme.InkSecondary
import java.time.LocalDate

@Composable
fun HistoryScreen(
    onOpenDay: (LocalDate) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryContent(state, viewModel::previousMonth, viewModel::nextMonth, onOpenDay)
}

@Composable
fun HistoryContent(
    state: HistoryUiState,
    onPrevMonth: () -> Unit = {},
    onNextMonth: () -> Unit = {},
    onOpenDay: (LocalDate) -> Unit = {},
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            stringResource(R.string.tab_history),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            // In RTL, "previous" points visually to the right — auto-mirrored icons handle it.
            IconButton(onClick = onPrevMonth) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
            Text(
                "${hebrewMonthName(state.month.monthValue)} ${state.month.year}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(onClick = onNextMonth) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
            }
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.days, key = { it.date.toEpochDay() }) { day ->
                SectionCard(modifier = Modifier.clickable { onOpenDay(day.date) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${hebrewDayName(day.date)} ${formatDate(day.date)}",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            if (day.summary.isNotBlank()) {
                                Text(
                                    day.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkSecondary,
                                )
                            }
                        }
                        if (day.totalMinutes > 0) {
                            Text(
                                formatDuration(day.totalMinutes),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }
                        StatusBadge(day.status)
                    }
                }
            }
        }
    }
}
