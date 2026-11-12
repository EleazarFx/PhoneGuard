package mw.phoneguard.app.data.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TokenStore private constructor(context: Context) {

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                "phoneguard_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.getSharedPreferences("phoneguard_secure_prefs_fallback", Context.MODE_PRIVATE)
        }
    }
    fun saveUserTokens(accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
    }

    fun saveUserEmail(email: String) {
        prefs.edit().putString(KEY_USER_EMAIL, email).apply()
    }

    fun getUserEmail(): String? = prefs.getString(KEY_USER_EMAIL, null)
    fun getUserAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)
    fun getUserRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    fun saveDeviceIdentity(deviceId: String, deviceToken: String) {
        prefs.edit()
            .putString(KEY_DEVICE_ID, deviceId)
            .putString(KEY_DEVICE_TOKEN, deviceToken)
            .apply()
    }

    fun getDeviceId(): String? = prefs.getString(KEY_DEVICE_ID, null)
    fun getDeviceToken(): String? = prefs.getString(KEY_DEVICE_TOKEN, null)

    fun hasDeviceRegistered(): Boolean = getDeviceId() != null && getDeviceToken() != null
    fun hasUserSession(): Boolean = getUserAccessToken() != null

    fun clearUserSession() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_USER_EMAIL)
            .apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    fun saveSimIdentity(
        operatorName: String,
        countryIso: String,
        operatorCode: String = "",
        subscriptionId: String = ""
    ) {
        prefs.edit()
            .putString(KEY_SIM_OPERATOR, operatorName)
            .putString(KEY_SIM_COUNTRY, countryIso)
            .putString(KEY_SIM_OPERATOR_CODE, operatorCode)
            .putString(KEY_SIM_SUBSCRIPTION_ID, subscriptionId)
            .apply()
    }

    fun getSimOperatorName(): String? = prefs.getString(KEY_SIM_OPERATOR, null)
    fun getSimCountryIso(): String? = prefs.getString(KEY_SIM_COUNTRY, null)
    fun getSimOperatorCode(): String? = prefs.getString(KEY_SIM_OPERATOR_CODE, null)
    fun getSimSubscriptionId(): String? = prefs.getString(KEY_SIM_SUBSCRIPTION_ID, null)

    fun saveOriginalSimIdentity(
        operators: Set<String>,
        countries: Set<String>,
        codes: Set<String>,
        subscriptions: Set<String>
    ) {
        prefs.edit()
            .putStringSet(KEY_ORIGINAL_SIM_OPERATORS, operators)
            .putStringSet(KEY_ORIGINAL_SIM_COUNTRIES, countries)
            .putStringSet(KEY_ORIGINAL_SIM_CODES, codes)
            .putStringSet(KEY_ORIGINAL_SIM_SUBSCRIPTIONS, subscriptions)
            .apply()
    }

    fun getOriginalSimOperators(): Set<String>? = prefs.getStringSet(KEY_ORIGINAL_SIM_OPERATORS, null)
    fun getOriginalSimCountries(): Set<String>? = prefs.getStringSet(KEY_ORIGINAL_SIM_COUNTRIES, null)
    fun getOriginalSimCodes(): Set<String>? = prefs.getStringSet(KEY_ORIGINAL_SIM_CODES, null)
    fun getOriginalSimSubscriptions(): Set<String>? = prefs.getStringSet(KEY_ORIGINAL_SIM_SUBSCRIPTIONS, null)

    fun hasOriginalSimIdentity(): Boolean = !getOriginalSimCodes().isNullOrEmpty()

    fun resetOriginalSimIdentity(
        operators: Set<String>,
        countries: Set<String>,
        codes: Set<String>,
        subscriptions: Set<String>
    ) {
        saveOriginalSimIdentity(operators, countries, codes, subscriptions)
        clearLastAlertedSimCodes()
        android.util.Log.i("TokenStore", "Original SIM identity reset to current configuration: codes=$codes")
    }

    fun getLastAlertedSimCodes(): Set<String>? = prefs.getStringSet(KEY_LAST_ALERTED_SIM_CODES, null)
    fun saveLastAlertedSimCodes(codes: Set<String>) {
        prefs.edit().putStringSet(KEY_LAST_ALERTED_SIM_CODES, codes).apply()
    }
    fun clearLastAlertedSimCodes() {
        prefs.edit().remove(KEY_LAST_ALERTED_SIM_CODES).apply()
    }





    fun saveLockTime(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_LOCK_TIME, timestamp).apply()
    }

    fun getLockTime(): Long = prefs.getLong(KEY_LAST_LOCK_TIME, 0L)

    fun clearLockTime() {
        prefs.edit().remove(KEY_LAST_LOCK_TIME).apply()
    }

    fun getTamperCount(): Int = prefs.getInt(KEY_TAMPER_COUNT, 0)
    fun incrementTamperCount(): Int {
        val current = getTamperCount() + 1
        prefs.edit().putInt(KEY_TAMPER_COUNT, current).apply()
        return current
    }
    fun resetTamperCount() {
        prefs.edit().putInt(KEY_TAMPER_COUNT, 0).apply()
    }

    fun getLastCheckInTime(): Long = prefs.getLong(KEY_LAST_CHECK_IN, System.currentTimeMillis())
    fun saveLastCheckInTime(time: Long) {
        prefs.edit().putLong(KEY_LAST_CHECK_IN, time).apply()
    }


    fun saveAlertModeArmed(armed: Boolean) {
        prefs.edit().putBoolean(KEY_ALERT_MODE_ARMED, armed).apply()
    }

    fun isAlertModeArmed(): Boolean = prefs.getBoolean(KEY_ALERT_MODE_ARMED, false)

    fun getTrustedContacts(): List<Pair<String, String>> {
        val set = prefs.getStringSet(KEY_TRUSTED_CONTACTS, emptySet()) ?: emptySet()
        return set.mapNotNull { item ->
            val parts = item.split("|", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }
    }

    fun saveTrustedContacts(contacts: List<Pair<String, String>>) {
        val set = contacts.map { "${it.first}|${it.second}" }.toSet()
        prefs.edit().putStringSet(KEY_TRUSTED_CONTACTS, set).apply()
    }

    fun isVideoEvidenceEnabled(): Boolean = prefs.getBoolean(KEY_VIDEO_EVIDENCE, false)
    fun saveVideoEvidenceEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_VIDEO_EVIDENCE, enabled).apply()

    fun isPeriodicLocationEnabled(): Boolean = prefs.getBoolean(KEY_PERIODIC_LOCATION, true)
    fun savePeriodicLocationEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_PERIODIC_LOCATION, enabled).apply()

    fun isOfflineAutoLockEnabled(): Boolean = prefs.getBoolean(KEY_OFFLINE_AUTOLOCK, false)
    fun saveOfflineAutoLockEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_OFFLINE_AUTOLOCK, enabled).apply()

    fun isRemoteWipeEnabled(): Boolean = prefs.getBoolean(KEY_REMOTE_WIPE, false)
    fun saveRemoteWipeEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_REMOTE_WIPE, enabled).apply()

    fun saveSafeZone(lat: Double, lng: Double, radiusMeters: Float, enabled: Boolean) {
        prefs.edit()
            .putFloat(KEY_SAFE_LAT, lat.toFloat())
            .putFloat(KEY_SAFE_LNG, lng.toFloat())
            .putFloat(KEY_SAFE_RADIUS, radiusMeters)
            .putBoolean(KEY_SAFE_ENABLED, enabled)
            .apply()
    }

    fun isSafeZoneEnabled(): Boolean = prefs.getBoolean(KEY_SAFE_ENABLED, false)
    fun getSafeLat(): Double = prefs.getFloat(KEY_SAFE_LAT, 0f).toDouble()
    fun getSafeLng(): Double = prefs.getFloat(KEY_SAFE_LNG, 0f).toDouble()
    fun getSafeRadius(): Float = prefs.getFloat(KEY_SAFE_RADIUS, 100f)

    fun isEscalationSmsEnabled(): Boolean = prefs.getBoolean(KEY_ESCALATION_SMS, false)
    fun saveEscalationSmsEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_ESCALATION_SMS, enabled).apply()

    fun isSilenceEmailEnabled(): Boolean = prefs.getBoolean(KEY_SILENCE_EMAIL, false)
    fun saveSilenceEmailEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_SILENCE_EMAIL, enabled).apply()

    fun getImei(): String? = prefs.getString(KEY_DEVICE_IMEI, null)
    fun saveImei(imei: String) = prefs.edit().putString(KEY_DEVICE_IMEI, imei).apply()

    fun getAppPin(): String? = prefs.getString(KEY_APP_PIN, null)
    fun saveAppPin(pin: String) = prefs.edit().putString(KEY_APP_PIN, pin).apply()
    fun hasAppPin(): Boolean = !getAppPin().isNullOrEmpty()

    fun getPinFailedAttempts(): Int = prefs.getInt(KEY_PIN_FAILED_ATTEMPTS, 0)
    fun savePinFailedAttempts(attempts: Int) = prefs.edit().putInt(KEY_PIN_FAILED_ATTEMPTS, attempts).apply()
    fun incrementPinFailedAttempts(): Int {
        val current = getPinFailedAttempts() + 1
        savePinFailedAttempts(current)
        return current
    }
    fun resetPinFailedAttempts() {
        prefs.edit()
            .remove(KEY_PIN_FAILED_ATTEMPTS)
            .remove(KEY_PIN_LOCKOUT_UNTIL)
            .remove(KEY_PIN_FIRST_FAILURE_TIME)
            .apply()
    }

    fun getPinLockoutUntil(): Long = prefs.getLong(KEY_PIN_LOCKOUT_UNTIL, 0L)
    fun savePinLockoutUntil(timestamp: Long) = prefs.edit().putLong(KEY_PIN_LOCKOUT_UNTIL, timestamp).apply()

    fun getPinFirstFailureTime(): Long = prefs.getLong(KEY_PIN_FIRST_FAILURE_TIME, 0L)
    fun savePinFirstFailureTime(timestamp: Long) = prefs.edit().putLong(KEY_PIN_FIRST_FAILURE_TIME, timestamp).apply()

    fun isAutoWipeOnPinFailureEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_WIPE_ON_PIN_FAILURE, false)
    fun saveAutoWipeOnPinFailureEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_AUTO_WIPE_ON_PIN_FAILURE, enabled).apply()

    fun getGracePeriodSeconds(): Int = prefs.getInt(KEY_GRACE_PERIOD_SECONDS, 20)
    fun saveGracePeriodSeconds(seconds: Int) = prefs.edit().putInt(KEY_GRACE_PERIOD_SECONDS, seconds).apply()

    fun getMotionSensitivity(): String = prefs.getString(KEY_MOTION_SENSITIVITY, "MEDIUM") ?: "MEDIUM"
    fun saveMotionSensitivity(sensitivity: String) = prefs.edit().putString(KEY_MOTION_SENSITIVITY, sensitivity).apply()

    fun isScheduledAlertEnabled(): Boolean = prefs.getBoolean(KEY_SCHEDULED_ALERT_ENABLED, false)
    fun saveScheduledAlertEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_SCHEDULED_ALERT_ENABLED, enabled).apply()

    fun getScheduledStartHour(): Int = prefs.getInt(KEY_SCHEDULED_START_HOUR, 22)
    fun getScheduledStartMinute(): Int = prefs.getInt(KEY_SCHEDULED_START_MINUTE, 0)
    fun getScheduledEndHour(): Int = prefs.getInt(KEY_SCHEDULED_END_HOUR, 6)
    fun getScheduledEndMinute(): Int = prefs.getInt(KEY_SCHEDULED_END_MINUTE, 0)

    fun saveScheduledAlertTimes(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        prefs.edit()
            .putInt(KEY_SCHEDULED_START_HOUR, startHour)
            .putInt(KEY_SCHEDULED_START_MINUTE, startMinute)
            .putInt(KEY_SCHEDULED_END_HOUR, endHour)
            .putInt(KEY_SCHEDULED_END_MINUTE, endMinute)
            .apply()
    }

    fun getCrashReportConsent(): Boolean = prefs.getBoolean("key_crash_consent", false)
    fun setCrashReportConsent(enabled: Boolean) = prefs.edit().putBoolean("key_crash_consent", enabled).apply()

    companion object {
        @Volatile
        private var INSTANCE: TokenStore? = null

        fun get(context: Context): TokenStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TokenStore(context.applicationContext).also { INSTANCE = it }
            }
        }

        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_TOKEN = "device_token"

        private const val KEY_SIM_OPERATOR = "sim_operator_name"
        private const val KEY_SIM_COUNTRY = "sim_country_iso"
        private const val KEY_SIM_OPERATOR_CODE = "sim_operator_code"
        private const val KEY_SIM_SUBSCRIPTION_ID = "sim_subscription_id"
        private const val KEY_ORIGINAL_SIM_OPERATORS = "original_sim_operators"
        private const val KEY_ORIGINAL_SIM_COUNTRIES = "original_sim_countries"
        private const val KEY_ORIGINAL_SIM_CODES = "original_sim_codes"
        private const val KEY_ORIGINAL_SIM_SUBSCRIPTIONS = "original_sim_subscriptions"
        private const val KEY_LAST_ALERTED_SIM_CODES = "last_alerted_sim_codes"
        private const val KEY_LAST_LOCK_TIME = "last_lock_time"
        private const val KEY_TAMPER_COUNT = "tamper_count"
        private const val KEY_LAST_CHECK_IN = "last_check_in_time"

        private const val KEY_ALERT_MODE_ARMED = "alert_mode_armed"
        private const val KEY_TRUSTED_CONTACTS = "trusted_contacts_set"
        private const val KEY_VIDEO_EVIDENCE = "opt_in_video_evidence"
        private const val KEY_PERIODIC_LOCATION = "periodic_location_enabled"
        private const val KEY_OFFLINE_AUTOLOCK = "opt_in_offline_autolock"
        private const val KEY_REMOTE_WIPE = "opt_in_remote_wipe"
        private const val KEY_SAFE_LAT = "safe_lat"
        private const val KEY_SAFE_LNG = "safe_lng"
        private const val KEY_SAFE_RADIUS = "safe_radius"
        private const val KEY_SAFE_ENABLED = "safe_enabled"

        private const val KEY_ESCALATION_SMS = "opt_in_escalation_sms"
        private const val KEY_SILENCE_EMAIL = "opt_in_silence_email"
        private const val KEY_DEVICE_IMEI = "device_imei"
        private const val KEY_APP_PIN = "app_local_pin"
        private const val KEY_PIN_FAILED_ATTEMPTS = "pin_failed_attempts"
        private const val KEY_PIN_LOCKOUT_UNTIL = "pin_lockout_until"
        private const val KEY_PIN_FIRST_FAILURE_TIME = "pin_first_failure_time"
        private const val KEY_AUTO_WIPE_ON_PIN_FAILURE = "auto_wipe_on_pin_failure"

        private const val KEY_GRACE_PERIOD_SECONDS = "grace_period_seconds"
        private const val KEY_MOTION_SENSITIVITY = "motion_sensitivity"
        private const val KEY_SCHEDULED_ALERT_ENABLED = "scheduled_alert_enabled"
        private const val KEY_SCHEDULED_START_HOUR = "scheduled_start_hour"
        private const val KEY_SCHEDULED_START_MINUTE = "scheduled_start_minute"
        private const val KEY_SCHEDULED_END_HOUR = "scheduled_end_hour"
        private const val KEY_SCHEDULED_END_MINUTE = "scheduled_end_minute"
    }
}