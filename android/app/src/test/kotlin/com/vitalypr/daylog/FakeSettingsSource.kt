package com.vitalypr.daylog

import com.vitalypr.daylog.data.settings.Settings
import com.vitalypr.daylog.data.settings.SettingsSource
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory settings for tests — avoids the process-wide DataStore singleton. */
class FakeSettingsSource(initial: Settings = Settings()) : SettingsSource {
    override val settings = MutableStateFlow(initial)
    fun update(transform: (Settings) -> Settings) { settings.value = transform(settings.value) }
}
