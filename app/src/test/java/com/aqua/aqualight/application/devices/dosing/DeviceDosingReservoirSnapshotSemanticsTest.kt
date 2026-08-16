package com.aqua.aqualight.application.devices.dosing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingReservoirSnapshotSemanticsTest {
    @Test
    fun `application semantic exposes authoritative reservoir attention state`() {
        val normal = DeviceDosingReservoirSnapshot(lowLevelActive = false)
        val attentionRequired = DeviceDosingReservoirSnapshot(lowLevelActive = true)

        assertFalse(normal.requiresLowReservoirAttention)
        assertTrue(attentionRequired.requiresLowReservoirAttention)
    }
}
