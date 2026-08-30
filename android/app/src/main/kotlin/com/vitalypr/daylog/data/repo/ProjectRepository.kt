package com.vitalypr.daylog.data.repo

import com.vitalypr.daylog.data.db.ProjectDao
import com.vitalypr.daylog.data.db.ProjectEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Projects an activity can be booked against (v1.2).
 *
 * Removal follows the category rule: a project still referenced by logged work is
 * **archived**, never deleted, so past days keep rendering. One that was never
 * used is genuinely deleted, because keeping it would just be clutter.
 */
@Singleton
class ProjectRepository @Inject constructor(
    private val projectDao: ProjectDao,
) {

    fun observeAll(): Flow<List<ProjectEntity>> = projectDao.observeAll()

    /** What the activity picker offers. */
    fun observeActive(): Flow<List<ProjectEntity>> = projectDao.observeActive()

    suspend fun all(): List<ProjectEntity> = projectDao.all()

    suspend fun add(name: String): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return -1
        val existing = projectDao.all().firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
        // Re-adding an archived name revives it rather than creating a duplicate.
        if (existing != null) {
            if (existing.isArchived) projectDao.update(existing.copy(isArchived = false))
            return existing.id
        }
        val order = (projectDao.all().maxOfOrNull { it.sortOrder } ?: -1) + 1
        return projectDao.insert(ProjectEntity(name = trimmed, sortOrder = order))
    }

    /** @return true when the row was deleted outright, false when archived. */
    suspend fun remove(project: ProjectEntity): Boolean {
        if (projectDao.activityCount(project.id) == 0) {
            projectDao.deleteById(project.id)
            return true
        }
        projectDao.update(project.copy(isArchived = true))
        return false
    }

    suspend fun restore(project: ProjectEntity) = projectDao.update(project.copy(isArchived = false))
}
