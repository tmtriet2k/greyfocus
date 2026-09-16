package com.indiedev2k.greyfocus

import android.view.accessibility.AccessibilityNodeInfo

/** Knows which packages are browsers and how to read their address bar. */
object Browsers {

    /** package name -> resource id of the address-bar view. */
    private val urlBarIds: Map<String, String> = mapOf(
        "com.android.chrome" to "com.android.chrome:id/url_bar",
        "com.chrome.beta" to "com.chrome.beta:id/url_bar",
        "com.chrome.dev" to "com.chrome.dev:id/url_bar",
        "com.chrome.canary" to "com.chrome.canary:id/url_bar",
        "org.chromium.chrome" to "org.chromium.chrome:id/url_bar",
        "com.brave.browser" to "com.brave.browser:id/url_bar",
        "com.microsoft.emmx" to "com.microsoft.emmx:id/url_bar",
        "com.vivaldi.browser" to "com.vivaldi.browser:id/url_bar",
        "com.kiwibrowser.browser" to "com.kiwibrowser.browser:id/url_bar",
        "org.mozilla.firefox" to "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
        "org.mozilla.firefox_beta" to "org.mozilla.firefox_beta:id/mozac_browser_toolbar_url_view",
        "org.mozilla.fenix" to "org.mozilla.fenix:id/mozac_browser_toolbar_url_view",
        "org.mozilla.focus" to "org.mozilla.focus:id/mozac_browser_toolbar_url_view",
        "com.sec.android.app.sbrowser" to "com.sec.android.app.sbrowser:id/location_bar_edit_text",
        "com.opera.browser" to "com.opera.browser:id/url_field",
        "com.opera.mini.native" to "com.opera.mini.native:id/url_field",
        "com.duckduckgo.mobile.android" to "com.duckduckgo.mobile.android:id/omnibarTextInput",
    )

    fun isBrowser(packageName: String): Boolean = packageName in urlBarIds

    /**
     * Returns the text currently shown in the browser's address bar, or null if it cannot be
     * found right now (toolbar hidden, page loading, root not yet available).
     */
    fun extractAddressBarText(root: AccessibilityNodeInfo?, packageName: String): String? {
        if (root == null) return null
        val id = urlBarIds[packageName]
        if (id != null) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            for (node in nodes) {
                val text = node.text?.toString()
                if (!text.isNullOrBlank()) return text
            }
        }
        // Fallback for browser builds whose ids differ: any EditText holding a domain.
        return findUrlLikeEditText(root, 0)
    }

    private fun findUrlLikeEditText(node: AccessibilityNodeInfo, depth: Int): String? {
        if (depth > 30) return null
        if (node.className == "android.widget.EditText") {
            val text = node.text?.toString()
            if (text != null && Prefs.hostOf(text) != null) return text
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findUrlLikeEditText(child, depth + 1)
            if (found != null) return found
        }
        return null
    }
}
