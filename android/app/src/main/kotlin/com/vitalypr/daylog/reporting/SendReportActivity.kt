package com.vitalypr.daylog.reporting

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.vitalypr.daylog.data.repo.DayRepository
import com.vitalypr.daylog.domain.report.ReportBuilder
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Trampoline-safe share launcher (spec §6.4): notification actions must launch an
 * Activity directly on Android 12+. Transparent; marks the day reported, fires the
 * share intent, finishes. Mis-marks are covered by the always-available re-send.
 */
@AndroidEntryPoint
class SendReportActivity : ComponentActivity() {

    @Inject lateinit var repository: DayRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val date = intent.getStringExtra(EXTRA_DATE)?.let(LocalDate::parse)
        if (date == null) {
            finish(); return
        }
        lifecycleScope.launch {
            val day = repository.getDay(date)
            if (day != null && day.hasData) {
                repository.markReported(date)
                try {
                    startActivity(ReportShare.intentFor(this@SendReportActivity, ReportBuilder.daily(day)))
                } catch (_: ActivityNotFoundException) {
                    // No shareable target at all — nothing further to do; re-send stays available.
                }
            }
            finish()
        }
    }

    companion object {
        const val EXTRA_DATE = "date"

        fun intent(context: Context, date: LocalDate): Intent =
            Intent(context, SendReportActivity::class.java)
                .putExtra(EXTRA_DATE, date.toString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
