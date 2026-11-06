package app.telecom.sitetracker.data.repo

import app.telecom.sitetracker.data.db.AppDatabase
import app.telecom.sitetracker.data.db.CategoryParetoRow
import app.telecom.sitetracker.data.db.SeverityCountRow
import app.telecom.sitetracker.data.db.TopProblemSiteRow
import app.telecom.sitetracker.util.AppClock
import app.telecom.sitetracker.util.Dates
import kotlinx.coroutines.flow.Flow

data class HomeKpiData(
    val totalSites: Int = 0,
    val sitesDownNow: Int = 0,
    val openIncidentsCount: Int = 0,
    val criticalOpenCount: Int = 0,
    val quietSites90dCount: Int = 0,
    val incidentsThisMonth: Int = 0,
    val avgMttrHours: Double = 0.0,
    val yesterdayDowntimeHours: Double = 0.0,
    val hasClockSkew: Boolean = false,
    val orphanCount: Int = 0
)

class StatsRepository(private val db: AppDatabase) {
    private val statsDao = db.statsDao()
    private val outageDao = db.outageDao()
    private val incidentDao = db.incidentDao()

    fun observeTotalSites(): Flow<Int> = statsDao.countTotalSites()

    fun observeSitesDownNow(): Flow<Int> = outageDao.countSitesDownNow()

    fun observeSitesWithOpenIncidents(): Flow<Int> = statsDao.countSitesWithOpenIncidents()

    fun observeCriticalOpenIncidents(): Flow<Int> = statsDao.countCriticalOpenIncidents()

    fun observeQuietSites(sinceEpochMs: Long): Flow<Int> =
        statsDao.countSitesWithNoIncidentsSince(sinceEpochMs)

    fun observeIncidentsThisMonth(startOfMonthMs: Long, nowMs: Long): Flow<Int> =
        statsDao.countIncidentsBetween(startOfMonthMs, nowMs)

    fun observeTopProblemSites(sinceEpochMs: Long): Flow<List<TopProblemSiteRow>> =
        statsDao.getTopProblemSites(sinceEpochMs)

    suspend fun getYesterdayDowntimeHours(clock: AppClock): Double {
        val now = clock.currentTimeMillis()
        val todayStart = Dates.getDayStartEpochMs(now)
        val yesterdayStart = todayStart - Dates.MS_PER_DAY
        val yesterdayEnd = todayStart
        return statsDao.getYesterdayDowntimeHours(yesterdayStart, yesterdayEnd, now)
    }

    suspend fun calculateAverageMttrHours(sinceEpochMs: Long): Double {
        val closedOutages = statsDao.getClosedOutagesSince(sinceEpochMs)
        if (closedOutages.isEmpty()) return 0.0
        val durations = closedOutages.mapNotNull {
            if (it.downAt != null && it.restoredAt != null && it.restoredAt >= it.downAt) {
                (it.restoredAt - it.downAt) / Dates.MS_PER_HOUR
            } else null
        }
        return if (durations.isNotEmpty()) durations.average() else 0.0
    }

    suspend fun isClockSkewed(clock: AppClock): Boolean {
        val newest = statsDao.getNewestRecordTimestamp()
        return Dates.isClockSkewed(clock.currentTimeMillis(), newest, buildYear = 2026)
    }

    suspend fun getCategoryPareto(sinceEpochMs: Long, nowMs: Long): List<CategoryParetoRow> =
        statsDao.getCategoryPareto(sinceEpochMs, nowMs)

    suspend fun getSeverityCounts(sinceEpochMs: Long): List<SeverityCountRow> =
        statsDao.getSeverityCounts(sinceEpochMs)

    suspend fun getTotalDowntimeHoursSince(sinceEpochMs: Long, nowMs: Long): Double =
        statsDao.getTotalDowntimeHoursSince(sinceEpochMs, nowMs)

    suspend fun getTotalIncidentsCountSince(sinceEpochMs: Long): Int =
        statsDao.getTotalIncidentsCountSince(sinceEpochMs)

    suspend fun getAffectedSitesCountSince(sinceEpochMs: Long): Int =
        statsDao.getAffectedSitesCountSince(sinceEpochMs)
}
