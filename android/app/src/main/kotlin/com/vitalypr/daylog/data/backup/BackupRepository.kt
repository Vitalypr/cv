package com.vitalypr.daylog.data.backup

import androidx.room.withTransaction
import com.vitalypr.daylog.data.db.CategoryDao
import com.vitalypr.daylog.data.db.DayDao
import com.vitalypr.daylog.data.db.DayLogDb
import com.vitalypr.daylog.data.db.JobLocationDao
import com.vitalypr.daylog.data.db.ProjectDao
import com.vitalypr.daylog.data.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * One button, everything: reads every table and every setting into a
 * [BackupDocument], and puts one back.
 *
 * Restore replaces rather than merges, inside a single transaction — a partial
 * restore would leave activities pointing at projects that no longer exist. The
 * whole point of a backup is that what comes back is exactly what was captured.
 */
@Singleton
class BackupRepository @Inject constructor(
    private val db: DayLogDb,
    private val dayDao: DayDao,
    private val categoryDao: CategoryDao,
    private val projectDao: ProjectDao,
    private val jobLocationDao: JobLocationDao,
    private val settingsRepository: SettingsRepository,
) {

    suspend fun export(): BackupDocument = BackupDocument(
        days = dayDao.allDays(),
        sessions = dayDao.allSessions(),
        activities = dayDao.allActivities(),
        categories = categoryDao.all(),
        projects = projectDao.all(),
        jobLocations = jobLocationDao.observeAll().first(),
        settings = settingsRepository.settings.first(),
    )

    suspend fun exportJson(): String = BackupCodec.encode(export())

    /**
     * Replaces all app data with [doc]. Child rows go first on the way out and
     * parents first on the way in, so foreign keys hold at every step.
     */
    suspend fun restore(doc: BackupDocument) {
        db.withTransaction {
            dayDao.clearActivities()
            dayDao.clearSessions()
            dayDao.clearDays()
            projectDao.clear()
            categoryDao.clear()
            jobLocationDao.clear()

            categoryDao.insertAll(doc.categories)
            projectDao.insertAll(doc.projects)
            jobLocationDao.insertAll(doc.jobLocations)
            dayDao.insertDays(doc.days)
            dayDao.insertSessions(doc.sessions)
            dayDao.insertActivities(doc.activities)
        }
        // Outside the DB transaction: DataStore has its own consistency.
        settingsRepository.replaceAll(doc.settings)
    }

    suspend fun restoreJson(json: String) = restore(BackupCodec.decode(json))
}
