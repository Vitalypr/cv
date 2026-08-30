package com.vitalypr.daylog.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings as SysSettings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitalypr.daylog.R
import com.vitalypr.daylog.data.settings.Settings
import com.vitalypr.daylog.domain.time.formatMinutes
import com.vitalypr.daylog.ui.components.SectionCard
import com.vitalypr.daylog.ui.components.TimePickerDialog
import com.vitalypr.daylog.ui.theme.InkMuted
import com.vitalypr.daylog.ui.theme.InkSecondary
import java.time.DayOfWeek

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val jobLocations by viewModel.jobLocations.collectAsStateWithLifecycle()
    val geofenceStatus by viewModel.geofenceStatus.collectAsStateWithLifecycle()
    val geofenceEvents by viewModel.geofenceEvents.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val pickBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::restoreBackup) }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.setGeofenceEnabled(true, hasPermission = true)
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is SettingsEffect.RequestLocationPermission ->
                    locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                is SettingsEffect.ShareFile -> {
                    val uri = FileProvider.getUriForFile(
                        context, "${context.packageName}.fileprovider", effect.file,
                    )
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = effect.mime
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            },
                            null,
                        ),
                    )
                }
                is SettingsEffect.OpenLocationSettings ->
                    context.startActivity(
                        Intent(
                            SysSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ),
                    )
                is SettingsEffect.Toast ->
                    Toast.makeText(context, effect.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    // Re-check registration whenever the user returns from system settings.
    androidx.compose.runtime.DisposableEffect(Unit) {
        viewModel.refreshGeofences()
        onDispose { }
    }

    SettingsContent(
        settings = settings,
        jobLocations = jobLocations,
        geofenceStatus = geofenceStatus,
        geofenceEvents = geofenceEvents,
        projects = projects,
        onAddProject = viewModel::addProject,
        onRemoveProject = viewModel::removeProject,
        onRestoreProject = viewModel::restoreProject,
        onSetOfficeRadius = viewModel::setOfficeRadius,
        onExportBackup = viewModel::exportBackup,
        onImportBackup = { pickBackup.launch(arrayOf("application/json", "text/plain", "*/*")) },
        onAddJobLocation = viewModel::addJobLocation,
        onRemoveJobLocation = viewModel::removeJobLocation,
        onToggleWorkDay = viewModel::toggleWorkDay,
        onSetReportTime = viewModel::setReportTime,
        onGeofenceEnabled = { enabled ->
            val has = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
            viewModel.setGeofenceEnabled(enabled, has)
        },
        onSilent = viewModel::setSilent,
        onCaptureOffice = viewModel::captureOffice,
        onOfficePicked = viewModel::setOfficeAt,
        onJobPicked = viewModel::addJobLocationAt,
        onExportJson = viewModel::exportJson,
        onExportCsv = viewModel::exportCsv,
        onBatterySettings = {
            context.startActivity(
                Intent(
                    SysSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                ),
            )
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsContent(
    settings: Settings,
    jobLocations: List<com.vitalypr.daylog.data.db.JobLocationEntity> = emptyList(),
    geofenceStatus: com.vitalypr.daylog.geofence.GeofenceStatus = com.vitalypr.daylog.geofence.GeofenceStatus.Unknown,
    geofenceEvents: List<String> = emptyList(),
    projects: List<com.vitalypr.daylog.data.db.ProjectEntity> = emptyList(),
    onAddProject: (String) -> Unit = {},
    onRemoveProject: (com.vitalypr.daylog.data.db.ProjectEntity) -> Unit = {},
    onRestoreProject: (com.vitalypr.daylog.data.db.ProjectEntity) -> Unit = {},
    onSetOfficeRadius: (Int) -> Unit = {},
    onExportBackup: () -> Unit = {},
    onImportBackup: () -> Unit = {},
    onAddJobLocation: (String) -> Unit = {},
    onRemoveJobLocation: (com.vitalypr.daylog.data.db.JobLocationEntity) -> Unit = {},
    onToggleWorkDay: (DayOfWeek) -> Unit = {},
    onSetReportTime: (Int) -> Unit = {},
    onGeofenceEnabled: (Boolean) -> Unit = {},
    onSilent: (Boolean) -> Unit = {},
    onCaptureOffice: () -> Unit = {},
    onOfficePicked: (Double, Double) -> Unit = { _, _ -> },
    onJobPicked: (String, Double, Double) -> Unit = { _, _, _ -> },
    onExportJson: () -> Unit = {},
    onExportCsv: () -> Unit = {},
    onBatterySettings: () -> Unit = {},
) {
    var pickTime by remember { mutableStateOf(false) }
    var pickOfficeOnMap by remember { mutableStateOf(false) }
    var pickJobOnMap by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.tab_settings),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 8.dp),
        )

        SectionCard(title = stringResource(R.string.settings_geofence_section)) {
            SettingsRow(
                title = stringResource(R.string.settings_office_location),
                subtitle = if (settings.officeLat != null) {
                    stringResource(R.string.settings_office_set, settings.officeRadiusM)
                } else {
                    stringResource(R.string.settings_office_unset)
                },
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = onCaptureOffice) {
                        Text(stringResource(R.string.settings_capture_location))
                    }
                    TextButton(onClick = { pickOfficeOnMap = true }) {
                        Text(stringResource(R.string.settings_pick_on_map))
                    }
                }
            }
            SettingsRow(
                title = stringResource(R.string.settings_geofence_toggle),
                subtitle = stringResource(R.string.settings_geofence_sub),
            ) {
                Switch(checked = settings.geofenceEnabled, onCheckedChange = onGeofenceEnabled)
            }
            SettingsRow(
                title = stringResource(R.string.settings_silent_toggle),
                subtitle = stringResource(R.string.settings_silent_sub),
            ) {
                Switch(checked = settings.silentGeofence, onCheckedChange = onSilent)
            }
            Text(
                stringResource(R.string.settings_radius),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                stringResource(R.string.settings_radius_sub),
                style = MaterialTheme.typography.bodySmall,
                color = com.vitalypr.daylog.ui.theme.InkSecondary,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                com.vitalypr.daylog.domain.geo.GeofenceRules.OFFICE_RADIUS_OPTIONS.forEach { meters ->
                    FilterChip(
                        selected = settings.officeRadiusM == meters,
                        onClick = { onSetOfficeRadius(meters) },
                        // Bare value: "רדיוס NNN מ׳" on every chip overflows a phone row.
                        label = { Text(radiusLabel(meters)) },
                    )
                }
            }
            GeofenceStatusRow(geofenceStatus, onOpenLocationSettings = onBatterySettings)
        }

        SectionCard(title = stringResource(R.string.settings_projects)) {
            Text(
                stringResource(R.string.settings_projects_sub),
                style = MaterialTheme.typography.bodySmall,
                color = com.vitalypr.daylog.ui.theme.InkSecondary,
            )
            projects.forEach { project ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    Text(
                        project.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (project.isArchived) {
                            com.vitalypr.daylog.ui.theme.InkMuted
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.weight(1f),
                    )
                    if (project.isArchived) {
                        Text(
                            stringResource(R.string.project_archived),
                            style = MaterialTheme.typography.labelSmall,
                            color = com.vitalypr.daylog.ui.theme.InkMuted,
                        )
                        TextButton(onClick = { onRestoreProject(project) }) {
                            Text(stringResource(R.string.project_restore))
                        }
                    } else {
                        TextButton(onClick = { onRemoveProject(project) }) {
                            Text(stringResource(R.string.remove))
                        }
                    }
                }
            }
            var newProject by rememberSaveable { mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                OutlinedTextField(
                    value = newProject,
                    onValueChange = { newProject = it },
                    placeholder = { Text(stringResource(R.string.settings_project_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { onAddProject(newProject); newProject = "" },
                    enabled = newProject.isNotBlank(),
                ) { Text(stringResource(R.string.settings_add_project)) }
            }
        }

        SectionCard(title = stringResource(R.string.settings_job_locations)) {
            Text(
                stringResource(R.string.settings_job_locations_sub),
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            jobLocations.forEach { loc ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("📍", Modifier.padding(end = 6.dp))
                    Column(Modifier.weight(1f)) {
                        Text(loc.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            radiusLabel(loc.radiusM),
                            style = MaterialTheme.typography.labelSmall,
                            color = InkMuted,
                        )
                    }
                    androidx.compose.material3.IconButton(onClick = { onRemoveJobLocation(loc) }) {
                        androidx.compose.material3.Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.remove),
                            tint = InkMuted,
                        )
                    }
                }
            }
            var newName by remember { mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text(stringResource(R.string.settings_job_name_hint), style = MaterialTheme.typography.bodySmall) },
                    textStyle = MaterialTheme.typography.bodySmall,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(
                        onClick = { onAddJobLocation(newName); newName = "" },
                        enabled = newName.isNotBlank(),
                    ) { Text(stringResource(R.string.settings_add_job_here)) }
                    TextButton(
                        onClick = { pickJobOnMap = newName },
                        enabled = newName.isNotBlank(),
                    ) { Text(stringResource(R.string.settings_pick_on_map)) }
                }
            }
        }

        SectionCard(title = stringResource(R.string.settings_schedule_section)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.settings_work_days),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Spacer(Modifier.size(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        DayOfWeek.SUNDAY to "א", DayOfWeek.MONDAY to "ב", DayOfWeek.TUESDAY to "ג",
                        DayOfWeek.WEDNESDAY to "ד", DayOfWeek.THURSDAY to "ה",
                        DayOfWeek.FRIDAY to "ו", DayOfWeek.SATURDAY to "ש",
                    ).forEach { (day, label) ->
                        FilterChip(
                            selected = day in settings.workDays,
                            onClick = { onToggleWorkDay(day) },
                            label = { Text(label) },
                        )
                    }
                }
            }
            SettingsRow(
                title = stringResource(R.string.settings_report_time),
                subtitle = stringResource(R.string.settings_report_time_sub),
            ) {
                TextButton(onClick = { pickTime = true }) {
                    Text(formatMinutes(settings.reportTimeMin), style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        SectionCard(title = stringResource(R.string.settings_diagnostics)) {
            Text(
                stringResource(R.string.settings_diagnostics_sub),
                style = MaterialTheme.typography.bodySmall,
                color = com.vitalypr.daylog.ui.theme.InkSecondary,
            )
            if (geofenceEvents.isEmpty()) {
                Text(
                    stringResource(R.string.settings_diagnostics_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = com.vitalypr.daylog.ui.theme.InkMuted,
                    modifier = Modifier.padding(top = 6.dp),
                )
            } else {
                geofenceEvents.take(12).forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        color = com.vitalypr.daylog.ui.theme.InkSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        SectionCard(title = stringResource(R.string.settings_backup)) {
            Text(
                stringResource(R.string.settings_backup_sub),
                style = MaterialTheme.typography.bodySmall,
                color = com.vitalypr.daylog.ui.theme.InkSecondary,
            )
            SettingsRow(
                title = stringResource(R.string.settings_backup_export),
                subtitle = stringResource(R.string.settings_backup_export_sub),
            ) {
                TextButton(onClick = onExportBackup) { Text(stringResource(R.string.settings_backup_export_action)) }
            }
            var confirmRestore by remember { mutableStateOf(false) }
            SettingsRow(
                title = stringResource(R.string.settings_backup_import),
                subtitle = stringResource(R.string.settings_backup_import_sub),
            ) {
                TextButton(onClick = { confirmRestore = true }) {
                    Text(stringResource(R.string.settings_backup_import_action))
                }
            }
            if (confirmRestore) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { confirmRestore = false },
                    title = { Text(stringResource(R.string.settings_backup_import)) },
                    text = { Text(stringResource(R.string.settings_backup_import_warning)) },
                    confirmButton = {
                        TextButton(onClick = { confirmRestore = false; onImportBackup() }) {
                            Text(stringResource(R.string.settings_backup_import_action))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmRestore = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    },
                )
            }
        }

        SectionCard(title = stringResource(R.string.settings_data_section)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onExportJson, Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_export_json))
                }
                OutlinedButton(onClick = onExportCsv, Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_export_csv))
                }
            }
            SettingsRow(
                title = stringResource(R.string.settings_battery),
                subtitle = stringResource(R.string.settings_battery_sub),
            ) {
                TextButton(onClick = onBatterySettings) { Text(stringResource(R.string.settings_open)) }
            }
        }
        Spacer(Modifier.padding(bottom = 8.dp))
    }

    if (pickOfficeOnMap) {
        com.vitalypr.daylog.ui.components.MapPickerDialog(
            initialLat = settings.officeLat,
            initialLon = settings.officeLon,
            onPick = { lat, lon -> onOfficePicked(lat, lon); pickOfficeOnMap = false },
            onDismiss = { pickOfficeOnMap = false },
        )
    }
    pickJobOnMap?.let { name ->
        com.vitalypr.daylog.ui.components.MapPickerDialog(
            initialLat = settings.officeLat,
            initialLon = settings.officeLon,
            onPick = { lat, lon -> onJobPicked(name, lat, lon); pickJobOnMap = null },
            onDismiss = { pickJobOnMap = null },
        )
    }

    if (pickTime) {
        TimePickerDialog(
            initialMinutes = settings.reportTimeMin,
            onConfirm = { onSetReportTime(it); pickTime = false },
            onDismiss = { pickTime = false },
        )
    }
}

