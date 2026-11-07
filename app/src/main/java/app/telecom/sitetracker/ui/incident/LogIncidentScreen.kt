package app.telecom.sitetracker.ui.incident

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.telecom.sitetracker.data.entity.IncidentPhotoEntity
import app.telecom.sitetracker.data.entity.SiteEntity
import app.telecom.sitetracker.ui.theme.BrandPrimary
import app.telecom.sitetracker.ui.theme.TagIssueBg
import app.telecom.sitetracker.ui.theme.TagIssueColor
import app.telecom.sitetracker.ui.theme.TagMaintBg
import app.telecom.sitetracker.ui.theme.TagMaintColor
import app.telecom.sitetracker.ui.theme.TagNoteBg
import app.telecom.sitetracker.ui.theme.TagNoteColor
import app.telecom.sitetracker.ui.theme.TagVisitBg
import app.telecom.sitetracker.ui.theme.TagVisitColor
import app.telecom.sitetracker.util.BitmapUtils
import app.telecom.sitetracker.util.Dates
import java.io.File

private enum class LogType(val categoryName: String, val label: String, val icon: String, val textColor: Color, val bgColor: Color) {
    ISSUE("Issue", "Issue / Fault", "⚠️", TagIssueColor, TagIssueBg),
    MAINTENANCE("Maintenance", "Maintenance", "🔧", TagMaintColor, TagMaintBg),
    VISIT("Visit", "Site Visit", "📋", TagVisitColor, TagVisitBg),
    NOTE("Note", "General Note", "📝", TagNoteColor, TagNoteBg)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogIncidentScreen(
    viewModel: LogIncidentViewModel,
    onNavigateBack: () -> Unit,
    onIncidentSaved: (sapId: String, incidentId: String) -> Unit,
    onIncidentDeleted: () -> Unit = onNavigateBack,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()

    var selectedType by remember { mutableStateOf(LogType.ISSUE) }

    LaunchedEffect(uiState.category) {
        val cat = uiState.category.lowercase().trim()
        selectedType = when {
            cat.contains("maint") || cat.contains("repair") -> LogType.MAINTENANCE
            cat.contains("visit") || cat.contains("site") || cat.contains("inspect") -> LogType.VISIT
            cat.contains("note") || cat.contains("general") -> LogType.NOTE
            else -> LogType.ISSUE
        }
    }

    var cameraCaptureUri by remember { mutableStateOf<Uri?>(null) }
    var cameraCaptureFile by remember { mutableStateOf<File?>(null) }
    var showDateTimePicker by remember { mutableStateOf(false) }

    // Camera Launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraCaptureFile != null) {
            val relPath = "photos/${cameraCaptureFile!!.name}"
            viewModel.addPhoto(relPath)
        }
    }

    // Photo Picker Launcher
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val relPath = BitmapUtils.copyUriToPhotos(context, it)
            if (relPath != null) {
                viewModel.addPhoto(relPath)
            }
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (uiState.isEditMode) "Edit Site Log" else "Add Site Log",
                            fontWeight = FontWeight.Bold
                        )
                        if (uiState.selectedSite != null) {
                            Text(
                                text = uiState.selectedSite!!.sapId,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.isEditMode) {
                        IconButton(onClick = { viewModel.setDeleteDialogVisible(true) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Log", tint = TagIssueColor)
                        }
                    }
                    TextButton(
                        onClick = {
                            viewModel.setCategory(selectedType.categoryName)
                            viewModel.saveIncident { incidentId, sapId ->
                                onIncidentSaved(sapId, incidentId)
                            }
                        },
                        enabled = !uiState.isSaving
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("SAVE", fontWeight = FontWeight.Bold, color = BrandPrimary, fontSize = 15.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Target Site Header Card
            if (uiState.selectedSite != null) {
                val site = uiState.selectedSite!!
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = site.sapId,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = BrandPrimary
                            )
                            if (!site.name.isNullOrBlank()) {
                                Text(
                                    text = site.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            val loc = listOfNotNull(site.district, site.city, site.circle).joinToString(" • ")
                            if (loc.isNotBlank()) {
                                Text(
                                    text = loc,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (uiState.initialSapId.isNullOrBlank()) {
                            OutlinedButton(onClick = { viewModel.clearSelectedSite() }) {
                                Text("Change")
                            }
                        }
                    }
                }
            } else {
                // Search Site box
                Column {
                    Text("Select Target Site *", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = uiState.siteSearchQuery,
                        onValueChange = { viewModel.onSiteSearchQueryChanged(it) },
                        placeholder = { Text("Search SAP ID, Site Name, or City...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (uiState.isSearchingSites) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )

                    if (uiState.siteSuggestions.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column {
                                for (site in uiState.siteSuggestions) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.selectSite(site) }
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                site.sapId,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = BrandPrimary
                                            )
                                            Text(site.name ?: "Unnamed Site", style = MaterialTheme.typography.bodySmall)
                                        }
                                        Text(site.city ?: site.district ?: "", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Log Type (4 clear, simple chips)
            Column {
                Text("Log Category", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (type in LogType.values()) {
                        val isSelected = selectedType == type
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedType = type
                                viewModel.setCategory(type.categoryName)
                            },
                            label = {
                                Text(
                                    text = "${type.icon} ${type.categoryName}",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = type.bgColor,
                                selectedLabelColor = type.textColor
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 3. What happened / Note Details
            Column {
                Text("Details / Remarks *", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = uiState.description,
                    onValueChange = {
                        viewModel.setDescription(it)
                        if (uiState.title.isBlank() || uiState.title.endsWith("Log") || uiState.title.endsWith("Issue")) {
                            viewModel.setTitle(it.take(50).trim())
                        }
                    },
                    placeholder = {
                        Text("Describe what happened, actions taken, or observations at this site (e.g. EB fault, replaced SMPS module, battery backup 2.5 hrs, fuel filled)...")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 8,
                    shape = RoundedCornerShape(8.dp)
                )
            }

            // 4. Date & Time Strip
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.EventNote, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Log Timestamp", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = Dates.formatDateTime(uiState.downAtMs),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { showDateTimePicker = true },
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Change", fontSize = 12.sp)
                    }
                }
            }

            // 5. Technician Name / Info (Optional)
            OutlinedTextField(
                value = uiState.engineer,
                onValueChange = { viewModel.setEngineer(it) },
                label = { Text("Technician / Engineer (Optional)") },
                placeholder = { Text("e.g. Ramesh Kumar (9876543210)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )

            // 6. Photos (Optional)
            Column {
                Text("Photos (${uiState.photos.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val pair = BitmapUtils.createImageCaptureUri(context)
                            cameraCaptureUri = pair.first
                            cameraCaptureFile = pair.second
                            cameraLauncher.launch(pair.first)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Take Photo")
                    }

                    OutlinedButton(
                        onClick = { pickerLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Gallery")
                    }
                }

                if (uiState.photos.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(uiState.photos, key = { it.id }) { photo ->
                            PhotoThumbnailItem(
                                photo = photo,
                                context = context,
                                onRemove = { viewModel.removePhoto(photo.id) }
                            )
                        }
                    }
                }
            }

            // 7. Save Button
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = {
                    viewModel.setCategory(selectedType.categoryName)
                    viewModel.saveIncident { incidentId, sapId ->
                        onIncidentSaved(sapId, incidentId)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                shape = RoundedCornerShape(8.dp),
                enabled = !uiState.isSaving && uiState.selectedSite != null
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SAVE LOG ENTRY", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            if (uiState.isEditMode) {
                OutlinedButton(
                    onClick = { viewModel.setDeleteDialogVisible(true) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TagIssueColor),
                    border = BorderStroke(1.dp, TagIssueColor),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !uiState.isSaving
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = TagIssueColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete This Log", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TagIssueColor)
                }
            }

            Spacer(modifier = Modifier.height(88.dp))
        }
    }

    if (uiState.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.setDeleteDialogVisible(false) },
            icon = {
                Icon(Icons.Default.Warning, contentDescription = null, tint = TagIssueColor, modifier = Modifier.size(36.dp))
            },
            title = {
                Text("Delete Site Log?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "⚠️ Warning: This will permanently remove this log from the database. Any notes, technician details, and photos attached to this log will be deleted.\n\nThis action cannot be undone. Are you sure you want to proceed?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCurrentIncident(onSuccess = onIncidentDeleted)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TagIssueColor)
                ) {
                    Text("Delete Permanently", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setDeleteDialogVisible(false) }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDateTimePicker) {
        DateTimePickerDialog(
            title = "Set Log Date & Time",
            initialEpochMs = uiState.downAtMs,
            onConfirm = {
                viewModel.setDownAtMs(it)
                showDateTimePicker = false
            },
            onDismiss = { showDateTimePicker = false }
        )
    }
}

@Composable
fun DateTimePickerDialog(
    title: String,
    initialEpochMs: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val initialDateStr = remember(initialEpochMs) { Dates.formatDate(initialEpochMs) }
    val initialTimeStr = remember(initialEpochMs) { Dates.formatTime(initialEpochMs) }

    var dateText by remember { mutableStateOf(initialDateStr) }
    var timeText by remember { mutableStateOf(initialTimeStr) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Enter date & time (IST):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it; errorText = null },
                    label = { Text("Date (dd/MM/yyyy)") },
                    placeholder = { Text("e.g. 07/11/2026") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = timeText,
                    onValueChange = { timeText = it; errorText = null },
                    label = { Text("Time (24-hour HH:mm)") },
                    placeholder = { Text("e.g. 14:30") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorText != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(errorText!!, color = TagIssueColor, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsed = Dates.parseDateTime(dateText, timeText)
                    if (parsed != null) {
                        onConfirm(parsed)
                    } else {
                        errorText = "Invalid date or time format."
                    }
                }
            ) {
                Text("Confirm", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun PhotoThumbnailItem(
    photo: IncidentPhotoEntity,
    context: Context,
    onRemove: () -> Unit
) {
    val bitmapState = produceState<Bitmap?>(initialValue = null, key1 = photo.relPath) {
        val file = File(context.filesDir, photo.relPath)
        value = BitmapUtils.loadThumbnailBitmap(file, 200)
    }

    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        val bmp = bitmapState.value
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        }

        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(24.dp)
                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}
