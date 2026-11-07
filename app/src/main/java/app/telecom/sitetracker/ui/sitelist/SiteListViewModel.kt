package app.telecom.sitetracker.ui.sitelist

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import app.telecom.sitetracker.data.entity.SiteEntity
import app.telecom.sitetracker.data.repo.IncidentRepository
import app.telecom.sitetracker.data.repo.OutageRepository
import app.telecom.sitetracker.data.repo.SiteRepository
import app.telecom.sitetracker.data.repo.SiteWithDistance
import app.telecom.sitetracker.util.Geo
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

data class SiteSearchResult(
    val site: SiteEntity,
    val distanceInfo: String? = null,
    val distanceKm: Double = Double.MAX_VALUE
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

    private val _searchResults = MutableStateFlow<List<SiteSearchResult>>(emptyList())
    val searchResults: StateFlow<List<SiteSearchResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _isNearMeActive = MutableStateFlow(false)
    val isNearMeActive: StateFlow<Boolean> = _isNearMeActive.asStateFlow()

    private val _userCoordinates = MutableStateFlow<Pair<Double, Double>?>(null)
    val userCoordinates: StateFlow<Pair<Double, Double>?> = _userCoordinates.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    val pagedSites: Flow<PagingData<SiteEntity>> = siteRepository.getPagedSites()
        .cachedIn(viewModelScope)

    val activeSitesCount: Flow<Int> = siteRepository.countActiveSites()

    private var searchJob: Job? = null

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        executeSearchOrNearMe()
    }

    fun clearSearch() {
        _searchQuery.value = ""
        executeSearchOrNearMe()
    }

    fun onNearMeClicked(context: Context) {
        val location = Geo.getLastKnownLocation(context)
        if (location == null) {
            _userMessage.value = "GPS location unavailable. Ensure GPS is enabled."
            return
        }

        // NOTE: Keep user's search query intact!
        _userCoordinates.value = Pair(location.latitude, location.longitude)
        _isNearMeActive.value = true
        executeSearchOrNearMe()
    }

    fun clearNearMe() {
        _isNearMeActive.value = false
        _userCoordinates.value = null
        executeSearchOrNearMe()
    }

    private fun executeSearchOrNearMe() {
        searchJob?.cancel()
        val query = _searchQuery.value.trim()
        val nearMe = _isNearMeActive.value
        val coords = _userCoordinates.value

        // If neither query nor near me is active -> empty search results (shows master paged directory)
        if (query.isEmpty() && !nearMe) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }

        searchJob = viewModelScope.launch {
            _isSearching.value = true
            if (query.isNotEmpty()) {
                delay(150)
            }

            try {
                if (query.isNotEmpty()) {
                    // Find sites matching the text query
                    val matches = siteRepository.searchSites(query)
                    if (nearMe && coords != null) {
                        // Calculate distance from current GPS and sort matching sites closest to farthest
                        val (refLat, refLng) = coords
                        val withDist = matches.map { site ->
                            if (site.lat != null && site.lng != null) {
                                val dist = Geo.haversineDistanceKm(refLat, refLng, site.lat, site.lng)
                                val fmt = Geo.formatDistanceAndBearing(refLat, refLng, site.lat, site.lng)
                                SiteSearchResult(site = site, distanceInfo = fmt, distanceKm = dist)
                            } else {
                                SiteSearchResult(site = site, distanceInfo = null, distanceKm = Double.MAX_VALUE)
                            }
                        }.sortedBy { it.distanceKm }
                        _searchResults.value = withDist
                    } else {
                        _searchResults.value = matches.map { SiteSearchResult(site = it, distanceInfo = null) }
                    }
                } else if (nearMe && coords != null) {
                    // Query is blank, but Near Me is active: show all nearby towers by proximity
                    val (refLat, refLng) = coords
                    val nearby = siteRepository.searchNearby(refLat, refLng)
                    _searchResults.value = nearby.map {
                        SiteSearchResult(site = it.site, distanceInfo = it.formattedDistance, distanceKm = it.distanceKm)
                    }
                    if (nearby.isEmpty()) {
                        _userMessage.value = "No nearby sites with GPS coordinates found."
                    }
                }
            } catch (e: Exception) {
                _userMessage.value = "Search error: ${e.message}"
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun dismissUserMessage() {
        _userMessage.value = null
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
