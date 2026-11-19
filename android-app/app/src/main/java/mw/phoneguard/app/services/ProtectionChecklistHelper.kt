package mw.phoneguard.app.services

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import mw.phoneguard.app.data.storage.TokenStore

data class ProtectionChecklistState(
    val hasBackgroundLocation: Boolean,
    val hasDeviceAdmin: Boolean,
    val isBatteryOptimizationDisabled: Boolean,
    val isAutoStartConfigured: Boolean
) {
    val isFullyProtected: Boolean
        get() = hasBackgroundLocation && hasDeviceAdmin && isBatteryOptimizationDisabled && isAutoStartConfigured

    val completedStepCount: Int
        get() = (if (hasBackgroundLocation) 1 else 0) +
                (if (hasDeviceAdmin) 1 else 0) +
                (if (isBatteryOptimizationDisabled) 1 else 0) +
                (if (isAutoStartConfigured) 1 else 0)
}

object ProtectionChecklistHelper {

    fun getProtectionState(context: Context): ProtectionChecklistState {
        val hasBgLoc = isBackgroundLocationGranted(context)
        val hasAdmin = isDeviceAdminActive(context)
        val isBatteryDisabled = isIgnoringBatteryOptimizations(context)
        val isAutoStartDone = isAutoStartConfigured(context)

        return ProtectionChecklistState(
            hasBackgroundLocation = hasBgLoc,
            hasDeviceAdmin = hasAdmin,
            isBatteryOptimizationDisabled = isBatteryDisabled,
            isAutoStartConfigured = isAutoStartDone
        )
    }

    fun isBackgroundLocationGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            fine || coarse
        }
    }

    fun isDeviceAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return false
        val adminComponent = ComponentName(context, PhoneGuardAdminReceiver::class.java)
        return dpm.isAdminActive(adminComponent)
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun isAutoStartConfigured(context: Context): Boolean {
        return TokenStore.get(context).isAutoStartAcknowledged()
    }

    fun markAutoStartAcknowledged(context: Context) {
        TokenStore.get(context).setAutoStartAcknowledged(true)
    }

    fun openBatteryOptimizationSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (_: Exception) {}
        }
    }

    fun openDeviceAdminSettings(context: Context) {
        val adminComponent = ComponentName(context, PhoneGuardAdminReceiver::class.java)
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "PhoneGuard needs Device Admin rights to detect wrong PIN attempts and secure your device."
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
