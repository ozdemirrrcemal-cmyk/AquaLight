package com.aqua.aqualight.ui.tabs.devices.detail.timer.presentation.program

import com.aqua.aqualight.application.devices.timer.control.DeviceTimerChannelRegime
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerChannelIdentity
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerChannelRuntime
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerChannelSnapshot
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerChannelState
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerChannelTransition
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerControlAuthority
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerControlCapabilities
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerControlOperations
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerControlResult
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerControlSnapshot
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerDisplayNameUpdate
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerMutationCapabilities
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerNextTransitionType
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerOperatingState
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerOutputHealth
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerRuntimeReason
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerScheduleDraft
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerScheduleIdentity
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerScheduleSnapshot
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerScheduleWindow
import com.aqua.aqualight.ui.tabs.devices.detail.timer.presentation.program.DeviceTimerProgramLoadState
import com.aqua.aqualight.ui.tabs.devices.detail.timer.presentation.program.DeviceTimerProgramDraft
import com.aqua.aqualight.ui.tabs.devices.detail.timer.presentation.program.DeviceTimerProgramValidationIssue
import com.aqua.aqualight.ui.tabs.devices.detail.timer.presentation.program.DeviceTimerProgramViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.timer.presentation.program.timerProgramValidationIssue
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
        assertEquals(TEST_REVISION, viewModel.uiState.value.expectedRevision)
        assertTrue(viewModel.uiState.value.editable)
        assertEquals(1, operations.refreshChannelCalls)
    }

    @Test
    fun `overnight program is replaced through the application operation`() = runTest {
        val operations = ProgramTimerOperations(timerProgramControl())
        val viewModel = DeviceTimerProgramViewModel(operations)
        viewModel.bind(DEVICE_UID, CHANNEL_SLOT_ID)

        viewModel.edit(
            DeviceTimerProgramEdit.UpdateStartTime(
                SCHEDULE_SLOT_ID,
                minutesOfDay(OVERNIGHT_START_HOUR)
            )
        )
        viewModel.edit(
            DeviceTimerProgramEdit.UpdateEndTime(
                SCHEDULE_SLOT_ID,
                minutesOfDay(OVERNIGHT_END_HOUR)
            )
        )
        assertTrue(viewModel.uiState.value.schedules.single().spansMidnight)
        assertTrue(viewModel.uiState.value.canSave)

        viewModel.save()

        val saved = operations.replacements.single().single()
        assertEquals(OVERNIGHT_START_MILLIS, saved.startTimeMillis)
        assertEquals(OVERNIGHT_END_MILLIS, saved.endTimeMillis)
        assertTrue(saved.spansMidnight)
        assertEquals(TEST_REVISION, operations.replacementRevisions.single())
        assertFalse(viewModel.uiState.value.dirty)
    }

    @Test
    fun `firmware utf8 name limit prevents an invalid save`() = runTest {
        val operations = ProgramTimerOperations(timerProgramControl())
        val viewModel = DeviceTimerProgramViewModel(operations)
        viewModel.bind(DEVICE_UID, CHANNEL_SLOT_ID)

        viewModel.edit(
            DeviceTimerProgramEdit.UpdateName(
                SCHEDULE_SLOT_ID,
                "🐠".repeat(OVERSIZED_NAME_CHARACTER_COUNT)
            )
        )

        assertFalse(viewModel.uiState.value.valid)
        assertFalse(viewModel.uiState.value.canSave)
        viewModel.save()
        assertTrue(operations.replacements.isEmpty())
    }

    @Test
    fun `overlapping active programs are rejected before firmware mutation`() {
        val issue = listOf(
            programDraft(
                slotId = 1,
                start = minutesOfDay(MORNING_START_HOUR),
                end = minutesOfDay(NOON_HOUR)
            ),
            programDraft(
                slotId = 2,
                start = minutesOfDay(BEFORE_NOON_HOUR),
                end = minutesOfDay(AFTERNOON_END_HOUR)
            )
        ).timerProgramValidationIssue(
            maxSchedules = TEST_MAX_SCHEDULES,
            spansMidnightSupported = true
        )

        assertEquals(DeviceTimerProgramValidationIssue.OVERLAPPING_PROGRAMS, issue)
    }

    @Test
    fun `sunday overnight overlap with monday is rejected across week boundary`() {
        val sundayOnly = List(TEST_WEEKDAY_COUNT) { index -> index == SUNDAY_INDEX }
        val mondayOnly = List(TEST_WEEKDAY_COUNT) { index -> index == 0 }
        val issue = listOf(
            programDraft(
                slotId = 1,
                start = minutesOfDay(DAY_END_HOUR),
                end = MINUTES_PER_HOUR,
                weekdays = sundayOnly
            ),
            programDraft(
                slotId = 2,
                start = HALF_HOUR_MINUTES,
                end = NINETY_MINUTES,
                weekdays = mondayOnly
            )
        ).timerProgramValidationIssue(
            maxSchedules = TEST_MAX_SCHEDULES,
            spansMidnightSupported = true
        )

        assertEquals(DeviceTimerProgramValidationIssue.OVERLAPPING_PROGRAMS, issue)
    }

    @Test
    fun `programs touching at one boundary remain valid`() {
        val issue = listOf(
            programDraft(
                slotId = 1,
                start = minutesOfDay(MORNING_START_HOUR),
                end = minutesOfDay(NOON_HOUR)
            ),
            programDraft(
                slotId = 2,
                start = minutesOfDay(NOON_HOUR),
                end = minutesOfDay(AFTERNOON_END_HOUR)
            )
        ).timerProgramValidationIssue(
            maxSchedules = TEST_MAX_SCHEDULES,
            spansMidnightSupported = true
        )

        assertEquals(null, issue)
    }

    @Test
    fun `overlapping disabled programs are rejected like firmware`() {
        val issue = listOf(
            programDraft(
                slotId = 1,
                start = minutesOfDay(MORNING_START_HOUR),
                end = minutesOfDay(NOON_HOUR),
                enabled = false
            ),
            programDraft(
                slotId = 2,
                start = minutesOfDay(BEFORE_NOON_HOUR),
                end = minutesOfDay(AFTERNOON_END_HOUR),
                enabled = false
            )
        ).timerProgramValidationIssue(
            maxSchedules = TEST_MAX_SCHEDULES,
            spansMidnightSupported = true
        )

        assertEquals(DeviceTimerProgramValidationIssue.OVERLAPPING_PROGRAMS, issue)
    }

    private fun programDraft(
        slotId: Int,
        start: Int,
        end: Int,
        enabled: Boolean = true,
        weekdays: List<Boolean> = listOf(true, false, false, false, false, false, false)
    ) = DeviceTimerProgramDraft(
        slotId = slotId,
        enabled = enabled,
        name = "Program $slotId",
        weekdays = weekdays,
        startMinutesOfDay = start,
        endMinutesOfDay = end
    )

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
    val replacementRevisions = mutableListOf<Long>()

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
        expectedRevision: Long,
        schedules: List<DeviceTimerScheduleDraft>
    ): DeviceTimerControlResult {
        replacementRevisions += expectedRevision
        replacements += schedules
        return result
    }
}

