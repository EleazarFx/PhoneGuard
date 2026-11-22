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
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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

    fun isLocationEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                locationManager.isLocationEnabled
            } else {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }
        } catch (_: Exception) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchLocation(
        context: Context,
        mode: TrackingMode = TrackingMode.NORMAL
    ): Location? = withContext(Dispatchers.IO) {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasBg = hasBackgroundLocationPermission(context)
        val isLocEnabled = isLocationEnabled(context)

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val isGps = try { locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true } catch (_: Exception) { false }
        val isNet = try { locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true } catch (_: Exception) { false }
        val isPass = try { locationManager?.isProviderEnabled(LocationManager.PASSIVE_PROVIDER) == true } catch (_: Exception) { false }

        Log.d(TAG, "fetchLocation start [mode=${mode.name}]: fine=$hasFine, coarse=$hasCoarse, bg=$hasBg, enabled=$isLocEnabled, providers(gps=$isGps, net=$isNet, pass=$isPass)")

        if (!hasFine && !hasCoarse) {
            Log.w(TAG, "Location permissions missing (FINE/COARSE)")
            return@withContext null
        }

        if (!isLocEnabled) {
            Log.e(TAG, "Location Services (GPS) are DISABLED in System Settings!")
            val cachedLoc = getCachedOrStoredLocation(context)
            if (cachedLoc != null) {
                Log.i(TAG, "Using cached location fallback while system location is disabled: lat=${cachedLoc.latitude}, lng=${cachedLoc.longitude}")
                return@withContext cachedLoc
            }
            return@withContext null
        }

        // 1. FAST CHECK: Immediate cached system location (0ms wait)
        val legacyCached = fallbackLocationManager(context)
        if (legacyCached != null) {
            Log.i(TAG, "Fast check: Acquired legacy cached location: lat=${legacyCached.latitude}, lng=${legacyCached.longitude}, provider=${legacyCached.provider}")
            updateLastReported(legacyCached, context)
            return@withContext legacyCached
        }

        // 2. FusedLocationProviderClient Last Known Location (Fast)
        val fusedClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
        val lastFused = try {
            withTimeoutOrNull(2000L) {
                fusedClient.lastLocation.awaitTask()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fused lastLocation check failed: ${e.message}")
            null
        }

        if (lastFused != null) {
            Log.d(TAG, "Acquired last known Fused Location: lat=${lastFused.latitude}, lng=${lastFused.longitude}")
            updateLastReported(lastFused, context)
            return@withContext lastFused
        }

        // 3. FusedLocationProviderClient Current Location (6s timeout)
        val cancellationSource = CancellationTokenSource()
        val fusedLocation = try {
            withTimeoutOrNull(6000L) {
                fusedClient.getCurrentLocation(mode.priority, cancellationSource.token).awaitTask()
            }
        } catch (e: Exception) {
            Log.w(TAG, "FusedLocationProviderClient getCurrentLocation error (${e.message}), falling back to active providers")
            null
        }

        if (fusedLocation != null) {
            Log.d(TAG, "Acquired Fused Location: lat=${fusedLocation.latitude}, lng=${fusedLocation.longitude}, acc=${fusedLocation.accuracy}m (mode=${mode.name})")
            updateLastReported(fusedLocation, context)
            return@withContext fusedLocation
        }

        // 4. Force Active Fused Fix (6s timeout)
        val activeFusedLoc = forceActiveLocationFix(context, mode)
        if (activeFusedLoc != null) {
            Log.i(TAG, "Acquired active Fused Location fix: lat=${activeFusedLoc.latitude}, lng=${activeFusedLoc.longitude}")
            updateLastReported(activeFusedLoc, context)
            return@withContext activeFusedLoc
        }

        // 5. System LocationManager Multi-Provider Active Update (15s timeout for budget chipsets)
        Log.i(TAG, "Fused location unavailable. Starting 15s multi-provider active update on system LocationManager...")
        val multiProviderLoc = requestMultiProviderActiveLocation(context, timeoutMs = 15000L)
        if (multiProviderLoc != null) {
            Log.i(TAG, "Acquired fresh location from multi-provider active update: lat=${multiProviderLoc.latitude}, lng=${multiProviderLoc.longitude}, provider=${multiProviderLoc.provider}")
            updateLastReported(multiProviderLoc, context)
            return@withContext multiProviderLoc
        }

        // 6. Memory / Persistent Storage Fallback
        val cachedLoc = getCachedOrStoredLocation(context)
        if (cachedLoc != null) {
            Log.i(TAG, "Using cached/persistent fallback location: lat=${cachedLoc.latitude}, lng=${cachedLoc.longitude}")
            updateLastReported(cachedLoc, context)
            return@withContext cachedLoc
        }

        Log.w(TAG, "Adaptive location unavailable: No GPS/Network fix or cached location found after full multi-provider search.")
        return@withContext null
    }

    fun fetchLocationBlocking(context: Context, timeoutMs: Long = 18000L): Location? {
        return try {
            runBlocking {
                withTimeoutOrNull(timeoutMs) {
                    fetchLocation(context)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchLocationBlocking error: ${e.message}", e)
            getCachedOrStoredLocation(context) ?: fallbackLocationManager(context)
        }
    }

    private fun getCachedOrStoredLocation(context: Context): Location? {
        val memoryLoc = lastReportedLocation.get()
        if (memoryLoc != null) return memoryLoc

        val tokenStore = mw.phoneguard.app.data.storage.TokenStore.get(context)
        val storedLat = tokenStore.getLastKnownLat()
        val storedLng = tokenStore.getLastKnownLng()
        if (storedLat != null && storedLng != null) {
            val storedLoc = Location("TokenStore").apply {
                latitude = storedLat
                longitude = storedLng
                accuracy = tokenStore.getLastKnownAccuracy()
            }
            lastReportedLocation.set(storedLoc)
            return storedLoc
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private suspend fun forceActiveLocationFix(context: Context, mode: TrackingMode): Location? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val locationRequest = LocationRequest.Builder(
                mode.priority,
                1000L
            ).setMinUpdateIntervalMillis(500L)
             .setMaxUpdateDelayMillis(2000L)
             .build()

            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            var resumed = false

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc = result.lastLocation
                    if (loc != null && !resumed) {
                        resumed = true
                        handler.removeCallbacksAndMessages(null)
                        try { fusedClient.removeLocationUpdates(this) } catch (_: Exception) {}
                        if (continuation.isActive) continuation.resume(loc)
                    }
                }
            }

            val timeoutRunnable = Runnable {
                if (!resumed) {
                    resumed = true
                    try { fusedClient.removeLocationUpdates(callback) } catch (_: Exception) {}
                    if (continuation.isActive) continuation.resume(null)
                }
            }

            try {
                handler.postDelayed(timeoutRunnable, 6000L)
                fusedClient.requestLocationUpdates(locationRequest, callback, android.os.Looper.getMainLooper())
            } catch (e: Exception) {
                Log.e(TAG, "forceActiveLocationFix error: ${e.message}")
                if (!resumed && continuation.isActive) {
                    resumed = true
                    continuation.resume(null)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun fallbackLocationManager(context: Context): Location? {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return try {
            val isGps = try { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) } catch (_: Exception) { false }
            val isNet = try { locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) } catch (_: Exception) { false }
            val isPassive = try { locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER) } catch (_: Exception) { false }

            val gpsLoc = if (isGps) try { locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) } catch (_: Exception) { null } else null
            val netLoc = if (isNet) try { locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (_: Exception) { null } else null
            val passLoc = if (isPassive) try { locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER) } catch (_: Exception) { null } else null

            val best = listOfNotNull(gpsLoc, netLoc, passLoc).minByOrNull { it.accuracy }
            if (best != null) {
                Log.i(TAG, "Using legacy LocationManager location: lat=${best.latitude}, lng=${best.longitude}, provider=${best.provider}")
                updateLastReported(best, context)
            }
            best
        } catch (e: Exception) {
            Log.e(TAG, "Legacy LocationManager fallback error: ${e.message}")
            null
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestMultiProviderActiveLocation(context: Context, timeoutMs: Long = 15000L): Location? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            if (locationManager == null) {
                if (continuation.isActive) continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            var resumed = false
            val listeners = mutableListOf<android.location.LocationListener>()

            fun cleanup() {
                if (!resumed) {
                    resumed = true
                    handler.removeCallbacksAndMessages(null)
                    for (l in listeners) {
                        try { locationManager.removeUpdates(l) } catch (_: Exception) {}
                    }
                }
            }

            val timeoutRunnable = Runnable {
                if (!resumed) {
                    Log.w(TAG, "Multi-provider active location search timed out after ${timeoutMs}ms")
                    cleanup()
                    if (continuation.isActive) continuation.resume(null)
                }
            }

            val activeProviders = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            ).filter { provider ->
                try { locationManager.isProviderEnabled(provider) } catch (_: Exception) { false }
            }

            if (activeProviders.isEmpty()) {
                Log.w(TAG, "No location providers (GPS/Network/Passive) are enabled!")
                if (continuation.isActive) continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            handler.postDelayed(timeoutRunnable, timeoutMs)

            for (provider in activeProviders) {
                val listener = object : android.location.LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (!resumed) {
                            Log.i(TAG, "Received active location fix from provider '$provider': lat=${location.latitude}, lng=${location.longitude}, acc=${location.accuracy}m")
                            cleanup()
                            if (continuation.isActive) continuation.resume(location)
                        }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(p: String?, status: Int, extras: android.os.Bundle?) {}
                    override fun onProviderEnabled(p: String) {}
                    override fun onProviderDisabled(p: String) {}
                }
                listeners.add(listener)
                try {
                    locationManager.requestLocationUpdates(provider, 1000L, 0f, listener, android.os.Looper.getMainLooper())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to request updates from $provider: ${e.message}")
                }
            }
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
        mode: TrackingMode,
        context: Context? = null
    ): LocationCheckResult {
        if (newLocation == null) return LocationCheckResult.LocationUnavailable

        val lastLoc = lastReportedLocation.get()
        val now = System.currentTimeMillis()

        if (lastLoc == null) {
            updateLastReported(newLocation, context)
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
            updateLastReported(newLocation, context)
            return LocationCheckResult.Dispatch(newLocation, distance)
        }

        return LocationCheckResult.Stationary(
            lastLocation = lastLoc,
            distanceMovedMeters = distance,
            reason = "Device stationary (${"%.1f".format(distance)}m movement < ${minDistanceThreshold}m threshold)"
        )
    }

    private fun updateLastReported(location: Location, context: Context? = null) {
        lastReportedLocation.set(location)
        lastReportedTimeMs = System.currentTimeMillis()
        if (context != null) {
            try {
                mw.phoneguard.app.data.storage.TokenStore.get(context)
                    .saveLastKnownLocation(location.latitude, location.longitude, location.accuracy)
            } catch (_: Exception) {}
        }
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
