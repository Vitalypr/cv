package com.vitalypr.daylog.data.export

import com.vitalypr.daylog.data.repo.DayRepository
import com.vitalypr.daylog.domain.model.DaySnapshot
import com.vitalypr.daylog.domain.report.ReportBuilder
import com.vitalypr.daylog.domain.stats.StatsCalculator
import com.vitalypr.daylog.domain.time.formatDuration
import com.vitalypr.daylog.domain.time.formatMinutes
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

/**
 * Human-facing exports (spec F12). The full-fidelity backup lives in
 * `data/backup` — this is the readable slice a person opens in a spreadsheet.
 */
@Singleton
class Exporter @Inject constructor(
    private val repository: DayRepository,
) {

    suspend fun exportJson(from: LocalDate, to: LocalDate): String {
        val days = repository.getRange(from, to)
        val root = JSONObject()
            // v3: worked time is a list of sessions.
            .put("schemaVersion", 3)
            .put("app", "DayLog")
            .put("from", from.toString())
            .put("to", to.toString())
        val arr = JSONArray()
        days.forEach { arr.put(it.toJson()) }
        root.put("days", arr)
        return root.toString(2)
    }

    private fun DaySnapshot.toJson(): JSONObject = JSONObject().apply {
        put("date", date.toString())
        put("dayType", dayType.name)
        put("notes", notes)
        put("totalMinutes", StatsCalculator.dayMinutes(this@toJson).total)
        val sessionsArr = JSONArray()
        sessions.forEach { s ->
            sessionsArr.put(
                JSONObject()
                    .put("mode", s.mode.name)
                    .put("title", s.title)
                    .put("startMin", s.startMin ?: JSONObject.NULL)
                    .put("endMin", s.endMin ?: JSONObject.NULL)
                    .put("minutes", s.spanMin ?: JSONObject.NULL)
                    .put(
                        "activities",
                        JSONArray().also { acts ->
                            s.activities.forEach { a ->
                                acts.put(
                                    JSONObject()
                                        .put("project", a.project)
                                        .put("category", a.category)
                                        .put("durationMin", a.durationMin ?: JSONObject.NULL)
                                        .put("note", a.note)
                                        .put("result", a.result),
                                )
                            }
                        },
                    ),
            )
        }
        put("sessions", sessionsArr)
    }

    /** One row per work session, so a spreadsheet can pivot by mode or project. */
    suspend fun exportCsv(from: LocalDate, to: LocalDate): String {
        val days = repository.getRange(from, to)
        val sb = StringBuilder("date,mode,title,start,end,minutes,projects,categories,dayType,notes\n")
        days.forEach { day ->
            if (day.sessions.isEmpty()) {
                sb.append(csvRow(day.date.toString(), "", "", "", "", "", "", "", day.dayType.name, day.notes))
            }
            day.sessions.forEach { s ->
                sb.append(
                    csvRow(
                        day.date.toString(),
                        ReportBuilder.modeName(s.mode),
                        s.title,
                        s.startMin?.let(::formatMinutes).orEmpty(),
                        s.endMin?.let(::formatMinutes).orEmpty(),
                        s.spanMin?.let(::formatDuration).orEmpty(),
                        s.activities.map { it.project }.filter { it.isNotBlank() }.distinct().joinToString(";"),
                        s.activities.map { it.category }.filter { it.isNotBlank() }.joinToString(";"),
                        day.dayType.name,
                        day.notes,
                    ),
                )
            }
        }
        return sb.toString()
    }

    private fun csvRow(vararg cells: String) =
        cells.joinToString(",") { cell ->
            if (cell.any { it == ',' || it == '"' || it == '\n' }) "\"" + cell.replace("\"", "\"\"") + "\"" else cell
        } + "\n"
}
