package app.telecom.sitetracker.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.telecom.sitetracker.data.entity.IncidentEntity
import app.telecom.sitetracker.data.repo.IncidentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ActivityUiState(
    val selectedFilter: String = "All", // "All", "Issue", "Maintenance", "Visit", "Note"
    val searchQuery: String = "",
    val allLogs: List<IncidentEntity> = emptyList(),
    val filteredLogs: List<IncidentEntity> = emptyList()
)

class ActivityViewModel(
    private val incidentRepository: IncidentRepository
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow("All")
    private val _searchQuery = MutableStateFlow("")

    val uiState: StateFlow<ActivityUiState> = combine(
        incidentRepository.observeAllIncidents(),
        _selectedFilter,
        _searchQuery
    ) { logs, filter, query ->
        val filtered = logs.filter { item ->
            val c = item.category.lowercase().trim()
            val matchesFilter = when (filter) {
                "Maintenance" -> c.contains("maint") || c.contains("repair")
                "Visit" -> c.contains("visit") || c.contains("site") || c.contains("inspect") || c.contains("audit")
                "Note" -> c.contains("note") || c.contains("general")
                "Issue" -> c.contains("issue") || (item.isOutage == 1 && !c.contains("maint") && !c.contains("visit") && !c.contains("note"))
                else -> true
            }

            val matchesQuery = if (query.isBlank()) true else {
                val q = query.trim().lowercase()
                item.sapId.lowercase().contains(q) ||
                item.title.lowercase().contains(q) ||
                (item.description?.lowercase()?.contains(q) == true) ||
                (item.engineer?.lowercase()?.contains(q) == true)
            }

            matchesFilter && matchesQuery
        }

        ActivityUiState(
            selectedFilter = filter,
            searchQuery = query,
            allLogs = logs,
            filteredLogs = filtered
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = ActivityUiState()
    )

    fun onFilterSelected(filter: String) {
        _selectedFilter.value = filter
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun deleteLog(incidentId: String) {
        viewModelScope.launch {
            incidentRepository.deleteIncident(incidentId, deletePermanently = true)
        }
    }

    class Factory(
        private val incidentRepository: IncidentRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ActivityViewModel(incidentRepository) as T
        }
    }
}
