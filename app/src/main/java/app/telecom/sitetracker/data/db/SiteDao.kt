package app.telecom.sitetracker.data.db

import androidx.paging.PagingSource
import androidx.room.ColumnInfo
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
        JOIN site_search ON s.sap_norm = site_search.sap_norm
        WHERE site_search MATCH :query AND s.deleted_at IS NULL
        LIMIT 100
    """)
    suspend fun searchFts(query: String): List<SiteEntity>

    @Query("""
        SELECT * FROM sites 
        WHERE deleted_at IS NULL AND (
            sap_id LIKE '%' || :query || '%' OR 
            sap_norm LIKE '%' || :query || '%' OR 
            (:normQuery != '' AND sap_norm LIKE '%' || :normQuery || '%') OR 
            LOWER(name) LIKE '%' || LOWER(:query) || '%' OR 
            LOWER(city) LIKE '%' || LOWER(:query) || '%' OR 
            LOWER(district) LIKE '%' || LOWER(:query) || '%' OR 
            LOWER(circle) LIKE '%' || LOWER(:query) || '%' OR
            LOWER(address) LIKE '%' || LOWER(:query) || '%' OR
            LOWER(technology) LIKE '%' || LOWER(:query) || '%' OR
            LOWER(site_type) LIKE '%' || LOWER(:query) || '%' OR
            LOWER(status) LIKE '%' || LOWER(:query) || '%' OR
            sap_id IN (
                SELECT sap_id FROM site_fields 
                WHERE LOWER(field_key) LIKE '%' || LOWER(:query) || '%' 
                   OR LOWER(field_value) LIKE '%' || LOWER(:query) || '%'
            )
        )
        ORDER BY 
            CASE 
                WHEN sap_id = :query OR (:normQuery != '' AND sap_norm = :normQuery) THEN 1
                WHEN sap_id LIKE :query || '%' OR (:normQuery != '' AND sap_norm LIKE :normQuery || '%') THEN 2
                WHEN LOWER(name) LIKE LOWER(:query) || '%' THEN 3
                ELSE 4
            END,
            sap_id ASC
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

    @Query("""
        SELECT 
            COUNT(CASE WHEN name IS NOT NULL AND TRIM(name) != '' THEN 1 END) AS hasName,
            COUNT(CASE WHEN lat IS NOT NULL AND lng IS NOT NULL THEN 1 END) AS hasCoordinates,
            COUNT(CASE WHEN circle IS NOT NULL AND TRIM(circle) != '' THEN 1 END) AS hasCircle,
            COUNT(CASE WHEN region IS NOT NULL AND TRIM(region) != '' THEN 1 END) AS hasRegion,
            COUNT(CASE WHEN state IS NOT NULL AND TRIM(state) != '' THEN 1 END) AS hasState,
            COUNT(CASE WHEN district IS NOT NULL AND TRIM(district) != '' THEN 1 END) AS hasDistrict,
            COUNT(CASE WHEN city IS NOT NULL AND TRIM(city) != '' THEN 1 END) AS hasCity,
            COUNT(CASE WHEN technology IS NOT NULL AND TRIM(technology) != '' THEN 1 END) AS hasTechnology,
            COUNT(CASE WHEN site_type IS NOT NULL AND TRIM(site_type) != '' THEN 1 END) AS hasSiteType,
            COUNT(CASE WHEN status IS NOT NULL AND TRIM(status) != '' THEN 1 END) AS hasStatus,
            COUNT(CASE WHEN address IS NOT NULL AND TRIM(address) != '' THEN 1 END) AS hasAddress,
            COUNT(CASE WHEN tags IS NOT NULL AND TRIM(tags) != '' THEN 1 END) AS hasTags,
            COUNT(CASE WHEN notes IS NOT NULL AND TRIM(notes) != '' THEN 1 END) AS hasNotes
        FROM sites WHERE deleted_at IS NULL
    """)
    suspend fun getPopulatedCoreFields(): PopulatedCoreFields?

    @Query("SELECT DISTINCT circle FROM sites WHERE deleted_at IS NULL AND circle IS NOT NULL AND TRIM(circle) != '' ORDER BY circle ASC")
    suspend fun getDistinctCircles(): List<String>

    @Query("SELECT DISTINCT city FROM sites WHERE deleted_at IS NULL AND city IS NOT NULL AND TRIM(city) != '' ORDER BY city ASC")
    suspend fun getDistinctCities(): List<String>

    @Query("SELECT DISTINCT district FROM sites WHERE deleted_at IS NULL AND district IS NOT NULL AND TRIM(district) != '' ORDER BY district ASC")
    suspend fun getDistinctDistricts(): List<String>

    @Query("SELECT DISTINCT technology FROM sites WHERE deleted_at IS NULL AND technology IS NOT NULL AND TRIM(technology) != '' ORDER BY technology ASC")
    suspend fun getDistinctTechnologies(): List<String>

    @Query("SELECT DISTINCT site_type FROM sites WHERE deleted_at IS NULL AND site_type IS NOT NULL AND TRIM(site_type) != '' ORDER BY site_type ASC")
    suspend fun getDistinctSiteTypes(): List<String>

    @Query("SELECT DISTINCT status FROM sites WHERE deleted_at IS NULL AND status IS NOT NULL AND TRIM(status) != '' ORDER BY status ASC")
    suspend fun getDistinctStatuses(): List<String>
}

data class PopulatedCoreFields(
    @ColumnInfo(name = "hasName") val hasName: Int = 0,
    @ColumnInfo(name = "hasCoordinates") val hasCoordinates: Int = 0,
    @ColumnInfo(name = "hasCircle") val hasCircle: Int = 0,
    @ColumnInfo(name = "hasRegion") val hasRegion: Int = 0,
    @ColumnInfo(name = "hasState") val hasState: Int = 0,
    @ColumnInfo(name = "hasDistrict") val hasDistrict: Int = 0,
    @ColumnInfo(name = "hasCity") val hasCity: Int = 0,
    @ColumnInfo(name = "hasTechnology") val hasTechnology: Int = 0,
    @ColumnInfo(name = "hasSiteType") val hasSiteType: Int = 0,
    @ColumnInfo(name = "hasStatus") val hasStatus: Int = 0,
    @ColumnInfo(name = "hasAddress") val hasAddress: Int = 0,
    @ColumnInfo(name = "hasTags") val hasTags: Int = 0,
    @ColumnInfo(name = "hasNotes") val hasNotes: Int = 0
)
