package mw.phoneguard.app.services

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import mw.phoneguard.app.data.storage.TokenStore
import java.util.Calendar

class ScheduledAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i("ScheduledAlertReceiver", "Received action: $action")
        val tokenStore = TokenStore.get(context.applicationContext)
        if (!tokenStore.isScheduledAlertEnabled()) {
            Log.i("ScheduledAlertReceiver", "Scheduled alerts are disabled; ignoring")
            return
        }

        when (action) {
            ScheduledAlertScheduler.ACTION_ARM -> {
                Log.i("ScheduledAlertReceiver", "Scheduled time reached -> Arming Alert Mode")
                tokenStore.saveAlertModeArmed(true)
                PhoneGuardService.setAlertMode(context, true)
                ScheduledAlertScheduler.scheduleAlarms(context)
            }
            ScheduledAlertScheduler.ACTION_DISARM -> {
                Log.i("ScheduledAlertReceiver", "Scheduled time reached -> Disarming Alert Mode")
                tokenStore.saveAlertModeArmed(false)
                PhoneGuardService.setAlertMode(context, false)
                ScheduledAlertScheduler.scheduleAlarms(context)
            }
        }
    }
}

object ScheduledAlertScheduler {
    const val ACTION_ARM = "mw.phoneguard.app.SCHEDULED_ARM"
    const val ACTION_DISARM = "mw.phoneguard.app.SCHEDULED_DISARM"

    fun scheduleAlarms(context: Context) {
        val tokenStore = TokenStore.get(context.applicationContext)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        cancelAlarms(context)

        if (!tokenStore.isScheduledAlertEnabled()) {
            Log.i("ScheduledAlertScheduler", "Scheduled alerts disabled")
            return
        }

        val startHour = tokenStore.getScheduledStartHour()
        val startMinute = tokenStore.getScheduledStartMinute()
        val endHour = tokenStore.getScheduledEndHour()
        val endMinute = tokenStore.getScheduledEndMinute()

        val now = Calendar.getInstance()

        val armCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, startHour)
            set(Calendar.MINUTE, startMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val disarmCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, endHour)
            set(Calendar.MINUTE, endMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val armIntent = Intent(context, ScheduledAlertReceiver::class.java).apply {
            action = ACTION_ARM
        }
        val armPendingIntent = PendingIntent.getBroadcast(
            context, 2001, armIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disarmIntent = Intent(context, ScheduledAlertReceiver::class.java).apply {
            action = ACTION_DISARM
        }
        val disarmPendingIntent = PendingIntent.getBroadcast(
            context, 2002, disarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, armCal.timeInMillis, armPendingIntent)
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, disarmCal.timeInMillis, disarmPendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, armCal.timeInMillis, armPendingIntent)
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, disarmCal.timeInMillis, disarmPendingIntent)
            }
            Log.i("ScheduledAlertScheduler", "Scheduled ARM at ${armCal.time} and DISARM at ${disarmCal.time}")
        } catch (e: Exception) {
            Log.e("ScheduledAlertScheduler", "Failed to schedule alarms: ${e.message}", e)
        }
    }

    fun cancelAlarms(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val armIntent = Intent(context, ScheduledAlertReceiver::class.java).apply {
            action = ACTION_ARM
        }
        val armPi = PendingIntent.getBroadcast(context, 2001, armIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.cancel(armPi)

        val disarmIntent = Intent(context, ScheduledAlertReceiver::class.java).apply {
            action = ACTION_DISARM
        }
        val disarmPi = PendingIntent.getBroadcast(context, 2002, disarmIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.cancel(disarmPi)
        Log.i("ScheduledAlertScheduler", "Cancelled scheduled alert alarms")
    }
}