/** Metres below a kilometre, kilometres above it — 1500 מ׳ reads as a mistake. */
@Composable
private fun radiusLabel(meters: Int): String =
    if (meters >= 1000) {
        val km = meters / 1000.0
        stringResource(R.string.radius_km, if (km % 1 == 0.0) "${km.toInt()}" else "$km")
    } else {
        stringResource(R.string.radius_value, meters)
    }

@Composable
private fun GeofenceStatusRow(
    status: com.vitalypr.daylog.geofence.GeofenceStatus,
    onOpenLocationSettings: () -> Unit,
) {
    val (text, isProblem) = when (status) {
        is com.vitalypr.daylog.geofence.GeofenceStatus.Active ->
            stringResource(R.string.geo_status_active, status.fenceCount) to false
        com.vitalypr.daylog.geofence.GeofenceStatus.Disabled ->
            stringResource(R.string.geo_status_disabled) to false
        com.vitalypr.daylog.geofence.GeofenceStatus.NoPermission ->
            stringResource(R.string.geo_status_no_permission) to true
        com.vitalypr.daylog.geofence.GeofenceStatus.NoBackgroundPermission ->
            stringResource(R.string.geo_status_no_bg) to true
        com.vitalypr.daylog.geofence.GeofenceStatus.NoLocations ->
            stringResource(R.string.geo_status_no_locations) to false
        is com.vitalypr.daylog.geofence.GeofenceStatus.Error ->
            stringResource(R.string.geo_status_error, status.message) to true
        com.vitalypr.daylog.geofence.GeofenceStatus.Unknown -> "" to false
    }
    if (text.isNotEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = if (isProblem) com.vitalypr.daylog.ui.theme.Amber else com.vitalypr.daylog.ui.theme.SendGreenDark,
                modifier = Modifier.weight(1f),
            )
            if (isProblem) {
                TextButton(onClick = onOpenLocationSettings) {
                    Text(stringResource(R.string.geo_open_settings))
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String?, trailing: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = InkMuted)
            }
        }
        trailing()
    }
}
