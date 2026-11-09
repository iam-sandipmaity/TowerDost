package app.telecom.sitetracker.ui.merger

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.telecom.sitetracker.data.db.AppDatabase
import app.telecom.sitetracker.data.entity.SiteTabEntity
import app.telecom.sitetracker.data.import.CsvReader
import app.telecom.sitetracker.data.import.DetectedFormat
import app.telecom.sitetracker.data.import.FileSniffer
import app.telecom.sitetracker.data.import.TargetField
import app.telecom.sitetracker.data.import.XlsxReader
import app.telecom.sitetracker.data.merger.MergeStrategy
import app.telecom.sitetracker.data.merger.OverwritePolicy
import app.telecom.sitetracker.data.merger.VlookupColumnMapping
import app.telecom.sitetracker.data.merger.VlookupEngine
import app.telecom.sitetracker.data.merger.VlookupPreview
import app.telecom.sitetracker.data.merger.VlookupResult
import app.telecom.sitetracker.util.AppClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class VlookupStep(val stepNumber: Int, val title: String) {
    SELECT_FILE(1, "Select Sheet"),
    SELECT_KEY(2, "Match Key"),
    MAP_COLUMNS(3, "Map Columns"),
    PREVIEW(4, "Preview Changes"),
    EXECUTING(5, "Merging"),
    COMPLETE(6, "Merge Complete")
}

data class VlookupMergerUiState(
    val step: VlookupStep = VlookupStep.SELECT_FILE,
    val selectedFileName: String? = null,
    val selectedFileSize: Long = 0L,
    val detectedFormat: DetectedFormat = DetectedFormat.UNKNOWN,
    val sheets: List<XlsxReader.SheetInfo> = emptyList(),
    val selectedSheet: XlsxReader.SheetInfo? = null,
    val headers: List<String> = emptyList(),
    val sampleRows: List<List<String>> = emptyList(),
    val keyColumnIndex: Int = -1,
    val columnMappings: List<VlookupColumnMapping> = emptyList(),
    val mergeStrategy: MergeStrategy = MergeStrategy.UPDATE_EXISTING_ONLY,
    val overwritePolicy: OverwritePolicy = OverwritePolicy.OVERWRITE_ALL,
    val previewData: VlookupPreview? = null,
    val availableTabs: List<SiteTabEntity> = emptyList(),
    val processedRows: Int = 0,
    val totalRowsToProcess: Int = 0,
    val result: VlookupResult? = null,
    val isProcessing: Boolean = false,
    val errorMessage: String? = null
)

