package com.vitalypr.daylog.di

import com.vitalypr.daylog.data.settings.SettingsRepository
import com.vitalypr.daylog.data.settings.SettingsSource
import com.vitalypr.daylog.reporting.DailyPdfRenderer
import com.vitalypr.daylog.reporting.PeriodPdf
import com.vitalypr.daylog.reporting.PeriodPdfRenderer
import com.vitalypr.daylog.reporting.ReportPdf
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    abstract fun settingsSource(impl: SettingsRepository): SettingsSource

    @Binds
    abstract fun dailyPdfRenderer(impl: ReportPdf): DailyPdfRenderer

    @Binds
    abstract fun periodPdfRenderer(impl: PeriodPdf): PeriodPdfRenderer
}
