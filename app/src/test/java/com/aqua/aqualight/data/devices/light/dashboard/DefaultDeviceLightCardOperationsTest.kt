package com.aqua.aqualight.data.devices.light.dashboard

import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightCardState
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightCardUnavailableReason
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightChannelOutputSnapshot
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlFailure
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlMode
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlOperations
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlResult
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlSnapshot
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightHeroSnapshot
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightModeMutationResult
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightPlanReason
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightPlanSnapshot
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceUid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultDeviceLightCardOperationsTest {

    @Test
    fun `complete central Light snapshot wins over concurrent preparation failure`() = runTest {
        val snapshot = snapshot()
        val control = FakeControlOperations(DeviceLightControlResult.Available(snapshot))
        val operations = DefaultDeviceLightCardOperations(
            runtimePort = FakeRuntimePort(
                family = DeviceFamily.LIGHT,
                connectResult = Result.failure(IllegalStateException("offline"))
            ),
            controlOperations = control,
            connectionDispatcher = Dispatchers.Unconfined
        )

        val state = operations.observe(DEVICE_UID).first()

        assertEquals(DeviceLightCardState.Ready(snapshot), state)
    }

    @Test
    fun `family mismatch is reported without inventing Light state`() = runTest {
        val control = FakeControlOperations(
            DeviceLightControlResult.Failed(DeviceLightControlFailure.UNAVAILABLE)
        )
        val operations = DefaultDeviceLightCardOperations(
            runtimePort = FakeRuntimePort(
                family = DeviceFamily.COOLING,
                connectResult = Result.success(Unit)
            ),
            controlOperations = control,
            connectionDispatcher = Dispatchers.Unconfined
        )

        val state = operations.observe(DEVICE_UID).first {
            it is DeviceLightCardState.Unavailable
        }

        assertEquals(
            DeviceLightCardState.Unavailable(
                DeviceLightCardUnavailableReason.DEVICE_FAMILY_MISMATCH
            ),
            state
        )
        assertTrue(control.refreshCount == 0)
    }

    private class FakeRuntimePort(
        private val family: DeviceFamily?,
        private val connectResult: Result<Unit>
    ) : DeviceLightCardRuntimePort {
        override fun currentDeviceFamily(deviceUid: DeviceUid): DeviceFamily? = family
        override fun connectRuntime(deviceUid: DeviceUid): Result<Unit> = connectResult
    }

    private class FakeControlOperations(
        initial: DeviceLightControlResult
    ) : DeviceLightControlOperations {
        private val state = MutableStateFlow(initial)
        var refreshCount = 0

        override fun observeControl(deviceUid: String): Flow<DeviceLightControlResult> = state
        override fun currentControl(deviceUid: String): DeviceLightControlResult = state.value

        override suspend fun refreshControl(deviceUid: String): DeviceLightControlResult {
            refreshCount += 1
            return state.value
        }

        override suspend fun setMode(
            deviceUid: String,
            mode: DeviceLightControlMode
        ): DeviceLightModeMutationResult = error("Not used by card tests")
    }

    private fun snapshot() = DeviceLightControlSnapshot(
        deviceUid = DEVICE_UID,
        productKey = "LIGHT_WRGB_PRO_ELITE",
        physicalChannelCount = 4,
        channelKeys = listOf("red", "green", "blue", "white"),
        channels = listOf(
            DeviceLightChannelOutputSnapshot("red", "Red", 0xFF3B30, 72),
            DeviceLightChannelOutputSnapshot("green", "Green", 0x34C759, 64),
            DeviceLightChannelOutputSnapshot("blue", "Blue", 0x2196F3, 81),
            DeviceLightChannelOutputSnapshot("white", "White", 0xFFFFFF, 45)
        ),
        plan = DeviceLightPlanSnapshot(
            available = true,
            reason = DeviceLightPlanReason.OK,
            nowTimeMs = 43_200_000L,
            channelScale = 1000,
            hasScheduleToday = true,
            points = emptyList()
        ),
        automaticProgramCount = 1,
        customCurvePointCount = 0,
        hero = DeviceLightHeroSnapshot(
            mode = DeviceLightControlMode.AUTOMATIC,
            outputActive = true
        )
    )

    private companion object {
        const val DEVICE_UID = "light-card-test"
    }
}
