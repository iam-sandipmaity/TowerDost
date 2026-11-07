package app.telecom.sitetracker.data.import

import androidx.room.withTransaction
import app.telecom.sitetracker.data.db.AppDatabase
import app.telecom.sitetracker.data.entity.IncidentEntity
import app.telecom.sitetracker.data.entity.SiteEntity
import app.telecom.sitetracker.util.AppClock
import app.telecom.sitetracker.util.Dates
import app.telecom.sitetracker.util.Geo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

enum class LogTargetField(val displayName: String, val isRequired: Boolean) {
    SAP_ID("SAP ID", true),
    TITLE("Title / Alarm", true),
    TYPE("Type (Outage / Visit)", false),
    SEVERITY("Severity", false),
    CATEGORY("Category", false),
    STATUS("Status", false),
    DOWN_AT("Down / Start At", false),
    RESTORED_AT("Restored At", false),
    ENGINEER("Engineer / Tech", false),
    TICKET_REF("Ticket / TT Ref", false),
    ACTION_TAKEN("Action Taken", false),
    DESCRIPTION("Description / Remarks", false),
    LAT("Latitude", false),
    LNG("Longitude", false),
    ID("Log ID", false),
    IGNORE("Ignore", false);

    companion object {
        fun allFields(): List<LogTargetField> = listOf(
            SAP_ID, TITLE, TYPE, SEVERITY, CATEGORY, STATUS,
            DOWN_AT, RESTORED_AT, ENGINEER, TICKET_REF,
            ACTION_TAKEN, DESCRIPTION, LAT, LNG, ID, IGNORE
        )
    }
}

data class LogColumnMapping(
    val columnIndex: Int,
    val sourceHeader: String,
    var targetField: LogTargetField
)

data class LogImportResult(
    val totalProcessed: Int,
    val insertedCount: Int,
    val updatedCount: Int,
    val elapsedMs: Long
)

object LogColumnMapper {

    private val ALIASES: Map<LogTargetField, List<String>> = mapOf(
        LogTargetField.SAP_ID to listOf(
            "sap id", "sap_id", "site id", "site_id", "site code", "sitecode",
            "sapid", "tower id", "tower code", "site no", "site_no", "sap"
        ),
        LogTargetField.TITLE to listOf(
            "title", "alarm", "alarm name", "fault", "issue", "problem", "fault summary", "summary"
        ),
        LogTargetField.TYPE to listOf(
            "type", "incident type", "event type", "log type", "work type"
        ),
        LogTargetField.SEVERITY to listOf(
            "severity", "priority", "criticality", "level"
        ),
        LogTargetField.CATEGORY to listOf(
            "category", "cause", "root cause", "fault category", "class"
        ),
        LogTargetField.STATUS to listOf(
            "status", "incident status", "state", "operational state"
        ),
        LogTargetField.DOWN_AT to listOf(
            "down at", "down at (ist)", "down_at", "occurred at", "start time",
            "outage start", "outage start time", "down time", "alarm time", "created at", "date"
        ),
        LogTargetField.RESTORED_AT to listOf(
            "restored at", "restored at (ist)", "restored_at", "clearance time",
            "cleared at", "end time", "resolution time", "up at"
        ),
        LogTargetField.ENGINEER to listOf(
            "engineer", "technician", "technician name", "tech", "attended by",
            "assigned to", "fse", "lead"
        ),
        LogTargetField.TICKET_REF to listOf(
            "ticket ref", "ticket_ref", "tt no", "tt number", "ticket no",
            "incident id", "crm id", "ticket", "tt"
        ),
        LogTargetField.ACTION_TAKEN to listOf(
            "action taken", "action_taken", "action", "remedy", "remedy / work done",
            "work done", "corrective action", "resolution"
        ),
        LogTargetField.DESCRIPTION to listOf(
            "description", "notes", "remarks", "comment", "comments", "details"
        ),
        LogTargetField.LAT to listOf(
            "lat", "latitude", "gps lat", "gps_lat"
        ),
        LogTargetField.LNG to listOf(
            "lng", "long", "longitude", "gps lng", "gps_long"
        ),
        LogTargetField.ID to listOf(
            "log id", "id", "incident_id", "uuid"
        ),
        LogTargetField.IGNORE to listOf(
            "downtime hours", "downtime", "duration", "hours", "sla", "serial no", "sr no"
        )
    )

    fun normalizeHeader(raw: String): String {
        return raw.lowercase(Locale.US)
            .replace("\n", " ")
            .replace("\r", " ")
            .replace("[_-]".toRegex(), " ")
            .replace("[^a-z0-9 ]".toRegex(), "")
            .trim()
            .replace("\\s+".toRegex(), " ")
    }

