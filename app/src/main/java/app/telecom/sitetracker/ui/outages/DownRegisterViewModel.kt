package app.telecom.sitetracker.ui.outages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.telecom.sitetracker.data.entity.IncidentEntity
import app.telecom.sitetracker.data.repo.OutageRepository
import app.telecom.sitetracker.util.AppClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DownRegisterUiState(
    val selectedTab: Int = 0, // 0 = Active Down, 1 = Restored, 2 = All
    val searchQuery: String = "",
    val openOutages: List<IncidentEntity> = emptyList(),
    val restoredOutages: List<IncidentEntity> = emptyList(),
    val allOutages: List<IncidentEntity> = emptyList(),
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
    private val clock: AppClock
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(0)
    private val _searchQuery = MutableStateFlow("")
    private val _undoNotice = MutableStateFlow(UndoNoticeState())

    private val outagesDataFlow = combine(
        outageRepository.observeOpenOutages(),
        outageRepository.observeRestoredOutages(),
        outageRepository.observeAllOutages()
    ) { open, restored, all ->
        Triple(open, restored, all)
    }

    val uiState: StateFlow<DownRegisterUiState> = combine(
        outagesDataFlow,
        _selectedTab,
        _searchQuery,
        _undoNotice
    ) { (open, restored, all), tab, query, notice ->
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

        DownRegisterUiState(
            selectedTab = tab,
            searchQuery = query,
            openOutages = filterList(open),
            restoredOutages = filterList(restored),
            allOutages = filterList(all),
            userMessage = notice.message,
            undoableIncidentId = notice.incidentId,
            undoableSapId = notice.sapId
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = DownRegisterUiState()
    )

    fun onTabSelected(index: Int) {
        _selectedTab.value = index
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

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

    fun dismissMessage() {
        _undoNotice.update { it.copy(message = null) }
    }

    class Factory(
        private val outageRepository: OutageRepository,
        private val clock: AppClock
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DownRegisterViewModel(outageRepository, clock) as T
        }
    }
}
