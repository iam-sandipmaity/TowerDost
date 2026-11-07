package app.telecom.sitetracker.ui.outages

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.telecom.sitetracker.data.entity.IncidentEntity
import app.telecom.sitetracker.data.entity.ReasonEntity
import app.telecom.sitetracker.data.export.Exporter
import app.telecom.sitetracker.data.repo.IncidentRepository
import app.telecom.sitetracker.data.repo.OutageRepository
import app.telecom.sitetracker.data.repo.ReasonRepository
import app.telecom.sitetracker.data.repo.SiteRepository
import app.telecom.sitetracker.util.AppClock
import app.telecom.sitetracker.util.Dates
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DayViewOutageItem(
    val incident: IncidentEntity,
    val clippedHours: Double,
    val reasonLabel: String
)

data class DownRegisterUiState(
    val selectedTab: Int = 0, // 0 = Down Now, 1 = Day View, 2 = All Incidents, 3 = Restored
    val searchQuery: String = "",
    val openOutages: List<IncidentEntity> = emptyList(),
    val allIncidents: List<IncidentEntity> = emptyList(),
    val restoredOutages: List<IncidentEntity> = emptyList(),
    val dayOutages: List<DayViewOutageItem> = emptyList(),
    val selectedDateEpochMs: Long = 0L,
    val daySitesDownCount: Int = 0,
    val dayTotalDowntimeHours: Double = 0.0,
    val selectedIncidentIds: Set<String> = emptySet(),
    val isMultiSelectMode: Boolean = false,
    val activeReasons: List<ReasonEntity> = emptyList(),
    val userMessage: String? = null,
    val undoableIncidentId: String? = null,
    val undoableSapId: String? = null
)

private data class UndoNoticeState(
    val message: String? = null,
    val incidentId: String? = null,
    val sapId: String? = null
)

