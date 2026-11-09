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
        WHERE ss MATCH :query AND s.deleted_at IS NULL
        LIMIT 100
    """)
    suspend fun searchFts(query: String): List<SiteEntity>

    @Query("""
        SELECT DISTINCT s.* FROM sites s 
        LEFT JOIN site_fields sf ON s.sap_id = sf.sap_id
        WHERE s.deleted_at IS NULL AND (
            s.sap_id LIKE '%' || :query || '%' OR 
            s.sap_norm LIKE '%' || :query || '%' OR 
            (:normQuery != '' AND s.sap_norm LIKE '%' || :normQuery || '%') OR 
            s.name LIKE '%' || :query || '%' OR 
            s.city LIKE '%' || :query || '%' OR 
            s.district LIKE '%' || :query || '%' OR 
            s.circle LIKE '%' || :query || '%' OR
            s.address LIKE '%' || :query || '%' OR
            s.technology LIKE '%' || :query || '%' OR
            s.site_type LIKE '%' || :query || '%' OR
            sf.field_key LIKE '%' || :query || '%' OR
            sf.field_value LIKE '%' || :query || '%'
        )
        ORDER BY 
            CASE 
                WHEN s.sap_id = :query OR (:normQuery != '' AND s.sap_norm = :normQuery) THEN 1
                WHEN s.sap_id LIKE :query || '%' OR (:normQuery != '' AND s.sap_norm LIKE :normQuery || '%') THEN 2
                WHEN s.name LIKE :query || '%' THEN 3
                ELSE 4
            END,
            s.sap_id ASC
        LIMIT 100
    """)
    suspend fun searchDirect(query: String, normQuery: String = ""): List<SiteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFts(items: List<app.telecom.sitetracker.data.entity.SiteFts>)

    @Query("DELETE FROM site_search WHERE sap_norm = :sapNorm")
    suspend fun deleteFts(sapNorm: String)

    @Query("DELETE FROM site_search")
    suspend fun clearFts()

    @Query("SELECT COUNT(*) FROM sites WHERE deleted_at IS NULL")
    suspend fun getActiveSiteCount(): Int

    @Query("DELETE FROM site_search WHERE sap_norm LIKE 'DEL9%' OR sap_norm LIKE 'MUM9%' OR sap_norm LIKE 'KOL9%' OR sap_norm LIKE 'CHN9%' OR sap_norm LIKE 'CHE9%' OR sap_norm LIKE 'BLR9%' OR sap_norm LIKE 'SAMPLE%'")
    suspend fun deleteSampleFts()

    @Query("DELETE FROM sites WHERE sap_id LIKE 'DEL-9%' OR sap_id LIKE 'MUM-9%' OR sap_id LIKE 'KOL-9%' OR sap_id LIKE 'CHN-9%' OR sap_id LIKE 'CHE-9%' OR sap_id LIKE 'BLR-9%' OR sap_id LIKE 'SAMPLE%'")
    suspend fun deleteSampleSites()

    @Query("DELETE FROM sites")
    suspend fun deleteAllSites()
}
