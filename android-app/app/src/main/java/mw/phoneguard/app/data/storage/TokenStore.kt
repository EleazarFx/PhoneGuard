package mw.phoneguard.app.data.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secure storage for authentication tokens and device identity.
 *
 * Uses EncryptedSharedPreferences, which encrypts both keys and values
 * using a key stored in the Android Keystore. If someone extracts the
 * app's data directory, the tokens cannot be read.
 */
class TokenStore(context: Context) {

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

    fun clearUserSession() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    fun hasUserSession(): Boolean = getUserAccessToken() != null

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_TOKEN = "device_token"
    }
}