private fun timerProgramControl(): DeviceTimerControlResult = DeviceTimerControlResult.Available(
    DeviceTimerControlSnapshot(
        deviceUid = "timer-pro-4",
        authority = DeviceTimerControlAuthority(
            revision = TEST_REVISION,
            lockLoop = false,
            uptimeMillis = TEST_UPTIME_MILLIS,
            maxSchedulesPerChannel = TEST_MAX_SCHEDULES
        ),
        capabilities = DeviceTimerControlCapabilities(
            readOnly = false,
            mutations = DeviceTimerMutationCapabilities(
                supportsConfigApply = true,
                supportsChannelState = true,
                supportsSchedules = true,
                supportsSpansMidnight = true,
                supportsTemporaryOverride = true,
                supportsChannelDisplayName = true
            )
        ),
        channels = listOf(
            DeviceTimerChannelSnapshot(
                identity = DeviceTimerChannelIdentity(
                    slotId = "timer:timer1",
                    channelNumber = 1,
                    defaultName = "Channel 1",
                    displayName = "Filter",
                    displayNameEditable = true
                ),
                state = DeviceTimerChannelState(
                    regime = DeviceTimerChannelRegime.AUTO,
                    operatingState = DeviceTimerOperatingState.ON,
                    scheduleCount = 1,
                    outputHealth = DeviceTimerOutputHealth.UNVERIFIED
                ),
                transition = DeviceTimerChannelTransition(
                    activeScheduleSlotId = 1,
                    activeScheduleName = "Day cycle",
                    nextTransitionType = DeviceTimerNextTransitionType.OFF,
                    nextTransitionAtEpochMillis = TEST_NEXT_TRANSITION_EPOCH_MILLIS
                ),
                runtime = DeviceTimerChannelRuntime(
                    reason = DeviceTimerRuntimeReason.SCHEDULE_ACTIVE,
                    clockReady = true,
                    temporaryOverrideActive = false,
                    temporaryOverrideRemainingMillis = 0L,
                    physicalFeedbackAvailable = false
                ),
                schedules = listOf(
                    DeviceTimerScheduleSnapshot(
                        identity = DeviceTimerScheduleIdentity(
                            index = 0,
                            slotId = 1,
                            enabled = true,
                            name = "Day cycle"
                        ),
                        window = DeviceTimerScheduleWindow(
                            weekdays = List(TEST_WEEKDAY_COUNT) { true },
                            startTimeMillis = MORNING_START_MILLIS,
                            endTimeMillis = EVENING_END_MILLIS,
                            spansMidnight = false
                        )
                    )
                )
            )
        )
    )
)

