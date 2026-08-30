package com.vitalypr.daylog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vitalypr.daylog.R
import com.vitalypr.daylog.domain.model.TimeBudget
import com.vitalypr.daylog.domain.time.formatDuration
import com.vitalypr.daylog.ui.theme.InkMuted
import com.vitalypr.daylog.ui.theme.InkSecondary
import com.vitalypr.daylog.ui.theme.Line
import com.vitalypr.daylog.ui.theme.Petrol
import com.vitalypr.daylog.ui.theme.SendGreen
import com.vitalypr.daylog.ui.theme.Warn
import com.vitalypr.daylog.ui.theme.WarnTint

/**
 * How much of the worked time the logged activities account for (spec §5.1):
 * a full-width bar plus the three numbers that matter — worked, described, left.
 *
 * The bar answers "am I nearly done filling this in?" at a glance; the numbers
 * carry the exact values, so the colour is never the only source. When the
 * activities claim more time than was worked the whole thing turns red and the
 * third number names the excess — that is a contradiction to fix, not progress.
 */
@Composable
fun TimeBudgetBar(budget: TimeBudget, modifier: Modifier = Modifier) {
    val span = budget.spanMin
    // Nothing worked and nothing described yet: there is no budget to show.
    if (span == null && budget.allocatedMin == 0) return

    if (span == null) {
        Text(
            stringResource(R.string.budget_no_span, formatDuration(budget.allocatedMin)),
            style = MaterialTheme.typography.bodySmall,
            color = InkSecondary,
            modifier = modifier,
        )
        return
    }

    val over = budget.overAllocated
    val complete = budget.remainingMin == 0
    val accent = when {
        over -> Warn
        complete -> SendGreen
        else -> Petrol
    }

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat(stringResource(R.string.budget_stat_worked), formatDuration(span), InkSecondary)
            Stat(
                stringResource(R.string.budget_stat_filled),
                formatDuration(budget.allocatedMin),
                accent,
                align = TextAlign.Center,
            )
            Stat(
                if (over) stringResource(R.string.budget_stat_over) else stringResource(R.string.budget_stat_left),
                formatDuration(if (over) -budget.remainingMin!! else budget.remainingMin!!),
                if (over) Warn else InkSecondary,
                align = TextAlign.End,
            )
        }
        Box(
            Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(BAR_HEIGHT)
                .background(if (over) WarnTint else Line, RoundedCornerShape(BAR_HEIGHT / 2)),
        ) {
            // Over-allocated fills the whole track: there is no "remaining" left
            // to draw, and a bar that overflowed its own track would say nothing.
            val fraction = if (over) 1f else (budget.allocatedMin.toFloat() / span).coerceIn(0f, 1f)
            if (fraction > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(accent, RoundedCornerShape(BAR_HEIGHT / 2)),
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Stat(
    label: String,
    value: String,
    valueColor: Color,
    align: TextAlign = TextAlign.Start,
) {
    Column(Modifier.weight(1f)) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = valueColor,
            textAlign = align,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = InkMuted,
            textAlign = align,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val BAR_HEIGHT = 12.dp
