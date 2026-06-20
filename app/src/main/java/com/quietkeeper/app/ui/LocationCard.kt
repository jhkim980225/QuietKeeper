package com.quietkeeper.app.ui

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.quietkeeper.app.R
import com.quietkeeper.app.ui.theme.GlassCard
import com.quietkeeper.app.ui.theme.TextPrimary
import com.quietkeeper.app.ui.theme.TextSecondary

private const val MAPS_KEY_PLACEHOLDER = "YOUR_MAPS_API_KEY"

/** True only when a real Google Maps API key is configured in the manifest. */
private fun mapsKeyConfigured(context: Context): Boolean = try {
    val ai = context.packageManager.getApplicationInfo(
        context.packageName, PackageManager.GET_META_DATA,
    )
    val key = ai.metaData?.getString("com.google.android.geo.API_KEY")
    !key.isNullOrBlank() && key != MAPS_KEY_PLACEHOLDER
} catch (t: Throwable) {
    false
}

/**
 * Shows where a noise event was measured: the reverse-geocoded address (or raw
 * coordinates), and — ONLY when a real Google Maps API key is configured — a small
 * non-interactive map preview.
 *
 * Rendering the map without a valid key produces a blank/unstable surface, so we
 * gate it on [mapsKeyConfigured]. To enable live tiles, set MAPS_API_KEY (gradle
 * property / local.properties); the map then appears automatically.
 */
@Composable
fun LocationCard(
    lat: Double?,
    lng: Double?,
    address: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val showMap = remember { mapsKeyConfigured(context) }

    GlassCard(modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.measure_location),
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))

        if (lat != null && lng != null) {
            if (showMap) {
                val point = LatLng(lat, lng)
                val cameraPositionState = rememberCameraPositionState {
                    position = CameraPosition.fromLatLngZoom(point, 16f)
                }
                GoogleMap(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    cameraPositionState = cameraPositionState,
                    uiSettings = MapUiSettings(
                        compassEnabled = false,
                        indoorLevelPickerEnabled = false,
                        mapToolbarEnabled = false,
                        myLocationButtonEnabled = false,
                        rotationGesturesEnabled = false,
                        scrollGesturesEnabled = false,
                        scrollGesturesEnabledDuringRotateOrZoom = false,
                        tiltGesturesEnabled = false,
                        zoomControlsEnabled = false,
                        zoomGesturesEnabled = false,
                    ),
                    properties = MapProperties(),
                ) {
                    Marker(state = MarkerState(position = point))
                }
                Spacer(Modifier.height(8.dp))
            }
            Text(
                text = address ?: stringResource(R.string.measure_location_coords, lat, lng),
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
            )
        } else {
            Text(
                text = stringResource(R.string.measure_location_none),
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
            )
        }
    }
}
