package com.vitalypr.daylog.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.vitalypr.daylog.data.repo.DayRepository
import com.vitalypr.daylog.di.Now
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 4x1 home-screen widget (spec F1: manual entry always possible). Renders the
 * current day and never schedules work of its own — it is redrawn on system
 * update broadcasts, at midnight via DATE_CHANGED, and whenever something
 * writes an arrival/departure ([DayWidgetRefresher]).
 */
@AndroidEntryPoint
class DayWidgetProvider : AppWidgetProvider() {

    @Inject lateinit var repository: DayRepository
    @Inject @Now lateinit var now: () -> LocalDateTime

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        redraw(context, manager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // Midnight / clock changes: yesterday's ✓ marks must not linger.
        if (intent.action in DATE_ACTIONS) {
            val manager = AppWidgetManager.getInstance(context)
            redraw(context, manager, manager.getAppWidgetIds(ComponentName(context, DayWidgetProvider::class.java)))
        }
    }

    /** Resized: re-render so the text scales to the new footprint. */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        redraw(context, manager, intArrayOf(appWidgetId))
    }

    private fun redraw(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val state = WidgetState.of(repository.getDay(now().toLocalDate()))
                // Each instance may have its own size, so render per id.
                ids.forEach { id ->
                    manager.updateAppWidget(id, DayWidgetRenderer.render(context, state, heightDp(manager, id)))
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun heightDp(manager: AppWidgetManager, id: Int): Int =
        manager.getAppWidgetOptions(id)
            ?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            ?.takeIf { it > 0 }
            ?: DayWidgetRenderer.REGULAR_HEIGHT_DP

    private companion object {
        val DATE_ACTIONS = setOf(
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
