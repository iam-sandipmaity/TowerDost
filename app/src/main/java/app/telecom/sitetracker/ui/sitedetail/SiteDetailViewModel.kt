package app.telecom.sitetracker.ui.sitedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.telecom.sitetracker.data.entity.IncidentEntity
import app.telecom.sitetracker.data.entity.SiteEntity
import app.telecom.sitetracker.data.entity.SiteFieldEntity
import app.telecom.sitetracker.data.repo.IncidentRepository
import app.telecom.sitetracker.data.repo.SiteRepository
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
    val errorMessage: String? = null
)

class SiteDetailViewModel(
    private val sapId: String,
    private val siteRepository: SiteRepository,
    private val incidentRepository: IncidentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SiteDetailUiState())
    val uiState: StateFlow<SiteDetailUiState> = _uiState.asStateFlow()

    init {
        loadSiteDetails()
    }

    private fun loadSiteDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val site = siteRepository.getSiteBySapId(sapId)
                val fields = siteRepository.getFieldsForSite(sapId)
                val incidents = incidentRepository.getIncidentsForSite(sapId)

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
                        contactPhone = phone,
                        incidents = incidents
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
