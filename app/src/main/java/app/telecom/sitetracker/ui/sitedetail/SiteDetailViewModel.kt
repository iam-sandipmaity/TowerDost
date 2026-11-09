package app.telecom.sitetracker.ui.sitedetail

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.telecom.sitetracker.data.backup.BackupManager
import app.telecom.sitetracker.data.entity.IncidentEntity
import app.telecom.sitetracker.data.entity.SiteEntity
import app.telecom.sitetracker.data.entity.SiteFieldEntity
import app.telecom.sitetracker.data.entity.SiteTabEntity
import app.telecom.sitetracker.data.export.Exporter
import app.telecom.sitetracker.data.repo.IncidentRepository
import app.telecom.sitetracker.data.repo.OutageRepository
import app.telecom.sitetracker.data.repo.SiteRepository
import app.telecom.sitetracker.util.Dates
import app.telecom.sitetracker.util.Geo
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SpecDraftItem(
    val id: Long = 0L,
    val tempId: String = java.util.UUID.randomUUID().toString(),
    val fieldKey: String = "",
    val fieldValue: String = ""
)

data class SpecDraftSection(
    val sectionName: String,
    val items: List<SpecDraftItem> = emptyList()
)

data class SiteDetailUiState(
    val isLoading: Boolean = true,
    val site: SiteEntity? = null,
    val tabs: List<SiteTabEntity> = emptyList(),
    val selectedTabId: String = "SPECS",
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
    // Spec edit & safeguards (inline editor + legacy single-dialog fallback)
    val isEditingSpecs: Boolean = false,
    val draftSpecsSections: List<SpecDraftSection> = emptyList(),
    val confirmSaveSpecs: Boolean = false,
    val isAddingSectionDialog: Boolean = false,
    val specToEdit: SiteFieldEntity? = null,
    val isAddingSpec: Boolean = false,
    val confirmDeleteSpec: SiteFieldEntity? = null,
    // Dynamic tab creation & safe pre-deletion backup gate
    val isCreatingTabDialog: Boolean = false,
    val tabToDelete: SiteTabEntity? = null,
    val tabDeleteSiteCount: Int = 0,
    val backupVerified: Boolean = false,
    val backupFilePath: String? = null
)

