package dev.anonymous.transfers_ledger.license

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Centralized license gatekeeper.
 *
 * Persists licensing data in [SharedPreferences] ("license_prefs").
 * Exposes a reactive [isActivated] StateFlow for UI observation.
 */
class LicenseManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: LicenseManager? = null

        fun getInstance(context: Context): LicenseManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: LicenseManager(context.applicationContext).also { INSTANCE = it }
            }

        private const val PREFS_NAME = "license_prefs"
        private const val KEY_IS_ACTIVATED = "is_activated"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isActivated = MutableStateFlow(prefs.getBoolean(KEY_IS_ACTIVATED, false))
    val isActivatedState: StateFlow<Boolean> = _isActivated.asStateFlow()

    val isActivated: Boolean
        get() = prefs.getBoolean(KEY_IS_ACTIVATED, false)

    /**
     * Attempts to activate the app with the given [code].
     * Returns true if successful, false if the code is invalid.
     */
    fun activate(code: String): Boolean {
        if (isReviewCodeValid(code)) {
            setActivated(code)
            return true
        }

        val deviceHash = DeviceIdProvider.getHashedId(context)
        if (!ActivationCodeVerifier.verify(deviceHash, code)) return false

        setActivated(code)
        return true
    }

    private fun setActivated(code: String) {
        prefs.edit {
            putBoolean(KEY_IS_ACTIVATED, true)
            putString("activation_code", code)
        }
        _isActivated.value = true
    }

    private fun isReviewCodeValid(code: String): Boolean {
        try {
            // Obfuscated code: "REVIEW-2026"
            val expectedCode = String(android.util.Base64.decode("UkVWSUVXLTIwMjY=", android.util.Base64.NO_WRAP))
            if (code != expectedCode) return false
            
            val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val expirationDate = format.parse("2026-03-06")
            if (expirationDate != null && System.currentTimeMillis() < expirationDate.time) {
                return true
            }
        } catch (e: Exception) {
            // Ignore
        }
        return false
    }
}
