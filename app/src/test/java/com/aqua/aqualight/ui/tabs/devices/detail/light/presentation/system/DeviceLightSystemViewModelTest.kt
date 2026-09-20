package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.system

import com.aqua.aqualight.application.devices.light.system.DeviceLightFanMode
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemCondition
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemFanSnapshot
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemMutationResult
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemOperations
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemReadResult
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemSettings
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemSnapshot
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemTemperaturePolicy
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.FakeLightDeviceRootOperations
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceLightSystemViewModelTest {

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
    fun `projects live thermal state and preserves a local draft`() {
        val operations = FakeSystemOperations(snapshot())
        val viewModel = DeviceLightSystemViewModel(operations, FakeLightDeviceRootOperations())

        viewModel.bind(DEVICE_UID)
        viewModel.updateMode(DeviceLightFanMode.ON)
        viewModel.updateStartTemperature(36)
        operations.emit(snapshot().copy(temperatureCelsius = 45.1))

        val state = viewModel.uiState.value
        assertEquals(45.1, state.snapshot?.temperatureCelsius ?: 0.0, 0.0)
        assertEquals(DeviceLightFanMode.ON, state.selectedMode)
        assertEquals(36, state.selectedStartTemperatureCelsius)
    }

    @Test
    fun `keeps automatic temperature points in strict order`() {
        val viewModel = DeviceLightSystemViewModel(FakeSystemOperations(snapshot()), FakeLightDeviceRootOperations())
        viewModel.bind(DEVICE_UID)

        viewModel.updateStartTemperature(80)
        assertEquals(49, viewModel.uiState.value.selectedStartTemperatureCelsius)

        viewModel.updateFullSpeedTemperature(1)
        assertEquals(50, viewModel.uiState.value.selectedFullSpeedTemperatureCelsius)
    }

    @Test
    fun `saves fan and protection settings as one screen operation`() = runTest {
        val operations = FakeSystemOperations(snapshot())
        val viewModel = DeviceLightSystemViewModel(operations, FakeLightDeviceRootOperations())
        viewModel.bind(DEVICE_UID)

        viewModel.updateMode(DeviceLightFanMode.OFF)
        viewModel.updateStartTemperature(28)
        viewModel.updateFullSpeedTemperature(52)
        viewModel.updateProtectionThreshold(64)
        viewModel.save()

        assertEquals(
            DeviceLightSystemSettings(
                mode = DeviceLightFanMode.OFF,
                startTemperatureCelsius = 28,
                fullSpeedTemperatureCelsius = 52,
                protectionThresholdCelsius = 64
            ),
            operations.savedSettings.single()
        )
        assertTrue(viewModel.uiState.value.canSave)
    }

    private class FakeSystemOperations(
        initialSnapshot: DeviceLightSystemSnapshot
    ) : DeviceLightSystemOperations {
        private val state = MutableStateFlow<DeviceLightSystemReadResult>(
            DeviceLightSystemReadResult.Available(initialSnapshot)
        )
        val savedSettings = mutableListOf<DeviceLightSystemSettings>()

        override fun observe(deviceUid: String): Flow<DeviceLightSystemReadResult> = state

        override fun current(deviceUid: String): DeviceLightSystemReadResult = state.value

        override suspend fun refresh(deviceUid: String): DeviceLightSystemReadResult = state.value

        override suspend fun save(
            deviceUid: String,
            settings: DeviceLightSystemSettings
        ): DeviceLightSystemMutationResult {
            savedSettings += settings
            val current = (state.value as DeviceLightSystemReadResult.Available).snapshot
            val updated = current.copy(
                mode = settings.mode,
                startTemperatureCelsius = settings.startTemperatureCelsius,
                fullSpeedTemperatureCelsius = settings.fullSpeedTemperatureCelsius,
                protectionThresholdCelsius = settings.protectionThresholdCelsius
            )
            state.value = DeviceLightSystemReadResult.Available(updated)
            return DeviceLightSystemMutationResult.Success(updated)
        }

        fun emit(snapshot: DeviceLightSystemSnapshot) {
            state.value = DeviceLightSystemReadResult.Available(snapshot)
        }
    }

    private companion object {
        const val DEVICE_UID = "device-light-system"

        fun snapshot() = DeviceLightSystemSnapshot(
            deviceUid = DEVICE_UID,
            temperatureCelsius = 42.8,
            condition = DeviceLightSystemCondition.NORMAL,
            sensorHealthy = true,
            fans = listOf(
                DeviceLightSystemFanSnapshot("FAN_1", 35, true),
                DeviceLightSystemFanSnapshot("FAN_2", 35, true)
            ),
            mode = DeviceLightFanMode.AUTOMATIC,
            startTemperatureCelsius = 30,
            fullSpeedTemperatureCelsius = 50,
            startTemperaturePolicy = DeviceLightSystemTemperaturePolicy(0, 80),
            fullSpeedTemperaturePolicy = DeviceLightSystemTemperaturePolicy(1, 90),
            protectionThresholdCelsius = 60,
            protectionThresholdPolicy = DeviceLightSystemTemperaturePolicy(50, 70),
            protectionActive = false,
            firmwareWriteAuthoritative = true
        )
    }
}
