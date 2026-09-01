package com.aqua.aqualight.ui.tabs.devices.detail.cooling.root

import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootRoute
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticCommandResult
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticFailure
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticSettingsOperations
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticSettingsSnapshot
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryLoadResult
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryOperations
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryRange
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlCapabilities
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlFailure
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlMode
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlOperations
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlResult
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlSnapshot
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingManualFanCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceCoolingRootViewModelTest {
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
    fun failedModeMutationNeverOptimisticallyChangesAuthoritativeMode() = runTest(dispatcher) {
        val control = FakeControlOperations(
            initial = available(mode = DeviceCoolingControlMode.AUTOMATIC),
            modeMutation = DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.Unsupported)
        )
        val viewModel = createViewModel(control)

        viewModel.bind(DEVICE_UID)
        viewModel.selectMode(DeviceCoolingControlMode.MANUAL)

        val state = viewModel.uiState.value
        assertEquals(DeviceCoolingControlMode.AUTOMATIC, state.selectedMode)
        assertEquals(DeviceCoolingControlMode.MANUAL, control.lastRequestedMode)
        assertEquals(40, state.manualFanPercent)
        assertTrue(state.controlAvailable)
    }

    @Test
    fun successfulModeMutationUsesReturnedAuthoritativeSnapshot() = runTest(dispatcher) {
        val control = FakeControlOperations(
            initial = available(mode = DeviceCoolingControlMode.AUTOMATIC),
            modeMutation = available(
                mode = DeviceCoolingControlMode.MANUAL,
                manualFanPercent = 40
            )
        )
        val viewModel = createViewModel(control)

        viewModel.bind(DEVICE_UID)
        viewModel.selectMode(DeviceCoolingControlMode.MANUAL)

        assertEquals(DeviceCoolingControlMode.MANUAL, viewModel.uiState.value.selectedMode)
        assertEquals(40, viewModel.uiState.value.manualFanPercent)
    }

    @Test
    fun manualFanMutationUsesOnlyReturnedAuthoritativePercent() = runTest(dispatcher) {
        val control = FakeControlOperations(
            initial = available(
                mode = DeviceCoolingControlMode.MANUAL,
                manualFanPercent = 40,
                manualWritable = true
            ),
            manualMutation = available(
                mode = DeviceCoolingControlMode.MANUAL,
                manualFanPercent = 70,
                manualWritable = true
            )
        )
        val viewModel = createViewModel(control)

        viewModel.bind(DEVICE_UID)
        viewModel.updateManualFanPercent(73)

        assertEquals(73, control.lastRequestedManualPercent)
        assertEquals(70, viewModel.uiState.value.manualFanPercent)
    }

    private fun createViewModel(control: DeviceCoolingControlOperations) = DeviceCoolingRootViewModel(
        operations = FakeRootOperations(),
        controlOperations = control,
        historyOperations = UnavailableHistoryOperations,
        automaticSettingsOperations = UnavailableAutomaticOperations
    )

    private class FakeRootOperations : DeviceRootOperations {
        private val snapshot = DeviceRootSnapshot(
            deviceUid = DEVICE_UID,
            title = "Cooling",
            availability = OwnerDeviceAvailability.REACHABLE,
            family = OwnerDeviceFamily.COOLING,
            catalogState = DeviceRootCatalogState.VALID
        )

        override fun observe(deviceUid: String): Flow<DeviceRootSnapshot?> = flowOf(snapshot)
        override fun current(deviceUid: String): DeviceRootSnapshot = snapshot
        override fun connect(deviceUid: String): Result<Unit> = Result.success(Unit)
        override fun authorizeRoute(deviceUid: String, route: DeviceRootRoute): Boolean = true
    }

    private class FakeControlOperations(
        private val initial: DeviceCoolingControlResult,
        private val modeMutation: DeviceCoolingControlResult = initial,
        private val manualMutation: DeviceCoolingControlResult = initial
    ) : DeviceCoolingControlOperations {
        var lastRequestedMode: DeviceCoolingControlMode? = null
            private set
        var lastRequestedManualPercent: Int? = null
            private set

        override fun observeControl(deviceUid: String): Flow<DeviceCoolingControlResult> =
            flowOf(initial)

        override fun currentControl(deviceUid: String): DeviceCoolingControlResult = initial

        override suspend fun refreshControl(deviceUid: String): DeviceCoolingControlResult = initial

        override suspend fun setMode(
            deviceUid: String,
            mode: DeviceCoolingControlMode
        ): DeviceCoolingControlResult {
            lastRequestedMode = mode
            return modeMutation
        }

        override suspend fun setManualFanPercent(
            deviceUid: String,
            percent: Int
        ): DeviceCoolingControlResult {
            lastRequestedManualPercent = percent
            return manualMutation
        }
    }

    private object UnavailableHistoryOperations : DeviceCoolingTemperatureHistoryOperations {
        override suspend fun loadTemperatureHistory(
            deviceUid: String,
            range: DeviceCoolingTemperatureHistoryRange
        ): DeviceCoolingTemperatureHistoryLoadResult =
            DeviceCoolingTemperatureHistoryLoadResult.Unavailable
    }

    private object UnavailableAutomaticOperations : DeviceCoolingAutomaticSettingsOperations {
        private val snapshot = DeviceCoolingAutomaticSettingsSnapshot()

        override fun observeAutomaticSettings(
            deviceUid: String
        ): Flow<DeviceCoolingAutomaticSettingsSnapshot> = flowOf(snapshot)

        override fun currentAutomaticSettings(
            deviceUid: String
        ): DeviceCoolingAutomaticSettingsSnapshot = snapshot

        override suspend fun refreshAutomaticSettings(
            deviceUid: String
        ): DeviceCoolingAutomaticCommandResult = DeviceCoolingAutomaticCommandResult.Failed(
            DeviceCoolingAutomaticFailure.Unavailable
        )

        override suspend fun saveAutomaticTemperatureRange(
            deviceUid: String,
            startTemperatureC: Double,
            maximumSpeedTemperatureC: Double
        ): DeviceCoolingAutomaticCommandResult = DeviceCoolingAutomaticCommandResult.Failed(
            DeviceCoolingAutomaticFailure.Unsupported
        )
    }

    private companion object {
        const val DEVICE_UID = "cooling-device"

        fun available(
            mode: DeviceCoolingControlMode,
            manualFanPercent: Int = 40,
            manualWritable: Boolean = false
        ): DeviceCoolingControlResult = DeviceCoolingControlResult.Available(
            DeviceCoolingControlSnapshot(
                mode = mode,
                manualFanPercent = manualFanPercent,
                actualFanPercent = manualFanPercent,
                tankTemperatureC = 25.0,
                capabilities = DeviceCoolingControlCapabilities(
                    supportedModes = setOf(
                        DeviceCoolingControlMode.AUTOMATIC,
                        DeviceCoolingControlMode.MANUAL
                    ),
                    modeSelectionWritable = true,
                    manualFan = DeviceCoolingManualFanCapabilities(
                        minimumPercent = 0,
                        maximumPercent = 100,
                        stepPercent = 1,
                        writable = manualWritable
                    )
                )
            )
        )
    }
}
