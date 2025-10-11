package com.ambrxsh.torchly

import android.content.Context
import androidx.core.content.edit

class SettingsManager(context: Context) {
    private val prefs = context.getSharedPreferences("torch_settings", Context.MODE_PRIVATE)

    fun saveShakeSensitivity(value: Float) {
        prefs.edit { putFloat("shake_sensitivity", value) }
    }

    fun loadShakeSensitivity(): Float = prefs.getFloat("shake_sensitivity", 0.5f)

    fun saveVibrationEnabled(value: Boolean) {
        prefs.edit { putBoolean("vibration_enabled", value) }
    }

    fun loadVibrationEnabled(): Boolean = prefs.getBoolean("vibration_enabled", true)

    fun saveAutoOffTimer(value: String) {
        prefs.edit { putString("auto_off_timer", value) }
    }

    fun loadAutoOffTimer(): String = prefs.getString("auto_off_timer", "5 min") ?: "5 min"

    fun saveShakeToFlash(value: Boolean) {
        prefs.edit { putBoolean("shake_to_flash", value) }
    }

    fun loadShakeToFlash(): Boolean = prefs.getBoolean("shake_to_flash", false)
}
