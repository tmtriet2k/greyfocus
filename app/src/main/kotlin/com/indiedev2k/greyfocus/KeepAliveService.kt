package com.indiedev2k.greyfocus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Keeps the process hosting [FocusAccessibilityService] alive after the app UI is closed.
 *
 * Some Android variants mark an accessibility service as crashed instead of rebinding it after
 * killing its process. A sticky restart repairs that state only when Android still lists
 * GreyFocus as an enabled accessibility service. A deliberate disable or Force stop is therefore
 * never undone.
 */
class KeepAliveService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var serviceProcessDied = false
    private val recoveryRunnable = Runnable {
        val processDied = serviceProcessDied
        serviceProcessDied = false
        recoverAccessibilityIfStillSelected(processDied)
    }
    private val accessibilityObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            scheduleRecovery(serviceProcessDied = false)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            foregroundType,
        )
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ACCESSIBILITY_ENABLED),
            false,
            accessibilityObserver,
        )
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            false,
            accessibilityObserver,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A normal start may be the first process launch after HyperOS OneKeyClean. In that case
        // Android has left the component selected but has already flipped the global flag off.
        scheduleRecovery(serviceProcessDied = false)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        contentResolver.unregisterContentObserver(accessibilityObserver)
        super.onDestroy()
    }

    private fun scheduleRecovery(serviceProcessDied: Boolean) {
        this.serviceProcessDied = this.serviceProcessDied || serviceProcessDied
        handler.removeCallbacks(recoveryRunnable)
        handler.postDelayed(recoveryRunnable, RECOVERY_DELAY_MS)
    }

    private fun recoverAccessibilityIfStillSelected(serviceProcessDied: Boolean) {
        val component = ComponentName(this, FocusAccessibilityService::class.java)
        val componentNames = setOf(component.flattenToString(), component.flattenToShortString())
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        )
        val globalAccessibilityEnabled = Settings.Secure.getInt(
            contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0,
        )
        val serviceStillSelected = enabledServices
            ?.split(':')
            ?.any { enabled -> componentNames.any { enabled.equals(it, ignoreCase = true) } } == true
        if (!serviceStillSelected) {
            stopSelf()
            return
        }
        if (!AccessibilityServiceState.shouldRecoverAfterProcessDeath(
                globalAccessibilityEnabled,
                enabledServices,
                componentNames,
                serviceProcessDied,
            ) || !GreyscaleController.hasPermission(this)
        ) {
            return
        }

        runCatching {
            // Rewriting the full list is not enough on HyperOS once a service is marked crashed.
            // Toggle it while global accessibility is already off, preserving every listed service.
            Settings.Secure.putString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                null,
            )
            Settings.Secure.putInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
            Settings.Secure.putString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                enabledServices,
            )
            Settings.Secure.putInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            Log.i(TAG, "recovered accessibility service after unexpected process death")
        }.onFailure { error ->
            Log.w(TAG, "could not recover accessibility service", error)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.keep_alive_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.keep_alive_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.keep_alive_title))
            .setContentText(getString(R.string.keep_alive_text))
            .setContentIntent(openApp)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    companion object {
        private const val TAG = "GreyFocusKeepAlive"
        private const val CHANNEL_ID = "greyfocus_monitor"
        private const val NOTIFICATION_ID = 1001
        private const val RECOVERY_DELAY_MS = 750L

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, KeepAliveService::class.java),
                )
            }.onFailure { error ->
                Log.w(TAG, "could not start keep-alive service", error)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, KeepAliveService::class.java))
        }
    }
}
