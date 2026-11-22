package mw.phoneguard.app.services

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location

object LocationHelper {
    @SuppressLint("MissingPermission")
    fun getLastKnownLocation(context: Context): Location? {
        return AdaptiveLocationManager.fetchLocationBlocking(context)
            ?: AdaptiveLocationManager.fallbackLocationManager(context)
    }

    fun formatMapsUrl(location: Location?): String {
        return AdaptiveLocationManager.formatMapsUrl(location)
    }
}
