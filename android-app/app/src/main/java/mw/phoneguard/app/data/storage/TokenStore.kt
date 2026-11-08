package mw.phoneguard.app.data.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TokenStore private constructor(context: Context) {

    private val prefs: SharedPreferences by lazy {
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


    fun saveAlertModeArmed(armed: Boolean) {
        prefs.edit().putBoolean(KEY_ALERT_MODE_ARMED, armed).apply()
    }

    fun isAlertModeArmed(): Boolean = prefs.getBoolean(KEY_ALERT_MODE_ARMED, false)






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

        private const val KEY_ALERT_MODE_ARMED = "alert_mode_armed"


    }
}