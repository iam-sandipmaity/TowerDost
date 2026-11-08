package app.telecom.sitetracker.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores the master specification template definitions inherited globally by all sites.
 */
@Entity(tableName = "universal_fields")
data class UniversalFieldEntity(
    @PrimaryKey
    @ColumnInfo(name = "field_key")
    val fieldKey: String,

    @ColumnInfo(name = "group_name")
    val groupName: String,

    @ColumnInfo(name = "default_value")
    val defaultValue: String? = null,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0
)
