package app.telecom.sitetracker.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Editable Root-Cause Catalogue.
 * Not an enum; users can seed, edit, hide, and reorder causes.
 */
@Entity(tableName = "reasons")
data class ReasonEntity(
    @PrimaryKey
    val id: String,

    val label: String,

    @ColumnInfo(name = "group_name")
    val groupName: String? = null,

    val category: String? = null,

    @ColumnInfo(name = "counts_toward_downtime", defaultValue = "1")
    val countsTowardDowntime: Int = 1,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int? = null,

    @ColumnInfo(name = "is_hidden", defaultValue = "0")
    val isHidden: Int = 0,

    @ColumnInfo(name = "is_seeded", defaultValue = "0")
    val isSeeded: Int = 0,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long? = null
)
