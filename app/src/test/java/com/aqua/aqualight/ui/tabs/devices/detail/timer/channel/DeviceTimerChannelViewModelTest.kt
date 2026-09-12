@file:Suppress("MagicNumber")

package com.aqua.aqualight.ui.tabs.devices.detail.timer.channel

import com.aqua.aqualight.application.devices.timer.DeviceTimerChannelRegime
import com.aqua.aqualight.application.devices.timer.DeviceTimerChannelSnapshot
import com.aqua.aqualight.application.devices.timer.DeviceTimerControlCapabilities
import com.aqua.aqualight.application.devices.timer.DeviceTimerControlOperations
import com.aqua.aqualight.application.devices.timer.DeviceTimerControlResult
import com.aqua.aqualight.application.devices.timer.DeviceTimerControlSnapshot
import com.aqua.aqualight.application.devices.timer.DeviceTimerDisplayNameUpdate
import com.aqua.aqualight.application.devices.timer.DeviceTimerNextTransitionType
import com.aqua.aqualight.application.devices.timer.DeviceTimerOperatingState
import com.aqua.aqualight.application.devices.timer.DeviceTimerOutputHealth
import com.aqua.aqualight.application.devices.timer.DeviceTimerRuntimeReason
import com.aqua.aqualight.application.devices.timer.DeviceTimerScheduleDraft
import com.aqua.aqualight.ui.tabs.devices.detail.timer.effectiveName
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceTimerChannelViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `effective channel name is never concatenated with a second label`() = runTest {
        val defaultOperations = ChannelTimerOperations(control(displayName = "Channel 1"))
        val defaultViewModel = DeviceTimerChannelViewModel(defaultOperations)
        defaultViewModel.bind(DEVICE_UID, CHANNEL_SLOT_ID)

        assertEquals("Channel 1", defaultViewModel.uiState.value.channel?.effectiveName)

        val renamedOperations = ChannelTimerOperations(control(displayName = "Filtre"))
        val renamedViewModel = DeviceTimerChannelViewModel(renamedOperations)
        renamedViewModel.bind(DEVICE_UID, CHANNEL_SLOT_ID)

        assertEquals("Filtre", renamedViewModel.uiState.value.channel?.effectiveName)
    }

    @Test
    fun `resume sends the existing persistent regime to cancel temporary override`() = runTest {
        val operations = ChannelTimerOperations(
            control(regime = DeviceTimerChannelRegime.AUTO, temporaryOverrideActive = true)
        )
        val viewModel = DeviceTimerChannelViewModel(operations)
        viewModel.bind(DEVICE_UID, CHANNEL_SLOT_ID)

        viewModel.resumePersistentMode()

        assertEquals(
            listOf(DeviceTimerChannelRegime.AUTO),
            operations.regimeCalls
        )
    }

    @Test
    fun `power in program mode uses an override ending at the next transition`() = runTest {
        val operations = ChannelTimerOperations(
            control(
                regime = DeviceTimerChannelRegime.AUTO,
                operatingState = DeviceTimerOperatingState.OFF,
                nextTransitionAtEpochMillis = TEST_NOW_EPOCH_MILLIS + FIVE_MINUTES_MILLIS
            )
        )
        val viewModel = DeviceTimerChannelViewModel(
            operations = operations,
            currentEpochMillis = { TEST_NOW_EPOCH_MILLIS }
        )
        viewModel.bind(DEVICE_UID, CHANNEL_SLOT_ID)

        viewModel.togglePower()

        assertEquals(emptyList<DeviceTimerChannelRegime>(), operations.regimeCalls)
        assertEquals(
            listOf(TemporaryOverrideCall(DeviceTimerChannelRegime.ON, FIVE_MINUTES_MILLIS)),
            operations.temporaryOverrideCalls
        )
        assertEquals(
            DeviceTimerChannelRegime.AUTO,
            viewModel.uiState.value.channel?.regime
        )
    }

    @Test
    fun `power in manual mode persists the opposite state`() = runTest {
        val operations = ChannelTimerOperations(
            control(
                regime = DeviceTimerChannelRegime.OFF,
                operatingState = DeviceTimerOperatingState.OFF
            )
        )
        val viewModel = DeviceTimerChannelViewModel(operations)
        viewModel.bind(DEVICE_UID, CHANNEL_SLOT_ID)

        viewModel.togglePower()

        assertEquals(listOf(DeviceTimerChannelRegime.ON), operations.regimeCalls)
        assertEquals(emptyList<TemporaryOverrideCall>(), operations.temporaryOverrideCalls)
    }

    @Test
    fun `manual work mode preserves the current physical state`() = runTest {
        val operations = ChannelTimerOperations(
            control(
                regime = DeviceTimerChannelRegime.AUTO,
                operatingState = DeviceTimerOperatingState.OFF
            )
        )
        val viewModel = DeviceTimerChannelViewModel(operations)
        viewModel.bind(DEVICE_UID, CHANNEL_SLOT_ID)

        viewModel.setWorkMode(DeviceTimerWorkMode.MANUAL)

        assertEquals(listOf(DeviceTimerChannelRegime.OFF), operations.regimeCalls)
    }

    @Test
    fun `program work mode maps only to firmware auto`() = runTest {
        val operations = ChannelTimerOperations(
            control(regime = DeviceTimerChannelRegime.ON)
        )
        val viewModel = DeviceTimerChannelViewModel(operations)
        viewModel.bind(DEVICE_UID, CHANNEL_SLOT_ID)

        viewModel.setWorkMode(DeviceTimerWorkMode.PROGRAM)

        assertEquals(listOf(DeviceTimerChannelRegime.AUTO), operations.regimeCalls)
    }

    @Test
    fun `reselecting manual mode never changes its persistent on off state`() = runTest {
        val operations = ChannelTimerOperations(
            control(
                regime = DeviceTimerChannelRegime.ON,
                operatingState = DeviceTimerOperatingState.OFF
            )
        )
        val viewModel = DeviceTimerChannelViewModel(operations)
        viewModel.bind(DEVICE_UID, CHANNEL_SLOT_ID)

        viewModel.setWorkMode(DeviceTimerWorkMode.MANUAL)

        assertEquals(emptyList<DeviceTimerChannelRegime>(), operations.regimeCalls)
    }

    @Test
    fun `rename and reset use the single firmware display name field`() = runTest {
        val operations = ChannelTimerOperations(control(displayName = "Channel 1"))
        val viewModel = DeviceTimerChannelViewModel(operations)
        viewModel.bind(DEVICE_UID, CHANNEL_SLOT_ID)

        viewModel.updateDisplayName("Filtre")
        viewModel.updateDisplayName(null)

        assertEquals(
            listOf(
                DeviceTimerDisplayNameUpdate.Value("Filtre"),
                DeviceTimerDisplayNameUpdate.ResetToDefault
            ),
            operations.displayNameCalls
        )
    }

    private companion object {
        const val DEVICE_UID = "timer-pro-4"
        const val CHANNEL_SLOT_ID = "timer:timer1"
        const val TEST_NOW_EPOCH_MILLIS = 1_800_000_000_000L
        const val FIVE_MINUTES_MILLIS = 300_000L
    }
}

