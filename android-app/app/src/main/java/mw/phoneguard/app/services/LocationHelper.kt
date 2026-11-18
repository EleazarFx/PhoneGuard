package mw.phoneguard.app.services

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.util.Log

object LocationHelper {
    private const val TAG = "LocationHelper"

    @SuppressLint("MissingPermission")
    fun getLastKnownLocation(context: Context): Location? {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return try {
            val isGpsEnabled = try { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) } catch (_: Exception) { false }
            val isNetEnabled = try { locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) } catch (_: Exception) { false }

            val gpsLoc = if (isGpsEnabled) {
                try { locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) } catch (_: Exception) { null }
            } else null

            val netLoc = if (isNetEnabled) {
                try { locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (_: Exception) { null }
            } else null

            val best = when {
                gpsLoc != null && netLoc != null -> if (gpsLoc.accuracy <= netLoc.accuracy) gpsLoc else netLoc
                gpsLoc != null -> gpsLoc
                else -> netLoc
            }

            if (best != null) best else {
                // Synchronous fallback or blocking wait if needed, or return null
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting location: ${e.message}")
            null
        }
    }

    fun formatMapsUrl(location: Location?): String {
        return if (location != null) {
            "https://maps.google.com/?q=${location.latitude},${location.longitude}"
        } else {
            "Location unavailable"
        }
    }
}
