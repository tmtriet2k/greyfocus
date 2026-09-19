package com.indiedev2k.greyfocus

/** Interprets Android's secure accessibility settings for one service component. */
internal object AccessibilityServiceState {

    fun isEnabled(
        globalAccessibilityEnabled: Int,
        enabledServices: String?,
        componentNames: Set<String>,
    ): Boolean = globalAccessibilityEnabled == 1 && shouldRunWatchdog(
        enabledServices,
        componentNames,
    )

    /**
     * The component list preserves the user's selection when an OEM kills the service and flips
     * the global flag off. Keep the watchdog alive in that stale state so it can repair the bind.
     */
    fun shouldRunWatchdog(
        enabledServices: String?,
        componentNames: Set<String>,
    ): Boolean = enabledServices
        ?.split(':')
        ?.any { enabled -> componentNames.any { enabled.equals(it, ignoreCase = true) } } == true

    fun shouldRecoverAfterProcessDeath(
        globalAccessibilityEnabled: Int,
        enabledServices: String?,
        componentNames: Set<String>,
        serviceProcessDied: Boolean = false,
    ): Boolean = (globalAccessibilityEnabled == 0 || serviceProcessDied) && shouldRunWatchdog(
        enabledServices,
        componentNames,
    )
}