class DownRegisterViewModel(
    private val outageRepository: OutageRepository,
    private val incidentRepository: IncidentRepository,
    private val reasonRepository: ReasonRepository,
    private val siteRepository: SiteRepository,
    private val clock: AppClock
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(0)
    private val _searchQuery = MutableStateFlow("")
    private val _undoNotice = MutableStateFlow(UndoNoticeState())
    private val _selectedDateEpochMs = MutableStateFlow(Dates.getDayStartEpochMs(clock.currentTimeMillis()))
    private val _selectedIncidentIds = MutableStateFlow<Set<String>>(emptySet())
    private val _isMultiSelectMode = MutableStateFlow(false)

    // Base incidents flow
    private val incidentsDataFlow = combine(
        outageRepository.observeOpenOutages(),
        incidentRepository.observeAllIncidents(),
        outageRepository.observeRestoredOutages()
    ) { open, all, restored ->
        Triple(open, all, restored)
    }

    // Active reasons flow
    private val reasonsFlow: Flow<List<ReasonEntity>> = reasonRepository.getActiveReasons()

    // Day View outages flow (switches reactively when date changes)
    @OptIn(ExperimentalCoroutinesApi::class)
    private val rawDayOutagesFlow: Flow<List<IncidentEntity>> = _selectedDateEpochMs.flatMapLatest { dayStart ->
        val dayEnd = dayStart + Dates.MS_PER_DAY
        outageRepository.observeOutagesForDay(dayStart, dayEnd)
    }

    // Combine day outages with active reasons and compute clipped hours
    private val dayViewDataFlow = combine(
        rawDayOutagesFlow,
        _selectedDateEpochMs,
        reasonsFlow
    ) { outages: List<IncidentEntity>, dayStart: Long, reasons: List<ReasonEntity> ->
        val now = clock.currentTimeMillis()
        val reasonMap = reasons.associateBy { it.id }

        val items = outages.map { incident ->
            val clipped = Dates.clipOutageToDayHours(
                downAt = incident.downAt ?: incident.occurredAt,
                restoredAt = incident.restoredAt,
                dayStartMs = dayStart,
                nowMs = now
            )
            val label = incident.reasonId?.let { reasonMap[it]?.label } ?: incident.title
            DayViewOutageItem(
                incident = incident,
                clippedHours = clipped,
                reasonLabel = label
            )
        }

        val sitesDownCount = items.map { it.incident.sapId }.distinct().size
        val totalDowntime = items.sumOf { it.clippedHours }
        Triple(items, sitesDownCount, totalDowntime)
    }

    // Composite UiState
    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<DownRegisterUiState> = combine(
        incidentsDataFlow,
        dayViewDataFlow,
        reasonsFlow,
        _selectedTab,
        _searchQuery,
        _selectedDateEpochMs,
        _selectedIncidentIds,
        _isMultiSelectMode,
        _undoNotice
    ) { args: Array<Any?> ->
        val incidentsTriple = args[0] as Triple<*, *, *>
        val open = incidentsTriple.first as List<IncidentEntity>
        val all = incidentsTriple.second as List<IncidentEntity>
        val restored = incidentsTriple.third as List<IncidentEntity>

        val dayTriple = args[1] as Triple<*, *, *>
        val dayItems = dayTriple.first as List<DayViewOutageItem>
        val daySitesCount = dayTriple.second as Int
        val dayDowntime = dayTriple.third as Double

        val reasons = args[2] as List<ReasonEntity>
        val tab = args[3] as Int
        val query = args[4] as String
        val dateMs = args[5] as Long
        val selIds = args[6] as Set<String>
        val multiSelect = args[7] as Boolean
        val notice = args[8] as UndoNoticeState

        fun filterList(list: List<IncidentEntity>): List<IncidentEntity> {
            if (query.isBlank()) return list
            val q = query.trim().lowercase()
            return list.filter {
                it.sapId.lowercase().contains(q) ||
                it.title.lowercase().contains(q) ||
                it.category.lowercase().contains(q) ||
                (it.engineer?.lowercase()?.contains(q) == true)
            }
        }

        val filteredDayItems = if (query.isBlank()) {
            dayItems
        } else {
            val q = query.trim().lowercase()
            dayItems.filter {
                it.incident.sapId.lowercase().contains(q) ||
                it.reasonLabel.lowercase().contains(q) ||
                it.incident.title.lowercase().contains(q) ||
                it.incident.category.lowercase().contains(q)
            }
        }

        DownRegisterUiState(
            selectedTab = tab,
            searchQuery = query,
            openOutages = filterList(open),
            allIncidents = filterList(all),
            restoredOutages = filterList(restored),
            dayOutages = filteredDayItems,
            selectedDateEpochMs = dateMs,
            daySitesDownCount = daySitesCount,
            dayTotalDowntimeHours = dayDowntime,
            selectedIncidentIds = selIds,
            isMultiSelectMode = multiSelect,
            activeReasons = reasons,
            userMessage = notice.message,
            undoableIncidentId = notice.incidentId,
            undoableSapId = notice.sapId
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = DownRegisterUiState(selectedDateEpochMs = Dates.getDayStartEpochMs(clock.currentTimeMillis()))
    )

    fun onTabSelected(index: Int) {
        _selectedTab.value = index
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    // Date Navigation for Day View
    fun onPreviousDay() {
        _selectedDateEpochMs.update { it - Dates.MS_PER_DAY }
    }

    fun onNextDay() {
        _selectedDateEpochMs.update { it + Dates.MS_PER_DAY }
    }

    fun onSelectDate(epochMs: Long) {
        _selectedDateEpochMs.value = Dates.getDayStartEpochMs(epochMs)
    }

    // Multi-select & Bulk Restore
    fun toggleMultiSelectMode() {
        _isMultiSelectMode.update { !it }
        if (!_isMultiSelectMode.value) {
            _selectedIncidentIds.value = emptySet()
        }
    }

    fun toggleIncidentSelection(id: String) {
        _selectedIncidentIds.update { set ->
            if (set.contains(id)) set - id else set + id
        }
    }

    fun selectAll(ids: List<String>) {
        _selectedIncidentIds.value = ids.toSet()
    }

    fun clearSelection() {
        _selectedIncidentIds.value = emptySet()
    }

    fun restoreSelected(engineer: String? = null) {
        val idsToRestore = _selectedIncidentIds.value.toList()
        if (idsToRestore.isEmpty()) return

        viewModelScope.launch {
            val nowMs = clock.currentTimeMillis()
            val restoredCount = outageRepository.bulkRestore(idsToRestore, nowMs, engineer)
            _selectedIncidentIds.value = emptySet()
            _isMultiSelectMode.value = false
            _undoNotice.value = UndoNoticeState(
                message = "Restored $restoredCount sites successfully."
            )
        }
    }

    // Single Outage Restore & Undo
    fun restoreSite(incident: IncidentEntity, engineer: String? = null) {
        viewModelScope.launch {
            val nowMs = clock.currentTimeMillis()
            val result = outageRepository.restoreOutage(incident.id, nowMs, engineer)
            if (result.isSuccess) {
                _undoNotice.value = UndoNoticeState(
                    message = "Restored ${incident.sapId} successfully.",
                    incidentId = incident.id,
                    sapId = incident.sapId
                )
            } else {
                _undoNotice.value = UndoNoticeState(
                    message = "Failed to restore: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }

    fun undoRestore() {
        val notice = _undoNotice.value
        val incidentId = notice.incidentId ?: return
        val sapId = notice.sapId ?: ""

        viewModelScope.launch {
            val result = outageRepository.undoRestore(incidentId)
            if (result.isSuccess) {
                _undoNotice.value = UndoNoticeState(
                    message = "Outage re-opened for $sapId"
                )
            }
        }
    }

    // Inline Reason Code Edit
    fun updateIncidentReason(incidentId: String, newReasonId: String) {
        viewModelScope.launch {
            try {
                incidentRepository.updateIncidentReason(incidentId, newReasonId, clock.currentTimeMillis())
                _undoNotice.value = UndoNoticeState(message = "Root cause reason updated.")
            } catch (e: Exception) {
                _undoNotice.value = UndoNoticeState(message = "Failed to update reason: ${e.message}")
            }
        }
    }

    // Dummy-free Management
    fun clearSampleDummyData() {
        viewModelScope.launch {
            try {
                siteRepository.deleteSampleSites()
                incidentRepository.clearSampleIncidents()
                _undoNotice.value = UndoNoticeState(message = "Sample/dummy records removed cleanly.")
            } catch (e: Exception) {
                _undoNotice.value = UndoNoticeState(message = "Error: ${e.message}")
            }
        }
    }

    fun clearAllRegisterData() {
        viewModelScope.launch {
            try {
                incidentRepository.clearAllIncidents()
                _undoNotice.value = UndoNoticeState(message = "All incident records cleared.")
            } catch (e: Exception) {
                _undoNotice.value = UndoNoticeState(message = "Error: ${e.message}")
            }
        }
    }

    // Export to CSV
    fun exportRegister(context: Context) {
        viewModelScope.launch {
            try {
                val list = incidentRepository.getAllIncidentsList()
                if (list.isEmpty()) {
                    _undoNotice.value = UndoNoticeState(message = "No records to export.")
                    return@launch
                }
                val file = Exporter.exportRegisterCsv(context, list)
                Exporter.shareCsvFile(context, file, "TowerDost Outage Register Export")
            } catch (e: Exception) {
                _undoNotice.value = UndoNoticeState(message = "Export failed: ${e.localizedMessage}")
            }
        }
    }

    fun dismissMessage() {
        _undoNotice.update { it.copy(message = null) }
    }

    class Factory(
        private val outageRepository: OutageRepository,
        private val incidentRepository: IncidentRepository,
        private val reasonRepository: ReasonRepository,
        private val siteRepository: SiteRepository,
        private val clock: AppClock
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DownRegisterViewModel(outageRepository, incidentRepository, reasonRepository, siteRepository, clock) as T
        }
    }
}
