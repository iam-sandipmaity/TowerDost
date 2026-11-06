package app.telecom.sitetracker.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Unified Event table: Both routine site maintenance visits and outages live here.
 * Foreign key to sites table is omitted intentionally to allow unknown SAP IDs to import as orphans.
 */
@Entity(
    tableName = "incidents",
    indices = [
        Index(value = ["sap_id", "occurred_at"], name = "idx_inc_sap_date"),
        Index(value = ["is_outage", "down_at", "restored_at"], name = "idx_inc_outage"),
        Index(value = ["reason_id", "down_at"], name = "idx_inc_reason"),
        Index(value = ["status", "severity"], name = "idx_inc_status")
    ]
)
data class IncidentEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "sap_id")
    val sapId: String,

    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long,

    @ColumnInfo(name = "reported_at")
    val reportedAt: Long? = null,

    @ColumnInfo(name = "attended_at")
    val attendedAt: Long? = null,

    val category: String,

    @ColumnInfo(name = "reason_id")
    val reasonId: String? = null,

    val severity: String,

    val status: String,

    @ColumnInfo(name = "is_outage", defaultValue = "0")
    val isOutage: Int = 0,

    @ColumnInfo(name = "down_at")
    val downAt: Long? = null,

    @ColumnInfo(name = "restored_at")
    val restoredAt: Long? = null,

    val title: String,

    val description: String? = null,

    @ColumnInfo(name = "action_taken")
    val actionTaken: String? = null,

    val engineer: String? = null,

    @ColumnInfo(name = "ticket_ref")
    val ticketRef: String? = null,

    val tags: String? = null,

    @ColumnInfo(name = "gps_lat")
    val gpsLat: Double? = null,

    @ColumnInfo(name = "gps_lng")
    val gpsLng: Double? = null,

    @ColumnInfo(name = "gps_accuracy_m")
    val gpsAccuracyM: Float? = null,

    @ColumnInfo(name = "gps_distance_from_site_m")
    val gpsDistanceFromSiteM: Double? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long? = null,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long? = null,

    @ColumnInfo(name = "updated_by")
    val updatedBy: String? = null,

    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null
)
