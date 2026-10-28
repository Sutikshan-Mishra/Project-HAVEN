// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.data.device

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import app.projecthaven.domain.geo.Coordinate
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** A location reading exactly as reported by Android, with nothing estimated or invented. */
data class LocationFix(
    val coordinate: Coordinate,
    val accuracyMeters: Float?,
    /** WGS84 ellipsoid height, when reported. */
    val altitudeMeters: Double?,
    /** Height above mean sea level, when the platform provides it (Android 14+). */
    val mslAltitudeMeters: Double?,
    val speedMps: Float?,
    val provider: String?,
    val timeEpochMs: Long,
    val elapsedRealtimeMs: Long,
) {
    fun ageMs(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Long = (nowElapsedMs - elapsedRealtimeMs).coerceAtLeast(0)
}

enum class LocationAvailability { PERMISSION_REQUIRED, PROVIDERS_OFF, READY }

/**
 * Uses the platform [LocationManager] directly (GPS and, when enabled, network), so it works
 * without Google Play services and with GPS alone when there is no mobile data.
 */
class LocationTracker(private val context: Context) {

    private val manager: LocationManager? = ContextCompat.getSystemService(context, LocationManager::class.java)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasPreciseLocation(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasGps(): Boolean = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)

    fun isGpsEnabled(): Boolean = runCatching { manager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true }.getOrDefault(false)

    fun availability(): LocationAvailability = when {
        !hasPermission() -> LocationAvailability.PERMISSION_REQUIRED
        manager == null -> LocationAvailability.PROVIDERS_OFF
        !anyProviderEnabled() -> LocationAvailability.PROVIDERS_OFF
        else -> LocationAvailability.READY
    }

    private fun anyProviderEnabled(): Boolean = runCatching {
        manager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
            manager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
    }.getOrDefault(false)

    @SuppressLint("MissingPermission")
    fun lastKnown(): LocationFix? {
        if (!hasPermission() || manager == null) return null
        val candidates = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).mapNotNull { p ->
            runCatching { manager?.getLastKnownLocation(p) }.getOrNull()
        }
        return candidates.maxByOrNull { it.elapsedRealtimeNanos }?.toFix()
    }

    /**
     * Live updates while collected. Emits nothing (and completes) if permission is missing;
     * callers check [availability] to show the right state.
     */
    @SuppressLint("MissingPermission")
    fun updates(intervalMs: Long = 2_000L): Flow<LocationFix> = callbackFlow {
        val lm = manager
        if (!hasPermission() || lm == null) {
            close()
            return@callbackFlow
        }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location.toFix())
            }
            // Explicit overrides: on Android 8–10 these are abstract in the platform interface.
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
            @Deprecated("Deprecated in Android API 29")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        val providers = buildList {
            if (runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)) add(LocationManager.GPS_PROVIDER)
            if (runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)) add(LocationManager.NETWORK_PROVIDER)
        }
        lastKnown()?.let { trySend(it) }
        for (p in providers) {
            try {
                lm.requestLocationUpdates(p, intervalMs, 0f, listener, Looper.getMainLooper())
            } catch (e: SecurityException) {
                close(e)
            } catch (e: IllegalArgumentException) {
                // Provider vanished between the check and the request; ignore it.
            }
        }
        awaitClose { runCatching { lm.removeUpdates(listener) } }
    }

    private fun Location.toFix(): LocationFix = LocationFix(
        coordinate = Coordinate(latitude.coerceIn(-90.0, 90.0), longitude.coerceIn(-180.0, 180.0)),
        accuracyMeters = if (hasAccuracy()) accuracy else null,
        altitudeMeters = if (hasAltitude()) altitude else null,
        mslAltitudeMeters = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && hasMslAltitude()) mslAltitudeMeters else null,
        speedMps = if (hasSpeed()) speed else null,
        provider = provider,
        timeEpochMs = time,
        elapsedRealtimeMs = elapsedRealtimeNanos / 1_000_000,
    )
}
