package app.telecom.sitetracker.data.repo

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import app.telecom.sitetracker.data.db.AppDatabase
import app.telecom.sitetracker.data.entity.SiteEntity
import app.telecom.sitetracker.data.entity.SiteFieldEntity
import app.telecom.sitetracker.data.entity.SiteFts
import app.telecom.sitetracker.data.entity.UniversalFieldEntity
import app.telecom.sitetracker.util.Geo
import kotlinx.coroutines.flow.Flow

data class SiteWithDistance(
    val site: SiteEntity,
    val distanceKm: Double,
    val bearingDegrees: Double,
    val formattedDistance: String
)

class SiteRepository(private val db: AppDatabase) {
    private val siteDao = db.siteDao()
    private val fieldDao = db.fieldDao()
    private val universalFieldDao = db.universalFieldDao()

    fun observeActiveSites(): Flow<List<SiteEntity>> = siteDao.getAllActiveSites()

    fun observeSite(sapId: String): Flow<SiteEntity?> = siteDao.observeSite(sapId)

    suspend fun getSiteBySapId(sapId: String): SiteEntity? = siteDao.getSiteBySapId(sapId)

    suspend fun getSiteBySapNorm(sapNorm: String): SiteEntity? = siteDao.getSiteBySapNorm(sapNorm)

    fun observeFieldsForSite(sapId: String): Flow<List<SiteFieldEntity>> =
        fieldDao.observeFieldsForSite(sapId)

    suspend fun getFieldsForSite(sapId: String): List<SiteFieldEntity> =
        fieldDao.getFieldsForSite(sapId)

    fun countActiveSites(): Flow<Int> = siteDao.countActiveSites()

    suspend fun getAllSitesList(): List<SiteEntity> = siteDao.getAllActiveSitesList()

    fun getPagedSites(): Flow<PagingData<SiteEntity>> {
        return Pager(
            config = PagingConfig(pageSize = 30, enablePlaceholders = false),
            pagingSourceFactory = { siteDao.getActiveSitesPaged() }
        ).flow
    }

