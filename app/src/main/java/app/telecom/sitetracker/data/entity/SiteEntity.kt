package app.telecom.sitetracker.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Master Site entity.
 * Represents a telecom tower/base station site.
 */
@Entity(
    tableName = "sites",
    indices = [
        Index(value = ["sap_norm"], name = "idx_sites_norm")
    ]
)
data class SiteEntity(
    @PrimaryKey
    @ColumnInfo(name = "sap_id")
    val sapId: String,

    @ColumnInfo(name = "sap_norm")
    val sapNorm: String,

    val name: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val circle: String? = null,
    val region: String? = null,
    val state: String? = null,
    val district: String? = null,
    val city: String? = null,
    val technology: String? = null,

    @ColumnInfo(name = "site_type")
    val siteType: String? = null,

    val status: String? = null,
    val address: String? = null,
    val tags: String? = null,
    val notes: String? = null,

    @ColumnInfo(name = "source_file")
    val sourceFile: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long? = null,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long? = null,

    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null
) {
    companion object {
        /**
         * Normalizes any SAP ID input to alphanumeric uppercase characters.
         * "DEL-12345" -> "DEL12345", "mum 9001" -> "MUM9001"
         */
        fun normalize(raw: String): String {
            return raw.uppercase().replace(Regex("[^A-Z0-9]"), "")
        }
    }
}
