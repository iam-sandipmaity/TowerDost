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

    private val _activeFilters = MutableStateFlow<List<ActiveFilter>>(emptyList())
    val activeFilters: StateFlow<List<ActiveFilter>> = _activeFilters.asStateFlow()

    private val _availableDimensions = MutableStateFlow<List<FilterDimension>>(emptyList())
    val availableDimensions: StateFlow<List<FilterDimension>> = _availableDimensions.asStateFlow()

    val pagedSites: Flow<PagingData<SiteEntity>> = siteRepository.getPagedSites()
        .cachedIn(viewModelScope)

    val activeSitesCount: Flow<Int> = siteRepository.countActiveSites()

    private var searchJob: Job? = null

    init {
        loadFilterDimensions()
    }

    fun loadFilterDimensions() {
        viewModelScope.launch {
            try {
                _availableDimensions.value = siteRepository.getAvailableFilterDimensions()
            } catch (_: Exception) {}
        }
    }

    fun addFilter(dimension: FilterDimension, selectedValue: String): Boolean {
        val current = _activeFilters.value
        if (current.size >= 4) {
            _userMessage.value = "Maximum 4 filters allowed at once."
            return false
        }
        val trimmedVal = selectedValue.trim()
        if (trimmedVal.isEmpty()) return false

        val existingIndex = current.indexOfFirst { it.dimensionKey.equals(dimension.key, ignoreCase = true) }
        val updated = current.toMutableList()
        val newFilter = ActiveFilter(
            dimensionKey = dimension.key,
            displayName = dimension.displayName,
            selectedValue = trimmedVal,
            isCoreField = dimension.isCoreField
        )
        if (existingIndex >= 0) {
            updated[existingIndex] = newFilter
        } else {
            updated.add(newFilter)
        }
        _activeFilters.value = updated
        executeSearchOrNearMe()
        return true
    }

    fun removeFilter(filterId: String) {
        _activeFilters.update { list -> list.filterNot { it.id == filterId } }
        executeSearchOrNearMe()
    }

    fun clearAllFilters() {
        _activeFilters.value = emptyList()
        executeSearchOrNearMe()
    }

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
        val filters = _activeFilters.value

        // If neither query nor near me nor dynamic filters active -> empty search results (shows master paged directory)
        if (query.isEmpty() && !nearMe && filters.isEmpty()) {
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
                // 1. Base site candidates
                val baseSites = when {
                    query.isNotEmpty() -> siteRepository.searchSites(query)
                    nearMe && coords != null -> siteRepository.searchNearby(coords.first, coords.second).map { it.site }
                    else -> siteRepository.getAllSitesList()
                }

                // 2. Conjunction filtering across all active filters
                var matchingSites = baseSites
                for (filter in filters) {
                    if (filter.isCoreField) {
                        val targetVal = filter.selectedValue.trim()
                        matchingSites = matchingSites.filter { site ->
                            val v = when (filter.dimensionKey.lowercase()) {
                                "circle" -> site.circle
                                "city" -> site.city
                                "district" -> site.district
                                "technology" -> site.technology
                                "site_type" -> site.siteType
                                "status" -> site.status
                                else -> null
                            }
                            v != null && v.trim().equals(targetVal, ignoreCase = true)
                        }
                    } else {
                        val validSapIds = siteRepository.getSapIdsMatchingSpec(filter.dimensionKey, filter.selectedValue).toSet()
                        matchingSites = matchingSites.filter { site -> validSapIds.contains(site.sapId) }
                    }
                }

                // 3. Distance calculation and sorting if Near Me is active
                if (nearMe && coords != null) {
                    val (refLat, refLng) = coords
                    val withDist = matchingSites.map { site ->
                        if (site.lat != null && site.lng != null) {
                            val dist = Geo.haversineDistanceKm(refLat, refLng, site.lat, site.lng)
                            val fmt = Geo.formatDistanceAndBearing(refLat, refLng, site.lat, site.lng)
                            SiteSearchResult(site = site, distanceInfo = fmt, distanceKm = dist)
                        } else {
                            SiteSearchResult(site = site, distanceInfo = null, distanceKm = Double.MAX_VALUE)
                        }
                    }.sortedBy { it.distanceKm }
                    _searchResults.value = withDist
                    if (withDist.isEmpty() && query.isEmpty()) {
                        _userMessage.value = "No nearby sites matching selected criteria found."
                    }
                } else {
                    _searchResults.value = matchingSites.map { SiteSearchResult(site = it, distanceInfo = null) }
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
