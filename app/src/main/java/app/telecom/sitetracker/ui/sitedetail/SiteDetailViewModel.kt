package app.telecom.sitetracker.ui.sitedetail

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.telecom.sitetracker.data.entity.IncidentEntity
import app.telecom.sitetracker.data.entity.SiteEntity
import app.telecom.sitetracker.data.entity.SiteFieldEntity
import app.telecom.sitetracker.data.export.Exporter
import app.telecom.sitetracker.data.repo.IncidentRepository
import app.telecom.sitetracker.data.repo.OutageRepository
import app.telecom.sitetracker.data.repo.SiteRepository
import app.telecom.sitetracker.util.Dates
import app.telecom.sitetracker.util.Geo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SiteDetailUiState(
    val isLoading: Boolean = true,
    val site: SiteEntity? = null,
    val groupedSpecs: Map<String, List<SiteFieldEntity>> = emptyMap(),
    val contactPhone: String? = null,
    val incidents: List<IncidentEntity> = emptyList(),
    val openOutage: IncidentEntity? = null,
    val repeatFaultCount: Int = 0,
    val isRepeatFault: Boolean = false,
    val dominantCategory: String? = null,
    val recommendation: String? = null,
    val errorMessage: String? = null,
    val userMessage: String? = null,
    // Mode toggle & GPS distance
    val isEditMode: Boolean = false,
    val distanceInfo: String? = null,
    // Overview edit & safeguards
    val isEditingOverview: Boolean = false,
    val draftOverview: SiteEntity? = null,
    val confirmSaveOverview: Boolean = false,
    // Spec edit & safeguards
    val specToEdit: SiteFieldEntity? = null,
    val isAddingSpec: Boolean = false,
    val confirmDeleteSpec: SiteFieldEntity? = null
)

