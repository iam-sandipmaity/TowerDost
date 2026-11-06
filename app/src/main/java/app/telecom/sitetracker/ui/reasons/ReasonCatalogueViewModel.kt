package app.telecom.sitetracker.ui.reasons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.telecom.sitetracker.data.entity.ReasonEntity
import app.telecom.sitetracker.data.repo.ReasonRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ReasonUiState(
    val reasons: List<ReasonEntity> = emptyList(),
    val searchQuery: String = "",
    val editingReason: ReasonEntity? = null,
    val isAddDialogOpen: Boolean = false,
    val userMessage: String? = null
)

class ReasonCatalogueViewModel(
    private val reasonRepository: ReasonRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _editingReason = MutableStateFlow<ReasonEntity?>(null)
    private val _isAddDialogOpen = MutableStateFlow(false)
    private val _userMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ReasonUiState> = combine(
        reasonRepository.getAllReasons(),
        _searchQuery,
        _editingReason,
        _isAddDialogOpen,
        _userMessage
    ) { allReasons, query, editing, isAddOpen, message ->
        val filtered = if (query.isBlank()) {
            allReasons
        } else {
            val q = query.trim().lowercase()
            allReasons.filter {
                it.label.lowercase().contains(q) || (it.category?.lowercase()?.contains(q) == true)
            }
        }
        ReasonUiState(
            reasons = filtered,
            searchQuery = query,
            editingReason = editing,
            isAddDialogOpen = isAddOpen,
            userMessage = message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = ReasonUiState()
    )

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun openAddDialog() {
        _isAddDialogOpen.value = true
    }

    fun closeAddDialog() {
        _isAddDialogOpen.value = false
    }

    fun openEditDialog(reason: ReasonEntity) {
        _editingReason.value = reason
    }

    fun closeEditDialog() {
        _editingReason.value = null
    }

    fun saveReason(label: String, category: String, countsTowardDowntime: Boolean) {
        if (label.isBlank()) return

        viewModelScope.launch {
            val id = "REAS_USER_" + UUID.randomUUID().toString().take(8).uppercase()
            val entity = ReasonEntity(
                id = id,
                label = label.trim(),
                groupName = category.trim().ifBlank { "Other" },
                category = category.trim().ifBlank { "Other" },
                countsTowardDowntime = if (countsTowardDowntime) 1 else 0,
                sortOrder = 100,
                isHidden = 0,
                isSeeded = 0,
                updatedAt = System.currentTimeMillis()
            )
            reasonRepository.saveReason(entity)
            _isAddDialogOpen.value = false
            _userMessage.value = "Added reason: ${entity.label}"
        }
    }

    fun updateReason(reason: ReasonEntity, newLabel: String, newCategory: String, countsTowardDowntime: Boolean) {
        if (newLabel.isBlank()) return

        viewModelScope.launch {
            val updated = reason.copy(
                label = newLabel.trim(),
                groupName = newCategory.trim().ifBlank { "Other" },
                category = newCategory.trim().ifBlank { "Other" },
                countsTowardDowntime = if (countsTowardDowntime) 1 else 0,
                updatedAt = System.currentTimeMillis()
            )
            reasonRepository.saveReason(updated)
            _editingReason.value = null
            _userMessage.value = "Updated reason: ${updated.label}"
        }
    }

    fun toggleReasonHidden(reason: ReasonEntity) {
        viewModelScope.launch {
            val willHide = reason.isHidden == 0
            reasonRepository.setReasonHidden(reason.id, willHide, System.currentTimeMillis())
            _userMessage.value = if (willHide) "Reason hidden from picker" else "Reason unhidden"
        }
    }

    fun dismissMessage() {
        _userMessage.value = null
    }

    class Factory(private val reasonRepository: ReasonRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ReasonCatalogueViewModel(reasonRepository) as T
        }
    }
}
