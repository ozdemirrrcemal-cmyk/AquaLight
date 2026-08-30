package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationConstraints
import org.junit.Assert.assertEquals
import org.junit.Test

class DosingCalibrationDurationContractTest {
    @Test
    fun `collection illustration matches product calibration run duration`() {
        val productDurationMs = DeviceDosingCalibrationConstraints().calibrationRunDurationMs

        assertEquals(3_000L, productDurationMs)
        assertEquals(productDurationMs, CALIBRATION_RUN_DURATION_MILLIS.toLong())
    }
}
