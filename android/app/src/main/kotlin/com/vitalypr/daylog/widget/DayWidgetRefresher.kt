package com.vitalypr.daylog.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pokes the widget after something changes an arrival/departure. Every write
 * path calls this: the widget's own taps, geofence writes, and the app itself
 * (MainActivity.onStop, which covers all in-app edits in one place).
 */
@Singleton
class DayWidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun refresh() {
        val ids = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, DayWidgetProvider::class.java))
        if (ids.isEmpty()) return // nothing installed on the home screen — do no work
        context.sendBroadcast(
            Intent(context, DayWidgetProvider::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
        )
    }
}
