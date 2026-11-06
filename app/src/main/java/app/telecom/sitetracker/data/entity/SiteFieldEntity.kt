package app.telecom.sitetracker.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores verbatim equipment and site specification columns from imported sheets.
 */
@Entity(
    tableName = "site_fields",
    foreignKeys = [
        ForeignKey(
            entity = SiteEntity::class,
            parentColumns = ["sap_id"],
            childColumns = ["sap_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sap_id"], name = "idx_fields_sap")
    ]
)
data class SiteFieldEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "sap_id")
    val sapId: String,

    @ColumnInfo(name = "field_key")
    val fieldKey: String,

    @ColumnInfo(name = "field_value")
    val fieldValue: String?,

    @ColumnInfo(name = "group_name")
    val groupName: String? = null,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int? = null
)
