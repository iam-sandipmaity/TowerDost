package app.telecom.sitetracker.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Immutable audit trail for incident field edits.
 */
@Entity(tableName = "incident_history")
data class IncidentHistoryEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "incident_id")
    val incidentId: String,

    @ColumnInfo(name = "changed_at")
    val changedAt: Long,

    @ColumnInfo(name = "changed_by")
    val changedBy: String? = null,

    val field: String,

    @ColumnInfo(name = "old_value")
    val oldValue: String? = null,

    @ColumnInfo(name = "new_value")
    val newValue: String? = null
)
