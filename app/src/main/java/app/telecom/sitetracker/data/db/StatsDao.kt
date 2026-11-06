package app.telecom.sitetracker.data.db

import androidx.room.Dao
import androidx.room.Query
import app.telecom.sitetracker.data.entity.IncidentEntity
import kotlinx.coroutines.flow.Flow

data class TopProblemSiteRow(
    val sap_id: String,
    val name: String?,
    val open_critical_count: Int,
    val open_major_count: Int,
    val ninety_day_count: Int
)

@Dao
interface StatsDao {
    @Query("SELECT COUNT(*) FROM sites WHERE deleted_at IS NULL")
    fun countTotalSites(): Flow<Int>

    @Query("""
        SELECT COUNT(DISTINCT sap_id) FROM incidents
        WHERE status IN ('Open', 'In Progress') AND deleted_at IS NULL
    """)
    fun countSitesWithOpenIncidents(): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM incidents
        WHERE severity = 'Critical' AND status IN ('Open', 'In Progress') AND deleted_at IS NULL
    """)
    fun countCriticalOpenIncidents(): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM sites s
        WHERE s.deleted_at IS NULL
          AND s.sap_id NOT IN (
            SELECT DISTINCT i.sap_id FROM incidents i
            WHERE i.occurred_at >= :sinceEpochMs AND i.deleted_at IS NULL
          )
    """)
    fun countSitesWithNoIncidentsSince(sinceEpochMs: Long): Flow<Int>

    @Query("""
        SELECT * FROM incidents
        WHERE is_outage = 1 AND restored_at IS NOT NULL AND occurred_at >= :sinceEpochMs AND deleted_at IS NULL
    """)
    suspend fun getClosedOutagesSince(sinceEpochMs: Long): List<IncidentEntity>

    @Query("""
        SELECT COUNT(*) FROM incidents
        WHERE occurred_at >= :startEpochMs AND occurred_at < :endEpochMs AND deleted_at IS NULL
    """)
    fun countIncidentsBetween(startEpochMs: Long, endEpochMs: Long): Flow<Int>

    @Query("""
        SELECT COALESCE(SUM(
            (MIN(COALESCE(restored_at, :nowMs), :yesterdayEnd) - MAX(down_at, :yesterdayStart)) / 3600000.0
        ), 0.0)
        FROM incidents
        WHERE is_outage = 1 AND deleted_at IS NULL
          AND down_at < :yesterdayEnd
          AND COALESCE(restored_at, :nowMs) > :yesterdayStart
    """)
    suspend fun getYesterdayDowntimeHours(yesterdayStart: Long, yesterdayEnd: Long, nowMs: Long): Double

    @Query("""
        SELECT MAX(latest_time) FROM (
            SELECT MAX(COALESCE(updated_at, occurred_at)) as latest_time FROM incidents
            UNION ALL
            SELECT MAX(COALESCE(updated_at, created_at)) as latest_time FROM sites
        )
    """)
    suspend fun getNewestRecordTimestamp(): Long?

    @Query("""
        SELECT s.sap_id, s.name,
               SUM(CASE WHEN i.severity = 'Critical' AND i.status IN ('Open', 'In Progress') THEN 1 ELSE 0 END) AS open_critical_count,
               SUM(CASE WHEN i.severity = 'Major' AND i.status IN ('Open', 'In Progress') THEN 1 ELSE 0 END) AS open_major_count,
               SUM(CASE WHEN i.occurred_at >= :sinceEpochMs THEN 1 ELSE 0 END) AS ninety_day_count
        FROM sites s
        JOIN incidents i ON s.sap_id = i.sap_id AND i.deleted_at IS NULL
        WHERE s.deleted_at IS NULL
        GROUP BY s.sap_id
        ORDER BY (open_critical_count * 25 + open_major_count * 10 + ninety_day_count * 3) DESC
        LIMIT 10
    """)
    fun getTopProblemSites(sinceEpochMs: Long): Flow<List<TopProblemSiteRow>>
}
