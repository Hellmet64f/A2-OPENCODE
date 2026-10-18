package com.example.model

import android.content.Context

object FocusCalibrationManager {
    private const val PREFS_NAME = "a2_miguel_focus_calibration_prefs"
    private const val KEY_FOCUS_X = "focus_x_ratio"
    private const val KEY_FOCUS_Y = "focus_y_ratio"
    private const val KEY_IS_CALIBRATED = "is_calibrated"

    const val DEFAULT_FOCUS_X = 0.50f
    const val DEFAULT_FOCUS_Y = 0.48f

    fun getCalibration(context: Context): Pair<Float, Float> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val x = prefs.getFloat(KEY_FOCUS_X, DEFAULT_FOCUS_X).coerceIn(0.05f, 0.95f)
        val y = prefs.getFloat(KEY_FOCUS_Y, DEFAULT_FOCUS_Y).coerceIn(0.05f, 0.95f)
        return Pair(x, y)
    }

    fun isCalibrated(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_IS_CALIBRATED, false)
    }

    fun saveCalibration(context: Context, xRatio: Float, yRatio: Float) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat(KEY_FOCUS_X, xRatio.coerceIn(0.05f, 0.95f))
            .putFloat(KEY_FOCUS_Y, yRatio.coerceIn(0.05f, 0.95f))
            .putBoolean(KEY_IS_CALIBRATED, true)
            .apply()
    }

    fun resetCalibration(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat(KEY_FOCUS_X, DEFAULT_FOCUS_X)
            .putFloat(KEY_FOCUS_Y, DEFAULT_FOCUS_Y)
            .putBoolean(KEY_IS_CALIBRATED, false)
            .apply()
    }
}
