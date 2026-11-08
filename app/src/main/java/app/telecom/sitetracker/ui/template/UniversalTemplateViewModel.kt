package app.telecom.sitetracker.ui.template

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.telecom.sitetracker.data.entity.UniversalFieldEntity
import app.telecom.sitetracker.data.repo.SiteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UniversalTemplateUiState(
    val isLoading: Boolean = true,
    val groupedFields: Map<String, List<UniversalFieldEntity>> = emptyMap(),
    val totalFieldCount: Int = 0,
    val userMessage: String? = null,
    val errorMessage: String? = null,
    val isAddingField: Boolean = false,
    val targetSectionForAdd: String? = null,
    val isAddingSection: Boolean = false,
    val isEditingField: Boolean = false,
    val fieldToEdit: UniversalFieldEntity? = null,
    val confirmDeleteField: UniversalFieldEntity? = null,
    val confirmDeleteSection: String? = null,
    val confirmResetDefaults: Boolean = false,
    val confirmClearAll: Boolean = false
)

class UniversalTemplateViewModel(
    private val siteRepository: SiteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UniversalTemplateUiState())
    val uiState: StateFlow<UniversalTemplateUiState> = _uiState.asStateFlow()

    init {
        observeTemplate()
    }

    private fun observeTemplate() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            siteRepository.observeUniversalFields()
                .catch { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Failed to load universal template: ${e.localizedMessage}"
                        )
                    }
                }
                .collect { fields ->
                    val grouped = fields.groupBy { it.groupName.uppercase() }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            groupedFields = grouped,
                            totalFieldCount = fields.size
                        )
                    }
                }
        }
    }

    fun startAddField(sectionName: String) {
        _uiState.update {
            it.copy(
                isAddingField = true,
                targetSectionForAdd = sectionName.trim().uppercase()
            )
        }
    }

    fun dismissAddField() {
        _uiState.update {
            it.copy(
                isAddingField = false,
                targetSectionForAdd = null
            )
        }
    }

    fun confirmAddField(key: String, defaultValue: String?) {
        val trimmedKey = key.trim().uppercase()
        val section = _uiState.value.targetSectionForAdd ?: return
        if (trimmedKey.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Specification field key cannot be blank.") }
            return
        }

        viewModelScope.launch {
            try {
                val currentFields = _uiState.value.groupedFields.values.flatten()
                val nextSort = (currentFields.maxOfOrNull { it.sortOrder } ?: 0) + 1
                val entity = UniversalFieldEntity(
                    fieldKey = trimmedKey,
                    groupName = section,
                    defaultValue = defaultValue?.trim()?.ifBlank { null },
                    sortOrder = nextSort
                )
                siteRepository.saveUniversalField(entity)
                _uiState.update {
                    it.copy(
                        isAddingField = false,
                        targetSectionForAdd = null,
                        userMessage = "Universal specification '$trimmedKey' saved."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Failed to add specification: ${e.localizedMessage}")
                }
            }
        }
    }

    fun startAddSection() {
        _uiState.update { it.copy(isAddingSection = true) }
    }

    fun dismissAddSection() {
        _uiState.update { it.copy(isAddingSection = false) }
    }

    fun confirmAddSection(sectionName: String, firstFieldKey: String, firstDefaultValue: String?) {
        val trimmedSection = sectionName.trim().uppercase()
        val trimmedKey = firstFieldKey.trim().uppercase()
        if (trimmedSection.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Section name cannot be blank.") }
            return
        }
        if (trimmedKey.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Initial specification field key cannot be blank.") }
            return
        }

        viewModelScope.launch {
            try {
                val currentFields = _uiState.value.groupedFields.values.flatten()
                val nextSort = (currentFields.maxOfOrNull { it.sortOrder } ?: 0) + 1
                val entity = UniversalFieldEntity(
                    fieldKey = trimmedKey,
                    groupName = trimmedSection,
                    defaultValue = firstDefaultValue?.trim()?.ifBlank { null },
                    sortOrder = nextSort
                )
                siteRepository.saveUniversalField(entity)
                _uiState.update {
                    it.copy(
                        isAddingSection = false,
                        userMessage = "Section '$trimmedSection' created with field '$trimmedKey'."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Failed to add section: ${e.localizedMessage}")
                }
            }
        }
    }

    fun requestDeleteField(field: UniversalFieldEntity) {
        _uiState.update { it.copy(confirmDeleteField = field) }
    }

    fun cancelDeleteField() {
        _uiState.update { it.copy(confirmDeleteField = null) }
    }

    fun confirmDeleteField() {
        val target = _uiState.value.confirmDeleteField ?: return
        viewModelScope.launch {
            try {
                siteRepository.deleteUniversalField(target.fieldKey)
                _uiState.update {
                    it.copy(
                        confirmDeleteField = null,
                        userMessage = "Specification '${target.fieldKey}' removed from universal template."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        confirmDeleteField = null,
                        errorMessage = "Failed to delete field: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun requestDeleteSection(groupName: String) {
        _uiState.update { it.copy(confirmDeleteSection = groupName) }
    }

    fun cancelDeleteSection() {
        _uiState.update { it.copy(confirmDeleteSection = null) }
    }

    fun confirmDeleteSection() {
        val target = _uiState.value.confirmDeleteSection ?: return
        viewModelScope.launch {
            try {
                siteRepository.deleteUniversalSection(target)
                _uiState.update {
                    it.copy(
                        confirmDeleteSection = null,
                        userMessage = "Section '$target' and all its universal fields removed."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        confirmDeleteSection = null,
                        errorMessage = "Failed to delete section: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun requestResetDefaults() {
        _uiState.update { it.copy(confirmResetDefaults = true) }
    }

    fun cancelResetDefaults() {
        _uiState.update { it.copy(confirmResetDefaults = false) }
    }

    fun confirmResetDefaults() {
        viewModelScope.launch {
            try {
                siteRepository.seedDefaultUniversalFields()
                _uiState.update {
                    it.copy(
                        confirmResetDefaults = false,
                        userMessage = "Universal specifications reset to standard telecom templates."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        confirmResetDefaults = false,
                        errorMessage = "Failed to reset defaults: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun startEditField(field: UniversalFieldEntity) {
        _uiState.update {
            it.copy(
                isEditingField = true,
                fieldToEdit = field
            )
        }
    }

    fun dismissEditField() {
        _uiState.update {
            it.copy(
                isEditingField = false,
                fieldToEdit = null
            )
        }
    }

    fun confirmEditField(
        oldKey: String,
        newKey: String,
        newGroup: String,
        newDefaultValue: String?
    ) {
        val trimmedKey = newKey.trim().uppercase()
        val trimmedGroup = newGroup.trim().uppercase()
        if (trimmedKey.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Specification field key cannot be blank.") }
            return
        }
        if (trimmedGroup.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Section name cannot be blank.") }
            return
        }

        viewModelScope.launch {
            try {
                val currentField = _uiState.value.fieldToEdit
                val sort = currentField?.sortOrder ?: 0
                val updatedEntity = UniversalFieldEntity(
                    fieldKey = trimmedKey,
                    groupName = trimmedGroup,
                    defaultValue = newDefaultValue?.trim()?.ifBlank { null },
                    sortOrder = sort
                )
                siteRepository.updateUniversalField(oldKey, updatedEntity)
                _uiState.update {
                    it.copy(
                        isEditingField = false,
                        fieldToEdit = null,
                        userMessage = "Universal specification '$trimmedKey' updated."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Failed to update specification: ${e.localizedMessage}")
                }
            }
        }
    }

    fun requestClearAll() {
        _uiState.update { it.copy(confirmClearAll = true) }
    }

    fun cancelClearAll() {
        _uiState.update { it.copy(confirmClearAll = false) }
    }

    fun confirmClearAll() {
        viewModelScope.launch {
            try {
                siteRepository.clearAllUniversalFields()
                _uiState.update {
                    it.copy(
                        confirmClearAll = false,
                        userMessage = "All universal specifications cleared. Your site records from Excel are untouched."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        confirmClearAll = false,
                        errorMessage = "Failed to clear template: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun dismissUserMessage() {
        _uiState.update { it.copy(userMessage = null, errorMessage = null) }
    }
}
