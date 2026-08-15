package com.aqua.aqualight.data.devices.dosing

import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationFailure
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnavailableDeviceDosingOperationsTest {

    @Test
    fun `production dosing boundaries fail closed without runtime data`() = runTest {
        assertNull(UnavailableDeviceDosingCalibrationOperations.observe("device", "pump-1").first())
        assertEquals(
            DeviceDosingCalibrationResult.Rejected(DeviceDosingCalibrationFailure.INTERNAL),
            UnavailableDeviceDosingCalibrationOperations.refresh("device", "pump-1")
        )
        assertNull(
            UnavailableDeviceDosingChannelNavigationOperations.resolve("device", "pump-1")
        )
    }
}
