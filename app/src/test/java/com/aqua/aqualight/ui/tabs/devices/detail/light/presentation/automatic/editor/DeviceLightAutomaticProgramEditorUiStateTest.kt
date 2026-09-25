package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.editor

import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticChannel
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLightAutomaticProgramEditorUiStateTest {

    @Test
    fun newProgramDraftStartsEnabledWithCommercialTimeDefaults() {
        val draft = DeviceLightAutomaticEditorDraft.forNewProgram(CHANNELS)

        assertEquals(hours(DEFAULT_START_HOUR), draft.startTimeMs)
        assertEquals(hours(DEFAULT_END_HOUR), draft.endTimeMs)
        assertTrue(draft.enabled)
    }

    @Test
    fun emptyCreateDraftStaysNeutralAndCannotBeSaved() {
        val state = createState(DeviceLightAutomaticEditorDraft.empty(CHANNELS))

        assertFalse(state.canSave)
        assertFalse(state.hasUnsavedChanges)
    }

    @Test
    fun completeOvernightDraftCanBeSaved() {
        val draft = DeviceLightAutomaticEditorDraft.empty(CHANNELS).copy(
            weekdaysMask = EVERY_DAY_MASK,
            startTimeMs = hours(OVERNIGHT_START_HOUR),
            endTimeMs = hours(OVERNIGHT_END_HOUR),
            rampDurationMs = minutes(OVERNIGHT_RAMP_MINUTES),
            channels = CHANNELS.associateWith { channel -> channel.percent() }
        )
        val state = createState(draft)

        assertEquals(draft, state.draft)
        assertTrue(state.canSave)
    }

    @Test
    fun rampThatDoesNotFitNeverProducesPreviewOrSaveRequest() {
        val draft = DeviceLightAutomaticEditorDraft.empty(CHANNELS).copy(
            weekdaysMask = EVERY_DAY_MASK,
            startTimeMs = hours(SHORT_START_HOUR),
            endTimeMs = hours(SHORT_END_HOUR),
            rampDurationMs = minutes(LONG_RAMP_MINUTES)
        )
        val state = createState(draft)

        assertFalse(state.canSave)
    }

    @Test
    fun authoredTimesMustRespectFirmwareTimeStep() {
        val draft = DeviceLightAutomaticEditorDraft.empty(CHANNELS).copy(
            weekdaysMask = EVERY_DAY_MASK,
            startTimeMs = minutes(NON_STEP_START_MINUTES),
            endTimeMs = hours(DAY_END_HOUR),
            rampDurationMs = minutes(VALID_RAMP_MINUTES)
        )
        val state = createState(draft, timeStepMs = minutes(FIVE_MINUTES))

        assertFalse(state.canSave)
    }

    private fun createState(
        draft: DeviceLightAutomaticEditorDraft,
        timeStepMs: Long = minutes(ONE_MINUTE)
    ): DeviceLightAutomaticProgramEditorUiState {
        val empty = DeviceLightAutomaticEditorDraft.empty(CHANNELS)
        return DeviceLightAutomaticProgramEditorUiState(
            source = DeviceLightAutomaticEditorSource(
                deviceUid = DEVICE_UID,
                revision = REVISION,
                programCount = 0,
                policy = DeviceLightAutomaticPolicy(
                    capacity = CAPACITY,
                    timeStepMs = timeStepMs,
                    rampDurationsMs = RAMP_DURATIONS
                ),
                channels = CHANNELS,
                baselineDraft = empty
            ),
            draft = draft,
            firmwareWriteAuthoritative = true
        )
    }
}

private fun DeviceLightAutomaticChannel.percent(): Int = when (this) {
    DeviceLightAutomaticChannel.RED -> RED_PERCENT
    DeviceLightAutomaticChannel.GREEN -> GREEN_PERCENT
    DeviceLightAutomaticChannel.BLUE -> BLUE_PERCENT
    DeviceLightAutomaticChannel.WHITE -> WHITE_PERCENT
}

private fun hours(value: Int): Long = minutes(value * MINUTES_PER_HOUR)
private fun minutes(value: Int): Long = value * MILLIS_PER_MINUTE

private const val DEVICE_UID = "light-editor-test-device"
private const val REVISION = 12L
private const val CAPACITY = 16
private const val EVERY_DAY_MASK = 0x7f
private const val DEFAULT_START_HOUR = 10
private const val DEFAULT_END_HOUR = 17
private const val OVERNIGHT_START_HOUR = 22
private const val OVERNIGHT_END_HOUR = 6
private const val OVERNIGHT_RAMP_MINUTES = 60
private const val SHORT_START_HOUR = 8
private const val SHORT_END_HOUR = 9
private const val LONG_RAMP_MINUTES = 60
private const val DAY_END_HOUR = 22
private const val NON_STEP_START_MINUTES = 481
private const val VALID_RAMP_MINUTES = 30
private const val ONE_MINUTE = 1
private const val FIVE_MINUTES = 5
private const val MINUTES_PER_HOUR = 60
private const val MILLIS_PER_MINUTE = 60_000L
private const val RED_PERCENT = 20
private const val GREEN_PERCENT = 40
private const val BLUE_PERCENT = 65
private const val WHITE_PERCENT = 85
private val CHANNELS = DeviceLightAutomaticChannel.entries
private val RAMP_DURATIONS = listOf(
    NO_RAMP_MINUTES,
    VALID_RAMP_MINUTES,
    OVERNIGHT_RAMP_MINUTES,
    RAMP_NINETY_MINUTES,
    RAMP_ONE_TWENTY_MINUTES,
    RAMP_ONE_FIFTY_MINUTES
).map(::minutes)
private const val NO_RAMP_MINUTES = 0
private const val RAMP_NINETY_MINUTES = 90
private const val RAMP_ONE_TWENTY_MINUTES = 120
private const val RAMP_ONE_FIFTY_MINUTES = 150
