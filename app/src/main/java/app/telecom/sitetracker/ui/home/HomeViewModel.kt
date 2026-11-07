package app.telecom.sitetracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.telecom.sitetracker.SiteTrackerApp
import app.telecom.sitetracker.data.db.TopProblemSiteRow
import app.telecom.sitetracker.data.entity.IncidentEntity
import app.telecom.sitetracker.data.repo.HomeKpiData
import app.telecom.sitetracker.data.seed.SampleDataSeeder
import app.telecom.sitetracker.util.Dates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val kpis: HomeKpiData = HomeKpiData(),
    val openOutages: List<IncidentEntity> = emptyList(),
    val topProblemSites: List<TopProblemSiteRow> = emptyList(),
    val isSeeding: Boolean = false,
    val userMessage: String? = null
)

class HomeViewModel(private val app: SiteTrackerApp) : ViewModel() {

    private val _isSeeding = MutableStateFlow(false)
    private val _userMessage = MutableStateFlow<String?>(null)
    private val _calculatedKpis = MutableStateFlow(HomeKpiData())

    private val statsRepo = app.statsRepository
    private val outageRepo = app.outageRepository

    init {
        refreshCalculatedMetrics()
    }

    fun refreshCalculatedMetrics() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val now = app.clock.currentTimeMillis()
                val ninetyDaysAgo = now - (90L * Dates.MS_PER_DAY)
                val yesterdayHours = statsRepo.getYesterdayDowntimeHours(app.clock)
                val mttrHours = statsRepo.calculateAverageMttrHours(ninetyDaysAgo)
                val clockSkew = statsRepo.isClockSkewed(app.clock)

                _calculatedKpis.value = _calculatedKpis.value.copy(
                    yesterdayDowntimeHours = yesterdayHours,
                    avgMttrHours = mttrHours,
                    hasClockSkew = clockSkew
                )
            } catch (e: Throwable) {
                // Defensive: keep UI responsive even if initial metrics calculation fails
            }
        }
    }

    private val baseKpisFlow = combine(
        statsRepo.observeTotalSites(),
        outageRepo.countSitesDownNow(),
        statsRepo.observeSitesWithOpenIncidents(),
        statsRepo.observeCriticalOpenIncidents(),
        _calculatedKpis
    ) { totalSites, sitesDownNow, openIncidents, criticalOpen, calc ->
        calc.copy(
            totalSites = totalSites,
            sitesDownNow = sitesDownNow,
            openIncidentsCount = openIncidents,
            criticalOpenCount = criticalOpen
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(
        baseKpisFlow,
        outageRepo.observeOpenOutages(),
        _isSeeding,
        _userMessage
    ) { kpis, openOutages, isSeeding, userMessage ->
        HomeUiState(
            kpis = kpis,
            openOutages = openOutages,
            isSeeding = isSeeding,
            userMessage = userMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = HomeUiState()
    )

    fun seedSampleData() {
        viewModelScope.launch {
            _isSeeding.value = true
            try {
                SampleDataSeeder.seedDatabase(app.database, app.clock)
                refreshCalculatedMetrics()
                _userMessage.value = "Loaded 40 sites and 90 incidents successfully."
            } catch (e: Exception) {
                _userMessage.value = "Failed to load sample data: ${e.message}"
            } finally {
                _isSeeding.value = false
            }
        }
    }

    fun clearSampleData() {
        viewModelScope.launch {
            try {
                app.siteRepository.deleteSampleSites()
                app.incidentRepository.clearSampleIncidents()
                refreshCalculatedMetrics()
                _userMessage.value = "Sample data cleared. Ready for real field operations."
            } catch (e: Exception) {
                _userMessage.value = "Failed to clear sample data: ${e.message}"
            }
        }
    }

    fun dismissUserMessage() {
        _userMessage.value = null
    }

    class Factory(private val app: SiteTrackerApp) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(app) as T
        }
    }
}
