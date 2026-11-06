package app.telecom.sitetracker.ui.incident

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.telecom.sitetracker.data.entity.IncidentEntity
import app.telecom.sitetracker.data.entity.IncidentPhotoEntity
import app.telecom.sitetracker.data.entity.ReasonEntity
import app.telecom.sitetracker.data.entity.SiteEntity
import app.telecom.sitetracker.data.repo.IncidentRepository
import app.telecom.sitetracker.data.repo.OutageRepository
import app.telecom.sitetracker.data.repo.ReasonRepository
import app.telecom.sitetracker.data.repo.SiteRepository
import app.telecom.sitetracker.util.AppClock
import app.telecom.sitetracker.util.Geo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class LogIncidentUiState(
    val initialSapId: String? = null,
    val selectedSite: SiteEntity? = null,
    val siteSearchQuery: String = "",
    val siteSuggestions: List<SiteEntity> = emptyList(),
    val isSearchingSites: Boolean = false,
    val isOutage: Boolean = true,
    val severity: String = "Major",
    val category: String = "Power",
    val selectedReasonId: String? = null,
    val activeReasons: List<ReasonEntity> = emptyList(),
    val title: String = "",
    val description: String = "",
    val actionTaken: String = "",
    val engineer: String = "",
    val ticketRef: String = "",
    val downAtMs: Long = 0L,
    val isRestored: Boolean = false,
    val restoredAtMs: Long = 0L,
    val gpsLat: Double? = null,
    val gpsLng: Double? = null,
    val gpsAccuracyM: Float? = null,
    val gpsDistanceFromSiteM: Double? = null,
    val photos: List<IncidentPhotoEntity> = emptyList(),
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val savedIncidentId: String? = null,
    val errorMessage: String? = null
)

