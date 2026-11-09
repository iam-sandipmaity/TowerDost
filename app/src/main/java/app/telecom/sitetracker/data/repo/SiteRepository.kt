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
import java.util.Locale
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
     * Searches sites using direct SQL matching across core attributes and equipment specs,
     * combined with tokenized full-text search (FTS).
     * Returns all matching sites deduplicated by sapId, ordered by relevance.
     */
    suspend fun searchSites(query: String): List<SiteEntity> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val normalizedQuery = SiteEntity.normalize(trimmed)

        // 1. Direct search querying sites table and site_fields specs table
        val directResults = try {
            siteDao.searchDirect(trimmed, normalizedQuery)
        } catch (_: Exception) {
            emptyList()
        }

        // 2. Full-Text Search across aggregated specs, address, name
        val ftsResults = try {
            val ftsQuery = buildFtsQuery(trimmed)
            if (ftsQuery.isNotBlank()) {
                siteDao.searchFts(ftsQuery)
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }

        // Combine direct results (prioritized by exact/prefix ranking) with FTS matches
        val combined = LinkedHashMap<String, SiteEntity>()
        for (site in directResults) {
            combined[site.sapId] = site
        }
        for (site in ftsResults) {
            if (!combined.containsKey(site.sapId)) {
                combined[site.sapId] = site
            }
        }
        return combined.values.toList()
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

    suspend fun createSite(
        site: SiteEntity,
        specs: List<SiteFieldEntity> = emptyList()
    ): Result<Unit> {
        val trimmedSap = site.sapId.trim()
        if (trimmedSap.isEmpty()) {
            return Result.failure(IllegalArgumentException("SAP ID cannot be blank"))
        }

        val sapNorm = SiteEntity.normalize(trimmedSap)
        if (sapNorm.isEmpty()) {
            return Result.failure(IllegalArgumentException("SAP ID must contain alphanumeric characters"))
        }

        val existing = siteDao.getSiteBySapId(trimmedSap) ?: siteDao.getSiteBySapNorm(sapNorm)
        if (existing != null) {
            return Result.failure(IllegalStateException("Site with SAP ID '$trimmedSap' already exists"))
        }

        if (site.lat != null && (site.lat < -90.0 || site.lat > 90.0)) {
            return Result.failure(IllegalArgumentException("Latitude must be between -90 and 90 degrees"))
        }
        if (site.lng != null && (site.lng < -180.0 || site.lng > 180.0)) {
            return Result.failure(IllegalArgumentException("Longitude must be between -180 and 180 degrees"))
        }

        val now = System.currentTimeMillis()
        val siteToInsert = site.copy(
            sapId = trimmedSap,
            sapNorm = sapNorm,
            createdAt = now,
            updatedAt = now,
            deletedAt = null
        )

        siteDao.insertSite(siteToInsert)

        val validSpecs = specs.mapNotNull { spec ->
            val key = spec.fieldKey.trim()
            val value = spec.fieldValue?.trim()
            if (key.isNotEmpty() && !value.isNullOrEmpty()) {
                spec.copy(
                    sapId = trimmedSap,
                    fieldKey = key,
                    fieldValue = value,
                    groupName = spec.groupName?.trim()?.ifBlank { "GENERAL" } ?: "GENERAL"
                )
            } else null
        }

        if (validSpecs.isNotEmpty()) {
            fieldDao.insertFields(validSpecs)
        }

        try {
            val fieldText = validSpecs.joinToString(" ") { "${it.fieldKey} ${it.fieldValue ?: ""}" }
            siteDao.deleteFts(sapNorm)
            siteDao.insertFts(
                listOf(
                    SiteFts(
                        sapNorm = sapNorm,
                        name = siteToInsert.name,
                        address = siteToInsert.address ?: siteToInsert.city ?: siteToInsert.district,
                        fieldText = fieldText
                    )
                )
            )
        } catch (_: Exception) {}

        return Result.success(Unit)
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
        siteDao.deleteSampleFts()
        fieldDao.deleteSampleFields()
        val totalSites = siteDao.getActiveSiteCount()
        if (totalSites == 0) {
            universalFieldDao.clearAll()
        } else {
            syncUniversalTemplateFromSites()
        }
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
        if (existing.isNotEmpty()) {
            return existing
        }
        val siteSpecs = getDistinctSpecsFromSites()
        if (siteSpecs.isNotEmpty()) {
            universalFieldDao.insertOrUpdateFields(siteSpecs)
            return universalFieldDao.getAllUniversalFields()
        }
        return emptyList()
    }

    suspend fun getAllUniversalFields(): List<UniversalFieldEntity> {
        return universalFieldDao.getAllUniversalFields()
    }

    suspend fun getDistinctSpecsFromSites(): List<UniversalFieldEntity> {
        val distinctSpecs = fieldDao.getDistinctSpecsFromSites()
        return distinctSpecs.mapIndexed { idx, spec ->
            UniversalFieldEntity(
                fieldKey = spec.fieldKey.trim().uppercase(Locale.US),
                groupName = spec.groupName.trim().uppercase(Locale.US),
                defaultValue = null,
                sortOrder = if (spec.minSort > 0) spec.minSort else idx + 1
            )
        }
    }

    suspend fun syncUniversalTemplateFromSites(): Int {
        val siteSpecs = getDistinctSpecsFromSites()
        if (siteSpecs.isNotEmpty()) {
            universalFieldDao.replaceAll(siteSpecs)
        }
        return siteSpecs.size
    }

    suspend fun countDistinctSiteSpecs(): Int {
        return fieldDao.getDistinctSpecsFromSites().size
    }

    suspend fun saveUniversalField(field: UniversalFieldEntity) {
        universalFieldDao.insertOrUpdateField(field)
    }

    suspend fun updateUniversalField(oldFieldKey: String, updatedField: UniversalFieldEntity) {
        if (!oldFieldKey.equals(updatedField.fieldKey, ignoreCase = true)) {
            universalFieldDao.deleteField(oldFieldKey)
        }
        universalFieldDao.insertOrUpdateField(updatedField)
        fieldDao.renameFieldKey(oldFieldKey, updatedField.fieldKey, updatedField.groupName)
    }

    suspend fun deleteUniversalField(fieldKey: String) {
        universalFieldDao.deleteField(fieldKey)
    }

    suspend fun deleteUniversalSection(groupName: String) {
        universalFieldDao.deleteSection(groupName)
    }

    suspend fun clearAllUniversalFields() {
        universalFieldDao.clearAll()
    }

    suspend fun replaceAllUniversalFields(fields: List<UniversalFieldEntity>) {
        universalFieldDao.replaceAll(fields)
    }

    suspend fun rebuildFtsIndex() {
        try {
            siteDao.clearFts()
            val allSites = siteDao.getAllActiveSitesList()
            if (allSites.isEmpty()) return
            val allFields = fieldDao.getAllFields().groupBy { it.sapId }
            val ftsList = allSites.map { site ->
                val fields = allFields[site.sapId] ?: emptyList()
                val fieldText = fields.joinToString(" ") { "${it.fieldKey} ${it.fieldValue ?: ""}" }
                SiteFts(
                    sapNorm = site.sapNorm,
                    name = site.name,
                    address = site.address ?: site.city ?: site.district,
                    fieldText = fieldText
                )
            }
            siteDao.insertFts(ftsList)
        } catch (_: Exception) {
            // Defensive against storage lock
        }
    }

    companion object {
        fun buildFtsQuery(raw: String): String {
            // Strip out SQLite FTS operator symbols to avoid syntax errors: ", *, -, :, ^, (, )
            val cleaned = raw.replace(Regex("[^a-zA-Z0-9\\s]"), " ").trim()
            val tokens = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (tokens.isEmpty()) return ""
            // Append * suffix wildcard to each token for prefix matching
            return tokens.joinToString(" ") { "$it*" }
        }
    }
}