    fun matchField(header: String): LogTargetField? {
        val norm = normalizeHeader(header)
        if (norm.isEmpty()) return null

        for ((field, aliases) in ALIASES) {
            for (alias in aliases) {
                if (norm == alias) {
                    return field
                }
            }
        }

        // Substring / boundary matches
        if (norm.contains("sap id") || norm.contains("site id") || norm.contains("tower id") || norm.contains("sapid")) {
            return LogTargetField.SAP_ID
        }
        if (norm.contains("down at") || norm.contains("outage start") || norm.contains("occurred at")) {
            return LogTargetField.DOWN_AT
        }
        if (norm.contains("restored at") || norm.contains("clearance time") || norm.contains("cleared at")) {
            return LogTargetField.RESTORED_AT
        }
        if (norm.contains("ticket") || norm.contains("tt no") || norm.contains("tt number")) {
            return LogTargetField.TICKET_REF
        }
        if (norm.contains("engineer") || norm.contains("technician")) {
            return LogTargetField.ENGINEER
        }
        if (norm.contains("action taken") || norm.contains("work done")) {
            return LogTargetField.ACTION_TAKEN
        }
        if (norm.contains("downtime hours") || norm.contains("downtime")) {
            return LogTargetField.IGNORE
        }

        return null
    }

    fun generateInitialMappings(headers: List<String>): List<LogColumnMapping> {
        val assigned = mutableSetOf<LogTargetField>()
        val result = mutableListOf<LogColumnMapping>()

        for (i in headers.indices) {
            val header = headers[i]
            val matched = matchField(header)

            val finalTarget = if (matched != null && (matched == LogTargetField.IGNORE || !assigned.contains(matched))) {
                if (matched != LogTargetField.IGNORE) assigned.add(matched)
                matched
            } else {
                LogTargetField.IGNORE
            }

            result.add(
                LogColumnMapping(
                    columnIndex = i,
                    sourceHeader = header,
                    targetField = finalTarget
                )
            )
        }
        return result
    }

    fun isMatch(
        existing: IncidentEntity,
        incomingId: String?,
        incomingSapId: String,
        incomingTimestamp: Long?,
        incomingTicketRef: String?
    ): Boolean {
        // 1. Direct ID match
        if (!incomingId.isNullOrBlank() && existing.id == incomingId.trim()) {
            return true
        }

        // 2. SAP ID must match
        val existingNorm = SiteEntity.normalizeSapId(existing.sapId)
        val incomingNorm = SiteEntity.normalizeSapId(incomingSapId)
        if (existingNorm != incomingNorm || existingNorm.isBlank()) {
            return false
        }

        // 3. Ticket Ref match (if both non-blank)
        if (!incomingTicketRef.isNullOrBlank() && !existing.ticketRef.isNullOrBlank()) {
            if (incomingTicketRef.trim().equals(existing.ticketRef.trim(), ignoreCase = true)) {
                return true
            }
        }

        // 4. Timestamp proximity match (within ±60 seconds / same minute)
        val existingTime = existing.downAt ?: existing.occurredAt
        if (incomingTimestamp != null && Dates.isWithinMinute(existingTime, incomingTimestamp)) {
            return true
        }

        return false
    }
}

