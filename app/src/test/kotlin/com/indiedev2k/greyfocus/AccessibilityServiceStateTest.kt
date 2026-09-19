package com.indiedev2k.greyfocus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityServiceStateTest {

    private val componentNames = setOf(
        "com.indiedev2k.greyfocus/com.indiedev2k.greyfocus.FocusAccessibilityService",
        "com.indiedev2k.greyfocus/.FocusAccessibilityService",
    )

    @Test
    fun `stale service entry is off when global accessibility is off`() {
        assertFalse(
            AccessibilityServiceState.isEnabled(
                globalAccessibilityEnabled = 0,
                enabledServices = componentNames.first(),
                componentNames = componentNames,
            )
        )
    }

    @Test
    fun `stale selected service keeps watchdog running so it can recover`() {
        assertTrue(
            AccessibilityServiceState.shouldRunWatchdog(
                enabledServices = componentNames.first(),
                componentNames = componentNames,
            )
        )
    }

    @Test
    fun `deliberately unselected service does not keep watchdog running`() {
        assertFalse(
            AccessibilityServiceState.shouldRunWatchdog(
                enabledServices = "example.other/.Service",
                componentNames = componentNames,
            )
        )
    }

    @Test
    fun `listed service is on when global accessibility is on`() {
        assertTrue(
            AccessibilityServiceState.isEnabled(
                globalAccessibilityEnabled = 1,
                enabledServices = componentNames.first(),
                componentNames = componentNames,
            )
        )
    }

    @Test
    fun `unlisted service is off when global accessibility is on`() {
        assertFalse(
            AccessibilityServiceState.isEnabled(
                globalAccessibilityEnabled = 1,
                enabledServices = "example.other/.Service",
                componentNames = componentNames,
            )
        )
    }

    @Test
    fun `unexpected process restart recovers a still-listed service`() {
        assertTrue(
            AccessibilityServiceState.shouldRecoverAfterProcessDeath(
                globalAccessibilityEnabled = 0,
                enabledServices = componentNames.first(),
                componentNames = componentNames,
            )
        )
    }

    @Test
    fun `unexpected process restart does not enable an unlisted service`() {
        assertFalse(
            AccessibilityServiceState.shouldRecoverAfterProcessDeath(
                globalAccessibilityEnabled = 0,
                enabledServices = "example.other/.Service",
                componentNames = componentNames,
            )
        )
    }

    @Test
    fun `dead service process recovers even before Android updates the global switch`() {
        assertTrue(
            AccessibilityServiceState.shouldRecoverAfterProcessDeath(
                globalAccessibilityEnabled = 1,
                enabledServices = componentNames.first(),
                componentNames = componentNames,
                serviceProcessDied = true,
            )
        )
    }
}
