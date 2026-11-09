package app.telecom.sitetracker.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Registry of tabs available for sites (e.g. SPECS, LOGS, OVERVIEW, or custom tabs like TENANT_INFO).
 */
@Entity(tableName = "site_tabs")
data class SiteTabEntity(
    @PrimaryKey
    @ColumnInfo(name = "tab_id")
    val tabId: String,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,

    @ColumnInfo(name = "is_system")
    val isSystem: Boolean = false
) {
    companion object {
        const val TAB_SPECS = "SPECS"
        const val TAB_LOGS = "LOGS"
        const val TAB_OVERVIEW = "OVERVIEW"

        val SYSTEM_SPECS = SiteTabEntity(TAB_SPECS, "Specs", 0, isSystem = true)
        val SYSTEM_LOGS = SiteTabEntity(TAB_LOGS, "Logs", 1, isSystem = true)
        val SYSTEM_OVERVIEW = SiteTabEntity(TAB_OVERVIEW, "Overview", 2, isSystem = true)

        val DEFAULT_TABS = listOf(SYSTEM_SPECS, SYSTEM_LOGS, SYSTEM_OVERVIEW)
    }
}
