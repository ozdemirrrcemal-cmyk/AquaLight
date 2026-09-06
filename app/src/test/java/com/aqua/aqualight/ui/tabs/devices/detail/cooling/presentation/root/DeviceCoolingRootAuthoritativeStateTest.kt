package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root

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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceCoolingRootAuthoritativeStateTest {
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
    fun `cold start never manufactures cooling values`() = runTest(dispatcher) {
        val viewModel = DeviceCoolingRootViewModel(
            operations = FakeRootOperations(),
            controlOperations = FakeControlOperations(
                DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.Unavailable)
            ),
            historyOperations = UnavailableHistoryOperations,
            automaticSettingsOperations = FakeAutomaticOperations(
                DeviceCoolingAutomaticSettingsSnapshot()
            ),
            controlSurfacePreparationOperations = PreparedCoolingSurfaceOperations
        )

        viewModel.bind(DEVICE_UID)

        val state = viewModel.uiState.value
        assertNull(state.selectedMode)
        assertNull(state.manualFanPercent)
        assertNull(state.autoStartTemperatureC)
        assertNull(state.autoMaxTemperatureC)
        assertNull(state.fanPercentNow)
        assertNull(state.tankTemperatureC)
        assertFalse(state.controlAvailable)
    }

    @Test
    fun `current authoritative snapshots populate the bound state directly`() = runTest(dispatcher) {
        val viewModel = DeviceCoolingRootViewModel(
            operations = FakeRootOperations(),
            controlOperations = FakeControlOperations(availableControl()),
            historyOperations = UnavailableHistoryOperations,
            automaticSettingsOperations = FakeAutomaticOperations(availableAutomatic()),
            controlSurfacePreparationOperations = PreparedCoolingSurfaceOperations
        )

        viewModel.bind(DEVICE_UID)

        val state = viewModel.uiState.value
        assertEquals(DeviceCoolingControlMode.AUTOMATIC, state.selectedMode)
        assertEquals(40, state.manualFanPercent)
        assertEquals(35.0, state.fanPercentNow ?: 0.0, 0.0)
        assertEquals(25.4, state.tankTemperatureC ?: 0.0, 0.0)
        assertEquals(25.0, state.autoStartTemperatureC ?: 0.0, 0.0)
        assertEquals(27.0, state.autoMaxTemperatureC ?: 0.0, 0.0)
        assertTrue(state.controlAvailable)
    }

    @Test
    fun `transient control failure preserves last authoritative presentation disabled`() =
        runTest(dispatcher) {
            val control = FakeControlOperations(availableControl())
            val viewModel = DeviceCoolingRootViewModel(
                operations = FakeRootOperations(),
                controlOperations = control,
                historyOperations = UnavailableHistoryOperations,
                automaticSettingsOperations = FakeAutomaticOperations(availableAutomatic()),
                controlSurfacePreparationOperations = PreparedCoolingSurfaceOperations
            )
            viewModel.bind(DEVICE_UID)

            control.emit(DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.Unavailable))

            val stale = viewModel.uiState.value
            assertEquals(DeviceCoolingControlMode.AUTOMATIC, stale.selectedMode)
            assertEquals(40, stale.manualFanPercent)
            assertEquals(35.0, stale.fanPercentNow ?: 0.0, 0.0)
            assertEquals(25.4, stale.tankTemperatureC ?: 0.0, 0.0)
            assertFalse(stale.controlAvailable)
            assertFalse(stale.modeSelectionWritable)
            val controlState = stale.controlState as CoolingDataState.Content<
                CoolingControlPresentation,
                DeviceCoolingControlFailure
                >
            assertEquals(CoolingDataFreshness.STALE, controlState.freshness)
        }

    @Test
    fun `partial automatic snapshot cannot replace authoritative temperature pair`() =
        runTest(dispatcher) {
            val automatic = FakeAutomaticOperations(availableAutomatic())
            val viewModel = DeviceCoolingRootViewModel(
                operations = FakeRootOperations(),
                controlOperations = FakeControlOperations(availableControl()),
                historyOperations = UnavailableHistoryOperations,
                automaticSettingsOperations = automatic,
                controlSurfacePreparationOperations = PreparedCoolingSurfaceOperations
            )
            viewModel.bind(DEVICE_UID)

            automatic.emit(
                availableAutomatic().copy(
                    startTemperatureC = 26.0,
                    maximumSpeedTemperatureC = null
                )
            )

            val state = viewModel.uiState.value
            assertEquals(25.0, state.autoStartTemperatureC ?: 0.0, 0.0)
            assertEquals(27.0, state.autoMaxTemperatureC ?: 0.0, 0.0)
            val automaticState = state.automaticSummaryState as CoolingDataState.Content<
                CoolingAutomaticSummaryPresentation,
                DeviceCoolingAutomaticFailure
                >
            assertEquals(CoolingDataFreshness.STALE, automaticState.freshness)
        }

    @Test
    fun `transient root invalidation preserves validated topology values`() = runTest(dispatcher) {
        val root = FakeRootOperations()
        val viewModel = DeviceCoolingRootViewModel(
            operations = root,
            controlOperations = FakeControlOperations(availableControl()),
            historyOperations = UnavailableHistoryOperations,
            automaticSettingsOperations = FakeAutomaticOperations(availableAutomatic()),
            controlSurfacePreparationOperations = PreparedCoolingSurfaceOperations
        )
        viewModel.bind(DEVICE_UID)

        root.emit(null)

        val state = viewModel.uiState.value
        assertEquals(1, state.fanOutputCount)
        assertEquals(1, state.temperatureSensorCount)
        assertFalse(state.contentEnabled)
    }

    private class FakeRootOperations : DeviceRootOperations {
        private val snapshots = MutableStateFlow<DeviceRootSnapshot?>(rootSnapshot())

        override fun observe(deviceUid: String): Flow<DeviceRootSnapshot?> = snapshots
        override fun current(deviceUid: String): DeviceRootSnapshot? = snapshots.value
        override fun connect(deviceUid: String): Result<Unit> = Result.success(Unit)
        override fun authorizeRoute(deviceUid: String, route: DeviceRootRoute): Boolean = true

        fun emit(snapshot: DeviceRootSnapshot?) {
            snapshots.value = snapshot
        }
    }

    private class FakeControlOperations(
        initial: DeviceCoolingControlResult
    ) : DeviceCoolingControlOperations {
        private val results = MutableStateFlow(initial)

        override fun observeControl(deviceUid: String): Flow<DeviceCoolingControlResult> = results
        override fun currentControl(deviceUid: String): DeviceCoolingControlResult = results.value
        override suspend fun refreshControl(deviceUid: String): DeviceCoolingControlResult = results.value
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

    private object UnavailableHistoryOperations : DeviceCoolingTemperatureHistoryOperations {
        override suspend fun loadTemperatureHistory(
            deviceUid: String,
            range: DeviceCoolingTemperatureHistoryRange
        ): DeviceCoolingTemperatureHistoryLoadResult =
            DeviceCoolingTemperatureHistoryLoadResult.Unavailable
    }

    private class FakeAutomaticOperations(
        initial: DeviceCoolingAutomaticSettingsSnapshot
    ) : DeviceCoolingAutomaticSettingsOperations {
        private val snapshots = MutableStateFlow(initial)

        override fun observeAutomaticSettings(
            deviceUid: String
        ): Flow<DeviceCoolingAutomaticSettingsSnapshot> = snapshots

        override fun currentAutomaticSettings(
            deviceUid: String
        ): DeviceCoolingAutomaticSettingsSnapshot = snapshots.value

        override suspend fun refreshAutomaticSettings(
            deviceUid: String
        ): DeviceCoolingAutomaticCommandResult = DeviceCoolingAutomaticCommandResult.Success

        override suspend fun saveAutomaticTemperatureRange(
            deviceUid: String,
            startTemperatureC: Double,
            maximumSpeedTemperatureC: Double
        ): DeviceCoolingAutomaticCommandResult = DeviceCoolingAutomaticCommandResult.Success

        fun emit(snapshot: DeviceCoolingAutomaticSettingsSnapshot) {
            snapshots.value = snapshot
        }
    }

    private companion object {
        const val DEVICE_UID = "cooling-device"

        fun rootSnapshot() = DeviceRootSnapshot(
            deviceUid = DEVICE_UID,
            title = "Cooling",
            availability = OwnerDeviceAvailability.REACHABLE,
            family = OwnerDeviceFamily.COOLING,
            catalogState = DeviceRootCatalogState.VALID,
            fanOutputCount = 1,
            temperatureSensorCount = 1
        )

        fun availableControl(): DeviceCoolingControlResult = DeviceCoolingControlResult.Available(
            DeviceCoolingControlSnapshot(
                mode = DeviceCoolingControlMode.AUTOMATIC,
                manualFanPercent = 40,
                actualFanPercent = 35.0,
                tankTemperatureC = 25.4,
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
                        writable = true
                    )
                )
            )
        )

        fun availableAutomatic() = DeviceCoolingAutomaticSettingsSnapshot(
            available = true,
            loaded = true,
            editable = true,
            startTemperatureC = 25.0,
            maximumSpeedTemperatureC = 27.0,
            policy = com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticTemperaturePolicy(
                startMinimumC = 18.0,
                startMaximumC = 30.0,
                maximumSpeedMinimumC = 18.5,
                maximumSpeedMaximumC = 32.0,
                stepC = 0.5,
                minimumGapC = 0.5,
                hysteresisC = 0.5
            )
        )
    }
}
