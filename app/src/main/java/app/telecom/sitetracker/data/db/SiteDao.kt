package app.telecom.sitetracker.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.telecom.sitetracker.data.entity.SiteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SiteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSites(sites: List<SiteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSite(site: SiteEntity)

    @Update
    suspend fun updateSite(site: SiteEntity)

    @Query("SELECT * FROM sites WHERE sap_id = :sapId AND deleted_at IS NULL")
    suspend fun getSiteBySapId(sapId: String): SiteEntity?

    @Query("SELECT * FROM sites WHERE sap_id = :sapId")
    suspend fun getSiteIncludingDeleted(sapId: String): SiteEntity?

    @Query("SELECT * FROM sites WHERE sap_norm = :sapNorm AND deleted_at IS NULL LIMIT 1")
    suspend fun getSiteBySapNorm(sapNorm: String): SiteEntity?

    @Query("SELECT * FROM sites WHERE sap_id = :sapId")
    fun observeSite(sapId: String): Flow<SiteEntity?>

    @Query("SELECT * FROM sites WHERE deleted_at IS NULL ORDER BY sap_id ASC")
    fun getAllActiveSites(): Flow<List<SiteEntity>>

    @Query("SELECT * FROM sites WHERE deleted_at IS NULL ORDER BY sap_id ASC")
    suspend fun getAllActiveSitesList(): List<SiteEntity>

    @Query("SELECT * FROM sites ORDER BY sap_id ASC")
    suspend fun getAllSitesForBackup(): List<SiteEntity>

    @Query("SELECT * FROM sites WHERE deleted_at IS NULL ORDER BY sap_id ASC")
    fun getActiveSitesPaged(): PagingSource<Int, SiteEntity>

    @Query("SELECT COUNT(*) FROM sites WHERE deleted_at IS NULL")
    fun countActiveSites(): Flow<Int>

    @Query("UPDATE sites SET deleted_at = :tombstoneTime WHERE sap_id NOT IN (:keepSapIds) AND deleted_at IS NULL")
    suspend fun tombstoneSitesNotIn(keepSapIds: List<String>, tombstoneTime: Long)

    @Query("UPDATE sites SET deleted_at = NULL, updated_at = :restoreTime WHERE sap_id = :sapId")
    suspend fun restoreTombstonedSite(sapId: String, restoreTime: Long)

    @Query("""
        SELECT DISTINCT s.* FROM sites s
        JOIN site_search ss ON s.sap_norm = ss.sap_norm
        WHERE site_search MATCH :query AND s.deleted_at IS NULL
        LIMIT 50
    """)
    suspend fun searchFts(query: String): List<SiteEntity>

    @Query("""
        SELECT * FROM sites 
        WHERE deleted_at IS NULL AND (
            sap_id LIKE '%' || :query || '%' OR 
            sap_norm LIKE '%' || :query || '%' OR 
            name LIKE '%' || :query || '%' OR 
            city LIKE '%' || :query || '%' OR 
            district LIKE '%' || :query || '%' OR 
            circle LIKE '%' || :query || '%'
        )
        ORDER BY sap_id ASC
        LIMIT 50
    """)
    suspend fun searchDirect(query: String): List<SiteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFts(items: List<app.telecom.sitetracker.data.entity.SiteFts>)

    @Query("DELETE FROM site_search WHERE sap_norm = :sapNorm")
    suspend fun deleteFts(sapNorm: String)

    @Query("DELETE FROM site_search")
    suspend fun clearFts()

    @Query("DELETE FROM sites WHERE sap_id LIKE 'DEL-9%' OR sap_id LIKE 'MUM-9%' OR sap_id LIKE 'KOL-9%' OR sap_id LIKE 'CHN-9%' OR sap_id LIKE 'BLR-9%'")
    suspend fun deleteSampleSites()

    @Query("DELETE FROM sites")
    suspend fun deleteAllSites()
}
