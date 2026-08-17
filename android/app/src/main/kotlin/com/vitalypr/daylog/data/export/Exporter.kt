package com.vitalypr.daylog.data.export

import com.vitalypr.daylog.data.repo.DayRepository
import com.vitalypr.daylog.domain.model.DaySnapshot
import com.vitalypr.daylog.domain.stats.StatsCalculator
import com.vitalypr.daylog.domain.time.formatDuration
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

/**
 * Data export per spec §6.7: JSON (full fidelity, versioned schema, future
 * re-import) and hand-rolled CSV (one row per day for spreadsheet analysis).
 */
@Singleton
class Exporter @Inject constructor(
    private val repository: DayRepository,
) {

    suspend fun exportJson(from: LocalDate, to: LocalDate): String {
        val days = repository.getRange(from, to)
        val root = JSONObject()
            // v2: activities carry durationMin instead of startMin/endMin.
            .put("schemaVersion", 2)
            .put("app", "DayLog")
            .put("from", from.toString())
            .put("to", to.toString())
        val arr = JSONArray()
        days.forEach { d -> arr.put(d.toJson()) }
        root.put("days", arr)
        return root.toString(2)
    }

    suspend fun exportCsv(from: LocalDate, to: LocalDate): String {
        val days = repository.getRange(from, to)
        val header = "date,day_type,arrival,departure,total_hours,field_jobs,activities,notes"
        val rows = days.map { d ->
            val m = StatsCalculator.dayMinutes(d)
            listOf(
                d.date.toString(),
                d.dayType.name,
                d.arrivalMin?.toString() ?: "",
                d.departureMin?.toString() ?: "",
                if (m.total > 0) formatDuration(m.total) else "",
                d.fieldJobs.joinToString(";") { it.title },
                d.activities.joinToString(";") { it.category },
                d.notes,
            ).joinToString(",") { csvEscape(it) }
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    private fun DaySnapshot.toJson(): JSONObject = JSONObject()
        .put("date", date.toString())
        .put("dayType", dayType.name)
        .put("arrivalMin", arrivalMin ?: JSONObject.NULL)
        .put("departureMin", departureMin ?: JSONObject.NULL)
        .put("arrivalSource", arrivalSource.name)
        .put("departureSource", departureSource.name)
        .put("notes", notes)
        .put("reported", reported)
        .put(
            "fieldJobs",
            JSONArray().also { a ->
                fieldJobs.forEach { j ->
                    a.put(
                        JSONObject()
                            .put("title", j.title)
                            .put("location", j.locationText ?: JSONObject.NULL)
                            .put("startMin", j.startMin ?: JSONObject.NULL)
                            .put("endMin", j.endMin ?: JSONObject.NULL),
                    )
                }
            },
        )
        .put(
            "activities",
            JSONArray().also { a ->
                activities.forEach { act ->
                    a.put(
                        JSONObject()
                            .put("category", act.category)
                            .put("durationMin", act.durationMin ?: JSONObject.NULL)
                            .put("note", act.note)
                            .put("result", act.result),
                    )
                }
            },
        )

    private fun csvEscape(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else value
}
