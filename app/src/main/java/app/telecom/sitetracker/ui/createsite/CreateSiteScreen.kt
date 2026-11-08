package app.telecom.sitetracker.ui.createsite

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.telecom.sitetracker.data.entity.SiteEntity
import app.telecom.sitetracker.ui.components.EnterpriseTopBar
import app.telecom.sitetracker.ui.theme.RFCyan
import app.telecom.sitetracker.ui.theme.StatusDownRed
import app.telecom.sitetracker.ui.theme.StatusRestoredGreen
import app.telecom.sitetracker.ui.theme.TelecomAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSiteScreen(
    viewModel: CreateSiteViewModel,
    onNavigateBack: () -> Unit,
    onSiteCreated: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddCustomSpecDialog by remember { mutableStateOf<String?>(null) } // holds groupName
    var newCustomSpecKey by remember { mutableStateOf("") }
    var newCustomSpecValue by remember { mutableStateOf("") }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.captureGpsLocation(context)
        } else {
            Toast.makeText(context, "Location permission required to stamp GPS", Toast.LENGTH_SHORT).show()
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
            EnterpriseTopBar(
                title = "Create New Site",
                subtitle = if (uiState.sapId.isNotBlank()) "SAP: ${uiState.sapId}" else "Offline Master Entry",
                onBack = onNavigateBack,
                actions = {
                    Button(
                        onClick = { viewModel.saveSite(onSiteCreated) },
                        enabled = !uiState.isSaving && uiState.sapId.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TelecomAmber,
                            contentColor = Color(0xFF0F172A)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = Color(0xFF0F172A),
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save Site", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 1. Core Identification Card
            item {
                SectionCard(title = "1. SITE IDENTIFICATION") {
                    OutlinedTextField(
                        value = uiState.sapId,
                        onValueChange = { viewModel.onSapIdChange(it) },
                        label = { Text("SAP ID / Site ID *") },
                        placeholder = { Text("e.g. DL-DEL-1001") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TelecomAmber,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (uiState.sapId.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Normalized Index Key: ${SiteEntity.normalize(uiState.sapId)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = RFCyan
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = uiState.name,
                        onValueChange = { viewModel.onNameChange(it) },
                        label = { Text("Site Name / Tower Description") },
                        placeholder = { Text("e.g. Connaught Place Hub") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TelecomAmber,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Operating Status",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Operational", "Under Construction", "Degraded", "Decommissioned").forEach { statusOption ->
                            FilterChipCustom(
                                text = statusOption,
                                isSelected = uiState.status.equals(statusOption, ignoreCase = true),
                                onClick = { viewModel.onStatusChange(statusOption) },
                                activeColor = when (statusOption) {
                                    "Operational" -> StatusRestoredGreen
                                    "Degraded" -> TelecomAmber
                                    "Under Construction" -> RFCyan
                                    else -> StatusDownRed
                                }
                            )
                        }
                    }
                }
            }

            // 2. Geography & Circle Card
            item {
                SectionCard(title = "2. LOCATION & CIRCLE") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.circle,
                            onValueChange = { viewModel.onCircleChange(it) },
                            label = { Text("Circle / CMP") },
                            placeholder = { Text("e.g. Delhi") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = uiState.city,
                            onValueChange = { viewModel.onCityChange(it) },
                            label = { Text("City / JC") },
                            placeholder = { Text("e.g. New Delhi") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.district,
                            onValueChange = { viewModel.onDistrictChange(it) },
                            label = { Text("District") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = uiState.state,
                            onValueChange = { viewModel.onStateChange(it) },
                            label = { Text("State") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = uiState.address,
                        onValueChange = { viewModel.onAddressChange(it) },
                        label = { Text("Full Address / Landmark") },
                        placeholder = { Text("Plot No, Street, Landmark...") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 3. Coordinates & GPS Card
            item {
                SectionCard(title = "3. GPS COORDINATES") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.latStr,
                            onValueChange = { viewModel.onLatChange(it) },
                            label = { Text("Latitude") },
                            placeholder = { Text("28.613939") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = uiState.lngStr,
                            onValueChange = { viewModel.onLngChange(it) },
                            label = { Text("Longitude") },
                            placeholder = { Text("77.209021") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            val hasFine = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                            val hasCoarse = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasFine || hasCoarse) {
                                viewModel.captureGpsLocation(context)
                            } else {
                                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        },
                        enabled = !uiState.isGpsFetching,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, RFCyan)
                    ) {
                        if (uiState.isGpsFetching) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = RFCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Acquiring Satellite GPS Fix...", color = RFCyan)
                        } else {
                            Icon(Icons.Default.MyLocation, contentDescription = null, tint = RFCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Stamp Current Device GPS Location", color = RFCyan, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // 4. Infrastructure & Technology Card
            item {
                SectionCard(title = "4. INFRASTRUCTURE & TECHNOLOGY") {
                    Text(
                        text = "Site Structure Type",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("GBT", "RTT", "RTP", "GBN", "POLE", "MICRO", "IBS", "COW").forEach { typeOption ->
                            FilterChipCustom(
                                text = typeOption,
                                isSelected = uiState.siteType.equals(typeOption, ignoreCase = true),
                                onClick = { viewModel.onSiteTypeChange(typeOption) },
                                activeColor = TelecomAmber
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Active RAN Technology",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("4G", "5G", "4G+5G", "2G+4G", "2G+3G+4G+5G").forEach { techOption ->
                            FilterChipCustom(
                                text = techOption,
                                isSelected = uiState.technology.equals(techOption, ignoreCase = true),
                                onClick = { viewModel.onTechnologyChange(techOption) },
                                activeColor = RFCyan
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = uiState.notes,
                        onValueChange = { viewModel.onNotesChange(it) },
                        label = { Text("Field Notes / Access Instructions") },
                        placeholder = { Text("Key custodian contact, tower access restrictions...") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 5. Preloaded Universal Specs Sections
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "5. EQUIPMENT SPECIFICATIONS",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TelecomAmber
                        )
                        Text(
                            text = "Pre-loaded from Universal Template • Ready to fill",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            val groupedSpecs = uiState.specs.groupBy { it.groupName.uppercase() }
            groupedSpecs.forEach { (groupName, specItems) ->
                item(key = groupName) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Tune,
                                        contentDescription = null,
                                        tint = TelecomAmber,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = groupName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        showAddCustomSpecDialog = groupName
                                        newCustomSpecKey = ""
                                        newCustomSpecValue = ""
                                    }
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add Spec", fontSize = 12.sp)
                                }
                            }

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )

                            specItems.forEach { spec ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = spec.fieldValue,
                                        onValueChange = { viewModel.onSpecValueChange(groupName, spec.fieldKey, it) },
                                        label = { Text(spec.fieldKey) },
                                        placeholder = { Text("Not specified") },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = TelecomAmber,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (!spec.isUniversal) {
                                        IconButton(
                                            onClick = { viewModel.removeSpec(groupName, spec.fieldKey) },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Remove field",
                                                tint = StatusDownRed.copy(alpha = 0.7f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Spacer & Big Save Button
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.saveSite(onSiteCreated) },
                    enabled = !uiState.isSaving && uiState.sapId.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TelecomAmber,
                        contentColor = Color(0xFF0F172A)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = Color(0xFF0F172A),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Creating Master Site...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create Site & Save All Specifications", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Add Custom Specification Dialog
    showAddCustomSpecDialog?.let { groupName ->
        AlertDialog(
            onDismissRequest = { showAddCustomSpecDialog = null },
            title = { Text("Add Specification to $groupName") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newCustomSpecKey,
                        onValueChange = { newCustomSpecKey = it },
                        label = { Text("Specification Name") },
                        placeholder = { Text("e.g. EB METER NUMBER") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newCustomSpecValue,
                        onValueChange = { newCustomSpecValue = it },
                        label = { Text("Value") },
                        placeholder = { Text("e.g. 782910394") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newCustomSpecKey.isNotBlank()) {
                            viewModel.addCustomSpec(groupName, newCustomSpecKey, newCustomSpecValue)
                            showAddCustomSpecDialog = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TelecomAmber)
                ) {
                    Text("Add Field", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCustomSpecDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = TelecomAmber
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
            )
            content()
        }
    }
}

@Composable
private fun FilterChipCustom(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    activeColor: Color
) {
    val borderColor = if (isSelected) activeColor else MaterialTheme.colorScheme.outlineVariant
    val bgColor = if (isSelected) activeColor.copy(alpha = 0.15f) else Color.Transparent
    val textColor = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textColor
            )
        )
    }
}
