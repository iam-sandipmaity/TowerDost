package app.telecom.sitetracker.ui.incident

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.telecom.sitetracker.R
import app.telecom.sitetracker.data.entity.IncidentPhotoEntity
import app.telecom.sitetracker.data.entity.SiteEntity
import app.telecom.sitetracker.ui.theme.SapIdStyle
import app.telecom.sitetracker.ui.theme.SeverityCritical
import app.telecom.sitetracker.ui.theme.SeverityMajor
import app.telecom.sitetracker.ui.theme.SeverityMinor
import app.telecom.sitetracker.ui.theme.TealPrimary
import app.telecom.sitetracker.util.BitmapUtils
import app.telecom.sitetracker.util.Dates
import java.io.File

private val CATEGORIES = listOf(
    "Power", "DG", "Battery", "SMPS", "Transmission", "OFC", "BTS-RAN", "AC", "Civil", "Security", "Other"
)

private val SEVERITIES = listOf("Critical", "Major", "Minor", "Info")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LogIncidentScreen(
    viewModel: LogIncidentViewModel,
    onNavigateBack: () -> Unit,
    onIncidentSaved: (sapId: String, incidentId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()

    var cameraCaptureUri by remember { mutableStateOf<Uri?>(null) }
    var cameraCaptureFile by remember { mutableStateOf<File?>(null) }

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

    // Location Permission Launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.stampGps(context)
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
                    Text(
                        text = if (uiState.isOutage) "Log Site Outage" else "Log Site Incident",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.saveIncident { incidentId, sapId ->
                                onIncidentSaved(sapId, incidentId)
                            }
                        },
                        enabled = !uiState.isSaving
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("SAVE", fontWeight = FontWeight.Bold, color = TealPrimary)
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // 1. Target Site Selector
            Text("Target Site *", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            if (uiState.selectedSite != null) {
                val site = uiState.selectedSite!!
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
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
                            Text(site.sapId, style = SapIdStyle.copy(fontSize = 18.sp, color = TealPrimary))
                            if (!site.name.isNullOrBlank()) {
                                Text(site.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                            val loc = listOfNotNull(site.district, site.circle, site.state).joinToString(", ")
                            if (loc.isNotBlank()) {
                                Text(loc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Column {
                    OutlinedTextField(
                        value = uiState.siteSearchQuery,
                        onValueChange = { viewModel.onSiteSearchQueryChanged(it) },
                        placeholder = { Text("Search SAP ID, Name, or City...") },
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
                                            Text(site.sapId, style = SapIdStyle.copy(color = TealPrimary))
                                            Text(site.name ?: "Unnamed Site", style = MaterialTheme.typography.bodySmall)
                                        }
                                        Text(site.district ?: site.circle ?: "", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Type Selector: Outage vs Incident
            Text("Issue Classification", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterChip(
                    selected = uiState.isOutage,
                    onClick = { viewModel.setIsOutage(true) },
                    label = { Text("🚨 Site Outage (Down)", fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFEF2F2),
                        selectedLabelColor = SeverityCritical
                    )
                )
                FilterChip(
                    selected = !uiState.isOutage,
                    onClick = { viewModel.setIsOutage(false) },
                    label = { Text("🔧 Visit / Incident", fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }

            // 3. Severity Chips
            Text("Severity", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (sev in SEVERITIES) {
                    val isSelected = uiState.severity == sev
                    val color = when (sev) {
                        "Critical" -> SeverityCritical
                        "Major" -> SeverityMajor
                        "Minor" -> SeverityMinor
                        else -> Color(0xFF64748B)
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setSeverity(sev) },
                        label = { Text(sev, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = color.copy(alpha = 0.2f),
                            selectedLabelColor = color
                        )
                    )
                }
            }

            // 4. Category Selector
            Text("Category / Subsystem", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (cat in CATEGORIES) {
                    val isSelected = uiState.category == cat
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setCategory(cat) },
                        label = { Text(cat, fontSize = 12.sp) }
                    )
                }
            }

            // 5. Root Cause Reason Dropdown
            Text("Root Cause Reason", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            var reasonDropdownExpanded by remember { mutableStateOf(false) }
            val selectedReason = uiState.activeReasons.find { it.id == uiState.selectedReasonId }

            ExposedDropdownMenuBox(
                expanded = reasonDropdownExpanded,
                onExpandedChange = { reasonDropdownExpanded = !reasonDropdownExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedReason?.label ?: "Select a root cause reason...",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = reasonDropdownExpanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                ExposedDropdownMenu(
                    expanded = reasonDropdownExpanded,
                    onDismissRequest = { reasonDropdownExpanded = false }
                ) {
                    for (reason in uiState.activeReasons) {
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(reason.label, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = "${reason.category ?: "Other"} • ${if (reason.countsTowardDowntime == 1) "Counts as downtime" else "Excluded"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                viewModel.setSelectedReason(reason.id)
                                reasonDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // 6. Incident Summary / Title
            Text("Incident Title / Short Summary *", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = uiState.title,
                onValueChange = { viewModel.setTitle(it) },
                placeholder = { Text("e.g. EB phase fail, DG cranked manually") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )

            // 7. Time & Restored Checkbox
            Text(
                if (uiState.isOutage) "Outage Start Time" else "Incident Timestamp",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = Dates.formatDateTime(uiState.downAtMs),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedButton(onClick = { viewModel.setDownAtMs(System.currentTimeMillis()) }) {
                    Text("Set to Now")
                }
            }

            if (uiState.isOutage) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = uiState.isRestored,
                        onCheckedChange = { viewModel.setIsRestored(it) }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Already Restored? (Log resolved past outage)", style = MaterialTheme.typography.bodyMedium)
                }

                if (uiState.isRestored) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Restored At", style = MaterialTheme.typography.labelMedium)
                            Text(
                                text = Dates.formatDateTime(uiState.restoredAtMs),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        OutlinedButton(onClick = { viewModel.setRestoredAtMs(System.currentTimeMillis()) }) {
                            Text("Set to Now")
                        }
                    }
                }
            }

            // 8. Description & Action Taken
            Text("Fault Details & Action Taken", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = uiState.description,
                onValueChange = { viewModel.setDescription(it) },
                label = { Text("Description / Observations") },
                placeholder = { Text("e.g. Discovered burnt fuse in EB cutout box, battery holding at 48.1V") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(8.dp)
            )

            OutlinedTextField(
                value = uiState.actionTaken,
                onValueChange = { viewModel.setActionTaken(it) },
                label = { Text("Action Taken / Remediation") },
                placeholder = { Text("e.g. Replaced 63A HRC fuse, DG run for 1.2 hrs until EB restored") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(8.dp)
            )

            // 9. Technician Name & Ticket Reference
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = uiState.engineer,
                    onValueChange = { viewModel.setEngineer(it) },
                    label = { Text("Technician") },
                    placeholder = { Text("Name / Phone") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = uiState.ticketRef,
                    onValueChange = { viewModel.setTicketRef(it) },
                    label = { Text("Ticket Ref") },
                    placeholder = { Text("TT / CR #") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
            }

            // 10. GPS Location Stamp
            Text("Field GPS Stamp", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (uiState.gpsLat != null && uiState.gpsLng != null) {
                            Text(
                                text = "Lat: %.5f, Lng: %.5f".format(uiState.gpsLat, uiState.gpsLng),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            val acc = uiState.gpsAccuracyM?.let { " (±%.1fm)".format(it) } ?: ""
                            Text("Accuracy$acc", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                            if (uiState.gpsDistanceFromSiteM != null) {
                                val dist = uiState.gpsDistanceFromSiteM!!
                                val color = if (dist <= 500) Color(0xFF16A34A) else SeverityMajor
                                Text(
                                    text = if (dist < 1000) "%.0fm from site".format(dist) else "%.2fkm from site".format(dist / 1000.0),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = color)
                                )
                            }
                        } else {
                            Text("No GPS location stamped", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (uiState.gpsLat != null) "Update" else "Stamp GPS")
                    }
                }
            }

            // 11. Photos
            Text("Photos & Evidence (${uiState.photos.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
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
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Take Photo")
                }

                OutlinedButton(
                    onClick = { pickerLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Gallery")
                }
            }

            if (uiState.photos.isNotEmpty()) {
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

            // 12. Submit Button
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    viewModel.saveIncident { incidentId, sapId ->
                        onIncidentSaved(sapId, incidentId)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                shape = RoundedCornerShape(8.dp),
                enabled = !uiState.isSaving
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiState.isOutage) "LOG OUTAGE & UPDATE REGISTER" else "RECORD INCIDENT",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PhotoThumbnailItem(
    photo: IncidentPhotoEntity,
    context: Context,
    onRemove: () -> Unit
) {
    val file = remember(photo.relPath) { File(context.filesDir, photo.relPath) }
    val bitmapState = produceState<Bitmap?>(initialValue = null, key1 = file.absolutePath) {
        value = BitmapUtils.loadThumbnailBitmap(file, maxDim = 180)
    }

    Box(
        modifier = Modifier
            .size(90.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.LightGray)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
    ) {
        if (bitmapState.value != null) {
            Image(
                bitmap = bitmapState.value!!.asImageBitmap(),
                contentDescription = photo.caption,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }

        // Delete button
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable { onRemove() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}
