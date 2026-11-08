package app.telecom.sitetracker.ui.importwizard

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.telecom.sitetracker.data.db.AppDatabase
import app.telecom.sitetracker.data.import.ColumnMapper
import app.telecom.sitetracker.data.import.ColumnMapping
import app.telecom.sitetracker.data.import.CsvReader
import app.telecom.sitetracker.data.import.DetectedFormat
import app.telecom.sitetracker.data.import.FileSniffer
import app.telecom.sitetracker.data.import.ImportProgress
import app.telecom.sitetracker.data.import.ImportResult
import app.telecom.sitetracker.data.import.Importer
import app.telecom.sitetracker.data.import.JsonReader
import app.telecom.sitetracker.data.import.MergePolicy
import app.telecom.sitetracker.data.import.TargetField
import app.telecom.sitetracker.data.import.ValidationSummary
import app.telecom.sitetracker.data.import.XlsxReader
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
import java.util.Locale

enum class ImportStep {
    SELECT_FILE,
    SELECT_SHEET,
    MAP_COLUMNS,
    VALIDATION_PREVIEW,
    IMPORT_PROGRESS
}

data class ImportWizardUiState(
    val currentStep: ImportStep = ImportStep.SELECT_FILE,
    val selectedFileName: String? = null,
    val selectedFileSize: Long = 0,
    val detectedFormat: DetectedFormat = DetectedFormat.UNKNOWN,
    val errorMessage: String? = null,
    val warningMessage: String? = null,
    val isProcessing: Boolean = false,

    // Step 2: Sheets
    val sheets: List<XlsxReader.SheetInfo> = emptyList(),
    val selectedSheet: XlsxReader.SheetInfo? = null,

    // Step 3: Column Mapping & Preview
    val columnMappings: List<ColumnMapping> = emptyList(),
    val previewRows: List<List<String>> = emptyList(),

    // Step 4: Validation
    val validationSummary: ValidationSummary? = null,
    val mergePolicy: MergePolicy = MergePolicy.UPDATE_AND_ADD,

    // Step 5: Progress
    val importProgress: ImportProgress? = null,
    val importResult: ImportResult? = null
)

