package com.aqua.aqualight.data.devices.cooling.control

import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlCapabilities
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlFailure
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlMode
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlOperations
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlResult
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class RefreshingDeviceCoolingControlOperationsTest {

    @Test
    fun `first preparation observation actively refreshes Cooling authority`() = runTest {
        val refreshed = DeviceCoolingControlResult.Available(
            DeviceCoolingControlSnapshot(
                mode = DeviceCoolingControlMode.AUTOMATIC,
                manualFanPercent = null,
                actualFanPercent = null,
                tankTemperatureC = null,
                capabilities = DeviceCoolingControlCapabilities(
                    supportedModes = setOf(DeviceCoolingControlMode.AUTOMATIC),
                    modeSelectionWritable = true,
                    manualFan = null
                )
            )
        )
        val delegate = FakeCoolingControlOperations(refreshed)
        val operations = RefreshingDeviceCoolingControlOperations(delegate)

        val result = operations.observeControl(DEVICE_UID)
            .filterIsInstance<DeviceCoolingControlResult.Available>()
            .first()

        assertSame(refreshed, result)
        assertEquals(1, delegate.refreshCalls)
        assertEquals(0, delegate.observeCalls)
    }

    private class FakeCoolingControlOperations(
        private val refreshed: DeviceCoolingControlResult
    ) : DeviceCoolingControlOperations {
        var refreshCalls = 0
        var observeCalls = 0

        override fun observeControl(deviceUid: String): Flow<DeviceCoolingControlResult> {
            observeCalls += 1
            return MutableStateFlow(
                DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.Unavailable)
            )
        }

        override fun currentControl(deviceUid: String): DeviceCoolingControlResult = refreshed

        override suspend fun refreshControl(deviceUid: String): DeviceCoolingControlResult {
            refreshCalls += 1
            return refreshed
        }

        override suspend fun setMode(
            deviceUid: String,
            mode: DeviceCoolingControlMode
        ): DeviceCoolingControlResult = refreshed

        override suspend fun setManualFanPercent(
            deviceUid: String,
            percent: Int
        ): DeviceCoolingControlResult = refreshed
    }

    private companion object {
        const val DEVICE_UID = "cool-pro-1f"
    }
}
