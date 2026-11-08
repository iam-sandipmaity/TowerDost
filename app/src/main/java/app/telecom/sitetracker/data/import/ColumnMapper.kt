package app.telecom.sitetracker.data.import

import java.util.Locale

/**
 * Standard target fields for site attributes.
 */
enum class TargetField(val displayName: String, val isRequired: Boolean) {
    SAP_ID("SAP ID", true),
    NAME("Site Name", false),
    LAT("Latitude", false),
    LNG("Longitude", false),
    CIRCLE("Circle / CMP", false),
    REGION("Region", false),
    STATE("State", false),
    DISTRICT("District", false),
    CITY("City / JC Name", false),
    TECHNOLOGY("Technology", false),
    SITE_TYPE("Site Type", false),
    STATUS("Status", false),
    ADDRESS("Address", false),
    TAGS("Tags / Scope", false),
    NOTES("Notes / Remarks", false),
    CUSTOM_SPEC("Custom Specification", false),
    IGNORE("Ignore Column", false);

    companion object {
        fun standardFields(): List<TargetField> = listOf(
            SAP_ID, NAME, LAT, LNG, CIRCLE, REGION, STATE,
            DISTRICT, CITY, TECHNOLOGY, SITE_TYPE, STATUS, ADDRESS,
            TAGS, NOTES, CUSTOM_SPEC, IGNORE
        )
    }
}

data class ColumnMapping(
    val columnIndex: Int,
    val sourceHeader: String,
    var targetField: TargetField,
    var specKey: String = sourceHeader,
    var specGroup: String? = null,
    var previousTarget: TargetField = if (targetField != TargetField.IGNORE) targetField else TargetField.CUSTOM_SPEC
)

object ColumnMapper {

    private val ALIASES: Map<TargetField, List<String>> = mapOf(
        TargetField.SAP_ID to listOf(
            "sap id", "sap_id", "site id", "site_id", "site code", "sitecode",
            "sapid", "tower id", "site_no", "site no", "sap", "sr no"
        ),
        TargetField.NAME to listOf(
            "site name", "sitename", "name", "tower name", "location", "site_name"
        ),
        TargetField.LAT to listOf(
            "lat", "latitude", "y", "gps_lat", "site_lat", "lattitude"
        ),
        TargetField.LNG to listOf(
            "lng", "long", "longitude", "x", "gps_long", "site_long", "longtitude"
        ),
        TargetField.CIRCLE to listOf(
            "circle", "telecom circle", "cmp", "zone", "cluster"
        ),
        TargetField.REGION to listOf(
            "region", "area", "division"
        ),
        TargetField.STATE to listOf(
            "state", "province"
        ),
        TargetField.DISTRICT to listOf(
            "district", "dist"
        ),
        TargetField.CITY to listOf(
            "city", "town", "jc name", "jc", "village", "jio center"
        ),
        TargetField.TECHNOLOGY to listOf(
            "technology", "tech", "2g/4g", "generation"
        ),
        TargetField.SITE_TYPE to listOf(
            "site type", "sitetype", "tower type", "structure", "facility type"
        ),
        TargetField.STATUS to listOf(
            "status", "site status", "operational status", "energy status", "power status"
        ),
        TargetField.ADDRESS to listOf(
            "address", "site address", "full address"
        ),
        TargetField.TAGS to listOf(
            "tags", "category", "scope", "sites scope"
        ),
        TargetField.NOTES to listOf(
            "notes", "remarks", "other remarks", "comment", "comments"
        )
    )

    /**
     * Normalizes a header string for comparison: removes punctuation, lowers case, trims extra whitespace.
     */
    fun normalizeHeader(raw: String): String {
        return raw.lowercase(Locale.US)
            .replace("\n", " ")
            .replace("\r", " ")
            .replace("[_-]".toRegex(), " ")
            .replace("[^a-z0-9 ]".toRegex(), "")
            .trim()
            .replace("\\s+".toRegex(), " ")
    }

