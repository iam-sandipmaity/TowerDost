package app.telecom.sitetracker.data.entity

import androidx.room.ColumnInfo
import androidx.room.DatabaseView

/**
 * Database View identifying orphan incidents whose SAP ID is not present in master sites.
 */
@DatabaseView(
    viewName = "v_orphan_incidents",
    value = "SELECT i.* FROM incidents i LEFT JOIN sites s ON s.sap_id = i.sap_id WHERE s.sap_id IS NULL AND i.deleted_at IS NULL"
)
data class OrphanIncidentView(
    val id: String,

    @ColumnInfo(name = "sap_id")
    val sapId: String,

    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long,

    val category: String,

    @ColumnInfo(name = "reason_id")
    val reasonId: String?,

    val severity: String,

    val status: String,

    @ColumnInfo(name = "is_outage")
    val isOutage: Int,

    @ColumnInfo(name = "down_at")
    val downAt: Long?,

    @ColumnInfo(name = "restored_at")
    val restoredAt: Long?,

    val title: String,

    val description: String?,

    val engineer: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long?,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long?
)
