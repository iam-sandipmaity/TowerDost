package app.telecom.sitetracker.data.import

import androidx.room.withTransaction
import app.telecom.sitetracker.data.db.AppDatabase
import app.telecom.sitetracker.data.entity.SiteEntity
import app.telecom.sitetracker.data.entity.SiteFieldEntity
import app.telecom.sitetracker.data.entity.SiteFts
import app.telecom.sitetracker.util.AppClock
import app.telecom.sitetracker.util.Geo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

enum class MergePolicy(val displayName: String, val description: String) {
    UPDATE_AND_ADD(
        "Update & Add",
        "Updates existing sites and adds new ones. Keeps existing sites not present in file."
    ),
    ADD_NEW_ONLY(
        "Add New Only",
        "Adds new sites only. Ignores sites that already exist in database."
    ),
    REPLACE_ALL(
        "Replace All",
        "Overwrites matched sites and archives/tombstones sites not in this file."
    )
}

data class ValidationSummary(
    val totalRows: Int,
    val validSites: Int,
    val duplicateSaps: Int,
    val existingSitesCount: Int,
    val newSitesCount: Int,
    val coordinateWarnings: Int,
    val specColumns: List<String>
)

data class ImportProgress(
    val processedCount: Int,
    val totalCount: Int,
    val currentSapId: String,
    val percent: Int
)

data class ImportResult(
    val totalProcessed: Int,
    val sitesInserted: Int,
    val sitesUpdated: Int,
    val specsAdded: Int,
    val elapsedMs: Long
)

