package mw.phoneguard.app.services

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

object OemAutoStartHelper {

    private const val TAG = "OemAutoStartHelper"

    /**
     * OEM intent packages for popular manufacturers in Africa & global markets
     * (Tecno, Infinix, Samsung, Xiaomi, Huawei, Oppo, Vivo, Nokia/Realme).
     */
    private val OEM_INTENTS = listOf(
        // Xiaomi / Redmi / POCO
        Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),

        // Tecno / Infinix / Transsion Phone Manager
        Intent().setComponent(ComponentName("com.transsion.phonemanager", "com.transsion.phonemanager.settings.AutoStartManagementActivity")),
        Intent().setComponent(ComponentName("com.transsion.phonemanager", "com.transsion.phonemanager.autostart.AutoStartActivity")),

        // Samsung Smart Manager / Battery
        Intent().setComponent(ComponentName("com.samsung.android.lovelock", "com.samsung.android.lovelock.hyper.HyperAutoStartActivity")),
        Intent().setComponent(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity")),

        // Huawei Mobile Manager
        Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
        Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),

        // Oppo / Realme ColorOS
        Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
        Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),

        // Vivo Funtouch OS
        Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
        Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),

        // Asus
        Intent().setComponent(ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"))
    )

    fun getManufacturerName(): String {
        return Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    fun openAutoStartSettings(context: Context): Boolean {
        for (intent in OEM_INTENTS) {
            try {
                if (context.packageManager.resolveActivity(intent, 0) != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    Log.i(TAG, "Opened OEM Auto-Start activity: ${intent.component}")
                    return true
                }
            } catch (e: Exception) {
                Log.d(TAG, "Attempted OEM intent ${intent.component} failed: ${e.message}")
            }
        }

        // Fallback: Open Android App Info Settings page
        return try {
            val appInfoIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(appInfoIntent)
            Log.i(TAG, "Fallback to Application Details Settings")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open App Info Settings: ${e.message}")
            false
        }
    }
}
