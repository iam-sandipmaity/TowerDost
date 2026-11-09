package app.telecom.sitetracker.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

/**
 * Full-Text Search virtual table over site names, SAP IDs, addresses, and aggregated specs.
 */
@Entity(tableName = "site_search")
@Fts4
data class SiteFts(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "rowid")
    val rowid: Int = 0,

    @ColumnInfo(name = "sap_norm")
    val sapNorm: String,

    val name: String?,

    val address: String?,

    @ColumnInfo(name = "field_text")
    val fieldText: String?
)
