package com.aqua.aqualight.data.devices.dosing

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingCalibrationDraftKeyTest {

    @Test
    fun `record identity is isolated by owner device and channel`() {
        val ownerOnePrefix = DeviceDosingCalibrationDraftKey.ownerPrefix("owner-1")
        val ownerTwoPrefix = DeviceDosingCalibrationDraftKey.ownerPrefix("owner-2")
        val baseline = DeviceDosingCalibrationDraftKey.record(
            ownerOnePrefix,
            "device-1",
            "channel-1"
        )

        assertTrue(baseline.startsWith(ownerOnePrefix))
        assertNotEquals(
            baseline,
            DeviceDosingCalibrationDraftKey.record(ownerTwoPrefix, "device-1", "channel-1")
        )
        assertNotEquals(
            baseline,
            DeviceDosingCalibrationDraftKey.record(ownerOnePrefix, "device-2", "channel-1")
        )
        assertNotEquals(
            baseline,
            DeviceDosingCalibrationDraftKey.record(ownerOnePrefix, "device-1", "channel-2")
        )
    }
}
