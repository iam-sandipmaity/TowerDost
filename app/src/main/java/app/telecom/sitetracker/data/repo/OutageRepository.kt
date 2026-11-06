package app.telecom.sitetracker.data.repo

import androidx.room.withTransaction
import app.telecom.sitetracker.data.db.AppDatabase
import app.telecom.sitetracker.data.db.DayDowntimeRow
import app.telecom.sitetracker.data.entity.IncidentEntity
import app.telecom.sitetracker.data.entity.IncidentHistoryEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

sealed class MarkDownResult {
    data class Success(val incidentId: String) : MarkDownResult()
    data class AlreadyDown(val existingIncidentId: String) : MarkDownResult()
    data class Error(val message: String) : MarkDownResult()
}

class OutageRepository(private val db: AppDatabase) {
    private val outageDao = db.outageDao()
    private val incidentDao = db.incidentDao()

    suspend fun getOpenOutageForSite(sapId: String): IncidentEntity? =
        outageDao.getOpenOutageForSite(sapId)

    fun observeOpenOutages(): Flow<List<IncidentEntity>> = outageDao.getOpenOutages()

    fun observeRestoredOutages(): Flow<List<IncidentEntity>> = outageDao.getRecentRestoredOutages()

    fun observeAllOutages(): Flow<List<IncidentEntity>> = outageDao.getAllOutages()

    fun countSitesDownNow(): Flow<Int> = outageDao.countSitesDownNow()

    suspend fun markDown(
        sapId: String,
        downAt: Long,
        category: String,
        reasonId: String?,
        severity: String,
        title: String,
        description: String? = null,
        engineer: String? = null,
        expectedRestoreAt: Long? = null
    ): MarkDownResult {
        return db.withTransaction {
            val existing = outageDao.getOpenOutageForSite(sapId)
            if (existing != null) {
                return@withTransaction MarkDownResult.AlreadyDown(existing.id)
            }

            val id = UUID.randomUUID().toString()
            val incident = IncidentEntity(
                id = id,
                sapId = sapId,
                occurredAt = downAt,
                category = category,
                reasonId = reasonId,
                severity = severity,
                status = "Open",
                isOutage = 1,
                downAt = downAt,
                restoredAt = null,
                title = title.ifBlank { "$category Outage" },
                description = description,
                engineer = engineer,
                createdAt = downAt,
                updatedAt = downAt
            )
            incidentDao.insertIncident(incident)
            MarkDownResult.Success(id)
        }
    }

    suspend fun restoreOutage(
        incidentId: String,
        restoredAt: Long,
        engineer: String? = null
    ): Result<Unit> {
        return db.withTransaction {
            val incident = incidentDao.getIncidentById(incidentId)
                ?: return@withTransaction Result.failure(IllegalArgumentException("Incident not found: $incidentId"))

            if (incident.downAt != null && restoredAt < incident.downAt) {
                return@withTransaction Result.failure(
                    IllegalArgumentException("Restoration time cannot be earlier than down time.")
                )
            }

            val updated = incident.copy(
                status = "Resolved",
                restoredAt = restoredAt,
                updatedAt = System.currentTimeMillis()
            )
            incidentDao.updateIncident(updated)

            incidentDao.insertHistory(
                IncidentHistoryEntity(
                    id = UUID.randomUUID().toString(),
                    incidentId = incidentId,
                    changedAt = System.currentTimeMillis(),
                    changedBy = engineer,
                    field = "restored_at",
                    oldValue = incident.restoredAt?.toString(),
                    newValue = restoredAt.toString()
                )
            )

            Result.success(Unit)
        }
    }

    suspend fun undoRestore(incidentId: String): Result<Unit> {
        return db.withTransaction {
            val incident = incidentDao.getIncidentById(incidentId)
                ?: return@withTransaction Result.failure(IllegalArgumentException("Incident not found: $incidentId"))

            val updated = incident.copy(
                status = "Open",
                restoredAt = null,
                updatedAt = System.currentTimeMillis()
            )
            incidentDao.updateIncident(updated)
            Result.success(Unit)
        }
    }

    suspend fun tombstoneIncident(incidentId: String): Result<Unit> {
        return db.withTransaction {
            val incident = incidentDao.getIncidentById(incidentId)
                ?: return@withTransaction Result.failure(IllegalArgumentException("Incident not found: $incidentId"))

            val updated = incident.copy(
                deletedAt = System.currentTimeMillis()
            )
            incidentDao.updateIncident(updated)
            Result.success(Unit)
        }
    }

    suspend fun untombstoneIncident(incidentId: String): Result<Unit> {
        return db.withTransaction {
            val incident = incidentDao.getIncidentById(incidentId)
                ?: return@withTransaction Result.failure(IllegalArgumentException("Incident not found: $incidentId"))

            val updated = incident.copy(
                deletedAt = null,
                updatedAt = System.currentTimeMillis()
            )
            incidentDao.updateIncident(updated)
            Result.success(Unit)
        }
    }

    suspend fun bulkMarkDown(
        sapIds: List<String>,
        downAt: Long,
        category: String,
        reasonId: String?,
        severity: String,
        note: String?,
        engineer: String?
    ): Map<String, MarkDownResult> {
        return db.withTransaction {
            val results = mutableMapOf<String, MarkDownResult>()
            for (sapId in sapIds) {
                val existing = outageDao.getOpenOutageForSite(sapId)
                if (existing != null) {
                    results[sapId] = MarkDownResult.AlreadyDown(existing.id)
                } else {
                    val id = UUID.randomUUID().toString()
                    val incident = IncidentEntity(
                        id = id,
                        sapId = sapId,
                        occurredAt = downAt,
                        category = category,
                        reasonId = reasonId,
                        severity = severity,
                        status = "Open",
                        isOutage = 1,
                        downAt = downAt,
                        restoredAt = null,
                        title = "Bulk Outage: $category",
                        description = note,
                        engineer = engineer,
                        createdAt = downAt,
                        updatedAt = downAt
                    )
                    incidentDao.insertIncident(incident)
                    results[sapId] = MarkDownResult.Success(id)
                }
            }
            results
        }
    }

    suspend fun bulkRestore(
        incidentIds: List<String>,
        restoredAt: Long,
        engineer: String?
    ): Int {
        return db.withTransaction {
            var count = 0
            for (id in incidentIds) {
                val res = restoreOutage(id, restoredAt, engineer)
                if (res.isSuccess) count++
            }
            count
        }
    }

    fun observeOutagesForDay(dayStart: Long, dayEnd: Long): Flow<List<IncidentEntity>> =
        outageDao.observeOutagesForDay(dayStart, dayEnd)

    suspend fun getOutagesForDay(dayStart: Long, dayEnd: Long): List<IncidentEntity> =
        outageDao.getOutagesForDay(dayStart, dayEnd)

    suspend fun getDailyDowntimeMatrix(rangeStart: Long, rangeEnd: Long, now: Long): List<DayDowntimeRow> =
        outageDao.getDailyDowntimeMatrix(rangeStart, rangeEnd, now)
}