class ImportWizardViewModel(
    application: Application,
    private val db: AppDatabase,
    private val clock: AppClock
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ImportWizardUiState())
    val uiState: StateFlow<ImportWizardUiState> = _uiState.asStateFlow()

    private var stagedFile: File? = null
    private var cachedAllRows: List<List<String>>? = null
    private val importer = Importer(db, clock)

    fun onFileSelected(uri: Uri, displayName: String?, fileSize: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
            try {
                val context = getApplication<Application>()
                val cacheDir = context.cacheDir
                val tempFile = File(cacheDir, "import_staged_${System.currentTimeMillis()}.tmp")

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
                            errorMessage = "Old Excel format (.xls) is not supported. Please open this file in Excel or Google Sheets and Save As .xlsx or .csv."
                        )
                    }
                    return@launch
                }

                _uiState.update {
                    it.copy(
                        selectedFileName = displayName ?: "Imported File",
                        selectedFileSize = fileSize,
                        detectedFormat = format
                    )
                }

                when (format) {
                    DetectedFormat.XLSX -> handleXlsxFile(tempFile)
                    DetectedFormat.CSV -> handleCsvFile(tempFile)
                    DetectedFormat.JSON -> handleJsonFile(tempFile)
                    else -> {
                        _uiState.update {
                            it.copy(
                                isProcessing = false,
                                errorMessage = "Unsupported file type. Please provide an .xlsx, .csv, or .json file."
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = "Failed to inspect file: ${e.localizedMessage ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    private suspend fun handleXlsxFile(file: File) = withContext(Dispatchers.IO) {
        val reader = XlsxReader(file)
        val sheets = reader.getSheets()

        if (sheets.isEmpty()) {
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    errorMessage = "No visible worksheets found in Excel file."
                )
            }
            return@withContext
        }

        if (sheets.size == 1) {
            loadSheetData(file, sheets.first())
        } else {
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    sheets = sheets,
                    selectedSheet = sheets.first(),
                    currentStep = ImportStep.SELECT_SHEET
                )
            }
        }
    }

    fun onSheetSelected(sheet: XlsxReader.SheetInfo) {
        val file = stagedFile ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, selectedSheet = sheet) }
            loadSheetData(file, sheet)
        }
    }

    private suspend fun loadSheetData(file: File, sheet: XlsxReader.SheetInfo) = withContext(Dispatchers.IO) {
        val reader = XlsxReader(file)
        val sharedStrings = reader.readSharedStrings()
        val headerData = reader.detectHeaders(sheet.targetPath, sharedStrings)

        val mappings = ColumnMapper.generateInitialMappings(
            headerData.headers,
            headerData.parentHeaders
        )

        val preview = reader.readPreviewRows(
            sheetPath = sheet.targetPath,
            sharedStrings = sharedStrings,
            firstDataRow = headerData.firstDataRowIndex,
            maxRows = 5
        )

        // Read all rows for import stage
        val allRows = mutableListOf<List<String>>()
        reader.streamSheetRows(
            sheetPath = sheet.targetPath,
            sharedStrings = sharedStrings,
            skipRows = headerData.firstDataRowIndex
        ) { _, cells ->
            val maxCol = mappings.size
            val rowList = mutableListOf<String>()
            for (c in 0 until maxCol) {
                rowList.add(cells[c] ?: "")
            }
            allRows.add(rowList)
        }
        cachedAllRows = allRows

        _uiState.update {
            it.copy(
                isProcessing = false,
                columnMappings = mappings,
                previewRows = preview,
                currentStep = ImportStep.MAP_COLUMNS
            )
        }
    }

    private suspend fun handleCsvFile(file: File) = withContext(Dispatchers.IO) {
        val reader = CsvReader(file)
        val headerData = reader.readHeaders()

        val mappings = ColumnMapper.generateInitialMappings(headerData.headers)
        val preview = reader.readPreviewRows(headerData.delimiter, headerData.charset, maxRows = 5)

        val allRows = mutableListOf<List<String>>()
        var isHeader = true
        reader.streamRecords(headerData.delimiter, headerData.charset) { row ->
            if (isHeader) {
                isHeader = false
            } else {
                allRows.add(row)
            }
        }
        cachedAllRows = allRows

        _uiState.update {
            it.copy(
                isProcessing = false,
                columnMappings = mappings,
                previewRows = preview,
                currentStep = ImportStep.MAP_COLUMNS
            )
        }
    }

    private suspend fun handleJsonFile(file: File) = withContext(Dispatchers.IO) {
        val reader = JsonReader(file)
        val headerData = reader.readHeaders()
        val mappings = ColumnMapper.generateInitialMappings(headerData.headers)
        val preview = reader.readPreviewRows(headerData.headers, maxRows = 5)

        val allRows = mutableListOf<List<String>>()
        reader.streamRecords(headerData.headers) { row ->
            allRows.add(row)
        }
        cachedAllRows = allRows

        _uiState.update {
            it.copy(
                isProcessing = false,
                columnMappings = mappings,
                previewRows = preview,
                currentStep = ImportStep.MAP_COLUMNS
            )
        }
    }

    fun onUpdateMapping(index: Int, targetField: TargetField) {
        _uiState.update { state ->
            val updated = state.columnMappings.toMutableList()
            if (index in updated.indices) {
                val current = updated[index]
                val prev = if (targetField != TargetField.IGNORE) targetField else current.previousTarget
                updated[index] = current.copy(targetField = targetField, previousTarget = prev)
            }
            state.copy(columnMappings = updated)
        }
    }

    fun toggleColumnSelection(index: Int, isIncluded: Boolean) {
        _uiState.update { state ->
            val updated = state.columnMappings.toMutableList()
            if (index in updated.indices) {
                val current = updated[index]
                if (isIncluded) {
                    val restored = if (current.previousTarget != TargetField.IGNORE) {
                        current.previousTarget
                    } else {
                        TargetField.CUSTOM_SPEC
                    }
                    updated[index] = current.copy(targetField = restored)
                } else {
                    val prev = if (current.targetField != TargetField.IGNORE) current.targetField else current.previousTarget
                    updated[index] = current.copy(
                        targetField = TargetField.IGNORE,
                        previousTarget = prev
                    )
                }
            }
            state.copy(columnMappings = updated)
        }
    }

    fun selectAllColumns() {
        _uiState.update { state ->
            val updated = state.columnMappings.map { mapping ->
                if (mapping.targetField == TargetField.IGNORE) {
                    val restored = if (mapping.previousTarget != TargetField.IGNORE) mapping.previousTarget else TargetField.CUSTOM_SPEC
                    mapping.copy(targetField = restored)
                } else {
                    mapping
                }
            }
            state.copy(columnMappings = updated)
        }
    }

    fun deselectAllSpecs() {
        _uiState.update { state ->
            val updated = state.columnMappings.map { mapping ->
                if (mapping.targetField == TargetField.CUSTOM_SPEC) {
                    mapping.copy(
                        targetField = TargetField.IGNORE,
                        previousTarget = TargetField.CUSTOM_SPEC
                    )
                } else {
                    mapping
                }
            }
            state.copy(columnMappings = updated)
        }
    }

    fun deselectAllOptional() {
        _uiState.update { state ->
            val updated = state.columnMappings.map { mapping ->
                if (mapping.targetField != TargetField.SAP_ID) {
                    val prev = if (mapping.targetField != TargetField.IGNORE) mapping.targetField else mapping.previousTarget
                    mapping.copy(
                        targetField = TargetField.IGNORE,
                        previousTarget = prev
                    )
                } else {
                    mapping
                }
            }
            state.copy(columnMappings = updated)
        }
    }

    fun onProceedToValidation() {
        val state = _uiState.value
        val hasSapId = state.columnMappings.any { it.targetField == TargetField.SAP_ID }
        if (!hasSapId) {
            _uiState.update {
                it.copy(errorMessage = "SAP ID column is required. Please map at least one column to SAP ID.")
            }
            return
        }

        val rows = cachedAllRows ?: emptyList()
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
            val summary = importer.validate(rows, state.columnMappings)
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    validationSummary = summary,
                    currentStep = ImportStep.VALIDATION_PREVIEW
                )
            }
        }
    }

    fun onMergePolicyChanged(policy: MergePolicy) {
        _uiState.update { it.copy(mergePolicy = policy) }
    }

    fun onExecuteImport() {
        val rows = cachedAllRows ?: return
        val state = _uiState.value
        val fileName = state.selectedFileName ?: "Imported File"

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    currentStep = ImportStep.IMPORT_PROGRESS,
                    importProgress = ImportProgress(0, rows.size, "Starting...", 0)
                )
            }

            try {
                val result = importer.executeImport(
                    rows = rows,
                    mappings = state.columnMappings,
                    policy = state.mergePolicy,
                    sourceFileName = fileName,
                    onProgress = { progress ->
                        _uiState.update { it.copy(importProgress = progress) }
                    }
                )

                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        importResult = result
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = "Import failed: ${e.localizedMessage ?: "Unknown database error"}"
                    )
                }
            }
        }
    }

    fun onReset() {
        stagedFile?.delete()
        stagedFile = null
        cachedAllRows = null
        _uiState.value = ImportWizardUiState()
    }

    override fun onCleared() {
        super.onCleared()
        stagedFile?.delete()
        stagedFile = null
        cachedAllRows = null
    }
}
