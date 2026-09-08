@file:Suppress("MagicNumber")

package com.aqua.aqualight.ui.tabs.devices.detail.timer

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
import com.aqua.aqualight.application.devices.timer.DeviceTimerScheduleSnapshot
import com.aqua.aqualight.ui.tabs.devices.detail.timer.program.DeviceTimerProgramLoadState
import com.aqua.aqualight.ui.tabs.devices.detail.timer.program.DeviceTimerProgramViewModel
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceTimerProgramViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `channel refresh exposes the complete firmware program document`() = runTest {
        val operations = ProgramTimerOperations(timerProgramControl())
        val viewModel = DeviceTimerProgramViewModel(operations)

        viewModel.bind(DEVICE_UID, CHANNEL_SLOT_ID)

        assertEquals(DeviceTimerProgramLoadState.CONTENT, viewModel.uiState.value.loadState)
        assertEquals("Filter", viewModel.uiState.value.channelTitle)
        assertEquals("Day cycle", viewModel.uiState.value.schedules.single().name)
        assertTrue(viewModel.uiState.value.editable)
        assertEquals(1, operations.refreshChannelCalls)
    }

    @Test
    fun `overnight program is replaced through the application operation`() = runTest {
        val operations = ProgramTimerOperations(timerProgramControl())
        val viewModel = DeviceTimerProgramViewModel(operations)
        viewModel.bind(DEVICE_UID, CHANNEL_SLOT_ID)

        viewModel.updateStartTime(SCHEDULE_SLOT_ID, 20 * 60)
        viewModel.updateEndTime(SCHEDULE_SLOT_ID, 6 * 60)
        assertTrue(viewModel.uiState.value.schedules.single().spansMidnight)
        assertTrue(viewModel.uiState.value.canSave)

        viewModel.save()

        val saved = operations.replacements.single().single()
        assertEquals(20L * 60L * 60_000L, saved.startTimeMillis)
        assertEquals(6L * 60L * 60_000L, saved.endTimeMillis)
        assertTrue(saved.spansMidnight)
        assertFalse(viewModel.uiState.value.dirty)
    }

    @Test
    fun `firmware utf8 name limit prevents an invalid save`() = runTest {
        val operations = ProgramTimerOperations(timerProgramControl())
        val viewModel = DeviceTimerProgramViewModel(operations)
        viewModel.bind(DEVICE_UID, CHANNEL_SLOT_ID)

        viewModel.updateName(SCHEDULE_SLOT_ID, "🐠".repeat(13))

        assertFalse(viewModel.uiState.value.valid)
        assertFalse(viewModel.uiState.value.canSave)
        viewModel.save()
        assertTrue(operations.replacements.isEmpty())
    }

    private companion object {
        const val DEVICE_UID = "timer-pro-4"
        const val CHANNEL_SLOT_ID = "timer:timer1"
        const val SCHEDULE_SLOT_ID = 1
    }
}

private class ProgramTimerOperations(
    private val result: DeviceTimerControlResult
) : DeviceTimerControlOperations {
    var refreshChannelCalls = 0
    val replacements = mutableListOf<List<DeviceTimerScheduleDraft>>()

    override fun observeControl(deviceUid: String): Flow<DeviceTimerControlResult> = flowOf(result)
    override fun currentControl(deviceUid: String): DeviceTimerControlResult = result
    override suspend fun refreshControl(deviceUid: String): DeviceTimerControlResult = result
    override suspend fun refreshChannel(
        deviceUid: String,
        slotId: String
    ): DeviceTimerControlResult {
        refreshChannelCalls += 1
        return result
    }

    override suspend fun setRegime(
        deviceUid: String,
        slotId: String,
        regime: DeviceTimerChannelRegime
    ): DeviceTimerControlResult = result

    override suspend fun setTemporaryOverride(
        deviceUid: String,
        slotId: String,
        regime: DeviceTimerChannelRegime,
        durationMillis: Long
    ): DeviceTimerControlResult = result

    override suspend fun setDisplayName(
        deviceUid: String,
        slotId: String,
        update: DeviceTimerDisplayNameUpdate
    ): DeviceTimerControlResult = result

    override suspend fun replaceSchedules(
        deviceUid: String,
        slotId: String,
        schedules: List<DeviceTimerScheduleDraft>
    ): DeviceTimerControlResult {
        replacements += schedules
        return result
    }
}

private fun timerProgramControl(): DeviceTimerControlResult = DeviceTimerControlResult.Available(
    DeviceTimerControlSnapshot(
        deviceUid = "timer-pro-4",
        revision = 12L,
        lockLoop = false,
        uptimeMillis = 90_000L,
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
                displayName = "Filter",
                regime = DeviceTimerChannelRegime.AUTO,
                operatingState = DeviceTimerOperatingState.ON,
                scheduleCount = 1,
                activeScheduleSlotId = 1,
                activeScheduleName = "Day cycle",
                nextTransitionType = DeviceTimerNextTransitionType.OFF,
                nextTransitionAtEpochMillis = 1_800_000_000_000L,
                runtimeReason = DeviceTimerRuntimeReason.SCHEDULE_ACTIVE,
                clockReady = true,
                temporaryOverrideActive = false,
                temporaryOverrideRemainingMillis = 0L,
                outputHealth = DeviceTimerOutputHealth.UNVERIFIED,
                physicalFeedbackAvailable = false,
                displayNameEditable = true,
                schedules = listOf(
                    DeviceTimerScheduleSnapshot(
                        index = 0,
                        slotId = 1,
                        enabled = true,
                        name = "Day cycle",
                        weekdays = List(7) { true },
                        startTimeMillis = 8L * 60L * 60_000L,
                        endTimeMillis = 18L * 60L * 60_000L,
                        spansMidnight = false
                    )
                )
            )
        )
    )
)
