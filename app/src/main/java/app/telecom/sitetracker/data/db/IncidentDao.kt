package app.telecom.sitetracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Update
import app.telecom.sitetracker.data.entity.IncidentEntity
import app.telecom.sitetracker.data.entity.IncidentHistoryEntity
import app.telecom.sitetracker.data.entity.IncidentPhotoEntity
import app.telecom.sitetracker.data.entity.OrphanIncidentView
import kotlinx.coroutines.flow.Flow

@Dao
interface IncidentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIncident(incident: IncidentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIncidents(incidents: List<IncidentEntity>)

    @Update
    suspend fun updateIncident(incident: IncidentEntity)

    @Query("SELECT * FROM incidents WHERE id = :id AND deleted_at IS NULL")
    suspend fun getIncidentById(id: String): IncidentEntity?

    @Query("SELECT * FROM incidents WHERE id = :id")
    fun observeIncident(id: String): Flow<IncidentEntity?>

    @Query("SELECT * FROM incidents WHERE sap_id = :sapId AND deleted_at IS NULL ORDER BY occurred_at DESC")
    fun getIncidentsForSite(sapId: String): Flow<List<IncidentEntity>>

    @Query("SELECT * FROM incidents WHERE sap_id = :sapId AND deleted_at IS NULL ORDER BY occurred_at DESC")
    suspend fun getIncidentsListForSite(sapId: String): List<IncidentEntity>

    @Query("SELECT * FROM incidents WHERE sap_id = :sapId AND occurred_at >= :sinceMs AND deleted_at IS NULL")
    suspend fun getRecentIncidentsForSite(sapId: String, sinceMs: Long): List<IncidentEntity>

    @Query("SELECT * FROM incidents ORDER BY occurred_at DESC")
    suspend fun getAllIncidentsForBackup(): List<IncidentEntity>

    @Query("SELECT * FROM incidents WHERE deleted_at IS NULL ORDER BY occurred_at DESC LIMIT :limit")
    fun getRecentIncidents(limit: Int = 10): Flow<List<IncidentEntity>>

    // Audit History
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: IncidentHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistories(histories: List<IncidentHistoryEntity>)

    @Query("SELECT * FROM incident_history WHERE incident_id = :incidentId ORDER BY changed_at DESC")
    fun getHistoryForIncident(incidentId: String): Flow<List<IncidentHistoryEntity>>

    @Query("SELECT * FROM incident_history ORDER BY changed_at ASC")
    suspend fun getAllHistoryForBackup(): List<IncidentHistoryEntity>

    // Photos
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: IncidentPhotoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhotos(photos: List<IncidentPhotoEntity>)

    @Query("SELECT * FROM incident_photos WHERE incident_id = :incidentId ORDER BY taken_at ASC")
    fun getPhotosForIncident(incidentId: String): Flow<List<IncidentPhotoEntity>>

    @Query("SELECT * FROM incident_photos WHERE sap_id = :sapId ORDER BY taken_at DESC")
    fun getPhotosForSite(sapId: String): Flow<List<IncidentPhotoEntity>>

    @Query("SELECT * FROM incident_photos ORDER BY taken_at ASC")
    suspend fun getAllPhotos(): List<IncidentPhotoEntity>

    @Query("DELETE FROM incident_photos WHERE id = :photoId")
    suspend fun deletePhoto(photoId: String)

    // Orphans
    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM v_orphan_incidents ORDER BY occurred_at DESC")
    fun getOrphanIncidents(): Flow<List<OrphanIncidentView>>

    @Query("SELECT COUNT(*) FROM v_orphan_incidents")
    fun countOrphans(): Flow<Int>

    @Query("UPDATE incidents SET sap_id = :newSapId, updated_at = :updatedAt WHERE sap_id = :oldSapId")
    suspend fun attachOrphans(oldSapId: String, newSapId: String, updatedAt: Long)

    @Query("SELECT * FROM incidents WHERE deleted_at IS NULL ORDER BY occurred_at DESC LIMIT 500")
    fun getAllIncidentsStream(): Flow<List<IncidentEntity>>

    @Query("SELECT * FROM incidents WHERE deleted_at IS NULL ORDER BY occurred_at DESC")
    suspend fun getAllIncidentsList(): List<IncidentEntity>

    @Query("UPDATE incidents SET deleted_at = :deletedAt, updated_at = :deletedAt WHERE id = :id")
    suspend fun softDeleteIncident(id: String, deletedAt: Long)

    @Query("DELETE FROM incidents WHERE id = :id")
    suspend fun hardDeleteIncident(id: String)

    @Query("DELETE FROM incidents WHERE sap_id LIKE 'DEL-9%' OR sap_id LIKE 'MUM-9%' OR sap_id LIKE 'KOL-9%' OR sap_id LIKE 'CHN-9%' OR sap_id LIKE 'CHE-9%' OR sap_id LIKE 'BLR-9%' OR sap_id LIKE 'SAMPLE%'")
    suspend fun deleteSampleIncidents()

    @Query("DELETE FROM incident_history WHERE incident_id NOT IN (SELECT id FROM incidents)")
    suspend fun deleteOrphanIncidentHistory()

    @Query("DELETE FROM incidents")
    suspend fun deleteAllIncidents()
}
