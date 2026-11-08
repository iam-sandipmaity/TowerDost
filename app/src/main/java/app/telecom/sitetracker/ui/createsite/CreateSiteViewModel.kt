package app.telecom.sitetracker.ui.createsite

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.telecom.sitetracker.data.entity.SiteEntity
import app.telecom.sitetracker.data.entity.SiteFieldEntity
import app.telecom.sitetracker.data.repo.SiteRepository
import app.telecom.sitetracker.util.Geo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

data class SpecEntryState(
    val fieldKey: String,
    val fieldValue: String,
    val groupName: String,
    val isUniversal: Boolean = true
)

data class CreateSiteUiState(
    val sapId: String = "",
    val name: String = "",
    val circle: String = "",
    val region: String = "",
    val state: String = "",
    val district: String = "",
    val city: String = "",
    val latStr: String = "",
    val lngStr: String = "",
    val siteType: String = "GBT",
    val technology: String = "4G",
    val status: String = "Operational",
    val address: String = "",
    val notes: String = "",
    val specs: List<SpecEntryState> = emptyList(),
    val isGpsFetching: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val createdSapId: String? = null
)

class CreateSiteViewModel(
    private val siteRepository: SiteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateSiteUiState())
    val uiState: StateFlow<CreateSiteUiState> = _uiState.asStateFlow()

    init {
        loadUniversalSpecs()
    }

    fun loadUniversalSpecs() {
        viewModelScope.launch(Dispatchers.IO) {
            val universalFields = siteRepository.getUniversalFields()
            val entries = universalFields.map { uni ->
                SpecEntryState(
                    fieldKey = uni.fieldKey,
                    fieldValue = uni.defaultValue ?: "",
                    groupName = uni.groupName,
                    isUniversal = true
                )
            }
            _uiState.update { state ->
                // Preserve any custom specs already added if reloading
                val custom = state.specs.filter { !it.isUniversal }
                state.copy(specs = entries + custom)
            }
        }
    }

    fun onSapIdChange(value: String) {
        _uiState.update { it.copy(sapId = value.uppercase(Locale.ROOT), errorMessage = null) }
    }

    fun onNameChange(value: String) {
        _uiState.update { it.copy(name = value) }
    }

    fun onCircleChange(value: String) {
        _uiState.update { it.copy(circle = value) }
    }

    fun onRegionChange(value: String) {
        _uiState.update { it.copy(region = value) }
    }

    fun onStateChange(value: String) {
        _uiState.update { it.copy(state = value) }
    }

    fun onDistrictChange(value: String) {
        _uiState.update { it.copy(district = value) }
    }

    fun onCityChange(value: String) {
        _uiState.update { it.copy(city = value) }
    }

    fun onLatChange(value: String) {
        _uiState.update { it.copy(latStr = value) }
    }

    fun onLngChange(value: String) {
        _uiState.update { it.copy(lngStr = value) }
    }

    fun onSiteTypeChange(value: String) {
        _uiState.update { it.copy(siteType = value) }
    }

    fun onTechnologyChange(value: String) {
        _uiState.update { it.copy(technology = value) }
    }

    fun onStatusChange(value: String) {
        _uiState.update { it.copy(status = value) }
    }

    fun onAddressChange(value: String) {
        _uiState.update { it.copy(address = value) }
    }

    fun onNotesChange(value: String) {
        _uiState.update { it.copy(notes = value) }
    }

    fun onSpecValueChange(groupName: String, fieldKey: String, value: String) {
        _uiState.update { state ->
            val updatedSpecs = state.specs.map { spec ->
                if (spec.groupName.equals(groupName, ignoreCase = true) &&
                    spec.fieldKey.equals(fieldKey, ignoreCase = true)
                ) {
                    spec.copy(fieldValue = value)
                } else {
                    spec
                }
            }
            state.copy(specs = updatedSpecs)
        }
    }

    fun addCustomSpec(groupName: String, fieldKey: String, value: String) {
        val trimmedKey = fieldKey.trim().uppercase(Locale.ROOT)
        if (trimmedKey.isEmpty()) return

        _uiState.update { state ->
            val exists = state.specs.any {
                it.groupName.equals(groupName, ignoreCase = true) &&
                it.fieldKey.equals(trimmedKey, ignoreCase = true)
            }
            if (exists) {
                state.copy(errorMessage = "Field '$trimmedKey' already exists in $groupName")
            } else {
                val newSpec = SpecEntryState(
                    fieldKey = trimmedKey,
                    fieldValue = value.trim(),
                    groupName = groupName.trim().uppercase(Locale.ROOT),
                    isUniversal = false
                )
                state.copy(specs = state.specs + newSpec, errorMessage = null)
            }
        }
    }

    fun removeSpec(groupName: String, fieldKey: String) {
        _uiState.update { state ->
            state.copy(
                specs = state.specs.filterNot {
                    it.groupName.equals(groupName, ignoreCase = true) &&
                    it.fieldKey.equals(fieldKey, ignoreCase = true)
                }
            )
        }
    }

    fun captureGpsLocation(context: Context) {
        _uiState.update { it.copy(isGpsFetching = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val loc = Geo.getLastKnownLocation(context)
            if (loc != null) {
                val latStr = String.format(Locale.US, "%.6f", loc.latitude)
                val lngStr = String.format(Locale.US, "%.6f", loc.longitude)
                _uiState.update {
                    it.copy(
                        latStr = latStr,
                        lngStr = lngStr,
                        isGpsFetching = false,
                        errorMessage = null
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isGpsFetching = false,
                        errorMessage = "Unable to fetch GPS fix. Please ensure location is enabled."
                    )
                }
            }
        }
    }

    fun saveSite(onSuccess: (String) -> Unit) {
        val state = _uiState.value
        val rawSap = state.sapId.trim()
        if (rawSap.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "SAP ID is required") }
            return
        }

        val latDouble = state.latStr.trim().toDoubleOrNull()
        if (state.latStr.isNotBlank() && latDouble == null) {
            _uiState.update { it.copy(errorMessage = "Invalid latitude value") }
            return
        }
        val lngDouble = state.lngStr.trim().toDoubleOrNull()
        if (state.lngStr.isNotBlank() && lngDouble == null) {
            _uiState.update { it.copy(errorMessage = "Invalid longitude value") }
            return
        }

        _uiState.update { it.copy(isSaving = true, errorMessage = null) }

        viewModelScope.launch(Dispatchers.IO) {
            val siteEntity = SiteEntity(
                sapId = rawSap,
                sapNorm = SiteEntity.normalize(rawSap),
                name = state.name.trim().ifBlank { null },
                lat = latDouble,
                lng = lngDouble,
                circle = state.circle.trim().ifBlank { null },
                region = state.region.trim().ifBlank { null },
                state = state.state.trim().ifBlank { null },
                district = state.district.trim().ifBlank { null },
                city = state.city.trim().ifBlank { null },
                technology = state.technology.trim().ifBlank { null },
                siteType = state.siteType.trim().ifBlank { null },
                status = state.status.trim().ifBlank { null },
                address = state.address.trim().ifBlank { null },
                notes = state.notes.trim().ifBlank { null }
            )

            val specEntities = state.specs.mapIndexed { index, spec ->
                SiteFieldEntity(
                    id = 0L,
                    sapId = rawSap,
                    fieldKey = spec.fieldKey.trim(),
                    fieldValue = spec.fieldValue.trim().ifBlank { null },
                    groupName = spec.groupName.trim(),
                    sortOrder = index + 1
                )
            }

            val result = siteRepository.createSite(siteEntity, specEntities)
            if (result.isSuccess) {
                _uiState.update { it.copy(isSaving = false, createdSapId = rawSap) }
                launch(Dispatchers.Main) {
                    onSuccess(rawSap)
                }
            } else {
                val error = result.exceptionOrNull()?.message ?: "Failed to create site"
                _uiState.update { it.copy(isSaving = false, errorMessage = error) }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
