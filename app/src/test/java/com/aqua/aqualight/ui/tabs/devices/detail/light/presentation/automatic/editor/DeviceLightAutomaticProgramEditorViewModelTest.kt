package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.editor

import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticChannel
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticMutationResult
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticOperations
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticPolicy
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticProgram
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticProgramDraft
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticReadResult
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticScene
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticSnapshot
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.FakeLightDeviceRootOperations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceLightAutomaticProgramEditorViewModelTest {

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
    fun `duplicate first save is disabled even when restored draft was enabled`() {
        val program = automaticProgram(enabled = true)
        val operations = FakeAutomaticOperations(snapshot(program))
        val restored = DeviceLightAutomaticEditorDraft.fromProgram(program).copy(enabled = true)
        val viewModel = DeviceLightAutomaticProgramEditorViewModel(
            operations,
            FakeLightDeviceRootOperations()
        )

        viewModel.bind(
            deviceUidText = DEVICE_UID,
            mode = DeviceLightAutomaticEditorMode.Duplicate(program.programId),
            restoredDraft = restored,
            restoredPresetId = null
        )
        viewModel.save()

        assertFalse(viewModel.currentState.draft.enabled)
        assertEquals(false, operations.createdEnabled)
    }

    @Test
    fun `retained firmware frame stays visible but cannot save while authority is absent`() {
        val program = automaticProgram(enabled = true)
        val operations = FakeAutomaticOperations(snapshot(program))
        val rootOperations = FakeLightDeviceRootOperations()
        val viewModel = DeviceLightAutomaticProgramEditorViewModel(
            operations,
            rootOperations
        )
        viewModel.bind(
            deviceUidText = DEVICE_UID,
            mode = DeviceLightAutomaticEditorMode.Edit(program.programId),
            restoredDraft = null,
            restoredPresetId = null
        )
        viewModel.draftEditor.updateChannel(DeviceLightAutomaticChannel.RED, 91)

        operations.publish(snapshot(program).copy(firmwareWriteAuthoritative = false))

        assertEquals(program.startTimeMs, viewModel.currentState.draft.startTimeMs)
        assertFalse(viewModel.currentState.firmwareWriteAuthoritative)
        assertFalse(viewModel.currentState.canSave)
        assertEquals(
            DeviceConnectionVisualState.ONLINE,
            viewModel.currentState.connectionVisualState
        )

        rootOperations.setAvailability(OwnerDeviceAvailability.UNREACHABLE)

        assertEquals(
            DeviceConnectionVisualState.OFFLINE,
            viewModel.currentState.connectionVisualState
        )
    }

    private class FakeAutomaticOperations(
        initial: DeviceLightAutomaticSnapshot
    ) : DeviceLightAutomaticOperations {
        private val state = MutableStateFlow<DeviceLightAutomaticReadResult>(
            DeviceLightAutomaticReadResult.Available(initial)
        )
        var createdEnabled: Boolean? = null

        override fun observe(deviceUid: String): Flow<DeviceLightAutomaticReadResult> = state

        override fun current(deviceUid: String): DeviceLightAutomaticReadResult = state.value

        override suspend fun read(deviceUid: String): DeviceLightAutomaticReadResult = state.value

        override suspend fun create(
            deviceUid: String,
            expectedRevision: Long,
            enabled: Boolean,
            draft: DeviceLightAutomaticProgramDraft
        ): DeviceLightAutomaticMutationResult {
            createdEnabled = enabled
            return DeviceLightAutomaticMutationResult.Success
        }

        override suspend fun update(
            deviceUid: String,
            expectedRevision: Long,
            programId: String,
            draft: DeviceLightAutomaticProgramDraft
        ) = DeviceLightAutomaticMutationResult.Success

        override suspend fun setEnabled(
            deviceUid: String,
            expectedRevision: Long,
            programId: String,
            enabled: Boolean
        ) = DeviceLightAutomaticMutationResult.Success

        override suspend fun delete(
            deviceUid: String,
            expectedRevision: Long,
            programId: String
        ) = DeviceLightAutomaticMutationResult.Success

        fun publish(snapshot: DeviceLightAutomaticSnapshot) {
            state.value = DeviceLightAutomaticReadResult.Available(snapshot)
        }
    }

    private companion object {
        const val DEVICE_UID = "automatic-editor-light"
        const val PROGRAM_ID = "ap-00000001"
        const val REVISION = 4L
        const val START_TIME_MS = 10L * 60L * 60_000L
        const val END_TIME_MS = 17L * 60L * 60_000L
        const val RAMP_DURATION_MS = 30L * 60_000L
        val CHANNELS = DeviceLightAutomaticChannel.entries

        fun automaticProgram(enabled: Boolean) = DeviceLightAutomaticProgram(
            programId = PROGRAM_ID,
            enabled = enabled,
            weekdaysMask = 127,
            startTimeMs = START_TIME_MS,
            endTimeMs = END_TIME_MS,
            rampDurationMs = RAMP_DURATION_MS,
            scene = DeviceLightAutomaticScene(
                CHANNELS.associateWith { channel -> 20 + channel.ordinal * 15 }
            )
        )

        fun snapshot(program: DeviceLightAutomaticProgram) = DeviceLightAutomaticSnapshot(
            deviceUid = DEVICE_UID,
            productDisplayName = "AquaLight WRGB Pro Elite",
            revision = REVISION,
            policy = DeviceLightAutomaticPolicy(
                capacity = 16,
                timeStepMs = 60_000L,
                rampDurationsMs = listOf(0L, RAMP_DURATION_MS, 60L * 60_000L)
            ),
            channels = CHANNELS,
            programs = listOf(program),
            firmwareWriteAuthoritative = true
        )
    }
}
