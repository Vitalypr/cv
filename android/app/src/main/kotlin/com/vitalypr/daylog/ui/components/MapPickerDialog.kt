package com.vitalypr.daylog.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vitalypr.daylog.R
import com.vitalypr.daylog.ui.theme.Petrol
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

/**
 * OpenStreetMap pin picker (spec N3 rev v0.7 — the app's only network use).
 * Pan/zoom the map under a fixed center pin; confirm returns the pin position.
 */
@Composable
fun MapPickerDialog(
    initialLat: Double?,
    initialLon: Double?,
    onPick: (Double, Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val mapRef = remember { mutableStateOf<MapView?>(null) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            controller.setZoom(if (initialLat != null) 16.0 else 8.0)
                            controller.setCenter(
                                GeoPoint(initialLat ?: ISRAEL_LAT, initialLon ?: ISRAEL_LON),
                            )
                            mapRef.value = this
                        }
                    },
                )
                DisposableEffect(Unit) {
                    onDispose { mapRef.value?.onDetach() }
                }

                // Fixed center pin — the map moves underneath it.
                Icon(
                    Icons.Default.Place,
                    contentDescription = null,
                    tint = Petrol,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(bottom = 34.dp) // tip of the pin on the exact center
                        .size(44.dp),
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                ) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                }

                Button(
                    onClick = {
                        mapRef.value?.mapCenter?.let { center ->
                            onPick(center.latitude, center.longitude)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text(stringResource(R.string.map_confirm), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

private const val ISRAEL_LAT = 31.9
private const val ISRAEL_LON = 35.0
