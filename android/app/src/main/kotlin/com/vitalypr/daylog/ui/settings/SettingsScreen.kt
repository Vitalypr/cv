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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
    val context = LocalContext.current

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
                is SettingsEffect.Toast ->
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    SettingsContent(
        settings = settings,
        jobLocations = jobLocations,
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

@Composable
fun SettingsContent(
    settings: Settings,
    jobLocations: List<com.vitalypr.daylog.data.db.JobLocationEntity> = emptyList(),
    onAddJobLocation: (String) -> Unit = {},
    onRemoveJobLocation: (com.vitalypr.daylog.data.db.JobLocationEntity) -> Unit = {},
    onToggleWorkDay: (DayOfWeek) -> Unit = {},
    onSetReportTime: (Int) -> Unit = {},
    onGeofenceEnabled: (Boolean) -> Unit = {},
    onSilent: (Boolean) -> Unit = {},
    onCaptureOffice: () -> Unit = {},
    onExportJson: () -> Unit = {},
    onExportCsv: () -> Unit = {},
    onBatterySettings: () -> Unit = {},
) {
    var pickTime by remember { mutableStateOf(false) }

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
                TextButton(onClick = onCaptureOffice) {
                    Text(stringResource(R.string.settings_capture_location))
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
                            stringResource(R.string.settings_job_radius, loc.radiusM),
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
                TextButton(
                    onClick = { onAddJobLocation(newName); newName = "" },
                    enabled = newName.isNotBlank(),
                ) { Text(stringResource(R.string.settings_add_job_here)) }
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

    if (pickTime) {
        TimePickerDialog(
            initialMinutes = settings.reportTimeMin,
            onConfirm = { onSetReportTime(it); pickTime = false },
            onDismiss = { pickTime = false },
        )
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
