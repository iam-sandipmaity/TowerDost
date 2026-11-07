package app.telecom.sitetracker.ui.sitelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import app.telecom.sitetracker.data.entity.SiteEntity
import app.telecom.sitetracker.data.repo.IncidentRepository
import app.telecom.sitetracker.data.repo.OutageRepository
import app.telecom.sitetracker.data.repo.SiteRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SiteListFilterState(
    val selectedCircle: String? = null,
    val selectedStatus: String? = null,
    val filterDownOnly: Boolean = false,
    val selectedTech: String? = null
)

class SiteListViewModel(
    private val siteRepository: SiteRepository,
    private val outageRepository: OutageRepository,
    private val incidentRepository: IncidentRepository
) : ViewModel() {

    private val _filterState = MutableStateFlow(SiteListFilterState())
    val filterState: StateFlow<SiteListFilterState> = _filterState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SiteEntity>>(emptyList())
    val searchResults: StateFlow<List<SiteEntity>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    val pagedSites: Flow<PagingData<SiteEntity>> = siteRepository.getPagedSites()
        .cachedIn(viewModelScope)

    val activeSitesCount: Flow<Int> = siteRepository.countActiveSites()

    private var searchJob: Job? = null

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()

        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }

        searchJob = viewModelScope.launch {
            _isSearching.value = true
            // Debounce slightly to keep typing buttery smooth
            delay(150)
            val results = siteRepository.searchSites(trimmed)
            _searchResults.value = results
            _isSearching.value = false
        }
    }

    fun clearSearch() {
        onSearchQueryChanged("")
    }

    fun onCircleSelected(circle: String?) {
        _filterState.update { 
            it.copy(selectedCircle = if (it.selectedCircle == circle) null else circle) 
        }
    }

    fun onTechSelected(tech: String?) {
        _filterState.update { 
            it.copy(selectedTech = if (it.selectedTech == tech) null else tech) 
        }
    }

    fun onStatusSelected(status: String?) {
        _filterState.update { 
            it.copy(selectedStatus = if (it.selectedStatus == status) null else status) 
        }
    }

    fun onToggleDownFilter() {
        _filterState.update { it.copy(filterDownOnly = !it.filterDownOnly) }
    }
}