private class ChannelTimerOperations(
    result: DeviceTimerControlResult
) : DeviceTimerControlOperations {
    private val results = MutableStateFlow(result)
    val regimeCalls = mutableListOf<DeviceTimerChannelRegime>()
    val displayNameCalls = mutableListOf<DeviceTimerDisplayNameUpdate>()
    val temporaryOverrideCalls = mutableListOf<TemporaryOverrideCall>()

    override fun observeControl(deviceUid: String): Flow<DeviceTimerControlResult> = results
    override fun currentControl(deviceUid: String): DeviceTimerControlResult = results.value
    override suspend fun refreshControl(deviceUid: String): DeviceTimerControlResult = results.value
    override suspend fun refreshChannel(
        deviceUid: String,
        slotId: String
    ): DeviceTimerControlResult = results.value

    override suspend fun setRegime(
        deviceUid: String,
        slotId: String,
        regime: DeviceTimerChannelRegime
    ): DeviceTimerControlResult {
        regimeCalls += regime
        return results.value
    }

    override suspend fun setTemporaryOverride(
        deviceUid: String,
        slotId: String,
        regime: DeviceTimerChannelRegime,
        durationMillis: Long
    ): DeviceTimerControlResult {
        temporaryOverrideCalls += TemporaryOverrideCall(regime, durationMillis)
        return results.value
    }

    override suspend fun setDisplayName(
        deviceUid: String,
        slotId: String,
        update: DeviceTimerDisplayNameUpdate
    ): DeviceTimerControlResult {
        displayNameCalls += update
        return results.value
    }

    override suspend fun replaceSchedules(
        deviceUid: String,
        slotId: String,
        expectedRevision: Long,
        schedules: List<DeviceTimerScheduleDraft>
    ): DeviceTimerControlResult = results.value
}

private fun control(
    displayName: String = "Channel 1",
    regime: DeviceTimerChannelRegime = DeviceTimerChannelRegime.AUTO,
    operatingState: DeviceTimerOperatingState = DeviceTimerOperatingState.OFF,
    temporaryOverrideActive: Boolean = false,
    nextTransitionAtEpochMillis: Long? = null
): DeviceTimerControlResult = DeviceTimerControlResult.Available(
    DeviceTimerControlSnapshot(
        deviceUid = "timer-pro-4",
        revision = 1L,
        lockLoop = false,
        uptimeMillis = 10_000L,
        maxSchedulesPerChannel = 8,
        capabilities = DeviceTimerControlCapabilities(
            readOnly = false,
            supportsConfigApply = true,
            supportsChannelState = true,
            supportsSchedules = true,
            supportsSpansMidnight = true,
            supportsTemporaryOverride = true,
            supportsChannelDisplayName = true
        ),
        channels = listOf(
            DeviceTimerChannelSnapshot(
                slotId = "timer:timer1",
                channelNumber = 1,
                defaultName = "Channel 1",
                displayName = displayName,
                regime = regime,
                operatingState = operatingState,
                scheduleCount = 0,
                activeScheduleSlotId = null,
                activeScheduleName = null,
                nextTransitionType = DeviceTimerNextTransitionType.NONE,
                nextTransitionAtEpochMillis = nextTransitionAtEpochMillis,
                runtimeReason = if (temporaryOverrideActive) {
                    DeviceTimerRuntimeReason.TEMPORARY_OVERRIDE_OFF
                } else {
                    DeviceTimerRuntimeReason.OUTSIDE_SCHEDULE
                },
                clockReady = true,
                temporaryOverrideActive = temporaryOverrideActive,
                temporaryOverrideRemainingMillis = if (temporaryOverrideActive) 1_800_000L else 0L,
                outputHealth = DeviceTimerOutputHealth.UNVERIFIED,
                physicalFeedbackAvailable = false,
                displayNameEditable = true,
                schedules = emptyList()
            )
        )
    )
)

private data class TemporaryOverrideCall(
    val regime: DeviceTimerChannelRegime,
    val durationMillis: Long
)
