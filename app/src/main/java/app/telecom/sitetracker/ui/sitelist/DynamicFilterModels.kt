package app.telecom.sitetracker.ui.sitelist

import java.util.UUID

/**
 * Represents an attribute dimension available for dynamic filtering.
 * [key] is either a standard core field key ("circle", "city", "district", "technology", "site_type", "status")
 * or an equipment specification field key from site_fields.
 */
data class FilterDimension(
    val key: String,
    val displayName: String,
    val isCoreField: Boolean,
    val groupName: String? = null,
    val availableValues: List<String> = emptyList()
)

/**
 * An active filter applied by the user on the Site Directory.
 * Up to 4 active filters can be concurrently applied.
 */
data class ActiveFilter(
    val id: String = UUID.randomUUID().toString(),
    val dimensionKey: String,
    val displayName: String,
    val selectedValue: String,
    val isCoreField: Boolean
)
