package app.telecom.sitetracker.data.merger

import androidx.room.withTransaction
import app.telecom.sitetracker.data.db.AppDatabase
import app.telecom.sitetracker.data.entity.SiteEntity
import app.telecom.sitetracker.data.entity.SiteFieldEntity
import app.telecom.sitetracker.data.entity.SiteFts
import app.telecom.sitetracker.data.import.ColumnMapper
import app.telecom.sitetracker.data.import.TargetField
import app.telecom.sitetracker.util.AppClock
import app.telecom.sitetracker.util.Geo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class VlookupEngine(
    private val db: AppDatabase,
    private val clock: AppClock
) {
    private val siteDao = db.siteDao()
    private val fieldDao = db.fieldDao()

    companion object {
        private val KEY_ALIASES = listOf(
            "sap id", "sap_id", "sapid", "site id", "site_id", "sitecode",
            "site code", "tower id", "site no", "site_no", "sap"
        )

        fun detectKeyColumn(headers: List<String>): Int {
            for ((index, header) in headers.withIndex()) {
                val norm = ColumnMapper.normalizeHeader(header)
                if (KEY_ALIASES.any { norm == it || norm.contains(it) }) {
                    return index
                }
            }
            return if (headers.isNotEmpty()) 0 else -1
        }

        fun autoMapColumns(headers: List<String>, keyIndex: Int): List<VlookupColumnMapping> {
            val list = mutableListOf<VlookupColumnMapping>()
            for ((index, header) in headers.withIndex()) {
                if (index == keyIndex) {
                    list.add(
                        VlookupColumnMapping(
                            columnIndex = index,
                            sourceHeader = header,
                            targetField = TargetField.SAP_ID
                        )
                    )
                    continue
                }

                val standardMatch = ColumnMapper.matchField(header)
                if (standardMatch != null && standardMatch != TargetField.SAP_ID && standardMatch != TargetField.IGNORE) {
                    list.add(
                        VlookupColumnMapping(
                            columnIndex = index,
                            sourceHeader = header,
                            targetField = standardMatch
                        )
                    )
                } else {
                    // Map as equipment specification
                    val group = inferSpecGroup(header)
                    list.add(
                        VlookupColumnMapping(
                            columnIndex = index,
                            sourceHeader = header,
                            targetField = TargetField.CUSTOM_SPEC,
                            specGroup = group,
                            specKey = header.trim().uppercase(Locale.ROOT)
                        )
                    )
                }
            }
            return list
        }

        private fun inferSpecGroup(header: String): String {
            val h = header.uppercase(Locale.ROOT)
            return when {
                h.contains("DG") || h.contains("DIESEL") || h.contains("GENSET") || h.contains("FUEL") -> "DG"
                h.contains("BATT") || h.contains("SOH") || h.contains("AH") || h.contains("LITHIUM") || h.contains("VRLA") -> "BATTERY"
                h.contains("SMPS") || h.contains("RECTIFIER") || h.contains("POWER") || h.contains("EB") || h.contains("TRANSFORMER") -> "POWER"
                h.contains("RAN") || h.contains("ANTENNA") || h.contains("TILT") || h.contains("AZIMUTH") || h.contains("RRU") || h.contains("RET") -> "RAN"
                h.contains("FIBRE") || h.contains("FIBER") || h.contains("TRANSMISSION") || h.contains("MW") || h.contains("MICROWAVE") || h.contains("OFC") -> "TRANSMISSION"
                h.contains("SAFETY") || h.contains("FIRE") || h.contains("EXTINGUISHER") -> "SAFETY"
                h.contains("AC") || h.contains("HVAC") || h.contains("COOLING") || h.contains("TEMP") -> "ENVIRONMENT"
                h.contains("SECURITY") || h.contains("GUARD") || h.contains("CCTV") -> "SECURITY"
                h.contains("TOWER") || h.contains("HEIGHT") || h.contains("CIVIL") || h.contains("SHELTER") -> "CIVIL"
                else -> "EQUIPMENT"
            }
        }
    }

    suspend fun generatePreview(
        rows: List<List<String>>,
        keyIndex: Int,
        mappings: List<VlookupColumnMapping>,
        mergeStrategy: MergeStrategy,
        overwritePolicy: OverwritePolicy
    ): VlookupPreview = withContext(Dispatchers.IO) {
        val existingSites = siteDao.getAllActiveSitesList()
        val siteByNorm = existingSites.associateBy { it.sapNorm }

        var matchedCount = 0
        var unmatchedCount = 0
        var totalFieldsToUpdate = 0
        val sampleDiffs = mutableListOf<RowDiffPreview>()

        val activeMappings = mappings.filter { it.columnIndex != keyIndex && it.targetField != TargetField.IGNORE }

        for (row in rows) {
            val rawKey = row.getOrNull(keyIndex)?.trim() ?: ""
            if (rawKey.isEmpty()) continue

            val normKey = SiteEntity.normalize(rawKey)
            if (normKey.isEmpty()) continue

            val existingSite = siteByNorm[normKey]
            val rowDiffs = mutableListOf<FieldDiff>()

            if (existingSite != null) {
                matchedCount++
                // Calculate field diffs for existing site
                for (mapping in activeMappings) {
                    val incomingVal = row.getOrNull(mapping.columnIndex)?.trim()
                    if (incomingVal.isNullOrEmpty()) continue

                    val fieldName = when (mapping.targetField) {
                        TargetField.CUSTOM_SPEC -> "${mapping.specGroup ?: "SPEC"} • ${mapping.specKey}"
                        else -> mapping.targetField.displayName
                    }

                    val oldVal = when (mapping.targetField) {
                        TargetField.NAME -> existingSite.name
                        TargetField.LAT -> existingSite.lat?.toString()
                        TargetField.LNG -> existingSite.lng?.toString()
                        TargetField.CIRCLE -> existingSite.circle
                        TargetField.REGION -> existingSite.region
                        TargetField.STATE -> existingSite.state
                        TargetField.DISTRICT -> existingSite.district
                        TargetField.CITY -> existingSite.city
                        TargetField.TECHNOLOGY -> existingSite.technology
                        TargetField.SITE_TYPE -> existingSite.siteType
                        TargetField.STATUS -> existingSite.status
                        TargetField.ADDRESS -> existingSite.address
                        TargetField.NOTES -> existingSite.notes
                        TargetField.TAGS -> existingSite.tags
                        TargetField.CUSTOM_SPEC -> null // will be evaluated at merge time
                        else -> null
                    }

                    val shouldApply = when (overwritePolicy) {
                        OverwritePolicy.OVERWRITE_ALL -> true
                        OverwritePolicy.FILL_BLANKS_ONLY -> oldVal.isNullOrBlank()
                    }

                    if (shouldApply) {
                        rowDiffs.add(FieldDiff(fieldName = fieldName, oldValue = oldVal, newValue = incomingVal))
                    }
                }
            } else {
                unmatchedCount++
                if (mergeStrategy == MergeStrategy.UPDATE_AND_CREATE_NEW) {
                    for (mapping in activeMappings) {
                        val incomingVal = row.getOrNull(mapping.columnIndex)?.trim()
                        if (!incomingVal.isNullOrEmpty()) {
                            val fieldName = when (mapping.targetField) {
                                TargetField.CUSTOM_SPEC -> "${mapping.specGroup ?: "SPEC"} • ${mapping.specKey}"
                                else -> mapping.targetField.displayName
                            }
                            rowDiffs.add(FieldDiff(fieldName = fieldName, oldValue = null, newValue = incomingVal))
                        }
                    }
                }
            }

            totalFieldsToUpdate += rowDiffs.size

            if (sampleDiffs.size < 10 && rowDiffs.isNotEmpty()) {
                sampleDiffs.add(
                    RowDiffPreview(
                        sapId = rawKey,
                        siteName = existingSite?.name,
                        isNewSite = (existingSite == null),
                        diffs = rowDiffs
                    )
                )
            }
        }

        VlookupPreview(
            totalRows = rows.size,
            matchedSitesCount = matchedCount,
            unmatchedRowsCount = unmatchedCount,
            mappedColumnsCount = activeMappings.size,
            estimatedFieldsToUpdate = totalFieldsToUpdate,
            samplePreviews = sampleDiffs
        )
    }

    suspend fun executeMerge(
        rows: List<List<String>>,
        keyIndex: Int,
        mappings: List<VlookupColumnMapping>,
        mergeStrategy: MergeStrategy,
        overwritePolicy: OverwritePolicy,
        onProgress: ((processed: Int, total: Int) -> Unit)? = null
    ): VlookupResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var sitesUpdated = 0
        var sitesCreated = 0
        var specsUpdated = 0
        var processedCount = 0

        val activeMappings = mappings.filter { it.columnIndex != keyIndex && it.targetField != TargetField.IGNORE }

        db.withTransaction {
            val existingSites = siteDao.getAllActiveSitesList()
            val siteByNorm = existingSites.associateBy { it.sapNorm }.toMutableMap()

            for (row in rows) {
                processedCount++
                if (processedCount % 50 == 0) {
                    onProgress?.invoke(processedCount, rows.size)
                }

                val rawKey = row.getOrNull(keyIndex)?.trim() ?: ""
                if (rawKey.isEmpty()) continue

                val normKey = SiteEntity.normalize(rawKey)
                if (normKey.isEmpty()) continue

                val existing = siteByNorm[normKey]

                if (existing != null) {
                    var modifiedSite: SiteEntity = existing
                    var siteAttributeChanged = false
                    val sapId = existing.sapId

                    // Core attributes
                    for (mapping in activeMappings) {
                        val rawVal = row.getOrNull(mapping.columnIndex)?.trim()
                        if (rawVal.isNullOrEmpty()) continue

                        val shouldUpdate = when (overwritePolicy) {
                            OverwritePolicy.OVERWRITE_ALL -> true
                            OverwritePolicy.FILL_BLANKS_ONLY -> isCoreFieldBlank(existing, mapping.targetField)
                        }

                        if (shouldUpdate) {
                            val (updated, changed) = applyCoreField(modifiedSite, mapping.targetField, rawVal)
                            if (changed) {
                                modifiedSite = updated
                                siteAttributeChanged = true
                            }
                        }
                    }

                    if (siteAttributeChanged) {
                        modifiedSite = modifiedSite.copy(updatedAt = clock.currentTimeMillis())
                        siteDao.updateSite(modifiedSite)
                        siteByNorm[normKey] = modifiedSite
                        sitesUpdated++
                    }

                    // Specs / Custom Fields
                    val specMappings = activeMappings.filter { it.targetField == TargetField.CUSTOM_SPEC }
                    if (specMappings.isNotEmpty()) {
                        val currentSpecs = fieldDao.getFieldsForSite(sapId).toMutableList()
                        var specsChanged = false

                        for (specMap in specMappings) {
                            val rawVal = row.getOrNull(specMap.columnIndex)?.trim()
                            if (rawVal.isNullOrEmpty()) continue

                            val key = specMap.specKey.trim()
                            val group = specMap.specGroup?.trim() ?: "EQUIPMENT"
                            val existingField = currentSpecs.find { it.fieldKey.equals(key, ignoreCase = true) }

                            if (existingField != null) {
                                val canOverwrite = when (overwritePolicy) {
                                    OverwritePolicy.OVERWRITE_ALL -> true
                                    OverwritePolicy.FILL_BLANKS_ONLY -> existingField.fieldValue.isNullOrBlank()
                                }
                                if (canOverwrite && existingField.fieldValue != rawVal) {
                                    fieldDao.updateField(existingField.copy(fieldValue = rawVal))
                                    specsUpdated++
                                    specsChanged = true
                                }
                            } else {
                                // Add new field
                                val newField = SiteFieldEntity(
                                    id = 0L,
                                    sapId = sapId,
                                    fieldKey = key,
                                    fieldValue = rawVal,
                                    groupName = group,
                                    sortOrder = (currentSpecs.maxOfOrNull { it.sortOrder ?: 0 } ?: 0) + 1
                                )
                                fieldDao.insertField(newField)
                                currentSpecs.add(newField)
                                specsUpdated++
                                specsChanged = true
                            }
                        }

                        if (specsChanged || siteAttributeChanged) {
                            updateFtsIndex(modifiedSite, currentSpecs)
                        }
                    }
                } else if (mergeStrategy == MergeStrategy.UPDATE_AND_CREATE_NEW) {
                    // Create new site
                    var newSite = SiteEntity(
                        sapId = rawKey,
                        sapNorm = normKey,
                        createdAt = clock.currentTimeMillis(),
                        updatedAt = clock.currentTimeMillis()
                    )

                    for (mapping in activeMappings) {
                        val rawVal = row.getOrNull(mapping.columnIndex)?.trim()
                        if (!rawVal.isNullOrEmpty()) {
                            val (updated, _) = applyCoreField(newSite, mapping.targetField, rawVal)
                            newSite = updated
                        }
                    }

                    siteDao.insertSite(newSite)
                    siteByNorm[normKey] = newSite
                    sitesCreated++

                    // Insert specs
                    val specMappings = activeMappings.filter { it.targetField == TargetField.CUSTOM_SPEC }
                    val newSpecs = mutableListOf<SiteFieldEntity>()
                    for ((specIdx, specMap) in specMappings.withIndex()) {
                        val rawVal = row.getOrNull(specMap.columnIndex)?.trim()
                        if (!rawVal.isNullOrEmpty()) {
                            newSpecs.add(
                                SiteFieldEntity(
                                    id = 0L,
                                    sapId = rawKey,
                                    fieldKey = specMap.specKey.trim(),
                                    fieldValue = rawVal,
                                    groupName = specMap.specGroup?.trim() ?: "EQUIPMENT",
                                    sortOrder = specIdx + 1
                                )
                            )
                        }
                    }
                    if (newSpecs.isNotEmpty()) {
                        fieldDao.insertFields(newSpecs)
                        specsUpdated += newSpecs.size
                    }

                    updateFtsIndex(newSite, newSpecs)
                }
            }
        }

        onProgress?.invoke(rows.size, rows.size)
        val elapsed = System.currentTimeMillis() - startTime

        VlookupResult(
            totalProcessed = processedCount,
            sitesUpdated = sitesUpdated,
            sitesCreated = sitesCreated,
            specsUpdated = specsUpdated,
            elapsedMs = elapsed
        )
    }

    private fun isCoreFieldBlank(site: SiteEntity, target: TargetField): Boolean {
        return when (target) {
            TargetField.NAME -> site.name.isNullOrBlank()
            TargetField.LAT -> site.lat == null
            TargetField.LNG -> site.lng == null
            TargetField.CIRCLE -> site.circle.isNullOrBlank()
            TargetField.REGION -> site.region.isNullOrBlank()
            TargetField.STATE -> site.state.isNullOrBlank()
            TargetField.DISTRICT -> site.district.isNullOrBlank()
            TargetField.CITY -> site.city.isNullOrBlank()
            TargetField.TECHNOLOGY -> site.technology.isNullOrBlank()
            TargetField.SITE_TYPE -> site.siteType.isNullOrBlank()
            TargetField.STATUS -> site.status.isNullOrBlank()
            TargetField.ADDRESS -> site.address.isNullOrBlank()
            TargetField.TAGS -> site.tags.isNullOrBlank()
            TargetField.NOTES -> site.notes.isNullOrBlank()
            else -> false
        }
    }

    private fun applyCoreField(site: SiteEntity, target: TargetField, value: String): Pair<SiteEntity, Boolean> {
        val trimmed = value.trim()
        return when (target) {
            TargetField.NAME -> site.copy(name = trimmed) to (site.name != trimmed)
            TargetField.LAT -> {
                val parsed = Geo.parseCoordinate(trimmed)
                if (parsed != null && parsed in -90.0..90.0) {
                    site.copy(lat = parsed) to (site.lat != parsed)
                } else site to false
            }
            TargetField.LNG -> {
                val parsed = Geo.parseCoordinate(trimmed)
                if (parsed != null && parsed in -180.0..180.0) {
                    site.copy(lng = parsed) to (site.lng != parsed)
                } else site to false
            }
            TargetField.CIRCLE -> site.copy(circle = trimmed) to (site.circle != trimmed)
            TargetField.REGION -> site.copy(region = trimmed) to (site.region != trimmed)
            TargetField.STATE -> site.copy(state = trimmed) to (site.state != trimmed)
            TargetField.DISTRICT -> site.copy(district = trimmed) to (site.district != trimmed)
            TargetField.CITY -> site.copy(city = trimmed) to (site.city != trimmed)
            TargetField.TECHNOLOGY -> site.copy(technology = trimmed) to (site.technology != trimmed)
            TargetField.SITE_TYPE -> site.copy(siteType = trimmed) to (site.siteType != trimmed)
            TargetField.STATUS -> site.copy(status = trimmed) to (site.status != trimmed)
            TargetField.ADDRESS -> site.copy(address = trimmed) to (site.address != trimmed)
            TargetField.TAGS -> site.copy(tags = trimmed) to (site.tags != trimmed)
            TargetField.NOTES -> site.copy(notes = trimmed) to (site.notes != trimmed)
            else -> site to false
        }
    }

    private suspend fun updateFtsIndex(site: SiteEntity, specs: List<SiteFieldEntity>) {
        try {
            val fieldText = specs.joinToString(" ") { "${it.fieldKey} ${it.fieldValue ?: ""}" }
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