private fun minutesOfDay(hour: Int): Int = hour * MINUTES_PER_HOUR

private const val TEST_REVISION = 12L
private const val MINUTES_PER_HOUR = 60
private const val MILLIS_PER_MINUTE = 60_000L
private const val OVERNIGHT_START_HOUR = 20
private const val OVERNIGHT_END_HOUR = 6
private const val MORNING_START_HOUR = 8
private const val BEFORE_NOON_HOUR = 11
private const val NOON_HOUR = 12
private const val AFTERNOON_END_HOUR = 14
private const val EVENING_END_HOUR = 18
private const val DAY_END_HOUR = 23
private const val HALF_HOUR_MINUTES = 30
private const val NINETY_MINUTES = 90
private const val TEST_WEEKDAY_COUNT = 7
private const val SUNDAY_INDEX = 6
private const val TEST_MAX_SCHEDULES = 8
private const val OVERSIZED_NAME_CHARACTER_COUNT = 13
private const val TEST_UPTIME_MILLIS = 90_000L
private const val TEST_NEXT_TRANSITION_EPOCH_MILLIS = 1_800_000_000_000L
private const val OVERNIGHT_START_MILLIS =
    OVERNIGHT_START_HOUR * MINUTES_PER_HOUR * MILLIS_PER_MINUTE
private const val OVERNIGHT_END_MILLIS =
    OVERNIGHT_END_HOUR * MINUTES_PER_HOUR * MILLIS_PER_MINUTE
private const val MORNING_START_MILLIS =
    MORNING_START_HOUR * MINUTES_PER_HOUR * MILLIS_PER_MINUTE
private const val EVENING_END_MILLIS =
    EVENING_END_HOUR * MINUTES_PER_HOUR * MILLIS_PER_MINUTE
