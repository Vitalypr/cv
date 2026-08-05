package com.vitalypr.daylog

import android.app.Application
import com.vitalypr.daylog.notifications.Channels
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DayLogApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Channels.ensure(this)
        // OSM tile policy requires an identifying user agent (map picker only).
        org.osmdroid.config.Configuration.getInstance().userAgentValue = packageName
    }
}
