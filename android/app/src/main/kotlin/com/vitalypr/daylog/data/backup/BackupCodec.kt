package com.vitalypr.daylog.data.backup

import com.vitalypr.daylog.data.db.ActivityEntity
import com.vitalypr.daylog.data.db.CategoryEntity
import com.vitalypr.daylog.data.db.WorkSessionEntity
import com.vitalypr.daylog.data.db.JobLocationEntity
import com.vitalypr.daylog.data.db.ProjectEntity
import com.vitalypr.daylog.data.db.WorkDayEntity
import com.vitalypr.daylog.data.settings.Settings
import java.time.DayOfWeek
import org.json.JSONArray
import org.json.JSONObject

/**
 * Everything the app owns, in one value: every table plus every setting.
 *
 * Row ids are carried verbatim so the relationships between days, activities,
 * categories and projects survive a restore — a backup that renumbered them
 * would silently re-file logged work under the wrong project.
 */
data class BackupDocument(
    val days: List<WorkDayEntity> = emptyList(),
    val sessions: List<WorkSessionEntity> = emptyList(),
    val activities: List<ActivityEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val projects: List<ProjectEntity> = emptyList(),
    val jobLocations: List<JobLocationEntity> = emptyList(),
    val settings: Settings = Settings(),
)

/**
 * Reads and writes the backup file. Plain JSON with a version field: a future
 * version can migrate an older document instead of rejecting it, and a file
 * newer than this build is refused rather than half-read.
 */
object BackupCodec {

    const val VERSION = 1
    const val APP = "DayLog"

    class IncompatibleBackup(message: String) : Exception(message)

    fun encode(doc: BackupDocument): String = JSONObject().apply {
        put("app", APP)
        put("backupVersion", VERSION)
        put("exportedAt", java.time.Instant.now().toString())
        put("days", doc.days.toArray(::dayJson))
        put("sessions", doc.sessions.toArray(::sessionJson))
        put("activities", doc.activities.toArray(::activityJson))
        put("categories", doc.categories.toArray(::categoryJson))
        put("projects", doc.projects.toArray(::projectJson))
        put("jobLocations", doc.jobLocations.toArray(::jobLocationJson))
        put("settings", settingsJson(doc.settings))
    }.toString(2)

    fun decode(json: String): BackupDocument {
        val root = runCatching { JSONObject(json) }
            .getOrElse { throw IncompatibleBackup("הקובץ אינו קובץ גיבוי תקין") }
        val version = root.optInt("backupVersion", -1)
        if (version <= 0) throw IncompatibleBackup("הקובץ אינו קובץ גיבוי של היומן")
        if (version > VERSION) throw IncompatibleBackup("הגיבוי נוצר בגרסה חדשה יותר של האפליקציה")

        return BackupDocument(
            days = root.list("days") { day(it) },
            sessions = root.list("sessions") { session(it) },
            activities = root.list("activities") { activity(it) },
            categories = root.list("categories") { category(it) },
            projects = root.list("projects") { project(it) },
            jobLocations = root.list("jobLocations") { jobLocation(it) },
            settings = root.optJSONObject("settings")?.let(::settings) ?: Settings(),
        )
    }

    // --- encode ------------------------------------------------------------

    private fun <T> List<T>.toArray(each: (T) -> JSONObject) =
        JSONArray().also { arr -> forEach { arr.put(each(it)) } }

    private fun dayJson(d: WorkDayEntity) = JSONObject().apply {
        put("date", d.date)
        put("notes", d.notes)
        put("dayType", d.dayType)
        put("reportedAt", d.reportedAt ?: JSONObject.NULL)
        put("editedAfterReport", d.editedAfterReport)
    }

    private fun sessionJson(s: WorkSessionEntity) = JSONObject().apply {
        put("id", s.id)
        put("date", s.date)
        put("mode", s.mode)
        put("startMin", s.startMin ?: JSONObject.NULL)
        put("endMin", s.endMin ?: JSONObject.NULL)
        put("title", s.title)
        put("locationText", s.locationText ?: JSONObject.NULL)
        put("startSource", s.startSource)
        put("endSource", s.endSource)
        put("startUncertain", s.startUncertain)
        put("jobLocationId", s.jobLocationId ?: JSONObject.NULL)
        put("sortOrder", s.sortOrder)
    }

    private fun activityJson(a: ActivityEntity) = JSONObject().apply {
        put("id", a.id)
        put("sessionId", a.sessionId)
        put("categoryId", a.categoryId)
        put("projectId", a.projectId)
        put("durationMin", a.durationMin ?: JSONObject.NULL)
        put("note", a.note)
        put("result", a.result)
        put("sortOrder", a.sortOrder)
    }

    private fun categoryJson(c: CategoryEntity) = JSONObject().apply {
        put("id", c.id)
        put("name", c.name)
        put("emoji", c.emoji ?: JSONObject.NULL)
        put("isHidden", c.isHidden)
        put("sortOrder", c.sortOrder)
    }

