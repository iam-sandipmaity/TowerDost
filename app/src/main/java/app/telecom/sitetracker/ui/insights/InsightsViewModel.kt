package app.telecom.sitetracker.ui.insights

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.telecom.sitetracker.data.db.CategoryParetoRow
import app.telecom.sitetracker.data.db.SeverityCountRow
import app.telecom.sitetracker.data.db.TopProblemSiteRow
import app.telecom.sitetracker.data.export.Exporter
import app.telecom.sitetracker.data.repo.IncidentRepository
import app.telecom.sitetracker.data.repo.OutageRepository
import app.telecom.sitetracker.data.repo.SiteRepository
import app.telecom.sitetracker.data.repo.StatsRepository
import app.telecom.sitetracker.util.AppClock
import app.telecom.sitetracker.util.Dates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TimeRange(val label: String, val days: Int) {
    SEVEN_DAYS("7 Days", 7),
    THIRTY_DAYS("30 Days", 30),
    NINETY_DAYS("90 Days", 90),
    ALL_TIME("All Time", 365)
}

data class InsightsUiState(
    val selectedRange: TimeRange = TimeRange.THIRTY_DAYS,
    val totalSites: Int = 0,
    val networkAvailabilityPercent: Double = 100.0,
    val totalDowntimeHours: Double = 0.0,
    val avgMttrHours: Double = 0.0,
    val totalIncidentsCount: Int = 0,
    val affectedSitesCount: Int = 0,
    val categoryPareto: List<CategoryParetoRow> = emptyList(),
    val severityCounts: List<SeverityCountRow> = emptyList(),
    val topProblemSites: List<TopProblemSiteRow> = emptyList(),
    val isCalculating: Boolean = false,
    val userMessage: String? = null
)

class InsightsViewModel(
    private val statsRepository: StatsRepository,
    private val outageRepository: OutageRepository,
    private val incidentRepository: IncidentRepository,
    private val siteRepository: SiteRepository,
    private val clock: AppClock
) : ViewModel() {

    private val _uiState = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    init {
        loadInsights(TimeRange.THIRTY_DAYS)
    }

    fun setTimeRange(range: TimeRange) {
        _uiState.update { it.copy(selectedRange = range) }
        loadInsights(range)
    }

    fun refresh() {
        loadInsights(_uiState.value.selectedRange)
    }

    private fun loadInsights(range: TimeRange) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isCalculating = true) }
            try {
                val nowMs = clock.currentTimeMillis()
                val sinceEpochMs = nowMs - (range.days.toLong() * Dates.MS_PER_DAY)

                val totalSites = statsRepository.observeTotalSites().firstOrNull() ?: 1
                val downtimeHours = statsRepository.getTotalDowntimeHoursSince(sinceEpochMs, nowMs)
                val incidentsCount = statsRepository.getTotalIncidentsCountSince(sinceEpochMs)
                val affectedSites = statsRepository.getAffectedSitesCountSince(sinceEpochMs)
                val mttr = statsRepository.calculateAverageMttrHours(sinceEpochMs)
                val pareto = statsRepository.getCategoryPareto(sinceEpochMs, nowMs)
                val severities = statsRepository.getSeverityCounts(sinceEpochMs)
                val topProblem = statsRepository.observeTopProblemSites(sinceEpochMs).firstOrNull() ?: emptyList()

                // Network Availability Formula:
                // Total potential operational hours = totalSites * range.days * 24
                // Availability % = (1 - (downtimeHours / totalOperationalHours)) * 100
                val totalOperationalHours = Math.max(1.0, totalSites.toDouble() * range.days.toDouble() * 24.0)
                val rawAvail = (1.0 - (downtimeHours / totalOperationalHours)) * 100.0
                val availability = Math.max(0.0, Math.min(100.0, rawAvail))

                _uiState.update {
                    it.copy(
                        isCalculating = false,
                        totalSites = totalSites,
                        networkAvailabilityPercent = availability,
                        totalDowntimeHours = downtimeHours,
                        avgMttrHours = mttr,
                        totalIncidentsCount = incidentsCount,
                        affectedSitesCount = affectedSites,
                        categoryPareto = pareto,
                        severityCounts = severities,
                        topProblemSites = topProblem
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isCalculating = false,
                        userMessage = "Calculation notice: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun exportOutagesCsv(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val all = incidentRepository.getAllIncidentsList()
                if (all.isEmpty()) {
                    _uiState.update { it.copy(userMessage = "No incidents recorded to export.") }
                    return@launch
                }
                val file = Exporter.exportRegisterCsv(context, all)
                Exporter.shareCsvFile(context, file, "TowerDost SLA & Incident Report")
            } catch (e: Exception) {
                _uiState.update { it.copy(userMessage = "Export failed: ${e.localizedMessage}") }
            }
        }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    class Factory(
        private val statsRepository: StatsRepository,
        private val outageRepository: OutageRepository,
        private val incidentRepository: IncidentRepository,
        private val siteRepository: SiteRepository,
        private val clock: AppClock
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return InsightsViewModel(
                statsRepository,
                outageRepository,
                incidentRepository,
                siteRepository,
                clock
            ) as T
        }
    }
}