class LogIncidentViewModel(
    private val initialSapId: String?,
    private val siteRepository: SiteRepository,
    private val incidentRepository: IncidentRepository,
    private val outageRepository: OutageRepository,
    private val reasonRepository: ReasonRepository,
    private val clock: AppClock
) : ViewModel() {

    private val now = clock.currentTimeMillis()
    private val _uiState = MutableStateFlow(
        LogIncidentUiState(
            initialSapId = initialSapId,
            downAtMs = now,
            restoredAtMs = now
        )
    )
    val uiState: StateFlow<LogIncidentUiState> = _uiState.asStateFlow()

    init {
        // Load active reasons
        viewModelScope.launch {
            reasonRepository.getActiveReasons().collect { reasons ->
                _uiState.update { current ->
                    val defaultReason = reasons.find { it.category.equals(current.category, ignoreCase = true) }
                        ?: reasons.firstOrNull()
                    current.copy(
                        activeReasons = reasons,
                        selectedReasonId = current.selectedReasonId ?: defaultReason?.id
                    )
                }
            }
        }

        // If sapId is provided, pre-fetch the site
        if (!initialSapId.isNullOrBlank()) {
            viewModelScope.launch {
                val site = siteRepository.getSiteBySapId(initialSapId)
                _uiState.update { it.copy(selectedSite = site) }
            }
        }
    }

    fun onSiteSearchQueryChanged(query: String) {
        _uiState.update { it.copy(siteSearchQuery = query, isSearchingSites = query.isNotBlank()) }
        if (query.isBlank()) {
            _uiState.update { it.copy(siteSuggestions = emptyList(), isSearchingSites = false) }
            return
        }

        viewModelScope.launch {
            try {
                val results = siteRepository.searchSites(query).take(10)
                _uiState.update { it.copy(siteSuggestions = results, isSearchingSites = false) }
            } catch (_: Exception) {
                _uiState.update { it.copy(siteSuggestions = emptyList(), isSearchingSites = false) }
            }
        }
    }

    fun selectSite(site: SiteEntity) {
        _uiState.update {
            it.copy(
                selectedSite = site,
                siteSearchQuery = "",
                siteSuggestions = emptyList(),
                isSearchingSites = false,
                errorMessage = null
            )
        }
        recalculateGpsDistance()
    }

    fun clearSelectedSite() {
        _uiState.update {
            it.copy(
                selectedSite = null,
                gpsDistanceFromSiteM = null
            )
        }
    }

    fun setIsOutage(isOutage: Boolean) {
        _uiState.update { it.copy(isOutage = isOutage) }
    }

    fun setSeverity(severity: String) {
        _uiState.update { it.copy(severity = severity) }
    }

    fun setCategory(category: String) {
        _uiState.update { current ->
            val matchingReason = current.activeReasons.find {
                it.category.equals(category, ignoreCase = true) || it.groupName.equals(category, ignoreCase = true)
            }
            val newReasonId = matchingReason?.id ?: current.selectedReasonId
            val defaultTitle = "${category} Issue"
            current.copy(
                category = category,
                selectedReasonId = newReasonId,
                title = if (current.title.isBlank() || current.title.endsWith("Issue")) defaultTitle else current.title
            )
        }
    }

    fun setSelectedReason(reasonId: String) {
        _uiState.update { current ->
            val reason = current.activeReasons.find { it.id == reasonId }
            val autoTitle = reason?.label ?: current.title
            current.copy(
                selectedReasonId = reasonId,
                title = if (current.title.isBlank() || current.title.endsWith("Issue")) autoTitle else current.title
            )
        }
    }

    fun setTitle(title: String) {
        _uiState.update { it.copy(title = title) }
    }

    fun setDescription(description: String) {
        _uiState.update { it.copy(description = description) }
    }

    fun setActionTaken(action: String) {
        _uiState.update { it.copy(actionTaken = action) }
    }

    fun setEngineer(engineer: String) {
        _uiState.update { it.copy(engineer = engineer) }
    }

    fun setTicketRef(ref: String) {
        _uiState.update { it.copy(ticketRef = ref) }
    }

    fun setDownAtMs(timeMs: Long) {
        _uiState.update { it.copy(downAtMs = timeMs) }
    }

    fun setIsRestored(restored: Boolean) {
        _uiState.update { it.copy(isRestored = restored) }
    }

    fun setRestoredAtMs(timeMs: Long) {
        _uiState.update { it.copy(restoredAtMs = timeMs) }
    }

    fun stampGps(context: Context) {
        val location = Geo.getLastKnownLocation(context)
        if (location != null) {
            _uiState.update {
                it.copy(
                    gpsLat = location.latitude,
                    gpsLng = location.longitude,
                    gpsAccuracyM = location.accuracy
                )
            }
            recalculateGpsDistance()
        } else {
            _uiState.update {
                it.copy(errorMessage = "Could not obtain GPS fix. Please ensure location services are enabled.")
            }
        }
    }

    private fun recalculateGpsDistance() {
        val current = _uiState.value
        val site = current.selectedSite
        if (site != null && site.lat != null && site.lng != null && current.gpsLat != null && current.gpsLng != null) {
            val dist = Geo.haversineDistanceKm(current.gpsLat, current.gpsLng, site.lat, site.lng) * 1000.0
            _uiState.update { it.copy(gpsDistanceFromSiteM = dist) }
        }
    }

    fun addPhoto(relPath: String, caption: String? = null) {
        val photo = IncidentPhotoEntity(
            id = UUID.randomUUID().toString(),
            incidentId = "", // Filled at save time
            sapId = _uiState.value.selectedSite?.sapId ?: "",
            relPath = relPath,
            caption = caption,
            takenAt = clock.currentTimeMillis(),
            bytes = null
        )
        _uiState.update { it.copy(photos = it.photos + photo) }
    }

    fun removePhoto(photoId: String) {
        _uiState.update { current ->
            current.copy(photos = current.photos.filter { it.id != photoId })
        }
    }

    fun saveIncident(onSuccess: (incidentId: String, sapId: String) -> Unit) {
        val current = _uiState.value
        val site = current.selectedSite

        if (site == null) {
            _uiState.update { it.copy(errorMessage = "Please select a target site.") }
            return
        }

        if (current.title.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter an incident summary or select a reason.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                val incidentId = UUID.randomUUID().toString()
                val nowMs = clock.currentTimeMillis()

                val incident = IncidentEntity(
                    id = incidentId,
                    sapId = site.sapId,
                    occurredAt = current.downAtMs,
                    reportedAt = nowMs,
                    attendedAt = nowMs,
                    category = current.category,
                    reasonId = current.selectedReasonId,
                    severity = current.severity,
                    status = if (current.isOutage && !current.isRestored) "Open" else "Resolved",
                    isOutage = if (current.isOutage) 1 else 0,
                    downAt = if (current.isOutage) current.downAtMs else null,
                    restoredAt = if (current.isOutage && current.isRestored) current.restoredAtMs else null,
                    title = current.title.trim(),
                    description = current.description.trim().ifBlank { null },
                    actionTaken = current.actionTaken.trim().ifBlank { null },
                    engineer = current.engineer.trim().ifBlank { null },
                    ticketRef = current.ticketRef.trim().ifBlank { null },
                    gpsLat = current.gpsLat,
                    gpsLng = current.gpsLng,
                    gpsAccuracyM = current.gpsAccuracyM,
                    gpsDistanceFromSiteM = current.gpsDistanceFromSiteM,
                    createdAt = nowMs,
                    updatedAt = nowMs
                )

                incidentRepository.saveIncident(incident, changedBy = current.engineer.ifBlank { null }, changeTimeMs = nowMs)

                // Save associated photos
                for (photo in current.photos) {
                    val fullPhoto = photo.copy(
                        incidentId = incidentId,
                        sapId = site.sapId
                    )
                    incidentRepository.addPhoto(fullPhoto)
                }

                _uiState.update {
                    it.copy(
                        isSaving = false,
                        isSaved = true,
                        savedIncidentId = incidentId
                    )
                }

                onSuccess(incidentId, site.sapId)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = "Failed to save: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    class Factory(
        private val sapId: String?,
        private val siteRepository: SiteRepository,
        private val incidentRepository: IncidentRepository,
        private val outageRepository: OutageRepository,
        private val reasonRepository: ReasonRepository,
        private val clock: AppClock
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LogIncidentViewModel(
                initialSapId = sapId,
                siteRepository = siteRepository,
                incidentRepository = incidentRepository,
                outageRepository = outageRepository,
                reasonRepository = reasonRepository,
                clock = clock
            ) as T
        }
    }
}
