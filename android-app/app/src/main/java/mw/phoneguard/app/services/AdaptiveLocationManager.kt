package mw.phoneguard.app.services

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

enum class TrackingMode(
    val intervalMs: Long,
    val priority: Int,
    val description: String
) {
    NORMAL(
        intervalMs = 15 * 60 * 1000L, // 15 minutes
        priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY,
        description = "Normal Mode (15-min low-power check)"
    ),
    SUSPICIOUS(
        intervalMs = 30 * 1000L, // 30 seconds
        priority = Priority.PRIORITY_HIGH_ACCURACY,
        description = "Suspicious Mode (30s high-accuracy GPS)"
    ),
    THEFT(
        intervalMs = 15 * 1000L, // 15 seconds
        priority = Priority.PRIORITY_HIGH_ACCURACY,
        description = "Theft Mode (15s live tracking + evidence)"
    )
}

sealed class LocationCheckResult {
    data class Dispatch(val location: Location, val distanceMovedMeters: Float) : LocationCheckResult()
    data class Stationary(val lastLocation: Location, val distanceMovedMeters: Float, val reason: String) : LocationCheckResult()
    data object PermissionDenied : LocationCheckResult()
    data object LocationUnavailable : LocationCheckResult()
}

object AdaptiveLocationManager {
    private const val TAG = "AdaptiveLocationManager"
    private const val NORMAL_MIN_DISTANCE_METERS = 100.0f
    private const val HIGH_ALERT_MIN_DISTANCE_METERS = 15.0f
    private const val MAX_STATIONARY_SUPPRESS_TIME_MS = 5 * 60 * 1000L // 5 minutes

    private val lastReportedLocation = AtomicReference<Location?>(null)
    private var lastReportedTimeMs = 0L

    fun hasLocationPermissions(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun hasBackgroundLocationPermission(context: Context): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            hasLocationPermissions(context)
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchLocation(
        context: Context,
        mode: TrackingMode = TrackingMode.NORMAL
    ): Location? = withContext(Dispatchers.IO) {
        if (!hasLocationPermissions(context)) {
            Log.w(TAG, "Location permissions missing")
            return@withContext null
        }

        val fusedClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
        val cancellationSource = CancellationTokenSource()

        try {
            val fusedLocation = fusedClient.getCurrentLocation(mode.priority, cancellationSource.token).awaitTask()
            if (fusedLocation != null) {
                Log.d(TAG, "Acquired Fused Location: lat=${fusedLocation.latitude}, lng=${fusedLocation.longitude}, acc=${fusedLocation.accuracy}m (mode=${mode.name})")
                return@withContext fusedLocation
            }
        } catch (e: Exception) {
            Log.w(TAG, "FusedLocationProviderClient error (${e.message}), falling back")
        }

        try {
            val lastFused = fusedClient.lastLocation.awaitTask()
            if (lastFused != null) {
                Log.d(TAG, "Using last known Fused Location: lat=${lastFused.latitude}, lng=${lastFused.longitude}")
                return@withContext lastFused
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fused lastLocation fallback failed: ${e.message}")
        }

        return@withContext fallbackLocationManager(context)
    }

    @SuppressLint("MissingPermission")
    fun fallbackLocationManager(context: Context): Location? {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return try {
            val isGps = try { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) } catch (_: Exception) { false }
            val isNet = try { locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) } catch (_: Exception) { false }

            val gpsLoc = if (isGps) try { locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) } catch (_: Exception) { null } else null
            val netLoc = if (isNet) try { locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (_: Exception) { null } else null

            val best = when {
                gpsLoc != null && netLoc != null -> if (gpsLoc.accuracy <= netLoc.accuracy) gpsLoc else netLoc
                gpsLoc != null -> gpsLoc
                else -> netLoc
            }
            if (best != null) {
                Log.i(TAG, "Using legacy LocationManager location: lat=${best.latitude}, lng=${best.longitude}")
            }
            best
        } catch (e: Exception) {
            Log.e(TAG, "Legacy LocationManager fallback error: ${e.message}")
            null
        }
    }

    private suspend fun <T> Task<T>.awaitTask(): T? = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            if (continuation.isActive) continuation.resume(result)
        }
        addOnFailureListener {
            if (continuation.isActive) continuation.resume(null)
        }
        addOnCanceledListener {
            if (continuation.isActive) continuation.resume(null)
        }
    }

    fun evaluateLocationMovement(
        newLocation: Location?,
        mode: TrackingMode
    ): LocationCheckResult {
        if (newLocation == null) return LocationCheckResult.LocationUnavailable

        val lastLoc = lastReportedLocation.get()
        val now = System.currentTimeMillis()

        if (lastLoc == null) {
            updateLastReported(newLocation)
            return LocationCheckResult.Dispatch(newLocation, 0.0f)
        }

        val distance = lastLoc.distanceTo(newLocation)
        val timeElapsed = now - lastReportedTimeMs

        val minDistanceThreshold = when (mode) {
            TrackingMode.NORMAL -> NORMAL_MIN_DISTANCE_METERS
            TrackingMode.SUSPICIOUS, TrackingMode.THEFT -> HIGH_ALERT_MIN_DISTANCE_METERS
        }

        val isMoved = distance >= minDistanceThreshold
        val isTimeExpired = timeElapsed >= MAX_STATIONARY_SUPPRESS_TIME_MS

        if (isMoved || isTimeExpired || mode == TrackingMode.THEFT) {
            updateLastReported(newLocation)
            return LocationCheckResult.Dispatch(newLocation, distance)
        }

        return LocationCheckResult.Stationary(
            lastLocation = lastLoc,
            distanceMovedMeters = distance,
            reason = "Device stationary (${"%.1f".format(distance)}m movement < ${minDistanceThreshold}m threshold)"
        )
    }

    private fun updateLastReported(location: Location) {
        lastReportedLocation.set(location)
        lastReportedTimeMs = System.currentTimeMillis()
    }

    fun formatMapsUrl(location: Location?): String {
        return if (location != null) {
            "https://maps.google.com/?q=${location.latitude},${location.longitude}"
        } else {
            "Location unavailable"
        }
    }

    fun resetLastReported() {
        lastReportedLocation.set(null)
        lastReportedTimeMs = 0L
    }
}