class LogImporter(
    private val db: AppDatabase,
    private val clock: AppClock
) {
    private val incidentDao = db.incidentDao()

    suspend fun executeImport(
        rows: List<List<String>>,
        mappings: List<LogColumnMapping>,
        policy: MergePolicy,
        onProgress: ((processed: Int, total: Int) -> Unit)? = null
    ): LogImportResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val now = clock.currentTimeMillis()

        val sapMapping = mappings.firstOrNull { it.targetField == LogTargetField.SAP_ID }
            ?: throw IllegalArgumentException("No SAP ID column mapped")

        val totalRows = rows.size
        var insertedCount = 0
        var updatedCount = 0

        // If REPLACE_ALL policy, clear existing incidents first
        if (policy == MergePolicy.REPLACE_ALL) {
            db.withTransaction {
                incidentDao.deleteAllIncidents()
            }
        }

        // Cache existing incidents for matching
        val allExistingIncidents = if (policy != MergePolicy.REPLACE_ALL) {
            incidentDao.getAllIncidentsList().groupBy { SiteEntity.normalizeSapId(it.sapId) }
        } else {
            emptyMap()
        }

        val chunkSize = 200
        val chunks = rows.chunked(chunkSize)
        var processedTotal = 0

        for (chunk in chunks) {
            val entitiesToInsert = mutableListOf<IncidentEntity>()

            for (row in chunk) {
                processedTotal++
                val rawSap = if (sapMapping.columnIndex in row.indices) row[sapMapping.columnIndex].trim() else ""
                if (rawSap.isBlank()) continue

                val sapId = rawSap.uppercase(Locale.US)
                val sapNorm = SiteEntity.normalizeSapId(sapId)

                var id: String? = null
                var title: String? = null
                var typeStr: String? = null
                var severity: String? = null
                var category: String? = null
                var status: String? = null
                var downAtStr: String? = null
                var restoredAtStr: String? = null
                var engineer: String? = null
                var ticketRef: String? = null
                var actionTaken: String? = null
                var description: String? = null
                var lat: Double? = null
                var lng: Double? = null

                for (mapping in mappings) {
                    if (mapping.columnIndex !in row.indices) continue
                    val cell = row[mapping.columnIndex].trim()
                    if (cell.isEmpty()) continue

                    when (mapping.targetField) {
                        LogTargetField.ID -> id = cell
                        LogTargetField.TITLE -> title = cell
                        LogTargetField.TYPE -> typeStr = cell
                        LogTargetField.SEVERITY -> severity = cell
                        LogTargetField.CATEGORY -> category = cell
                        LogTargetField.STATUS -> status = cell
                        LogTargetField.DOWN_AT -> downAtStr = cell
                        LogTargetField.RESTORED_AT -> restoredAtStr = cell
                        LogTargetField.ENGINEER -> engineer = cell
                        LogTargetField.TICKET_REF -> ticketRef = cell
                        LogTargetField.ACTION_TAKEN -> actionTaken = cell
                        LogTargetField.DESCRIPTION -> description = cell
                        LogTargetField.LAT -> Geo.parseCoordinate(cell)?.let { lat = it }
                        LogTargetField.LNG -> Geo.parseCoordinate(cell)?.let { lng = it }
                        else -> {}
                    }
                }

                val downAt = Dates.parseFlexibleDateTime(downAtStr) ?: now
                val restoredAt = Dates.parseFlexibleDateTime(restoredAtStr)

                val isOutage = if (typeStr != null) {
                    val t = typeStr.lowercase(Locale.US)
                    if (t.contains("outage") || t.contains("down") || t.contains("fault") || t.contains("issue")) 1 else 0
                } else if (downAtStr != null && restoredAtStr != null) {
                    1
                } else {
                    0
                }

                // Check for match among existing incidents for this SAP ID
                val existingForSite = allExistingIncidents[sapNorm] ?: emptyList()
                val matched = existingForSite.firstOrNull {
                    LogColumnMapper.isMatch(it, id, sapId, downAt, ticketRef)
                }

                if (matched != null && policy == MergePolicy.ADD_NEW_ONLY) {
                    continue
                }

                val finalId = matched?.id ?: (id?.ifBlank { null } ?: UUID.randomUUID().toString())
                val finalTitle = title ?: (if (isOutage == 1) "Outage - $sapId" else "Site Activity - $sapId")
                val finalCategory = category ?: (if (isOutage == 1) "Issue" else "Maintenance")
                val finalSeverity = severity ?: (if (isOutage == 1) "Critical" else "Info")
                val finalStatus = status ?: (if (restoredAt != null) "Resolved" else if (isOutage == 1) "Open" else "Closed")

                if (matched != null) {
                    updatedCount++
                } else {
                    insertedCount++
                }

                val entity = IncidentEntity(
                    id = finalId,
                    sapId = sapId,
                    occurredAt = downAt,
                    category = finalCategory,
                    severity = finalSeverity,
                    status = finalStatus,
                    isOutage = isOutage,
                    downAt = if (isOutage == 1) downAt else null,
                    restoredAt = restoredAt,
                    title = finalTitle,
                    description = description ?: matched?.description,
                    actionTaken = actionTaken ?: matched?.actionTaken,
                    engineer = engineer ?: matched?.engineer,
                    ticketRef = ticketRef ?: matched?.ticketRef,
                    gpsLat = lat ?: matched?.gpsLat,
                    gpsLng = lng ?: matched?.gpsLng,
                    createdAt = matched?.createdAt ?: now,
                    updatedAt = now
                )
                entitiesToInsert.add(entity)
            }

            if (entitiesToInsert.isNotEmpty()) {
                db.withTransaction {
                    incidentDao.insertIncidents(entitiesToInsert)
                }
            }

            onProgress?.invoke(processedTotal, totalRows)
        }

        val elapsed = System.currentTimeMillis() - startTime
        LogImportResult(
            totalProcessed = totalRows,
            insertedCount = insertedCount,
            updatedCount = updatedCount,
            elapsedMs = elapsed
        )
    }
}