class SiteDetailViewModel(
    private val sapId: String,
    private val siteRepository: SiteRepository,
    private val incidentRepository: IncidentRepository,
    private val outageRepository: OutageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SiteDetailUiState())
    val uiState: StateFlow<SiteDetailUiState> = _uiState.asStateFlow()

    init {
        loadSiteDetails()
        observeIncidents()
    }

    private fun observeIncidents() {
        viewModelScope.launch {
            incidentRepository.observeIncidentsForSite(sapId).collect { incidentList ->
                val now = System.currentTimeMillis()
                val open = incidentList.firstOrNull {
                    it.isOutage == 1 && it.restoredAt == null && it.status != "Resolved" && it.status != "Closed"
                }

                val thirtyDaysAgo = now - (30L * Dates.MS_PER_DAY)
                val recentOutages = incidentList.filter { it.isOutage == 1 && it.occurredAt >= thirtyDaysAgo }
                val count = recentOutages.size
                val isRepeat = count >= 3
                val dominant = recentOutages.groupingBy { it.category }.eachCount().maxByOrNull { it.value }?.key

                val reco = when (dominant?.lowercase()) {
                    "power" -> "Check EB cutout fuses, input phase voltage, and RM rectifier modules."
                    "battery" -> "Carry battery impedance meter and verify backup discharge hold time."
                    "dg" -> "Check DG diesel fuel level, 12V starter battery, and auto-start GCU controller."
                    "transmission", "ofc" -> "Carry OTDR / laser source and optical fibre fusion splicing kit."
                    "bts-ran" -> "Inspect BB unit cards, SFP modules, and RF jumper connections."
                    else -> "Inspect site equipment and review past action logs before dispatch."
                }

                _uiState.update {
                    it.copy(
                        incidents = incidentList,
                        openOutage = open,
                        repeatFaultCount = count,
                        isRepeatFault = isRepeat,
                        dominantCategory = dominant,
                        recommendation = reco
                    )
                }
            }
        }
    }

    fun deleteIncident(incidentId: String) {
        viewModelScope.launch {
            incidentRepository.deleteIncident(incidentId, deletePermanently = true)
        }
    }

    fun reload() {
        loadSiteDetails()
    }

    fun restoreCurrentOutage(engineer: String? = null) {
        val outage = _uiState.value.openOutage ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val res = outageRepository.restoreOutage(outage.id, now, engineer)
            if (res.isSuccess) {
                _uiState.update { it.copy(userMessage = "Site restored successfully.") }
            } else {
                _uiState.update { it.copy(errorMessage = "Failed to restore: ${res.exceptionOrNull()?.message}") }
            }
        }
    }

    fun dismissUserMessage() {
        _uiState.update { it.copy(userMessage = null, errorMessage = null) }
    }

    // --- View Only vs Edit Mode & GPS Location ---

    fun toggleEditMode() {
        _uiState.update { current ->
            val newMode = !current.isEditMode
            current.copy(
                isEditMode = newMode,
                isEditingOverview = if (!newMode) false else current.isEditingOverview
            )
        }
    }

    fun setEditMode(enabled: Boolean) {
        _uiState.update { current ->
            current.copy(
                isEditMode = enabled,
                isEditingOverview = if (!enabled) false else current.isEditingOverview
            )
        }
    }

    fun updateCurrentLocation(lat: Double, lng: Double) {
        val site = _uiState.value.site ?: return
        if (site.lat != null && site.lng != null) {
            val distFormatted = Geo.formatDistanceAndBearing(lat, lng, site.lat, site.lng)
            _uiState.update { it.copy(distanceInfo = distFormatted) }
        }
    }

    // --- Overview Edit & Accidental Modification Safeguards ---

    fun startEditingOverview() {
        val currentSite = _uiState.value.site ?: return
        _uiState.update {
            it.copy(
                isEditMode = true,
                isEditingOverview = true,
                draftOverview = currentSite.copy(),
                confirmSaveOverview = false
            )
        }
    }

    fun updateOverviewDraft(transform: (SiteEntity) -> SiteEntity) {
        _uiState.update { current ->
            val draft = current.draftOverview ?: current.site ?: return@update current
            current.copy(draftOverview = transform(draft))
        }
    }

    fun requestSaveOverview() {
        _uiState.update { it.copy(confirmSaveOverview = true) }
    }

    fun dismissSaveOverviewConfirmation() {
        _uiState.update { it.copy(confirmSaveOverview = false) }
    }

    fun cancelEditingOverview() {
        _uiState.update {
            it.copy(
                isEditingOverview = false,
                draftOverview = null,
                confirmSaveOverview = false
            )
        }
    }

    fun confirmAndSaveOverview() {
        val draft = _uiState.value.draftOverview ?: return
        viewModelScope.launch {
            try {
                siteRepository.updateSiteOverview(draft)
                _uiState.update {
                    it.copy(
                        site = draft,
                        isEditingOverview = false,
                        draftOverview = null,
                        confirmSaveOverview = false,
                        userMessage = "Site overview updated successfully."
                    )
                }
                loadSiteDetails()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        confirmSaveOverview = false,
                        errorMessage = "Failed to update site overview: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    // --- Specifications Edit & Accidental Modification Safeguards ---

    fun startEditingSpec(spec: SiteFieldEntity) {
        _uiState.update { it.copy(specToEdit = spec, isAddingSpec = false) }
    }

    fun startAddingSpec() {
        _uiState.update { it.copy(isAddingSpec = true, specToEdit = null) }
    }

    fun dismissSpecEditor() {
        _uiState.update { it.copy(specToEdit = null, isAddingSpec = false) }
    }

    fun saveSpec(fieldKey: String, fieldValue: String, groupName: String) {
        if (fieldKey.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Specification name cannot be blank.") }
            return
        }
        viewModelScope.launch {
            try {
                val currentSite = _uiState.value.site ?: return@launch
                val editing = _uiState.value.specToEdit
                if (editing != null) {
                    siteRepository.updateSiteSpec(
                        editing.copy(
                            fieldKey = fieldKey.trim(),
                            fieldValue = fieldValue.trim(),
                            groupName = groupName.trim().ifBlank { null } ?: editing.groupName
                        )
                    )
                } else {
                    siteRepository.addOrUpdateSiteSpec(
                        sapId = currentSite.sapId,
                        fieldKey = fieldKey,
                        fieldValue = fieldValue,
                        groupName = groupName
                    )
                }
                _uiState.update {
                    it.copy(
                        specToEdit = null,
                        isAddingSpec = false,
                        userMessage = "Specification '$fieldKey' saved successfully."
                    )
                }
                loadSiteDetails()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Failed to save specification: ${e.localizedMessage}")
                }
            }
        }
    }

    fun requestDeleteSpec(spec: SiteFieldEntity) {
        _uiState.update { it.copy(confirmDeleteSpec = spec) }
    }

    fun cancelDeleteSpec() {
        _uiState.update { it.copy(confirmDeleteSpec = null) }
    }

    fun confirmDeleteSpec() {
        val target = _uiState.value.confirmDeleteSpec ?: return
        viewModelScope.launch {
            try {
                siteRepository.deleteSiteSpec(target.id, target.sapId)
                _uiState.update {
                    it.copy(
                        confirmDeleteSpec = null,
                        specToEdit = null,
                        userMessage = "Specification '${target.fieldKey}' deleted."
                    )
                }
                loadSiteDetails()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        confirmDeleteSpec = null,
                        errorMessage = "Failed to delete spec: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    // --- Export Single Site Report ---

    fun exportSingleSiteReport(context: Context) {
        val site = _uiState.value.site ?: return
        viewModelScope.launch {
            try {
                val specs = siteRepository.getFieldsForSite(site.sapId)
                val incidents = incidentRepository.getIncidentsForSite(site.sapId)
                val file = Exporter.exportSingleSiteCsv(context, site, specs, incidents)
                Exporter.shareCsvFile(context, file, "TowerDost Site Report: ${site.sapId}")
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Failed to export site data: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun loadSiteDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val site = siteRepository.getSiteBySapId(sapId)
                    ?: siteRepository.getSiteBySapNorm(SiteEntity.normalize(sapId))
                val actualSapId = site?.sapId ?: sapId
                val fields = siteRepository.getFieldsForSite(actualSapId)

                // Detect contact phone number from specs
                val phoneField = fields.firstOrNull { field ->
                    val k = field.fieldKey.lowercase()
                    (k.contains("number") || k.contains("phone") || k.contains("mobile") || k.contains("tech no")) &&
                            field.fieldValue?.any { it.isDigit() } == true
                }
                val phone = phoneField?.fieldValue?.filter { it.isDigit() || it == '+' }

                // Group specs by groupName
                val grouped = fields.groupBy { it.groupName ?: "General Specifications" }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        site = site,
                        groupedSpecs = grouped,
                        contactPhone = phone
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to load site details: ${e.localizedMessage}"
                    )
                }
            }
        }
    }
}
