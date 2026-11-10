package com.deskflow.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

open class SecurePreferences(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "deskflow_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        context.getSharedPreferences("deskflow_fallback_prefs", Context.MODE_PRIVATE)
    }

    open var deviceToken: String?
        get() = prefs.getString(KEY_DEVICE_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_DEVICE_TOKEN, value).apply()

    open var deviceId: String?
        get() = prefs.getString(KEY_DEVICE_ID, null)
        set(value) = prefs.edit().putString(KEY_DEVICE_ID, value).apply()

    open var deviceName: String
        get() = prefs.getString(KEY_DEVICE_NAME, "Android Phone") ?: "Android Phone"
        set(value) = prefs.edit().putString(KEY_DEVICE_NAME, value).apply()

    open var pairedAt: String?
        get() = prefs.getString(KEY_PAIRED_AT, null)
        set(value) = prefs.edit().putString(KEY_PAIRED_AT, value).apply()

    open var lastKnownFocusActive: Boolean
        get() = prefs.getBoolean(KEY_LAST_FOCUS_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_LAST_FOCUS_ACTIVE, value).apply()

    open var scopes: List<String>
        get() = (prefs.getStringSet(KEY_SCOPES, setOf("focus", "control", "spotify"))
            ?: setOf("focus", "control", "spotify")).toList()
        set(value) = prefs.edit().putStringSet(KEY_SCOPES, value.toSet()).apply()

    open fun hasScope(scope: String): Boolean {
        return scopes.contains(scope)
    }

    open var apiUrl: String
        get() {
            val stored = prefs.getString(KEY_API_URL, null)
            if (stored.isNullOrBlank() || stored.contains("focus-api.10th012013.duckdns.org")) {
                prefs.edit().putString(KEY_API_URL, DEFAULT_API_URL).apply()
                return DEFAULT_API_URL
            }
            return stored.trimEnd('/')
        }
        set(value) {
            val normalized = if (value.isBlank() || value.contains("focus-api.10th012013.duckdns.org")) {
                DEFAULT_API_URL
            } else {
                value.trimEnd('/')
            }
            prefs.edit().putString(KEY_API_URL, normalized).apply()
        }

    open var hasCompletedOnboarding: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()

    open var onboardingCompleted: Boolean
        get() = hasCompletedOnboarding
        set(value) {
            hasCompletedOnboarding = value
        }

    // Reactive Flows for UI Settings
    private val _languageFlow = MutableStateFlow(prefs.getString(KEY_LANGUAGE, "vi") ?: "vi")
    val languageFlow: StateFlow<String> = _languageFlow.asStateFlow()

    open var language: String
        get() = _languageFlow.value
        set(value) {
            prefs.edit().putString(KEY_LANGUAGE, value).apply()
            _languageFlow.value = value
        }

    // Navigation Bar Position: "bottom" (default) or "top"
    private val _navBarPositionFlow = MutableStateFlow(prefs.getString(KEY_NAV_BAR_POSITION, "bottom") ?: "bottom")
    val navBarPositionFlow: StateFlow<String> = _navBarPositionFlow.asStateFlow()

    open var navBarPosition: String
        get() = _navBarPositionFlow.value
        set(value) {
            val normalized = if (value == "top") "top" else "bottom"
            prefs.edit().putString(KEY_NAV_BAR_POSITION, normalized).apply()
            _navBarPositionFlow.value = normalized
        }

    // App Theme: "system" (default), "light", "dark", or "scheduled"
    private val _appThemeFlow = MutableStateFlow(normalizeTheme(prefs.getString(KEY_APP_THEME, "system")))
    val appThemeFlow: StateFlow<String> = _appThemeFlow.asStateFlow()

    open var appTheme: String
        get() = _appThemeFlow.value
        set(value) {
            val normalized = normalizeTheme(value)
            prefs.edit().putString(KEY_APP_THEME, normalized).apply()
            _appThemeFlow.value = normalized
        }

    // Scheduled Dark Mode Start Time ("HH:MM", default "20:00")
    private val _darkModeStartFlow = MutableStateFlow(
        normalizeTimeStr(prefs.getString(KEY_DARK_MODE_START, "20:00"), "20:00")
    )
    val darkModeStartFlow: StateFlow<String> = _darkModeStartFlow.asStateFlow()

    open var darkModeStart: String
        get() = _darkModeStartFlow.value
        set(value) {
            val normalized = normalizeTimeStr(value, _darkModeStartFlow.value)
            prefs.edit().putString(KEY_DARK_MODE_START, normalized).apply()
            _darkModeStartFlow.value = normalized
        }

    // Scheduled Dark Mode End Time ("HH:MM", default "06:00")
    private val _darkModeEndFlow = MutableStateFlow(
        normalizeTimeStr(prefs.getString(KEY_DARK_MODE_END, "06:00"), "06:00")
    )
    val darkModeEndFlow: StateFlow<String> = _darkModeEndFlow.asStateFlow()

    open var darkModeEnd: String
        get() = _darkModeEndFlow.value
        set(value) {
            val normalized = normalizeTimeStr(value, _darkModeEndFlow.value)
            prefs.edit().putString(KEY_DARK_MODE_END, normalized).apply()
            _darkModeEndFlow.value = normalized
        }

    // Material You Dynamic Colors (Android 12+)
    private val _dynamicColorFlow = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC_COLOR, false))
    val dynamicColorFlow: StateFlow<Boolean> = _dynamicColorFlow.asStateFlow()

    open var dynamicColor: Boolean
        get() = _dynamicColorFlow.value
        set(value) {
            prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, value).apply()
            _dynamicColorFlow.value = value
        }

    open val isPaired: Boolean
        get() = !deviceToken.isNullOrBlank()

    open fun clearAuth() {
        prefs.edit()
            .remove(KEY_DEVICE_TOKEN)
            .remove(KEY_DEVICE_ID)
            .remove(KEY_PAIRED_AT)
            .remove(KEY_LAST_FOCUS_ACTIVE)
            .remove(KEY_SCOPES)
            .apply()
    }

    companion object {
        private const val KEY_DEVICE_TOKEN = "device_token"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_PAIRED_AT = "paired_at"
        private const val KEY_LAST_FOCUS_ACTIVE = "last_focus_active"
        private const val KEY_SCOPES = "device_scopes"
        private const val KEY_API_URL = "api_url"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_NAV_BAR_POSITION = "nav_bar_position"
        private const val KEY_APP_THEME = "app_theme"
        private const val KEY_DARK_MODE_START = "dark_mode_start"
        private const val KEY_DARK_MODE_END = "dark_mode_end"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"

        const val DEFAULT_API_URL = "https://api.10th012013.duckdns.org"

        fun normalizeTheme(raw: String?): String {
            return when (raw?.trim()?.lowercase()) {
                "light" -> "light"
                "dark" -> "dark"
                "scheduled" -> "scheduled"
                else -> "system"
            }
        }

        fun normalizeTimeStr(raw: String?, fallback: String): String {
            if (raw.isNullOrBlank()) return fallback
            val parts = raw.trim().split(":")
            if (parts.size != 2) return fallback
            val h = parts[0].toIntOrNull() ?: return fallback
            val m = parts[1].toIntOrNull() ?: return fallback
            if (h !in 0..23 || m !in 0..59) return fallback
            return "%02d:%02d".format(h, m)
        }
    }
}