class Importer(
    private val db: AppDatabase,
    private val clock: AppClock
) {
    private val siteDao = db.siteDao()
    private val fieldDao = db.fieldDao()

    /**
     * Pre-validates rows without altering database state.
     */
    suspend fun validate(
        rows: List<List<String>>,
        mappings: List<ColumnMapping>
    ): ValidationSummary = withContext(Dispatchers.IO) {
        val sapCol = mappings.firstOrNull { it.targetField == TargetField.SAP_ID }?.columnIndex ?: -1
        val latCol = mappings.firstOrNull { it.targetField == TargetField.LAT }?.columnIndex ?: -1
        val lngCol = mappings.firstOrNull { it.targetField == TargetField.LNG }?.columnIndex ?: -1

        val specCols = mappings.filter { it.targetField == TargetField.CUSTOM_SPEC }.map { it.sourceHeader }

        val seenNorms = mutableSetOf<String>()
        var duplicates = 0
        var validCount = 0
        var coordWarnings = 0
        var existingCount = 0

        for (row in rows) {
            val rawSap = if (sapCol in row.indices) row[sapCol].trim() else ""
            if (rawSap.isNotBlank()) {
                val norm = SiteEntity.normalizeSapId(rawSap)
                if (norm.isNotBlank()) {
                    validCount++
                    if (!seenNorms.add(norm)) {
                        duplicates++
                    } else {
                        val existing = siteDao.getSiteBySapNorm(norm)
                        if (existing != null) {
                            existingCount++
                        }
                    }
                }
            }

            val rawLat = if (latCol in row.indices) row[latCol] else null
            val rawLng = if (lngCol in row.indices) row[lngCol] else null
            val lat = Geo.parseCoordinate(rawLat)
            val lng = Geo.parseCoordinate(rawLng)

            if (lat != null && lng != null) {
                if (!Geo.isWithinIndia(lat, lng)) {
                    coordWarnings++
                }
            } else if (!rawLat.isNullOrBlank() || !rawLng.isNullOrBlank()) {
                coordWarnings++
            }
        }

        val newCount = seenNorms.size - existingCount

        ValidationSummary(
            totalRows = rows.size,
            validSites = validCount,
            duplicateSaps = duplicates,
            existingSitesCount = existingCount,
            newSitesCount = maxOf(0, newCount),
            coordinateWarnings = coordWarnings,
            specColumns = specCols
        )
    }

    /**
     * Executes the chunked import using Room transactions.
     */
    suspend fun executeImport(
        rows: List<List<String>>,
        mappings: List<ColumnMapping>,
        policy: MergePolicy,
        sourceFileName: String,
        onProgress: (ImportProgress) -> Unit
    ): ImportResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val now = clock.currentTimeMillis()

        val sapMapping = mappings.firstOrNull { it.targetField == TargetField.SAP_ID }
            ?: throw IllegalArgumentException("No SAP ID column mapped")

        val customSpecMappings = mappings.filter { it.targetField == TargetField.CUSTOM_SPEC }

        val totalRows = rows.size
        var insertedCount = 0
        var updatedCount = 0
        var totalSpecsCount = 0

        val importedSapNorms = mutableSetOf<String>()
        val importedSapIds = mutableSetOf<String>()

        // Process in chunks of 300 to balance memory and transaction throughput
        val chunkSize = 300
        val chunks = rows.chunked(chunkSize)

        for (chunk in chunks) {
            val siteEntities = mutableListOf<SiteEntity>()
            val fieldEntities = mutableListOf<SiteFieldEntity>()
            val ftsEntities = mutableListOf<SiteFts>()
            val sapIdsInChunk = mutableListOf<String>()

            for (row in chunk) {
                val rawSap = if (sapMapping.columnIndex in row.indices) row[sapMapping.columnIndex].trim() else ""
                if (rawSap.isBlank()) continue

                val sapId = rawSap.uppercase(Locale.US)
                val sapNorm = SiteEntity.normalizeSapId(sapId)
                if (sapNorm.isBlank()) continue

                importedSapNorms.add(sapNorm)
                importedSapIds.add(sapId)
                sapIdsInChunk.add(sapId)

                // Check existing site
                val existing = siteDao.getSiteBySapNorm(sapNorm)
                if (existing != null && policy == MergePolicy.ADD_NEW_ONLY) {
                    continue
                }

                if (existing != null) {
                    updatedCount++
                } else {
                    insertedCount++
                }

                // Extract standard columns
                var name: String? = existing?.name
                var lat: Double? = existing?.lat
                var lng: Double? = existing?.lng
                var circle: String? = existing?.circle
                var region: String? = existing?.region
                var state: String? = existing?.state
                var district: String? = existing?.district
                var city: String? = existing?.city
                var tech: String? = existing?.technology
                var siteType: String? = existing?.siteType
                var status: String? = existing?.status
                var address: String? = existing?.address
                var tags: String? = existing?.tags
                var notes: String? = existing?.notes

                for (mapping in mappings) {
                    if (mapping.columnIndex !in row.indices) continue
                    val cellVal = row[mapping.columnIndex].trim()
                    if (cellVal.isEmpty()) continue

                    when (mapping.targetField) {
                        TargetField.NAME -> name = cellVal
                        TargetField.LAT -> Geo.parseCoordinate(cellVal)?.let { lat = it }
                        TargetField.LNG -> Geo.parseCoordinate(cellVal)?.let { lng = it }
                        TargetField.CIRCLE -> circle = cellVal
                        TargetField.REGION -> region = cellVal
                        TargetField.STATE -> state = cellVal
                        TargetField.DISTRICT -> district = cellVal
                        TargetField.CITY -> city = cellVal
                        TargetField.TECHNOLOGY -> tech = cellVal
                        TargetField.SITE_TYPE -> siteType = cellVal
                        TargetField.STATUS -> status = cellVal
                        TargetField.ADDRESS -> address = cellVal
                        TargetField.TAGS -> tags = cellVal
                        TargetField.NOTES -> notes = cellVal
                        else -> { /* Ignore / Custom specs handled below */ }
                    }
                }

                val finalSite = SiteEntity(
                    sapId = sapId,
                    sapNorm = sapNorm,
                    name = name,
                    lat = lat,
                    lng = lng,
                    circle = circle,
                    region = region,
                    state = state,
                    district = district,
                    city = city,
                    technology = tech,
                    siteType = siteType,
                    status = status ?: "Active",
                    address = address,
                    tags = tags,
                    notes = notes,
                    sourceFile = sourceFileName,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                    deletedAt = null
                )
                siteEntities.add(finalSite)

                // Extract custom equipment specs
                val specsSummaryBuilder = StringBuilder()
                var sortOrder = 0
                for (specMapping in customSpecMappings) {
                    if (specMapping.columnIndex in row.indices) {
                        val specVal = row[specMapping.columnIndex].trim()
                        if (specVal.isNotEmpty()) {
                            val fieldEntity = SiteFieldEntity(
                                sapId = sapId,
                                fieldKey = specMapping.specKey,
                                fieldValue = specVal,
                                groupName = specMapping.specGroup ?: "General Specifications",
                                sortOrder = sortOrder++
                            )
                            fieldEntities.add(fieldEntity)
                            totalSpecsCount++

                            specsSummaryBuilder.append(specMapping.specKey)
                                .append(": ")
                                .append(specVal)
                                .append(" ")
                        }
                    }
                }

                // Add to FTS
                ftsEntities.add(
                    SiteFts(
                        sapNorm = sapNorm,
                        name = name,
                        address = address ?: city ?: district,
                        fieldText = specsSummaryBuilder.toString()
                    )
                )

                val processedSoFar = siteEntities.size + (chunks.indexOf(chunk) * chunkSize)
                val percent = if (totalRows > 0) (processedSoFar * 100) / totalRows else 100
                onProgress(ImportProgress(processedSoFar, totalRows, sapId, percent))
            }

            // Persist chunk inside a Room transaction
            db.withTransaction {
                if (siteEntities.isNotEmpty()) {
                    siteDao.insertSites(siteEntities)
                }
                if (sapIdsInChunk.isNotEmpty()) {
                    fieldDao.deleteFieldsForSites(sapIdsInChunk)
                }
                if (fieldEntities.isNotEmpty()) {
                    fieldDao.insertFields(fieldEntities)
                }
                if (ftsEntities.isNotEmpty()) {
                    siteDao.insertFts(ftsEntities)
                }
            }
        }

        // Handle REPLACE_ALL policy: tombstone sites not in the imported file
        if (policy == MergePolicy.REPLACE_ALL && importedSapIds.isNotEmpty()) {
            db.withTransaction {
                siteDao.tombstoneSitesNotIn(importedSapIds.toList(), now)
            }
        }

        val elapsed = System.currentTimeMillis() - startTime
        onProgress(ImportProgress(totalRows, totalRows, "Complete", 100))

        ImportResult(
            totalProcessed = importedSapIds.size,
            sitesInserted = insertedCount,
            sitesUpdated = updatedCount,
            specsAdded = totalSpecsCount,
            elapsedMs = elapsed
        )
    }
}
