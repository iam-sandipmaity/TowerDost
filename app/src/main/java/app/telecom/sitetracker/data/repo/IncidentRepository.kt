package app.telecom.sitetracker.data.repo

import androidx.room.withTransaction
import app.telecom.sitetracker.data.db.AppDatabase
import app.telecom.sitetracker.data.entity.IncidentEntity
import app.telecom.sitetracker.data.entity.IncidentHistoryEntity
import app.telecom.sitetracker.data.entity.IncidentPhotoEntity
import app.telecom.sitetracker.data.entity.OrphanIncidentView
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class IncidentRepository(private val db: AppDatabase) {
    private val incidentDao = db.incidentDao()

    fun observeIncident(id: String): Flow<IncidentEntity?> = incidentDao.observeIncident(id)

    suspend fun getIncident(id: String): IncidentEntity? = incidentDao.getIncidentById(id)

    fun observeIncidentsForSite(sapId: String): Flow<List<IncidentEntity>> =
        incidentDao.getIncidentsForSite(sapId)

    suspend fun getIncidentsForSite(sapId: String): List<IncidentEntity> =
        incidentDao.getIncidentsListForSite(sapId)

    suspend fun getRecentIncidentsForSite(sapId: String, sinceMs: Long): List<IncidentEntity> =
        incidentDao.getRecentIncidentsForSite(sapId, sinceMs)

    fun observeRecentIncidents(limit: Int = 10): Flow<List<IncidentEntity>> =
        incidentDao.getRecentIncidents(limit)

    suspend fun saveIncident(
        incident: IncidentEntity,
        changedBy: String? = null,
        changeTimeMs: Long = System.currentTimeMillis()
    ) {
        db.withTransaction {
            val existing = incidentDao.getIncidentById(incident.id)
            if (existing == null) {
                incidentDao.insertIncident(incident)
            } else {
                // Record diffs into history
                recordHistoryDiffs(existing, incident, changedBy, changeTimeMs)
                incidentDao.updateIncident(incident)
            }
        }
    }

    private suspend fun recordHistoryDiffs(
        old: IncidentEntity,
        new: IncidentEntity,
        changedBy: String?,
        changeTimeMs: Long
    ) {
        val diffs = mutableListOf<IncidentHistoryEntity>()

        fun check(field: String, oldVal: String?, newVal: String?) {
            if (oldVal != newVal) {
                diffs.add(
                    IncidentHistoryEntity(
                        id = UUID.randomUUID().toString(),
                        incidentId = new.id,
                        changedAt = changeTimeMs,
                        changedBy = changedBy,
                        field = field,
                        oldValue = oldVal,
                        newValue = newVal
                    )
                )
            }
        }

        check("status", old.status, new.status)
        check("severity", old.severity, new.severity)
        check("category", old.category, new.category)
        check("reason_id", old.reasonId, new.reasonId)
        check("down_at", old.downAt?.toString(), new.downAt?.toString())
        check("restored_at", old.restoredAt?.toString(), new.restoredAt?.toString())
        check("title", old.title, new.title)
        check("action_taken", old.actionTaken, new.actionTaken)

        if (diffs.isNotEmpty()) {
            incidentDao.insertHistories(diffs)
        }
    }

    fun observeHistory(incidentId: String): Flow<List<IncidentHistoryEntity>> =
        incidentDao.getHistoryForIncident(incidentId)

    fun observePhotos(incidentId: String): Flow<List<IncidentPhotoEntity>> =
        incidentDao.getPhotosForIncident(incidentId)

    suspend fun addPhoto(photo: IncidentPhotoEntity) = incidentDao.insertPhoto(photo)

    suspend fun deletePhoto(photoId: String) = incidentDao.deletePhoto(photoId)

    fun observeOrphans(): Flow<List<OrphanIncidentView>> = incidentDao.getOrphanIncidents()

    fun countOrphans(): Flow<Int> = incidentDao.countOrphans()

    suspend fun attachOrphans(oldSapId: String, newSapId: String, updatedAt: Long) {
        incidentDao.attachOrphans(oldSapId, newSapId, updatedAt)
    }

    fun observeAllIncidents(): Flow<List<IncidentEntity>> = incidentDao.getAllIncidentsStream()

    suspend fun getAllIncidentsList(): List<IncidentEntity> = incidentDao.getAllIncidentsList()

    suspend fun clearSampleIncidents() {
        incidentDao.deleteSampleIncidents()
    }

    suspend fun clearAllIncidents() {
        incidentDao.deleteAllIncidents()
    }
}