class SiteDetailViewModel(
    private val sapId: String,
    private val siteRepository: SiteRepository,
    private val incidentRepository: IncidentRepository,
    private val outageRepository: OutageRepository,
    private val backupManager: BackupManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(SiteDetailUiState())
    val uiState: StateFlow<SiteDetailUiState> = _uiState.asStateFlow()

    init {
        loadSiteDetails()
        observeIncidents()
        observeUniversalTemplate()
        observeTabs()
    }

    private fun observeTabs() {
        viewModelScope.launch {
            siteRepository.observeAllTabs().collect { tabList ->
                _uiState.update { it.copy(tabs = if (tabList.isNotEmpty()) tabList else SiteTabEntity.DEFAULT_TABS) }
            }
        }
    }

    private fun observeUniversalTemplate() {
        viewModelScope.launch {
            siteRepository.observeUniversalFields().collect {
                loadSiteDetails()
            }
        }
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

    fun refreshSiteDetails() {
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
                isEditingOverview = if (!newMode) false else current.isEditingOverview,
                isEditingSpecs = if (!newMode) false else current.isEditingSpecs
            )
        }
    }

    fun setEditMode(enabled: Boolean) {
        _uiState.update { current ->
            current.copy(
                isEditMode = enabled,
                isEditingOverview = if (!enabled) false else current.isEditingOverview,
                isEditingSpecs = if (!enabled) false else current.isEditingSpecs
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

    // --- Specifications Inline Card Editing & Safeguards ---

    fun startEditingSpecs() {
        val grouped = _uiState.value.groupedSpecs
        val sections = if (grouped.isNotEmpty()) {
            grouped.map { (groupName, fields) ->
                SpecDraftSection(
                    sectionName = groupName,
                    items = fields.map { SpecDraftItem(id = it.id, fieldKey = it.fieldKey, fieldValue = it.fieldValue ?: "") }
                )
            }
        } else {
            listOf(
                SpecDraftSection(
                    sectionName = "GENERAL SPECIFICATIONS",
                    items = listOf(SpecDraftItem())
                )
            )
        }
        _uiState.update {
            it.copy(
                isEditMode = true,
                isEditingSpecs = true,
                draftSpecsSections = sections,
                confirmSaveSpecs = false
            )
        }
    }

    fun updateDraftSpecItem(sectionIndex: Int, tempId: String, key: String, value: String) {
        _uiState.update { state ->
            val sections = state.draftSpecsSections.toMutableList()
            if (sectionIndex in sections.indices) {
                val sec = sections[sectionIndex]
                val items = sec.items.map {
                    if (it.tempId == tempId) it.copy(fieldKey = key, fieldValue = value) else it
                }
                sections[sectionIndex] = sec.copy(items = items)
            }
            state.copy(draftSpecsSections = sections)
        }
    }

    fun updateDraftSpecItem(sectionIndex: Int, itemIndex: Int, key: String, value: String) {
        _uiState.update { state ->
            val sections = state.draftSpecsSections.toMutableList()
            if (sectionIndex in sections.indices) {
                val sec = sections[sectionIndex]
                val items = sec.items.toMutableList()
                if (itemIndex in items.indices) {
                    items[itemIndex] = items[itemIndex].copy(fieldKey = key, fieldValue = value)
                    sections[sectionIndex] = sec.copy(items = items)
                }
            }
            state.copy(draftSpecsSections = sections)
        }
    }

    fun addDraftSpecItem(sectionIndex: Int) {
        _uiState.update { state ->
            val sections = state.draftSpecsSections.toMutableList()
            if (sectionIndex in sections.indices) {
                val sec = sections[sectionIndex]
                val items = sec.items.toMutableList()
                items.add(SpecDraftItem())
                sections[sectionIndex] = sec.copy(items = items)
            }
            state.copy(draftSpecsSections = sections)
        }
    }

    fun removeDraftSpecItem(sectionIndex: Int, tempId: String) {
        _uiState.update { state ->
            val sections = state.draftSpecsSections.toMutableList()
            if (sectionIndex in sections.indices) {
                val sec = sections[sectionIndex]
                val items = sec.items.filter { it.tempId != tempId }
                sections[sectionIndex] = sec.copy(items = items)
            }
            state.copy(draftSpecsSections = sections)
        }
    }

    fun removeDraftSpecItem(sectionIndex: Int, itemIndex: Int) {
        _uiState.update { state ->
            val sections = state.draftSpecsSections.toMutableList()
            if (sectionIndex in sections.indices) {
                val sec = sections[sectionIndex]
                val items = sec.items.toMutableList()
                if (itemIndex in items.indices) {
                    items.removeAt(itemIndex)
                    sections[sectionIndex] = sec.copy(items = items)
                }
            }
            state.copy(draftSpecsSections = sections)
        }
    }

    fun showAddSectionDialog() {
        _uiState.update { it.copy(isAddingSectionDialog = true) }
    }

    fun dismissAddSectionDialog() {
        _uiState.update { it.copy(isAddingSectionDialog = false) }
    }

    fun addDraftSection(name: String) {
        val cleanName = name.trim().ifBlank { "NEW SECTION" }.uppercase()
        _uiState.update { state ->
            val sections = state.draftSpecsSections.toMutableList()
            val existingIdx = sections.indexOfFirst { it.sectionName.equals(cleanName, ignoreCase = true) }
            if (existingIdx != -1) {
                val sec = sections[existingIdx]
                val items = sec.items.toMutableList()
                items.add(SpecDraftItem())
                sections[existingIdx] = sec.copy(items = items)
            } else {
                sections.add(
                    SpecDraftSection(
                        sectionName = cleanName,
                        items = listOf(SpecDraftItem())
                    )
                )
            }
            state.copy(draftSpecsSections = sections, isAddingSectionDialog = false)
        }
    }

    fun renameDraftSection(sectionIndex: Int, newName: String) {
        val cleanName = newName.trim().ifBlank { "SECTION" }.uppercase()
        _uiState.update { state ->
            val sections = state.draftSpecsSections.toMutableList()
            if (sectionIndex in sections.indices) {
                sections[sectionIndex] = sections[sectionIndex].copy(sectionName = cleanName)
            }
            state.copy(draftSpecsSections = sections)
        }
    }

    fun removeDraftSection(sectionIndex: Int) {
        _uiState.update { state ->
            val sections = state.draftSpecsSections.toMutableList()
            if (sectionIndex in sections.indices) {
                sections.removeAt(sectionIndex)
            }
            state.copy(draftSpecsSections = sections)
        }
    }

    fun cancelEditingSpecs() {
        _uiState.update {
            it.copy(
                isEditingSpecs = false,
                draftSpecsSections = emptyList(),
                confirmSaveSpecs = false,
                isAddingSectionDialog = false
            )
        }
    }

    fun requestSaveSpecs() {
        val sections = _uiState.value.draftSpecsSections
        for (sec in sections) {
            for (item in sec.items) {
                if (item.fieldKey.isBlank() && item.fieldValue.isNotBlank()) {
                    _uiState.update { it.copy(errorMessage = "Specification in '${sec.sectionName}' has a value without a field name.") }
                    return
                }
            }
        }
        _uiState.update { it.copy(confirmSaveSpecs = true) }
    }

    fun dismissSaveSpecsConfirmation() {
        _uiState.update { it.copy(confirmSaveSpecs = false) }
    }

    fun confirmAndSaveSpecs() {
        val site = _uiState.value.site ?: return
        val sections = _uiState.value.draftSpecsSections
        val newEntities = mutableListOf<SiteFieldEntity>()
        var sortIndex = 1
        val currentTabId = if (_uiState.value.selectedTabId in listOf("LOGS", "OVERVIEW")) "SPECS" else _uiState.value.selectedTabId

        for (sec in sections) {
            val secName = sec.sectionName.trim().ifBlank { "GENERAL SPECIFICATIONS" }.uppercase()
            for (item in sec.items) {
                val key = item.fieldKey.trim()
                val value = item.fieldValue.trim()
                if (key.isNotBlank()) {
                    newEntities.add(
                        SiteFieldEntity(
                            sapId = site.sapId,
                            fieldKey = key,
                            fieldValue = value.ifBlank { null },
                            groupName = secName,
                            tabId = currentTabId,
                            sortOrder = sortIndex++
                        )
                    )
                }
            }
        }

        viewModelScope.launch {
            try {
                siteRepository.replaceSiteSpecsForTab(site.sapId, currentTabId, newEntities)
                _uiState.update {
                    it.copy(
                        isEditingSpecs = false,
                        draftSpecsSections = emptyList(),
                        confirmSaveSpecs = false,
                        userMessage = "Specifications updated successfully."
                    )
                }
                loadSiteDetails()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        confirmSaveSpecs = false,
                        errorMessage = "Failed to save specifications: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    // --- Legacy Dialog-based Specification Actions (Fallback) ---

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
                val loadedTabs = siteRepository.getAllTabs()
                val activeTabs = if (loadedTabs.isNotEmpty()) loadedTabs else SiteTabEntity.DEFAULT_TABS

                val currentTabId = if (_uiState.value.selectedTabId in listOf("LOGS", "OVERVIEW")) "SPECS" else _uiState.value.selectedTabId
                val fields = siteRepository.getFieldsForSiteAndTab(actualSapId, currentTabId) ?: emptyList()
                val allSiteFields = siteRepository.getFieldsForSite(actualSapId) ?: emptyList()
                val universalFields = (siteRepository.getUniversalFields() ?: emptyList()).filter { it.tabId.equals(currentTabId, ignoreCase = true) }

                // If site already has recorded technical specifications for this tab, display them exclusively.
                // Never inject repetitive blank placeholder fields into existing sites.
                // If site has NO recorded specifications for this tab yet, inherit the global blueprint for this tab.
                val activeFields = if (fields.isNotEmpty()) {
                    fields
                } else if (universalFields.isNotEmpty()) {
                    universalFields.map { uni ->
                        SiteFieldEntity(
                            id = 0L,
                            sapId = actualSapId,
                            fieldKey = uni.fieldKey,
                            fieldValue = uni.defaultValue,
                            groupName = uni.groupName,
                            tabId = currentTabId,
                            sortOrder = uni.sortOrder
                        )
                    }
                } else {
                    emptyList()
                }

                // Detect contact phone number from specs
                val phoneField = allSiteFields.firstOrNull { field ->
                    val k = field.fieldKey.lowercase()
                    (k.contains("number") || k.contains("phone") || k.contains("mobile") || k.contains("tech no")) &&
                            field.fieldValue?.any { it.isDigit() } == true
                }
                val phone = phoneField?.fieldValue?.filter { it.isDigit() || it == '+' }

                // Build global ranking from universal blueprint template
                val groupRank = universalFields.map { it.groupName.trim().uppercase(java.util.Locale.US) }
                    .distinct()
                    .mapIndexed { idx, grp -> grp to idx }
                    .toMap()

                val fieldRank = universalFields.mapIndexed { idx, u ->
                    (u.groupName.trim().uppercase(java.util.Locale.US) to u.fieldKey.trim().uppercase(java.util.Locale.US)) to idx
                }.toMap()

                val keyOnlyRank = universalFields.mapIndexed { idx, u ->
                    u.fieldKey.trim().uppercase(java.util.Locale.US) to idx
                }.toMap()

                // Group specs by groupName preserving template hierarchy
                val rawGrouped = activeFields.groupBy { it.groupName?.trim()?.ifBlank { "General Specifications" } ?: "General Specifications" }

                // Sort section categories according to universal template
                val sortedGroupEntries = rawGrouped.entries.sortedWith(
                    compareBy { (grp, _) -> groupRank[grp.uppercase(java.util.Locale.US)] ?: Int.MAX_VALUE }
                )

                // Sort fields within each category according to universal template
                val grouped = sortedGroupEntries.associate { (grp, fieldsList) ->
                    val sortedFields = fieldsList.sortedWith(
                        compareBy(
                            { field -> fieldRank[grp.uppercase(java.util.Locale.US) to field.fieldKey.trim().uppercase(java.util.Locale.US)] ?: keyOnlyRank[field.fieldKey.trim().uppercase(java.util.Locale.US)] ?: (Int.MAX_VALUE - 1000 + (field.sortOrder ?: 0)) },
                            { it.sortOrder ?: 0 },
                            { it.fieldKey }
                        )
                    )
                    grp to sortedFields
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        site = site,
                        tabs = activeTabs,
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

    // --- Dynamic Universal Tab Actions ---

    fun selectTab(tabId: String) {
        val normalized = tabId.trim().uppercase(Locale.US)
        _uiState.update {
            it.copy(
                selectedTabId = normalized,
                isEditingSpecs = false,
                isEditingOverview = false,
                draftSpecsSections = emptyList(),
                draftOverview = null
            )
        }
        loadSiteDetails()
    }

    fun showCreateTabDialog() {
        _uiState.update { it.copy(isCreatingTabDialog = true) }
    }

    fun dismissCreateTabDialog() {
        _uiState.update { it.copy(isCreatingTabDialog = false) }
    }

    fun createUniversalTab(displayName: String) {
        val trimmed = displayName.trim()
        if (trimmed.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Tab name cannot be blank.") }
            return
        }
        val tabId = trimmed.replace("\\s+".toRegex(), "_").uppercase(Locale.US)
        viewModelScope.launch {
            try {
                siteRepository.insertTab(tabId, trimmed)
                _uiState.update {
                    it.copy(
                        selectedTabId = tabId,
                        isCreatingTabDialog = false,
                        userMessage = "Tab '$trimmed' created successfully."
                    )
                }
                loadSiteDetails()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = e.localizedMessage ?: "Failed to create tab")
                }
            }
        }
    }

    fun requestDeleteTab(tabId: String) {
        val normalized = tabId.trim().uppercase(Locale.US)
        if (SiteTabEntity.DEFAULT_TABS.any { it.tabId == normalized }) {
            _uiState.update { it.copy(errorMessage = "Cannot delete built-in system tab: $normalized") }
            return
        }
        viewModelScope.launch {
            try {
                val tab = siteRepository.getTabById(normalized)
                if (tab == null) {
                    _uiState.update { it.copy(errorMessage = "Tab '$normalized' not found") }
                    return@launch
                }
                val siteCount = siteRepository.countDistinctSitesForTab(normalized)
                _uiState.update {
                    it.copy(
                        tabToDelete = tab,
                        tabDeleteSiteCount = siteCount,
                        backupVerified = false,
                        backupFilePath = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to prepare tab deletion: ${e.localizedMessage}") }
            }
        }
    }

    fun dismissDeleteTabDialog() {
        _uiState.update {
            it.copy(
                tabToDelete = null,
                tabDeleteSiteCount = 0,
                backupVerified = false,
                backupFilePath = null
            )
        }
    }

    fun generateSafetyBackup(context: Context): kotlinx.coroutines.Job = viewModelScope.launch {
        val tab = _uiState.value.tabToDelete ?: return@launch
        try {
            val bm = backupManager
            if (bm == null) {
                _uiState.update { it.copy(errorMessage = "BackupManager is unavailable") }
                return@launch
            }
            val result = bm.generateAndVerifySafetyBackup(context, "tab_${tab.tabId.lowercase()}_delete")
            if (result.isSuccess) {
                val file = result.getOrThrow()
                _uiState.update {
                    it.copy(
                        backupVerified = true,
                        backupFilePath = file.absolutePath,
                        userMessage = "Safety backup verified (${file.length()} bytes)."
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        backupVerified = false,
                        errorMessage = "Backup failed: ${result.exceptionOrNull()?.message}"
                    )
                }
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    backupVerified = false,
                    errorMessage = "Backup failed: ${e.localizedMessage}"
                )
            }
        }
    }

    fun confirmDeleteTab(context: Context): kotlinx.coroutines.Job? {
        val tab = _uiState.value.tabToDelete ?: return null
        if (!_uiState.value.backupVerified) {
            _uiState.update { it.copy(errorMessage = "Cannot delete tab without verified safety backup.") }
            return null
        }
        return viewModelScope.launch {
            try {
                siteRepository.deleteTab(tab.tabId)
                _uiState.update {
                    it.copy(
                        tabToDelete = null,
                        tabDeleteSiteCount = 0,
                        backupVerified = false,
                        backupFilePath = null,
                        selectedTabId = "SPECS",
                        userMessage = "Tab '${tab.displayName}' and its data deleted permanently."
                    )
                }
                loadSiteDetails()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Failed to delete tab: ${e.localizedMessage}")
                }
            }
        }
    }
}
