package app.telecom.sitetracker.ui.sitelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import app.telecom.sitetracker.data.entity.SiteEntity
import app.telecom.sitetracker.data.repo.IncidentRepository
import app.telecom.sitetracker.data.repo.OutageRepository
import app.telecom.sitetracker.data.repo.SiteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

data class SiteListFilterState(
    val selectedCircle: String? = null,
    val selectedStatus: String? = null,
    val filterDownOnly: Boolean = false
)

class SiteListViewModel(
    private val siteRepository: SiteRepository,
    private val outageRepository: OutageRepository,
    private val incidentRepository: IncidentRepository
) : ViewModel() {

    private val _filterState = MutableStateFlow(SiteListFilterState())
    val filterState: StateFlow<SiteListFilterState> = _filterState.asStateFlow()

    val pagedSites: Flow<PagingData<SiteEntity>> = siteRepository.getPagedSites()
        .cachedIn(viewModelScope)

    val activeSitesCount: Flow<Int> = siteRepository.countActiveSites()

    fun onCircleSelected(circle: String?) {
        _filterState.update { it.copy(selectedCircle = circle) }
    }

    fun onStatusSelected(status: String?) {
        _filterState.update { it.copy(selectedStatus = status) }
    }

    fun onToggleDownFilter() {
        _filterState.update { it.copy(filterDownOnly = !it.filterDownOnly) }
    }
}
