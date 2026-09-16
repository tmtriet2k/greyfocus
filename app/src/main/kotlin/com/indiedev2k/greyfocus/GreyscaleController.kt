package com.indiedev2k.greyfocus

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log

/**
 * Turns the whole display greyscale by enabling Android's built-in colour correction in
 * "monochromacy" mode. This is the same setting as Settings > Accessibility > Colour correction.
 *
 * Writing it needs WRITE_SECURE_SETTINGS, which only adb (or root) can grant:
 *   adb shell pm grant com.indiedev2k.greyfocus android.permission.WRITE_SECURE_SETTINGS
 */
object GreyscaleController {

    private const val TAG = "GreyscaleController"
    private const val KEY_DALTONIZER_ENABLED = "accessibility_display_daltonizer_enabled"
    private const val KEY_DALTONIZER_MODE = "accessibility_display_daltonizer"
    private const val MODE_MONOCHROMACY = 0

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    fun isSystemGrey(context: Context): Boolean {
        val cr = context.contentResolver
        return Settings.Secure.getInt(cr, KEY_DALTONIZER_ENABLED, 0) == 1 &&
            Settings.Secure.getInt(cr, KEY_DALTONIZER_MODE, -1) == MODE_MONOCHROMACY
    }

    /**
     * Applies or removes greyscale. Remembers the user's own colour-correction setting the first
     * time we turn greyscale on, and restores exactly that when we turn it off, so a user who
     * already runs system-wide greyscale is left alone.
     */
    @Synchronized
    fun apply(context: Context, grey: Boolean): Boolean {
        if (!hasPermission(context)) return false
        val prefs = Prefs(context)
        val cr = context.contentResolver
        return try {
            if (grey) {
                if (!prefs.weSetGrey) {
                    prefs.prevDaltonizerEnabled = Settings.Secure.getInt(cr, KEY_DALTONIZER_ENABLED, 0)
                    prefs.prevDaltonizerMode = Settings.Secure.getInt(cr, KEY_DALTONIZER_MODE, -1)
                    prefs.weSetGrey = true
                }
                if (!isSystemGrey(context)) {
                    Settings.Secure.putInt(cr, KEY_DALTONIZER_MODE, MODE_MONOCHROMACY)
                    Settings.Secure.putInt(cr, KEY_DALTONIZER_ENABLED, 1)
                }
            } else if (prefs.weSetGrey) {
                if (prefs.prevDaltonizerMode >= 0) {
                    Settings.Secure.putInt(cr, KEY_DALTONIZER_MODE, prefs.prevDaltonizerMode)
                }
                Settings.Secure.putInt(cr, KEY_DALTONIZER_ENABLED, prefs.prevDaltonizerEnabled)
                prefs.weSetGrey = false
            }
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "WRITE_SECURE_SETTINGS not granted", e)
            false
        }
    }
}