class VlookupMergerViewModel(
    application: Application,
    private val db: AppDatabase,
    private val clock: AppClock
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(VlookupMergerUiState())
    val uiState: StateFlow<VlookupMergerUiState> = _uiState.asStateFlow()

    private var stagedFile: File? = null
    private var allRows: List<List<String>> = emptyList()
    private val mergerEngine = VlookupEngine(db, clock)
    private var populatedCoreFields: Set<TargetField> = emptySet()
    private var existingSpecKeys: Set<String> = emptySet()

    init {
        loadAvailableTabs()
    }

    private fun loadAvailableTabs() {
        viewModelScope.launch {
            try {
                val tabs = db.siteTabDao().getAllTabs()
                val effectiveTabs = if (tabs.isNotEmpty()) tabs else SiteTabEntity.DEFAULT_TABS
                _uiState.update { it.copy(availableTabs = effectiveTabs) }
            } catch (_: Exception) {
                _uiState.update { it.copy(availableTabs = SiteTabEntity.DEFAULT_TABS) }
            }
        }
    }

    companion object {
        fun checkIsExistingInDb(
            target: TargetField,
            specKey: String?,
            populatedCore: Set<TargetField>,
            existingKeys: Set<String>
        ): Boolean {
            return when (target) {
                TargetField.CUSTOM_SPEC -> {
                    val k = specKey?.trim()?.uppercase(java.util.Locale.US) ?: ""
                    existingKeys.contains(k)
                }
                TargetField.SAP_ID, TargetField.IGNORE -> false
                else -> populatedCore.contains(target)
            }
        }
    }

    private fun checkIsExistingInDb(target: TargetField, specKey: String?): Boolean {
        return checkIsExistingInDb(target, specKey, populatedCoreFields, existingSpecKeys)
    }

    private suspend fun refreshPopulatedDatabaseAttributes() {
        val coreSummary = db.siteDao().getPopulatedCoreFields() ?: app.telecom.sitetracker.data.db.PopulatedCoreFields()
        val populatedCore = mutableSetOf<TargetField>()
        if (coreSummary.hasName > 0) populatedCore.add(TargetField.NAME)
        if (coreSummary.hasCoordinates > 0) {
            populatedCore.add(TargetField.LAT)
            populatedCore.add(TargetField.LNG)
        }
        if (coreSummary.hasCircle > 0) populatedCore.add(TargetField.CIRCLE)
        if (coreSummary.hasRegion > 0) populatedCore.add(TargetField.REGION)
        if (coreSummary.hasState > 0) populatedCore.add(TargetField.STATE)
        if (coreSummary.hasDistrict > 0) populatedCore.add(TargetField.DISTRICT)
        if (coreSummary.hasCity > 0) populatedCore.add(TargetField.CITY)
        if (coreSummary.hasTechnology > 0) populatedCore.add(TargetField.TECHNOLOGY)
        if (coreSummary.hasSiteType > 0) populatedCore.add(TargetField.SITE_TYPE)
        if (coreSummary.hasStatus > 0) populatedCore.add(TargetField.STATUS)
        if (coreSummary.hasAddress > 0) populatedCore.add(TargetField.ADDRESS)
        if (coreSummary.hasTags > 0) populatedCore.add(TargetField.TAGS)
        if (coreSummary.hasNotes > 0) populatedCore.add(TargetField.NOTES)
        populatedCoreFields = populatedCore

        val distinctSpecs = db.fieldDao().getDistinctSpecsFromSites()
        existingSpecKeys = distinctSpecs.map { it.fieldKey.trim().uppercase(java.util.Locale.US) }.toSet()

        try {
            val tabs = db.siteTabDao().getAllTabs()
            val effectiveTabs = if (tabs.isNotEmpty()) tabs else SiteTabEntity.DEFAULT_TABS
            _uiState.update { it.copy(availableTabs = effectiveTabs) }
        } catch (_: Exception) {}
    }

    fun onFileSelected(uri: Uri, displayName: String?, fileSize: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
            try {
                val context = getApplication<Application>()
                val cacheDir = context.cacheDir
                val tempFile = File(cacheDir, "vlookup_staged_${System.currentTimeMillis()}.tmp")

                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                stagedFile?.delete()
                stagedFile = tempFile

                val format = withContext(Dispatchers.IO) {
                    tempFile.inputStream().buffered().use { stream ->
                        FileSniffer.sniffFormat(stream)
                    }
                }

                if (format == DetectedFormat.XLS_UNSUPPORTED) {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            detectedFormat = format,
                            errorMessage = "Old Excel format (.xls) is not supported. Please save as .xlsx or .csv."
                        )
                    }
                    return@launch
                }

                _uiState.update {
                    it.copy(
                        selectedFileName = displayName ?: "Supplemental Sheet",
                        selectedFileSize = fileSize,
                        detectedFormat = format
                    )
                }

                when (format) {
                    DetectedFormat.XLSX -> handleXlsx(tempFile)
                    DetectedFormat.CSV -> handleCsv(tempFile)
                    else -> {
                        _uiState.update {
                            it.copy(
                                isProcessing = false,
                                errorMessage = "Unsupported file format. Please provide an Excel (.xlsx) or CSV file."
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = "Error reading file: ${e.localizedMessage ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    private suspend fun handleXlsx(file: File) = withContext(Dispatchers.IO) {
        val reader = XlsxReader(file)
        val sheets = reader.getSheets()

        if (sheets.isEmpty()) {
            _uiState.update {
                it.copy(isProcessing = false, errorMessage = "No visible sheets found in Excel file.")
            }
            return@withContext
        }

        if (sheets.size == 1) {
            loadXlsxSheet(file, sheets.first())
        } else {
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    sheets = sheets,
                    selectedSheet = sheets.first(),
                    step = VlookupStep.SELECT_FILE
                )
            }
        }
    }

    fun onSheetSelected(sheet: XlsxReader.SheetInfo) {
        val file = stagedFile ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, selectedSheet = sheet) }
            loadXlsxSheet(file, sheet)
        }
    }

    private suspend fun loadXlsxSheet(file: File, sheet: XlsxReader.SheetInfo) = withContext(Dispatchers.IO) {
        val reader = XlsxReader(file)
        val sharedStrings = reader.readSharedStrings()
        val headerData = reader.detectHeaders(sheet.targetPath, sharedStrings)

        val headers = headerData.headers
        val sample = reader.readPreviewRows(
            sheetPath = sheet.targetPath,
            sharedStrings = sharedStrings,
            firstDataRow = headerData.firstDataRowIndex,
            maxRows = 5
        )

        val fullRowList = mutableListOf<List<String>>()
        reader.streamSheetRows(
            sheetPath = sheet.targetPath,
            sharedStrings = sharedStrings,
            skipRows = headerData.firstDataRowIndex
        ) { _, cells ->
            val rowList = mutableListOf<String>()
            for (c in 0 until headers.size) {
                rowList.add(cells[c] ?: "")
            }
            fullRowList.add(rowList)
        }
        allRows = fullRowList

        val detectedKey = VlookupEngine.detectKeyColumn(headers)
        val initialMappings = VlookupEngine.autoMapColumns(headers, detectedKey)

        refreshPopulatedDatabaseAttributes()

        for (m in initialMappings) {
            m.isExistingInDatabase = checkIsExistingInDb(m.targetField, m.specKey)
        }

        _uiState.update {
            it.copy(
                isProcessing = false,
                headers = headers,
                sampleRows = sample,
                keyColumnIndex = detectedKey,
                columnMappings = initialMappings,
                step = VlookupStep.SELECT_KEY
            )
        }
    }

    private suspend fun handleCsv(file: File) = withContext(Dispatchers.IO) {
        val reader = CsvReader(file)
        val headerData = reader.readHeaders()
        val headers = headerData.headers
        val sample = reader.readPreviewRows(headerData.delimiter, headerData.charset, maxRows = 5)

        val fullRowList = mutableListOf<List<String>>()
        var isHeader = true
        reader.streamRecords(headerData.delimiter, headerData.charset) { row ->
            if (isHeader) {
                isHeader = false
            } else {
                val rowList = mutableListOf<String>()
                for (c in 0 until headers.size) {
                    rowList.add(row.getOrNull(c) ?: "")
                }
                fullRowList.add(rowList)
            }
        }
        allRows = fullRowList

        val detectedKey = VlookupEngine.detectKeyColumn(headers)
        val initialMappings = VlookupEngine.autoMapColumns(headers, detectedKey)

        refreshPopulatedDatabaseAttributes()

        for (m in initialMappings) {
            m.isExistingInDatabase = checkIsExistingInDb(m.targetField, m.specKey)
        }

        _uiState.update {
            it.copy(
                isProcessing = false,
                headers = headers,
                sampleRows = sample,
                keyColumnIndex = detectedKey,
                columnMappings = initialMappings,
                step = VlookupStep.SELECT_KEY
            )
        }
    }

    fun onKeyColumnSelected(index: Int) {
        val headers = _uiState.value.headers
        val remapped = VlookupEngine.autoMapColumns(headers, index)
        for (m in remapped) {
            m.isExistingInDatabase = checkIsExistingInDb(m.targetField, m.specKey)
        }
        _uiState.update {
            it.copy(
                keyColumnIndex = index,
                columnMappings = remapped
            )
        }
    }

    fun onProceedToMapping() {
        if (_uiState.value.keyColumnIndex < 0) {
            _uiState.update { it.copy(errorMessage = "Please select the key column containing SAP IDs.") }
            return
        }
        _uiState.update { it.copy(step = VlookupStep.MAP_COLUMNS, errorMessage = null) }
    }

    fun updateColumnTarget(
        columnIndex: Int,
        target: TargetField,
        specGroup: String? = null,
        specKey: String? = null,
        tabId: String = "SPECS",
        tabDisplayName: String = "Specs"
    ) {
        _uiState.update { state ->
            val updated = state.columnMappings.map { mapping ->
                if (mapping.columnIndex == columnIndex) {
                    val prev = if (target != TargetField.IGNORE) target else mapping.previousTarget
                    val effectiveKey = specKey ?: mapping.specKey
                    val isExisting = checkIsExistingInDb(target, effectiveKey)
                    mapping.copy(
                        targetField = target,
                        specGroup = specGroup ?: mapping.specGroup,
                        specKey = effectiveKey,
                        previousTarget = prev,
                        isExistingInDatabase = isExisting,
                        tabId = tabId,
                        tabDisplayName = tabDisplayName
                    )
                } else mapping
            }
            state.copy(columnMappings = updated)
        }
    }

    fun createNewTabAndMap(columnIndex: Int, tabName: String, sectionName: String?) {
        val cleanTabName = tabName.trim()
        if (cleanTabName.isBlank()) return

        val derivedTabId = cleanTabName.replace(Regex("[^A-Za-z0-9]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .uppercase(java.util.Locale.US)
            .ifBlank { "CUSTOM_TAB" }

        val group = sectionName?.trim()?.ifBlank { "GENERAL" } ?: "GENERAL"

        val currentTabs = _uiState.value.availableTabs
        val existingTab = currentTabs.find { it.tabId.equals(derivedTabId, ignoreCase = true) }
        val effectiveTabId = existingTab?.tabId ?: derivedTabId
        val effectiveDisplayName = existingTab?.displayName ?: cleanTabName

        val updatedTabs = if (existingTab == null) {
            val nextSort = (currentTabs.maxOfOrNull { it.sortOrder } ?: -1) + 1
            currentTabs + SiteTabEntity(
                tabId = effectiveTabId,
                displayName = effectiveDisplayName,
                sortOrder = nextSort,
                isSystem = false
            )
        } else currentTabs

        _uiState.update { state ->
            val updatedMappings = state.columnMappings.map { mapping ->
                if (mapping.columnIndex == columnIndex) {
                    val key = mapping.specKey.ifBlank { mapping.sourceHeader }
                    mapping.copy(
                        targetField = TargetField.CUSTOM_SPEC,
                        specGroup = group,
                        specKey = key,
                        previousTarget = TargetField.CUSTOM_SPEC,
                        tabId = effectiveTabId,
                        tabDisplayName = effectiveDisplayName,
                        isExistingInDatabase = checkIsExistingInDb(TargetField.CUSTOM_SPEC, key)
                    )
                } else mapping
            }
            state.copy(
                availableTabs = updatedTabs,
                columnMappings = updatedMappings
            )
        }
    }

    fun toggleColumnSelection(columnIndex: Int, isIncluded: Boolean) {
        _uiState.update { state ->
            val updated = state.columnMappings.map { mapping ->
                if (mapping.columnIndex == columnIndex) {
                    if (isIncluded) {
                        val restored = if (mapping.previousTarget != TargetField.IGNORE) mapping.previousTarget else TargetField.CUSTOM_SPEC
                        val isExisting = checkIsExistingInDb(restored, mapping.specKey)
                        mapping.copy(
                            targetField = restored,
                            isExistingInDatabase = isExisting
                        )
                    } else {
                        val prev = if (mapping.targetField != TargetField.IGNORE) mapping.targetField else mapping.previousTarget
                        mapping.copy(targetField = TargetField.IGNORE, previousTarget = prev)
                    }
                } else mapping
            }
            state.copy(columnMappings = updated)
        }
    }

    fun selectAllColumns() {
        _uiState.update { state ->
            val keyCol = state.keyColumnIndex
            val updated = state.columnMappings.map { mapping ->
                if (mapping.columnIndex != keyCol && mapping.targetField == TargetField.IGNORE) {
                    val restored = if (mapping.previousTarget != TargetField.IGNORE) mapping.previousTarget else TargetField.CUSTOM_SPEC
                    val isExisting = checkIsExistingInDb(restored, mapping.specKey)
                    mapping.copy(
                        targetField = restored,
                        isExistingInDatabase = isExisting
                    )
                } else mapping
            }
            state.copy(columnMappings = updated)
        }
    }

    fun deselectAllColumns() {
        _uiState.update { state ->
            val keyCol = state.keyColumnIndex
            val updated = state.columnMappings.map { mapping ->
                if (mapping.columnIndex != keyCol && mapping.targetField != TargetField.IGNORE) {
                    mapping.copy(
                        targetField = TargetField.IGNORE,
                        previousTarget = mapping.targetField
                    )
                } else mapping
            }
            state.copy(columnMappings = updated)
        }
    }

    fun setMergeStrategy(strategy: MergeStrategy) {
        _uiState.update { it.copy(mergeStrategy = strategy) }
    }

    fun setOverwritePolicy(policy: OverwritePolicy) {
        _uiState.update { it.copy(overwritePolicy = policy) }
    }

    fun generatePreview() {
        val state = _uiState.value
        if (state.keyColumnIndex < 0) {
            _uiState.update { it.copy(errorMessage = "SAP ID key column is required.") }
            return
        }

        _uiState.update { it.copy(isProcessing = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val preview = mergerEngine.generatePreview(
                    rows = allRows,
                    keyIndex = state.keyColumnIndex,
                    mappings = state.columnMappings,
                    mergeStrategy = state.mergeStrategy,
                    overwritePolicy = state.overwritePolicy
                )

                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        previewData = preview,
                        step = VlookupStep.PREVIEW
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = "Failed to calculate preview: ${e.localizedMessage ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun executeMerge() {
        val state = _uiState.value
        _uiState.update {
            it.copy(
                isProcessing = true,
                step = VlookupStep.EXECUTING,
                totalRowsToProcess = allRows.size,
                processedRows = 0,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            try {
                val result = mergerEngine.executeMerge(
                    rows = allRows,
                    keyIndex = state.keyColumnIndex,
                    mappings = state.columnMappings,
                    mergeStrategy = state.mergeStrategy,
                    overwritePolicy = state.overwritePolicy,
                    onProgress = { processed, total ->
                        _uiState.update {
                            it.copy(
                                processedRows = processed,
                                totalRowsToProcess = total
                            )
                        }
                    }
                )

                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        result = result,
                        step = VlookupStep.COMPLETE
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        step = VlookupStep.PREVIEW,
                        errorMessage = "Merge failed: ${e.localizedMessage ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun goToStep(step: VlookupStep) {
        _uiState.update { it.copy(step = step, errorMessage = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        stagedFile?.delete()
    }
}
