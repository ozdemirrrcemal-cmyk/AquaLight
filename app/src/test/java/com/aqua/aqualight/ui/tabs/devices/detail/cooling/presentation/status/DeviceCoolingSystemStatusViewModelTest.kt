package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.status

import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootRoute
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAlarmSeverity
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingFanHealth
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingSensorHealth
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTelemetrySnapshot
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlCapabilities
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlFailure
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlMode
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlOperations
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlResult
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlSnapshot
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingDataFreshness
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingDataState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceCoolingSystemStatusViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `bind exposes central live diagnostics without requesting another owner`() =
        runTest(dispatcher) {
            val root = FakeRootOperations(rootSnapshot())
            val control = FakeControlOperations(availableControl())
            val viewModel = DeviceCoolingSystemStatusViewModel(root, control)

            viewModel.bind(DEVICE_UID)

            assertTrue(viewModel.uiState.value.online)
            assertTrue(viewModel.uiState.value.telemetryAvailable)
            assertEquals(0, viewModel.uiState.value.snapshot?.telemetry?.activeAlarmCount)
            assertEquals(0, root.connectCalls)
            assertEquals(0, control.refreshCalls)
        }

    @Test
    fun `temporary central failure preserves the last diagnostic snapshot as stale`() =
        runTest(dispatcher) {
            val root = FakeRootOperations(rootSnapshot())
            val control = FakeControlOperations(availableControl())
            val viewModel = DeviceCoolingSystemStatusViewModel(root, control)
            viewModel.bind(DEVICE_UID)

            root.emit(null)
            control.emit(DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.Unavailable))

            val state = viewModel.uiState.value
            assertFalse(state.online)
            assertTrue(state.stale)
            assertNotNull(state.snapshot)
            val content = state.dataState as CoolingDataState.Content<
                DeviceCoolingControlSnapshot,
                DeviceCoolingControlFailure
                >
            assertEquals(CoolingDataFreshness.STALE, content.freshness)
            assertEquals(DeviceCoolingControlFailure.Unavailable, content.refreshFailure)
        }

    private class FakeRootOperations(
        initial: DeviceRootSnapshot?
    ) : DeviceRootOperations {
        private val snapshots = MutableStateFlow(initial)
        var connectCalls = 0
            private set

        override fun observe(deviceUid: String): Flow<DeviceRootSnapshot?> = snapshots
        override fun current(deviceUid: String): DeviceRootSnapshot? = snapshots.value

        override fun connect(deviceUid: String): Result<Unit> {
            connectCalls += 1
            return Result.success(Unit)
        }

        override fun authorizeRoute(deviceUid: String, route: DeviceRootRoute): Boolean = true

        fun emit(snapshot: DeviceRootSnapshot?) {
            snapshots.value = snapshot
        }
    }

    private class FakeControlOperations(
        initial: DeviceCoolingControlResult
    ) : DeviceCoolingControlOperations {
        private val results = MutableStateFlow(initial)
        var refreshCalls = 0
            private set

        override fun observeControl(deviceUid: String): Flow<DeviceCoolingControlResult> = results
        override fun currentControl(deviceUid: String): DeviceCoolingControlResult = results.value

        override suspend fun refreshControl(deviceUid: String): DeviceCoolingControlResult {
            refreshCalls += 1
            return results.value
        }

        override suspend fun setMode(
            deviceUid: String,
            mode: DeviceCoolingControlMode
        ): DeviceCoolingControlResult = results.value

        override suspend fun setManualFanPercent(
            deviceUid: String,
            percent: Int
        ): DeviceCoolingControlResult = results.value

        fun emit(result: DeviceCoolingControlResult) {
            results.value = result
        }
    }

    private companion object {
        const val DEVICE_UID = "cooling-device"

        fun rootSnapshot() = DeviceRootSnapshot(
            deviceUid = DEVICE_UID,
            title = "Cooling",
            availability = OwnerDeviceAvailability.REACHABLE,
            family = OwnerDeviceFamily.COOLING,
            catalogState = DeviceRootCatalogState.VALID
        )

        fun availableControl(): DeviceCoolingControlResult = DeviceCoolingControlResult.Available(
            DeviceCoolingControlSnapshot(
                mode = DeviceCoolingControlMode.AUTOMATIC,
                manualFanPercent = 40,
                actualFanPercent = 35.0,
                tankTemperatureC = 25.4,
                capabilities = DeviceCoolingControlCapabilities(
                    supportedModes = setOf(DeviceCoolingControlMode.AUTOMATIC),
                    modeSelectionWritable = true,
                    manualFan = null
                ),
                telemetry = DeviceCoolingTelemetrySnapshot(
                    roomTemperatureC = 24.0,
                    humidityPercent = 50.0,
                    powerWatts = 0.2,
                    estimatedKwhPerDay = 0.0048,
                    fanHealth = DeviceCoolingFanHealth.UNVERIFIED,
                    sensorHealth = DeviceCoolingSensorHealth.OK,
                    alarms = emptyList(),
                    activeAlarmCount = 0,
                    highestAlarmSeverity = DeviceCoolingAlarmSeverity.NONE
                )
            )
        )
    }
}
