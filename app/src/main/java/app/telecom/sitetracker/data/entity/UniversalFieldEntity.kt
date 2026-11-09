package app.telecom.sitetracker.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * Stores the master specification template definitions inherited globally by all sites.
 * Uses a composite primary key (tab_id, field_key) allowing identical field keys across different tabs.
 */
@Entity(
    tableName = "universal_fields",
    primaryKeys = ["tab_id", "field_key"]
)
data class UniversalFieldEntity(
    @ColumnInfo(name = "field_key")
    val fieldKey: String,

    @ColumnInfo(name = "group_name")
    val groupName: String,

    @ColumnInfo(name = "default_value")
    val defaultValue: String? = null,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,

    @ColumnInfo(name = "tab_id", defaultValue = "'SPECS'")
    val tabId: String = "SPECS"
)