    /**
     * Determines whether a given header matches a target field.
     */
    fun matchField(header: String): TargetField? {
        val norm = normalizeHeader(header)
        if (norm.isEmpty()) return null

        // 1. Exact match on normalized aliases
        for ((field, aliases) in ALIASES) {
            for (alias in aliases) {
                if (norm == alias) {
                    return field
                }
            }
        }

        // 2. Contains match with boundary check
        // e.g. "sap id (as per erp)" matches SAP_ID, but avoid "technician name" matching "name"
        if (norm.contains("sap id") || norm.contains("sap code") || norm.contains("site id") || norm.contains("sapid")) {
            return TargetField.SAP_ID
        }
        if (norm == "lat" || norm.startsWith("lat ") || norm.endsWith(" lat")) {
            return TargetField.LAT
        }
        if (norm == "long" || norm == "lng" || norm.startsWith("long ") || norm.endsWith(" long")) {
            return TargetField.LNG
        }
        if (norm == "site name" || norm == "tower name") {
            return TargetField.NAME
        }

        return null
    }

    /**
     * Determines the group name for equipment specifications.
     */
    fun inferSpecGroup(header: String, parentHeader: String? = null): String {
        if (!parentHeader.isNullOrBlank()) {
            val parentUpper = parentHeader.trim().uppercase(Locale.US)
            when {
                parentUpper.contains("SMPS") -> return "SMPS"
                parentUpper.contains("BM") || parentUpper.contains("BATTERY") -> return "Battery Bank"
                parentUpper.contains("RM") || parentUpper.contains("RECTIFIER") -> return "Rectifier Module"
                parentUpper.contains("DG") || parentUpper.contains("GENERATOR") -> return "Diesel Generator"
                parentUpper.contains("SOLAR") || parentUpper.contains("MPPT") -> return "Solar MPPT"
                parentUpper.contains("GCU") -> return "GCU"
                parentUpper.contains("VEGETATION") -> return "Site Environment"
                else -> return parentHeader.trim()
            }
        }

        val upper = header.uppercase(Locale.US)
        return when {
            upper.contains("SMPS") -> "SMPS"
            upper.contains("BATTERY") || upper.contains("BM ") || upper.startsWith("BM") || upper.contains("BB ") -> "Battery Bank"
            upper.contains("RECTIFIER") || upper.contains("RM ") || upper.startsWith("RM") -> "Rectifier Module"
            upper.contains("DG ") || upper.startsWith("DG") || upper.contains("GENERATOR") || upper.contains("FUEL") -> "Diesel Generator"
            upper.contains("SOLAR") || upper.contains("MPPT") -> "Solar MPPT"
            upper.contains("GCU") -> "GCU"
            upper.contains("TECH") || upper.contains("MOBILE") || upper.contains("PHONE") -> "Technician & Contact"
            upper.contains("EB") || upper.contains("LOAD") || upper.contains("POWER") || upper.contains("ENERGY") -> "Electrical & Power"
            upper.contains("VEGETATION") -> "Site Environment"
            else -> "General Specifications"
        }
    }

    /**
     * Produces initial mappings for a list of raw column headers and optional parent headers.
     */
    fun generateInitialMappings(
        headers: List<String>,
        parentHeaders: List<String?>? = null
    ): List<ColumnMapping> {
        val assigned = mutableSetOf<TargetField>()
        val result = mutableListOf<ColumnMapping>()

        for (i in headers.indices) {
            val header = headers[i]
            val parent = parentHeaders?.getOrNull(i)

            // Combined header display if parent header exists and is distinct
            val displayHeader = if (!parent.isNullOrBlank() && parent != header) {
                "$parent - $header"
            } else {
                header
            }

            val matched = matchField(header)
            // Allow only one mapping per standard field (first match wins, except IGNORE/CUSTOM)
            val finalTarget = if (matched != null && !assigned.contains(matched)) {
                assigned.add(matched)
                matched
            } else {
                TargetField.CUSTOM_SPEC
            }

            val group = inferSpecGroup(header, parent)

            result.add(
                ColumnMapping(
                    columnIndex = i,
                    sourceHeader = displayHeader,
                    targetField = finalTarget,
                    specKey = displayHeader,
                    specGroup = group
                )
            )
        }

        return result
    }
}
