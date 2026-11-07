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
            val matchesFilter = when (filter) {
                "Issue" -> item.category.contains("Issue", ignoreCase = true) || item.isOutage == 1
                "Maintenance" -> item.category.contains("Maintenance", ignoreCase = true) || item.category.contains("Repair", ignoreCase = true)
                "Visit" -> item.category.contains("Visit", ignoreCase = true) || item.category.contains("Inspection", ignoreCase = true)
                "Note" -> item.category.contains("Note", ignoreCase = true) || item.category.contains("General", ignoreCase = true)
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

    class Factory(
        private val incidentRepository: IncidentRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ActivityViewModel(incidentRepository) as T
        }
    }
}
