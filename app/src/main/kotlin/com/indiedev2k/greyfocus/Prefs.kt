package com.indiedev2k.greyfocus

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences

/** Persistent user settings plus the saved system colour state we restore on exit. */
class Prefs(context: Context) {

    private val appContext = context.applicationContext

    @Suppress("DEPRECATION")
    private val sp: SharedPreferences
        get() = appContext.getSharedPreferences("greyfocus", Context.MODE_MULTI_PROCESS)

    /** Master switch. */
    var enabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, true)
        set(value) {
            sp.edit().putBoolean(KEY_ENABLED, value).commit()
            notifyChanged()
        }

    /** Package names that should always be shown in greyscale. */
    var blockedPackages: Set<String>
        get() = sp.getStringSet(KEY_PACKAGES, emptySet())?.toSet() ?: emptySet()
        set(value) {
            sp.edit().putStringSet(KEY_PACKAGES, value.toSet()).commit()
            notifyChanged()
        }

    /** Normalised domains (no scheme, no www., no path). */
    var sites: List<String>
        get() = (sp.getString(KEY_SITES, "") ?: "")
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        set(value) {
            sp.edit().putString(KEY_SITES, value.joinToString("\n")).commit()
            notifyChanged()
        }

    fun addSite(raw: String): Boolean {
        val site = normalizeSite(raw)
        if (site.isEmpty() || !site.contains('.') || site in sites) return false
        sites = sites + site
        return true
    }

    fun removeSite(site: String) {
        sites = sites - site
    }

    private fun notifyChanged() {
        appContext.sendBroadcast(Intent(ACTION_CHANGED).setPackage(appContext.packageName))
    }

    /** True when the text shown in a browser address bar belongs to one of [sites]. */
    fun matchesSite(addressBarText: String): Boolean {
        val host = hostOf(addressBarText) ?: return false
        return sites.any { site -> host == site || host.endsWith(".$site") }
    }

    // ---- Saved system state so we can undo our own change and nothing else ----

    var weSetGrey: Boolean
        get() = sp.getBoolean(KEY_WE_SET_GREY, false)
        set(value) = sp.edit().putBoolean(KEY_WE_SET_GREY, value).apply()

    var prevDaltonizerEnabled: Int
        get() = sp.getInt(KEY_PREV_ENABLED, 0)
        set(value) = sp.edit().putInt(KEY_PREV_ENABLED, value).apply()

    var prevDaltonizerMode: Int
        get() = sp.getInt(KEY_PREV_MODE, -1)
        set(value) = sp.edit().putInt(KEY_PREV_MODE, value).apply()

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_PACKAGES = "packages"
        private const val KEY_SITES = "sites"
        private const val KEY_WE_SET_GREY = "we_set_grey"
        private const val KEY_PREV_ENABLED = "prev_daltonizer_enabled"
        private const val KEY_PREV_MODE = "prev_daltonizer_mode"
        internal const val ACTION_CHANGED = "com.indiedev2k.greyfocus.PREFS_CHANGED"

        fun normalizeSite(raw: String): String = hostOf(raw) ?: ""

        /**
         * Extracts a host from whatever a browser shows in its address bar:
         * "https://www.youtube.com/watch?v=x" -> "youtube.com", "m.reddit.com" -> "m.reddit.com".
         * Returns null for search queries, empty text or the new-tab page.
         */
        fun hostOf(text: String): String? {
            var t = text.trim().lowercase()
            if (t.isEmpty() || t.any { it.isWhitespace() }) return null
            t = t.removePrefix("https://").removePrefix("http://")
            t = t.substringBefore('/').substringBefore('?').substringBefore('#')
            t = t.substringBefore(':') // strip port
            t = t.removePrefix("www.")
            if (t.isEmpty() || !t.contains('.')) return null
            return t
        }
    }
}
