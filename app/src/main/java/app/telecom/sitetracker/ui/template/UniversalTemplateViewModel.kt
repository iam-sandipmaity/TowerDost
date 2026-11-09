package app.telecom.sitetracker.ui.template

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.telecom.sitetracker.data.entity.UniversalFieldEntity
import app.telecom.sitetracker.data.repo.SiteRepository
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UniversalTemplateUiState(
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val groupedFields: Map<String, List<UniversalFieldEntity>> = emptyMap(),
    val totalFieldCount: Int = 0,
    val siteSpecsAvailableCount: Int = 0,
    val userMessage: String? = null,
    val errorMessage: String? = null,
    val isAddingField: Boolean = false,
    val targetSectionForAdd: String? = null,
    val isAddingSection: Boolean = false,
    val isEditingField: Boolean = false,
    val fieldToEdit: UniversalFieldEntity? = null,
    val confirmDeleteField: UniversalFieldEntity? = null,
    val confirmDeleteSection: String? = null,
    val confirmClearAll: Boolean = false,
    val confirmSyncFromSites: Boolean = false,
    val isReorderMode: Boolean = false,
    val fieldToMove: UniversalFieldEntity? = null
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

            try {
                // If template is empty, populate directly from real imported site specifications
                val existing = siteRepository.getAllUniversalFields()
                if (existing.isEmpty()) {
                    val siteSpecs = siteRepository.getDistinctSpecsFromSites()
                    if (siteSpecs.isNotEmpty()) {
                        siteRepository.replaceAllUniversalFields(siteSpecs)
                    }
                }
                val siteSpecsCount = siteRepository.countDistinctSiteSpecs()
                _uiState.update { it.copy(siteSpecsAvailableCount = siteSpecsCount) }
            } catch (e: Exception) {
                // Ignore initialization sync error and continue observing
            }

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
                    val grouped = fields.groupBy { it.groupName.uppercase(Locale.US) }
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

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
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

    fun requestSyncFromSites() {
        _uiState.update { it.copy(confirmSyncFromSites = true) }
    }

    fun cancelSyncFromSites() {
        _uiState.update { it.copy(confirmSyncFromSites = false) }
    }

    fun confirmSyncFromSites() {
        viewModelScope.launch {
            try {
                val syncedCount = siteRepository.syncUniversalTemplateFromSites()
                _uiState.update {
                    it.copy(
                        confirmSyncFromSites = false,
                        userMessage = if (syncedCount > 0) {
                            "Synchronized $syncedCount specifications from imported site records."
                        } else {
                            "No imported site specifications found in database."
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        confirmSyncFromSites = false,
                        errorMessage = "Failed to sync from sites: ${e.localizedMessage}"
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

    fun toggleReorderMode() {
        _uiState.update { it.copy(isReorderMode = !it.isReorderMode) }
    }

    fun startMoveField(field: UniversalFieldEntity) {
        _uiState.update { it.copy(fieldToMove = field) }
    }

    fun dismissMoveField() {
        _uiState.update { it.copy(fieldToMove = null) }
    }

    fun moveFieldUp(field: UniversalFieldEntity) {
        val groups = _uiState.value.groupedFields.mapValues { it.value.toMutableList() }.toMutableMap()
        val grpKey = field.groupName.uppercase(Locale.US)
        val list = groups[grpKey] ?: return
        val secIndex = list.indexOfFirst { it.fieldKey == field.fieldKey }

        if (secIndex > 0) {
            // Swap within section
            val prev = list[secIndex - 1]
            list[secIndex - 1] = field
            list[secIndex] = prev
            persistGroups(groups)
        } else {
            // Move into previous section at the bottom
            val allGroupKeys = groups.keys.toList()
            val grpIndex = allGroupKeys.indexOf(grpKey)
            if (grpIndex > 0) {
                val prevGrpKey = allGroupKeys[grpIndex - 1]
                list.removeAt(secIndex)
                val moved = field.copy(groupName = prevGrpKey)
                groups[prevGrpKey]?.add(moved)
                persistGroups(groups)
            }
        }
    }

    fun moveFieldDown(field: UniversalFieldEntity) {
        val groups = _uiState.value.groupedFields.mapValues { it.value.toMutableList() }.toMutableMap()
        val grpKey = field.groupName.uppercase(Locale.US)
        val list = groups[grpKey] ?: return
        val secIndex = list.indexOfFirst { it.fieldKey == field.fieldKey }

        if (secIndex >= 0 && secIndex < list.size - 1) {
            // Swap within section
            val next = list[secIndex + 1]
            list[secIndex + 1] = field
            list[secIndex] = next
            persistGroups(groups)
        } else if (secIndex == list.size - 1) {
            // Move into next section at the top
            val allGroupKeys = groups.keys.toList()
            val grpIndex = allGroupKeys.indexOf(grpKey)
            if (grpIndex < allGroupKeys.size - 1) {
                val nextGrpKey = allGroupKeys[grpIndex + 1]
                list.removeAt(secIndex)
                val moved = field.copy(groupName = nextGrpKey)
                groups[nextGrpKey]?.add(0, moved)
                persistGroups(groups)
            }
        }
    }

    fun moveFieldToTop(field: UniversalFieldEntity) {
        val groups = _uiState.value.groupedFields.mapValues { it.value.toMutableList() }.toMutableMap()
        val grpKey = field.groupName.uppercase(Locale.US)
        val list = groups[grpKey] ?: return
        val secIndex = list.indexOfFirst { it.fieldKey == field.fieldKey }
        if (secIndex > 0) {
            list.removeAt(secIndex)
            list.add(0, field)
            persistGroups(groups)
        }
    }

    fun moveFieldToBottom(field: UniversalFieldEntity) {
        val groups = _uiState.value.groupedFields.mapValues { it.value.toMutableList() }.toMutableMap()
        val grpKey = field.groupName.uppercase(Locale.US)
        val list = groups[grpKey] ?: return
        val secIndex = list.indexOfFirst { it.fieldKey == field.fieldKey }
        if (secIndex >= 0 && secIndex < list.size - 1) {
            list.removeAt(secIndex)
            list.add(field)
            persistGroups(groups)
        }
    }

    fun moveFieldToSection(field: UniversalFieldEntity, targetSection: String) {
        val trimmedTarget = targetSection.trim().uppercase(Locale.US)
        if (trimmedTarget.isBlank() || trimmedTarget == field.groupName.uppercase(Locale.US)) return
        val groups = _uiState.value.groupedFields.mapValues { it.value.toMutableList() }.toMutableMap()
        val srcKey = field.groupName.uppercase(Locale.US)
        groups[srcKey]?.removeAll { it.fieldKey == field.fieldKey }
        val moved = field.copy(groupName = trimmedTarget)
        groups.getOrPut(trimmedTarget) { mutableListOf() }.add(moved)
        persistGroups(groups)
    }

    fun moveSectionUp(groupName: String) {
        val grpKey = groupName.trim().uppercase(Locale.US)
        val groupKeys = _uiState.value.groupedFields.keys.toMutableList()
        val index = groupKeys.indexOf(grpKey)
        if (index > 0) {
            val prevKey = groupKeys[index - 1]
            groupKeys[index - 1] = grpKey
            groupKeys[index] = prevKey
            val newGroups = LinkedHashMap<String, List<UniversalFieldEntity>>()
            for (key in groupKeys) {
                _uiState.value.groupedFields[key]?.let { newGroups[key] = it }
            }
            persistGroups(newGroups)
        }
    }

    fun moveSectionDown(groupName: String) {
        val grpKey = groupName.trim().uppercase(Locale.US)
        val groupKeys = _uiState.value.groupedFields.keys.toMutableList()
        val index = groupKeys.indexOf(grpKey)
        if (index >= 0 && index < groupKeys.size - 1) {
            val nextKey = groupKeys[index + 1]
            groupKeys[index + 1] = grpKey
            groupKeys[index] = nextKey
            val newGroups = LinkedHashMap<String, List<UniversalFieldEntity>>()
            for (key in groupKeys) {
                _uiState.value.groupedFields[key]?.let { newGroups[key] = it }
            }
            persistGroups(newGroups)
        }
    }

    private fun persistGroups(groups: Map<String, List<UniversalFieldEntity>>) {
        val filtered = groups.filterValues { it.isNotEmpty() }
        val flattened = filtered.values.flatten()
        val normalized = flattened.mapIndexed { idx, item ->
            item.copy(sortOrder = idx + 1)
        }
        viewModelScope.launch {
            try {
                siteRepository.saveUniversalFields(normalized)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to update spec order: ${e.localizedMessage}") }
            }
        }
    }

    fun dismissUserMessage() {
        _uiState.update { it.copy(userMessage = null, errorMessage = null) }
    }
}