    private fun projectJson(p: ProjectEntity) = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("isArchived", p.isArchived)
        put("sortOrder", p.sortOrder)
    }

    private fun jobLocationJson(l: JobLocationEntity) = JSONObject().apply {
        put("id", l.id)
        put("name", l.name)
        put("lat", l.lat)
        put("lon", l.lon)
        put("radiusM", l.radiusM)
        put("isActive", l.isActive)
    }

    private fun settingsJson(s: Settings) = JSONObject().apply {
        put("workDays", JSONArray().also { arr -> s.workDays.sorted().forEach { arr.put(it.value) } })
        put("reportTimeMin", s.reportTimeMin)
        put("geofenceEnabled", s.geofenceEnabled)
        put("silentGeofence", s.silentGeofence)
        put("officeLat", s.officeLat ?: JSONObject.NULL)
        put("officeLon", s.officeLon ?: JSONObject.NULL)
        put("officeRadiusM", s.officeRadiusM)
    }

    // --- decode ------------------------------------------------------------

    private fun <T> JSONObject.list(name: String, each: (JSONObject) -> T): List<T> {
        val arr = optJSONArray(name) ?: return emptyList()
        return (0 until arr.length()).map { each(arr.getJSONObject(it)) }
    }

    private fun JSONObject.intOrNull(name: String): Int? = if (isNull(name)) null else optInt(name)
    private fun JSONObject.longOrNull(name: String): Long? = if (isNull(name)) null else optLong(name)
    private fun JSONObject.doubleOrNull(name: String): Double? = if (isNull(name)) null else optDouble(name)
    private fun JSONObject.stringOrNull(name: String): String? = if (isNull(name)) null else optString(name)

    private fun day(o: JSONObject) = WorkDayEntity(
        date = o.getString("date"),
        notes = o.optString("notes", ""),
        dayType = o.optString("dayType", "WORK"),
        reportedAt = o.longOrNull("reportedAt"),
        editedAfterReport = o.optBoolean("editedAfterReport", false),
    )

    private fun session(o: JSONObject) = WorkSessionEntity(
        id = o.optLong("id"),
        date = o.getString("date"),
        mode = o.optString("mode", "BASE"),
        startMin = o.intOrNull("startMin"),
        endMin = o.intOrNull("endMin"),
        title = o.optString("title", ""),
        locationText = o.stringOrNull("locationText"),
        startSource = o.optString("startSource", "MANUAL"),
        endSource = o.optString("endSource", "MANUAL"),
        startUncertain = o.optBoolean("startUncertain", false),
        jobLocationId = o.longOrNull("jobLocationId"),
        sortOrder = o.optInt("sortOrder", 0),
    )

    private fun activity(o: JSONObject) = ActivityEntity(
        id = o.optLong("id"),
        sessionId = o.optLong("sessionId"),
        categoryId = o.optLong("categoryId"),
        projectId = o.optLong("projectId"),
        durationMin = o.intOrNull("durationMin"),
        note = o.optString("note", ""),
        result = o.optString("result", ""),
        sortOrder = o.optInt("sortOrder", 0),
    )

    private fun category(o: JSONObject) = CategoryEntity(
        id = o.optLong("id"),
        name = o.optString("name", ""),
        emoji = o.stringOrNull("emoji"),
        isHidden = o.optBoolean("isHidden", false),
        sortOrder = o.optInt("sortOrder", 0),
    )

    private fun project(o: JSONObject) = ProjectEntity(
        id = o.optLong("id"),
        name = o.optString("name", ""),
        isArchived = o.optBoolean("isArchived", false),
        sortOrder = o.optInt("sortOrder", 0),
    )

    private fun jobLocation(o: JSONObject) = JobLocationEntity(
        id = o.optLong("id"),
        name = o.optString("name", ""),
        lat = o.optDouble("lat"),
        lon = o.optDouble("lon"),
        radiusM = o.optInt("radiusM", 2000),
        isActive = o.optBoolean("isActive", true),
    )

    private fun settings(o: JSONObject): Settings {
        val defaults = Settings()
        val days = o.optJSONArray("workDays")
            ?.let { arr -> (0 until arr.length()).map { DayOfWeek.of(arr.getInt(it)) }.toSet() }
            ?.takeIf { it.isNotEmpty() }
            ?: defaults.workDays
        return Settings(
            workDays = days,
            reportTimeMin = o.optInt("reportTimeMin", defaults.reportTimeMin),
            geofenceEnabled = o.optBoolean("geofenceEnabled", defaults.geofenceEnabled),
            silentGeofence = o.optBoolean("silentGeofence", defaults.silentGeofence),
            officeLat = o.doubleOrNull("officeLat"),
            officeLon = o.doubleOrNull("officeLon"),
            officeRadiusM = o.optInt("officeRadiusM", defaults.officeRadiusM),
        )
    }
}
