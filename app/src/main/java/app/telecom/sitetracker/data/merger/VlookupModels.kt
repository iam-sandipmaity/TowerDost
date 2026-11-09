package app.telecom.sitetracker.data.merger

import app.telecom.sitetracker.data.import.TargetField

enum class MergeStrategy(val label: String, val description: String) {
    UPDATE_EXISTING_ONLY(
        "Update Existing Only",
        "Only updates sites that already exist in TowerDost. Supplemental rows for unknown sites are ignored."
    ),
    UPDATE_AND_CREATE_NEW(
        "Update & Create New",
        "Updates existing sites, and automatically registers new master sites for any unknown SAP IDs found in file."
    )
}

enum class OverwritePolicy(val label: String, val description: String) {
    OVERWRITE_ALL(
        "Overwrite Existing Values",
        "Replaces existing site fields and specs with non-blank values from the incoming sheet."
    ),
    FILL_BLANKS_ONLY(
        "Fill Missing / Blanks Only",
        "Only populates fields that are currently empty on the site. Never overwrites already recorded data."
    )
}

data class VlookupColumnMapping(
    val columnIndex: Int,
    val sourceHeader: String,
    var targetField: TargetField,
    var specGroup: String? = null,
    var specKey: String = sourceHeader,
    var previousTarget: TargetField = if (targetField != TargetField.IGNORE) targetField else TargetField.CUSTOM_SPEC,
    var isExistingInDatabase: Boolean = false
)

data class FieldDiff(
    val fieldName: String,
    val oldValue: String?,
    val newValue: String
)

data class RowDiffPreview(
    val sapId: String,
    val siteName: String?,
    val isNewSite: Boolean,
    val diffs: List<FieldDiff>
)

data class VlookupPreview(
    val totalRows: Int,
    val matchedSitesCount: Int,
    val unmatchedRowsCount: Int,
    val mappedColumnsCount: Int,
    val estimatedFieldsToUpdate: Int,
    val samplePreviews: List<RowDiffPreview>
)

data class VlookupResult(
    val totalProcessed: Int,
    val sitesUpdated: Int,
    val sitesCreated: Int,
    val specsUpdated: Int,
    val elapsedMs: Long
)
