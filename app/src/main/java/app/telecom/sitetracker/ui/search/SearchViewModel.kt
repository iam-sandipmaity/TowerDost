package app.telecom.sitetracker.ui.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.telecom.sitetracker.data.entity.SiteEntity
import app.telecom.sitetracker.data.repo.SiteRepository
import app.telecom.sitetracker.data.repo.SiteWithDistance
import app.telecom.sitetracker.util.Geo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchResultItem(
    val site: SiteEntity,
    val distanceInfo: String? = null
)

data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<SearchResultItem> = emptyList(),
    val isNearMeActive: Boolean = false,
    val detectedCoordinates: Pair<Double, Double>? = null,
    val errorMessage: String? = null
)

class SearchViewModel(
    private val siteRepository: SiteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    fun onQueryChanged(newQuery: String) {
        _uiState.update { it.copy(query = newQuery, errorMessage = null) }
        val trimmed = newQuery.trim()

        if (trimmed.isEmpty()) {
            _uiState.update {
                it.copy(
                    results = emptyList(),
                    isSearching = false,
                    detectedCoordinates = null
                )
            }
            return
        }

        // Check if query is a coordinate pair
        val coords = Geo.parseCoordinatePair(trimmed)
        if (coords != null) {
            _uiState.update { it.copy(detectedCoordinates = coords, isNearMeActive = false) }
            searchNearCoordinates(coords.first, coords.second)
            return
        }

        _uiState.update { it.copy(detectedCoordinates = null, isNearMeActive = false) }
        searchByText(trimmed)
    }

    private fun searchByText(query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            try {
                val sites = siteRepository.searchSites(query)
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        results = sites.distinctBy { s -> s.sapId }.map { site -> SearchResultItem(site = site) }
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        errorMessage = "Search error: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    private fun searchNearCoordinates(lat: Double, lng: Double) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            try {
                val nearby = siteRepository.searchNearby(lat, lng)
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        results = nearby.map { item ->
                            SearchResultItem(
                                site = item.site,
                                distanceInfo = item.formattedDistance
                            )
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        errorMessage = "Location search error: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun onNearMeClicked(context: Context) {
        val location = Geo.getLastKnownLocation(context)
        if (location == null) {
            _uiState.update {
                it.copy(
                    errorMessage = "Could not obtain current GPS location. Ensure location services are enabled."
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isNearMeActive = true,
                query = "",
                detectedCoordinates = Pair(location.latitude, location.longitude)
            )
        }
        searchNearCoordinates(location.latitude, location.longitude)
    }
}
