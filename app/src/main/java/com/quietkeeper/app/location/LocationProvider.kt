package com.quietkeeper.app.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/**
 * One-shot GPS location capture + reverse geocoding. No Google Maps API key required
 * (the Android [Geocoder] is keyless). Never throws — returns null on any failure
 * (permission missing, location unavailable, geocoder error).
 */
object LocationProvider {

    data class Fix(val lat: Double, val lng: Double, val address: String?)

    /**
     * Returns the current location reverse-geocoded to a short address, or null if
     * location permission is missing or no fix could be obtained.
     */
    suspend fun current(context: Context): Fix? {
        if (!hasLocationPermission(context)) return null
        return try {
            val location = awaitCurrentLocation(context) ?: return null
            val address = reverseGeocode(context, location.latitude, location.longitude)
            Fix(location.latitude, location.longitude, address)
        } catch (t: Throwable) {
            null
        }
    }

    private fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private suspend fun awaitCurrentLocation(context: Context): Location? =
        suspendCancellableCoroutine { cont ->
            try {
                val client = LocationServices.getFusedLocationProviderClient(context)
                val cts = CancellationTokenSource()
                cont.invokeOnCancellation { cts.cancel() }
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                    .addOnSuccessListener { loc ->
                        if (!cont.isActive) return@addOnSuccessListener
                        // getCurrentLocation can resolve null (e.g. cold provider, emulator).
                        // Fall back to the last known location before giving up.
                        if (loc != null) {
                            cont.resume(loc)
                        } else {
                            client.lastLocation
                                .addOnSuccessListener { last -> if (cont.isActive) cont.resume(last) }
                                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
                        }
                    }
                    .addOnFailureListener {
                        if (!cont.isActive) return@addOnFailureListener
                        client.lastLocation
                            .addOnSuccessListener { last -> if (cont.isActive) cont.resume(last) }
                            .addOnFailureListener { if (cont.isActive) cont.resume(null) }
                    }
            } catch (se: SecurityException) {
                if (cont.isActive) cont.resume(null)
            } catch (t: Throwable) {
                if (cont.isActive) cont.resume(null)
            }
        }

    private suspend fun reverseGeocode(context: Context, lat: Double, lng: Double): String? =
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // API 33+ async listener call (the sync overload is deprecated here).
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(lat, lng, 1) { addresses ->
                        if (cont.isActive) cont.resume(formatAddress(addresses.firstOrNull()))
                    }
                }
            } else {
                // Deprecated sync call is acceptable off the main thread.
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    formatAddress(geocoder.getFromLocation(lat, lng, 1)?.firstOrNull())
                }
            }
        } catch (t: Throwable) {
            null
        }

    private fun formatAddress(address: Address?): String? {
        if (address == null) return null
        val parts = listOfNotNull(
            address.adminArea,
            address.subLocality ?: address.locality,
            address.thoroughfare,
        ).filter { it.isNotBlank() }
        val joined = parts.joinToString(" ").trim()
        return joined.ifBlank { null }
    }
}
