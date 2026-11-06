package app.telecom.sitetracker.data.db

import androidx.room.Dao
import androidx.room.Query
import app.telecom.sitetracker.data.entity.IncidentEntity
import kotlinx.coroutines.flow.Flow

data class DayDowntimeRow(
    val day_start: Long,
    val sap_id: String,
    val hours: Double
)

@Dao
interface OutageDao {
    @Query("""
        SELECT * FROM incidents
        WHERE sap_id = :sapId AND is_outage = 1 AND restored_at IS NULL AND deleted_at IS NULL
        LIMIT 1
    """)
    suspend fun getOpenOutageForSite(sapId: String): IncidentEntity?

    @Query("""
        SELECT * FROM incidents
        WHERE is_outage = 1 AND restored_at IS NULL AND deleted_at IS NULL
        ORDER BY down_at ASC
    """)
    fun getOpenOutages(): Flow<List<IncidentEntity>>

    @Query("""
        SELECT * FROM incidents
        WHERE is_outage = 1 AND restored_at IS NOT NULL AND deleted_at IS NULL
        ORDER BY restored_at DESC LIMIT 100
    """)
    fun getRecentRestoredOutages(): Flow<List<IncidentEntity>>

    @Query("""
        SELECT * FROM incidents
        WHERE is_outage = 1 AND deleted_at IS NULL
        ORDER BY down_at DESC LIMIT 200
    """)
    fun getAllOutages(): Flow<List<IncidentEntity>>

    @Query("""
        SELECT COUNT(DISTINCT sap_id) FROM incidents
        WHERE is_outage = 1 AND restored_at IS NULL AND deleted_at IS NULL
    """)
    fun countSitesDownNow(): Flow<Int>

    /**
     * Finds every outage active at any point on day D (span overlap).
     */
    @Query("""
        SELECT * FROM incidents
        WHERE is_outage = 1 AND deleted_at IS NULL AND down_at < :dayEnd
          AND (restored_at IS NULL OR restored_at > :dayStart)
        ORDER BY down_at DESC
    """)
    suspend fun getOutagesForDay(dayStart: Long, dayEnd: Long): List<IncidentEntity>

    /**
     * Observes outages active on a specific day.
     */
    @Query("""
        SELECT * FROM incidents
        WHERE is_outage = 1 AND deleted_at IS NULL AND down_at < :dayEnd
          AND (restored_at IS NULL OR restored_at > :dayStart)
        ORDER BY down_at DESC
    """)
    fun observeOutagesForDay(dayStart: Long, dayEnd: Long): Flow<List<IncidentEntity>>

    /**
     * Recursive SQL query computing precise clipped downtime hours per site per day.
     */
    @Query("""
        WITH RECURSIVE days(d0) AS (
          SELECT :rangeStart UNION ALL SELECT d0 + 86400000 FROM days WHERE d0 + 86400000 < :rangeEnd
        )
        SELECT d.d0 AS day_start, o.sap_id,
               (MIN(COALESCE(o.restored_at, :now), d.d0 + 86400000) - MAX(o.down_at, d.d0)) / 3600000.0 AS hours
        FROM days d JOIN incidents o
          ON o.is_outage = 1 AND o.deleted_at IS NULL AND o.down_at < d.d0 + 86400000
         AND COALESCE(o.restored_at, :now) > d.d0
        LEFT JOIN reasons r ON r.id = o.reason_id
        WHERE COALESCE(r.counts_toward_downtime, 1) = 1
    """)
    suspend fun getDailyDowntimeMatrix(rangeStart: Long, rangeEnd: Long, now: Long): List<DayDowntimeRow>

    /**
     * Raw daily downtime hours without filtering out planned/force-majeure maintenance.
     */
    @Query("""
        WITH RECURSIVE days(d0) AS (
          SELECT :rangeStart UNION ALL SELECT d0 + 86400000 FROM days WHERE d0 + 86400000 < :rangeEnd
        )
        SELECT d.d0 AS day_start, o.sap_id,
               (MIN(COALESCE(o.restored_at, :now), d.d0 + 86400000) - MAX(o.down_at, d.d0)) / 3600000.0 AS hours
        FROM days d JOIN incidents o
          ON o.is_outage = 1 AND o.deleted_at IS NULL AND o.down_at < d.d0 + 86400000
         AND COALESCE(o.restored_at, :now) > d.d0
    """)
    suspend fun getRawDailyDowntimeMatrix(rangeStart: Long, rangeEnd: Long, now: Long): List<DayDowntimeRow>
}
