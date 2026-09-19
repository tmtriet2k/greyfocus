package com.indiedev2k.greyfocus

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat

/**
 * Tracks the foreground app (and, for browsers, the site in the address bar) and switches the
 * display to greyscale whenever it matches the user's lists.
 */
class FocusAccessibilityService : AccessibilityService() {

    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())
    private val evaluateRunnable = Runnable { evaluate() }

    private var currentPackage: String? = null
    private var lastAddressBarText: String? = null
    private var lastApplied: Boolean? = null
    private var imePackages: Set<String> = emptySet()

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    // Never leave the phone stuck in greyscale on the lock screen.
                    currentPackage = null
                    lastAddressBarText = null
                    setGrey(false)
                    LiveStatus.update(null, null, false)
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> scheduleEvaluate(500)
                Prefs.ACTION_CHANGED -> {
                    prefs = Prefs(this@FocusAccessibilityService)
                    scheduleEvaluate(0)
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = Prefs(this)
        imePackages = loadImePackages()
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Prefs.ACTION_CHANGED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        LiveStatus.serviceRunning = true
        lastApplied = null
        KeepAliveService.start(this)
        scheduleEvaluate(300)
        Log.i(TAG, "connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) Log.d(TAG, "DBG state pkg=$pkg cls=${event.className}")
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Keyboards, the notification shade and permission dialogs float above the real
                // app; ignore them so greyscale does not flicker off while typing.
                if (pkg in IGNORED_PACKAGES || pkg in imePackages) return
                if (pkg != currentPackage) {
                    currentPackage = pkg
                    lastAddressBarText = null
                }
                scheduleEvaluate(if (Browsers.isBrowser(pkg)) 250 else 0)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // In a browser the URL changes without a new window, so re-check (debounced).
                if (pkg == currentPackage && Browsers.isBrowser(pkg)) scheduleEvaluate(300)
            }
        }
    }

    private fun scheduleEvaluate(delayMs: Long) {
        handler.removeCallbacks(evaluateRunnable)
        handler.postDelayed(evaluateRunnable, delayMs)
    }

    private fun evaluate() {
        val pkg = currentPackage
            ?: rootInActiveWindow?.packageName?.toString()?.also { currentPackage = it }
        if (!prefs.enabled || pkg == null) {
            setGrey(false)
            LiveStatus.update(pkg, null, false)
            return
        }

        var site: String? = null
        val shouldBeGrey: Boolean? = when {
            pkg in prefs.blockedPackages -> true
            Browsers.isBrowser(pkg) -> {
                val text = Browsers.extractAddressBarText(rootInActiveWindow, pkg)
                if (text == null) {
                    // Toolbar hidden (scrolling, full-screen video): keep the previous decision.
                    site = lastAddressBarText?.let { Prefs.hostOf(it) }
                    null
                } else {
                    lastAddressBarText = text
                    site = Prefs.hostOf(text)
                    prefs.matchesSite(text)
                }
            }
            else -> false
        }

        Log.d(TAG, "DBG evaluate pkg=$pkg enabled=${prefs.enabled} blocked=${prefs.blockedPackages} sites=${prefs.sites} shouldBeGrey=$shouldBeGrey")
        if (shouldBeGrey != null) setGrey(shouldBeGrey)
        LiveStatus.update(pkg, site, lastApplied ?: false)
    }

    private fun setGrey(grey: Boolean) {
        if (lastApplied == grey) return
        val ok = GreyscaleController.apply(this, grey)
        Log.d(TAG, "DBG setGrey $grey ok=$ok")
        if (ok) lastApplied = grey
    }

    private fun loadImePackages(): Set<String> {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            ?: return emptySet()
        return imm.enabledInputMethodList.map { it.packageName }.toSet()
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(screenReceiver) }
        if (::prefs.isInitialized) GreyscaleController.apply(this, false)
        KeepAliveService.stop(this)
        LiveStatus.serviceRunning = false
        LiveStatus.update(null, null, false)
        lastApplied = null
    }

    companion object {
        private const val TAG = "FocusService"
        private val IGNORED_PACKAGES = setOf(
            "com.android.systemui",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
        )
    }
}
