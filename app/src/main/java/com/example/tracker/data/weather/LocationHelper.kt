package com.example.tracker.data.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

data class DeviceLocation(
    val latitude: Double,
    val longitude: Double,
    val cityName: String,
)

object LocationHelper {

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    suspend fun detectCurrentLocation(context: Context): DeviceLocation? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission(context)) return@withContext null

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return@withContext null

        // 1. Ask for a fresh, precise fix first (fused on Android 12+, else GPS, else network).
        val fresh = withTimeoutOrNull(12_000L) { currentFix(context, lm) }
        if (fresh != null) {
            return@withContext DeviceLocation(fresh.latitude, fresh.longitude, resolveCityName(context, fresh.latitude, fresh.longitude))
        }

        // 2. Fall back to the most accurate recent cached fix (< 15 min), then any cached fix.
        val cached = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
        val recent = cached.filter { it.time > System.currentTimeMillis() - 15 * 60_000L }.minByOrNull { it.accuracy }
        val best = recent ?: cached.maxByOrNull { it.time }
            ?: withTimeoutOrNull(6_000L) { requestSingleUpdate(lm) }
            ?: return@withContext null
        DeviceLocation(best.latitude, best.longitude, resolveCityName(context, best.latitude, best.longitude))
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentFix(context: Context, lm: LocationManager): Location? {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return null
        val provider = when {
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
                runCatching { lm.isProviderEnabled(LocationManager.FUSED_PROVIDER) }.getOrDefault(false) -> LocationManager.FUSED_PROVIDER
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return null
        }
        return suspendCancellableCoroutine { cont ->
            val signal = android.os.CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            runCatching {
                lm.getCurrentLocation(provider, signal, context.mainExecutor) { loc -> if (cont.isActive) cont.resume(loc) }
            }.onFailure { if (cont.isActive) cont.resume(null) }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestSingleUpdate(lm: LocationManager): Location? = suspendCancellableCoroutine { cont ->
        val provider = when {
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> null
        }

        if (provider == null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                runCatching { lm.removeUpdates(this) }
                if (cont.isActive) cont.resume(location)
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
            override fun onProviderEnabled(p: String) {}
            override fun onProviderDisabled(p: String) {
                runCatching { lm.removeUpdates(this) }
                if (cont.isActive) cont.resume(null)
            }
        }

        try {
            lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            cont.invokeOnCancellation {
                runCatching { lm.removeUpdates(listener) }
            }
        } catch (e: Exception) {
            cont.resume(null)
        }
    }

    /** "Ashulia, Savar" style: the neighbourhood first, then the town. Never invents "Dhaka". */
    fun resolveCityName(context: Context, lat: Double, lon: Double): String {
        val address = runCatching {
            @Suppress("DEPRECATION")
            Geocoder(context, Locale.ENGLISH).getFromLocation(lat, lon, 1)?.firstOrNull()
        }.getOrNull() ?: return "%.3f, %.3f".format(Locale.US, lat, lon)
        val parts = listOfNotNull(
            address.subLocality ?: address.thoroughfare ?: address.featureName?.takeUnless { it.any(Char::isDigit) },
            address.locality ?: address.subAdminArea ?: address.adminArea,
        ).distinct()
        return parts.joinToString(", ").ifBlank { "%.3f, %.3f".format(Locale.US, lat, lon) }
    }
}
