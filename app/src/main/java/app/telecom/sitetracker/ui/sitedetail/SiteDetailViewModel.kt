package app.telecom.sitetracker.ui.sitedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.telecom.sitetracker.data.entity.IncidentEntity
import app.telecom.sitetracker.data.entity.SiteEntity
import app.telecom.sitetracker.data.entity.SiteFieldEntity
import app.telecom.sitetracker.data.repo.IncidentRepository
import app.telecom.sitetracker.data.repo.OutageRepository
import app.telecom.sitetracker.data.repo.SiteRepository
import app.telecom.sitetracker.util.Dates
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
    val userMessage: String? = null
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
