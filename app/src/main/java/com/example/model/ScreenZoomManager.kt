package com.example.model

import android.content.Context

object ScreenZoomManager {
    private const val PREFS_NAME = "a2_miguel_screen_zoom_prefs"
    private const val KEY_ZOOM_SCALE = "screen_zoom_scale"
    private const val KEY_PAN_X = "screen_pan_x"
    private const val KEY_PAN_Y = "screen_pan_y"
    private const val KEY_PRESET = "screen_zoom_preset"

    const val PRESET_NORMAL = "NORMAL"
    const val PRESET_FIT = "FIT"
    const val PRESET_CINEMA = "CINEMA"
    const val PRESET_FULL = "FULL"
    const val PRESET_CUSTOM = "CUSTOM"

    data class ZoomConfig(
        val scale: Float,
        val panX: Float,
        val panY: Float,
        val preset: String
    )

    fun getZoomConfig(context: Context): ZoomConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val scale = prefs.getFloat(KEY_ZOOM_SCALE, 1.0f).coerceIn(1.0f, 2.5f)
        val panX = prefs.getFloat(KEY_PAN_X, 0f)
        val panY = prefs.getFloat(KEY_PAN_Y, 0f)
        val preset = prefs.getString(KEY_PRESET, PRESET_NORMAL) ?: PRESET_NORMAL
        return ZoomConfig(scale, panX, panY, preset)
    }

    fun saveZoomConfig(context: Context, scale: Float, panX: Float, panY: Float, preset: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat(KEY_ZOOM_SCALE, scale.coerceIn(1.0f, 2.5f))
            .putFloat(KEY_PAN_X, panX)
            .putFloat(KEY_PAN_Y, panY)
            .putString(KEY_PRESET, preset)
            .apply()
    }

    fun resetZoom(context: Context): ZoomConfig {
        val default = ZoomConfig(1.0f, 0f, 0f, PRESET_NORMAL)
        saveZoomConfig(context, default.scale, default.panX, default.panY, default.preset)
        return default
    }

    fun getPresetLabel(preset: String, scale: Float): String {
        return when (preset) {
            PRESET_NORMAL -> "Normal (100%)"
            PRESET_FIT -> "Ajustar (115%)"
            PRESET_CINEMA -> "Cinema (130%)"
            PRESET_FULL -> "Tela Cheia (150%)"
            else -> "Personalizado (${(scale * 100).toInt()}%)"
        }
    }

    fun cycleNextPreset(context: Context, current: ZoomConfig): ZoomConfig {
        val next = when (current.preset) {
            PRESET_NORMAL -> ZoomConfig(1.15f, 0f, 0f, PRESET_FIT)
            PRESET_FIT -> ZoomConfig(1.30f, 0f, 0f, PRESET_CINEMA)
            PRESET_CINEMA -> ZoomConfig(1.50f, 0f, 0f, PRESET_FULL)
            PRESET_FULL -> ZoomConfig(1.00f, 0f, 0f, PRESET_NORMAL)
            else -> ZoomConfig(1.00f, 0f, 0f, PRESET_NORMAL)
        }
        saveZoomConfig(context, next.scale, next.panX, next.panY, next.preset)
        return next
    }
}