    /**
     * Searches sites using full-text search with fallback to direct substring matching.
     */
    suspend fun searchSites(query: String): List<SiteEntity> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        return try {
            val sanitized = trimmed.replace("\"", "\"\"")
            val results = siteDao.searchFts("*$sanitized*")
            if (results.isNotEmpty()) {
                results.distinctBy { it.sapId }
            } else {
                siteDao.searchDirect(trimmed).distinctBy { it.sapId }
            }
        } catch (_: Exception) {
            // If FTS fails (e.g. SQLite syntax query error with dashes or asterisks), fallback
            siteDao.searchDirect(trimmed).distinctBy { it.sapId }
        }
    }

    /**
     * Finds sites nearest to the provided reference coordinates, sorted by distance.
     */
    suspend fun searchNearby(refLat: Double, refLng: Double, limit: Int = 50): List<SiteWithDistance> {
        val allSites = siteDao.getAllActiveSitesList()
        return allSites
            .filter { it.lat != null && it.lng != null }
            .map { site ->
                val dist = Geo.haversineDistanceKm(refLat, refLng, site.lat!!, site.lng!!)
                val bearing = Geo.bearingDegrees(refLat, refLng, site.lat, site.lng)
                val formatted = Geo.formatDistanceAndBearing(refLat, refLng, site.lat, site.lng)
                SiteWithDistance(site, dist, bearing, formatted)
            }
            .sortedBy { it.distanceKm }
            .take(limit)
    }

    suspend fun saveSite(site: SiteEntity) {
        siteDao.insertSite(site)
    }

    suspend fun getAllFields(): List<SiteFieldEntity> = fieldDao.getAllFields()

    suspend fun updateSiteOverview(site: SiteEntity) {
        val updated = site.copy(updatedAt = System.currentTimeMillis())
        siteDao.updateSite(updated)
        try {
            val fields = fieldDao.getFieldsForSite(site.sapId)
            val fieldText = fields.joinToString(" ") { "${it.fieldKey} ${it.fieldValue ?: ""}" }
            siteDao.deleteFts(site.sapNorm)
            siteDao.insertFts(
                listOf(
                    SiteFts(
                        sapNorm = site.sapNorm,
                        name = site.name,
                        address = site.address ?: site.city ?: site.district,
                        fieldText = fieldText
                    )
                )
            )
        } catch (_: Exception) {}
    }

    suspend fun updateSiteSpec(field: SiteFieldEntity) {
        fieldDao.updateField(field)
        val site = siteDao.getSiteBySapId(field.sapId)
        if (site != null) {
            siteDao.updateSite(site.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun addOrUpdateSiteSpec(
        sapId: String,
        fieldKey: String,
        fieldValue: String?,
        groupName: String?
    ) {
        val existingFields = fieldDao.getFieldsForSite(sapId)
        val trimmedKey = fieldKey.trim()
        val existing = existingFields.find { it.fieldKey.equals(trimmedKey, ignoreCase = true) }
        if (existing != null) {
            fieldDao.updateField(
                existing.copy(
                    fieldValue = fieldValue?.trim(),
                    groupName = groupName?.trim()?.ifBlank { null } ?: existing.groupName
                )
            )
        } else {
            val maxSort = existingFields.maxOfOrNull { it.sortOrder ?: 0 } ?: 0
            fieldDao.insertField(
                SiteFieldEntity(
                    sapId = sapId,
                    fieldKey = trimmedKey,
                    fieldValue = fieldValue?.trim(),
                    groupName = groupName?.trim()?.ifBlank { null } ?: "GENERAL SPECIFICATIONS",
                    sortOrder = maxSort + 1
                )
            )
        }
        val site = siteDao.getSiteBySapId(sapId)
        if (site != null) {
            siteDao.updateSite(site.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun replaceSiteSpecs(sapId: String, fields: List<SiteFieldEntity>) {
        fieldDao.replaceFieldsForSite(sapId, fields)
        val site = siteDao.getSiteBySapId(sapId)
        if (site != null) {
            siteDao.updateSite(site.copy(updatedAt = System.currentTimeMillis()))
            try {
                val fieldText = fields.joinToString(" ") { "${it.fieldKey} ${it.fieldValue ?: ""}" }
                siteDao.deleteFts(site.sapNorm)
                siteDao.insertFts(
                    listOf(
                        SiteFts(
                            sapNorm = site.sapNorm,
                            name = site.name,
                            address = site.address ?: site.city ?: site.district,
                            fieldText = fieldText
                        )
                    )
                )
            } catch (_: Exception) {}
        }
    }

    suspend fun deleteSiteSpec(fieldId: Long, sapId: String) {
        fieldDao.deleteFieldById(fieldId)
        val site = siteDao.getSiteBySapId(sapId)
        if (site != null) {
            siteDao.updateSite(site.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun tombstoneSite(sapId: String, tombstoneTime: Long) {
        val site = siteDao.getSiteBySapId(sapId)
        if (site != null) {
            siteDao.updateSite(site.copy(deletedAt = tombstoneTime))
        }
    }

    suspend fun deleteSampleSites() {
        siteDao.deleteSampleSites()
    }

    suspend fun deleteAllSites() {
        siteDao.deleteAllSites()
        siteDao.clearFts()
    }

    // --- Universal Specifications Template Methods ---

    fun observeUniversalFields(): Flow<List<UniversalFieldEntity>> =
        universalFieldDao.observeAllUniversalFields()

    suspend fun getUniversalFields(): List<UniversalFieldEntity> {
        val existing = universalFieldDao.getAllUniversalFields()
        if (existing.isEmpty()) {
            seedDefaultUniversalFields()
            return universalFieldDao.getAllUniversalFields()
        }
        return existing
    }

    suspend fun saveUniversalField(field: UniversalFieldEntity) {
        universalFieldDao.insertOrUpdateField(field)
    }

    suspend fun deleteUniversalField(fieldKey: String) {
        universalFieldDao.deleteField(fieldKey)
    }

    suspend fun deleteUniversalSection(groupName: String) {
        universalFieldDao.deleteSection(groupName)
    }

    suspend fun replaceAllUniversalFields(fields: List<UniversalFieldEntity>) {
        universalFieldDao.replaceAll(fields)
    }

    suspend fun seedDefaultUniversalFields() {
        val defaults = listOf(
            UniversalFieldEntity("SMPS MAKE & MODEL", "POWER", null, 1),
            UniversalFieldEntity("SMPS CAPACITY", "POWER", null, 2),
            UniversalFieldEntity("RECTIFIER MODULE COUNT", "POWER", null, 3),
            UniversalFieldEntity("EB SANCTIONED LOAD", "POWER", null, 4),
            UniversalFieldEntity("BATTERY BANK CAPACITY", "BATTERY", null, 5),
            UniversalFieldEntity("BATTERY BACKUP HOURS", "BATTERY", null, 6),
            UniversalFieldEntity("BATTERY BANK MAKE", "BATTERY", null, 7),
            UniversalFieldEntity("DG RATING KVA", "DG", null, 8),
            UniversalFieldEntity("DG FUEL TANK CAPACITY", "DG", null, 9),
            UniversalFieldEntity("DG BATTERY STATUS", "DG", null, 10),
            UniversalFieldEntity("TRANSMISSION MEDIA", "TRANSMISSION", null, 11),
            UniversalFieldEntity("OPTICAL FIBRE STATUS", "TRANSMISSION", null, 12),
            UniversalFieldEntity("MW ANTENNA SPEC", "TRANSMISSION", null, 13),
            UniversalFieldEntity("TOWER HEIGHT", "CIVIL", null, 14),
            UniversalFieldEntity("AC PRECISION UNITS", "ENVIRONMENT", null, 15),
            UniversalFieldEntity("FIRE EXTINGUISHER EXPIRY", "SAFETY", null, 16),
            UniversalFieldEntity("SECURITY GUARD CONTACT", "SECURITY", null, 17)
        )
        universalFieldDao.insertOrUpdateFields(defaults)
    }
}
