package app.telecom.sitetracker.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Photos attached to an incident or visit.
 * Paths are stored relative to filesDir so backups remain fully portable across devices.
 */
@Entity(tableName = "incident_photos")
data class IncidentPhotoEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "incident_id")
    val incidentId: String,

    @ColumnInfo(name = "sap_id")
    val sapId: String,

    @ColumnInfo(name = "rel_path")
    val relPath: String,

    val caption: String? = null,

    @ColumnInfo(name = "taken_at")
    val takenAt: Long? = null,

    val bytes: Long? = null
)
