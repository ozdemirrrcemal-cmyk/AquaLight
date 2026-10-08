package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom

import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomChannel
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomFailure
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomMutationResult
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomOperations
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomPoint
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomReadResult
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomScene
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomSnapshot
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomWriteResult
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryChannel
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryChannelDescriptor
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryCustomPoint
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryEntry
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryKind
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryMutationResult
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryOperations
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryPayload
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryResult
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibrarySnapshot
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryTarget
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryScene
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.FakeLightDeviceRootOperations
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class DeviceLightCustomCurveViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `fresh editor hydrates the installed firmware curve as a clean draft`() {
        val viewModel = boundViewModel()

        assertFalse(viewModel.currentState.initialLoading)
        assertFalse(viewModel.currentState.hasUnsavedChanges)
        assertFalse(viewModel.currentState.hasUnappliedChanges)
        assertTrue(viewModel.currentState.deviceProgramInstalled)
        assertEquals(EVERY_DAY_MASK, viewModel.currentState.draft.weekdaysMask)
        assertEquals(1, viewModel.currentState.draft.points.size)
        assertEquals(INITIAL_TIME_MS, viewModel.currentState.selectedPoint?.timeMs)
        val state = viewModel.currentState
        val deviceTimeMs = requireNotNull(state.deviceTimeMs)
        val clockTolerance = CLOCK_TIME_MS until CLOCK_TIME_MS + CLOCK_TICK_TOLERANCE_MS
        assertTrue(state.previewTimeMs in clockTolerance)
        assertTrue(deviceTimeMs in clockTolerance)
        assertEquals(DeviceLightCustomPlayheadMode.CLOCK, state.playheadMode)
        assertEquals(
            WRGB_CHANNELS.map(DeviceLightCustomChannel::toUiChannel),
            viewModel.currentState.channels
        )
    }

    @Test
    fun `point and weekday edits mark draft dirty`() {
        val viewModel = boundViewModel()
        viewModel.addInitialPoint()

        viewModel.pointEditor.updateSelectedChannel(DeviceLightCustomChannelId.BLUE, UPDATED_BLUE)
        viewModel.dayEditor.toggleWeekday(SUNDAY_INDEX)

        assertTrue(viewModel.currentState.hasUnsavedChanges)
        assertTrue(viewModel.currentState.hasUnappliedChanges)
        assertEquals(
            UPDATED_BLUE,
            viewModel.currentState.selectedPoint?.channels?.get(DeviceLightCustomChannelId.BLUE)
        )
        assertEquals(
            EVERY_DAY_MASK xor customWeekdayMask(SUNDAY_INDEX),
            viewModel.currentState.draft.weekdaysMask
        )
    }

    @Test
    fun `save as writes exact draft to DataStore boundary and clears dirty state`() {
        val library = FakeLibraryOperations()
        val viewModel = boundViewModel(libraryOperations = library)
        viewModel.addInitialPoint()
        viewModel.pointEditor.updateSelectedChannel(DeviceLightCustomChannelId.RED, UPDATED_RED)

        viewModel.saveAs("Morning reef")

        assertEquals("Morning reef", library.savedName)
        assertEquals(EVERY_DAY_MASK, library.savedWeekdaysMask)
        assertEquals(UPDATED_RED, library.savedPoints.single().scene.channels.values.first())
        assertFalse(viewModel.currentState.hasUnsavedChanges)
        assertTrue(viewModel.currentState.hasUnappliedChanges)
    }

    @Test
    fun `opening a saved profile changes editor only and remains unapplied`() {
        val profile = customLibraryEntry(
            name = "Evening profile",
            red = UPDATED_RED
        )
        val library = FakeLibraryOperations(
            libraryResult = DeviceLightLibraryResult.Available(
                DeviceLightLibrarySnapshot(
                    target = libraryTarget(),
                    entries = listOf(profile)
                )
            )
        )
        val custom = FakeCustomOperations(snapshot())
        val viewModel = boundViewModel(
            customOperations = custom,
            libraryOperations = library
        )

        viewModel.openLibraryProfile(profile.id)

        assertEquals(
            UPDATED_RED,
            viewModel.currentState.selectedPoint?.channels?.get(DeviceLightCustomChannelId.RED)
        )
        assertFalse(viewModel.currentState.hasUnsavedChanges)
        assertTrue(viewModel.currentState.hasUnappliedChanges)
        assertEquals(0, custom.applyCount)
    }

    @Test
    fun `process restoration keeps saved profile checkpoint distinct from device baseline`() {
        val profile = customLibraryEntry(
            name = "Profile checkpoint",
            red = UPDATED_RED
        )
        val library = FakeLibraryOperations(
            libraryResult = DeviceLightLibraryResult.Available(
                DeviceLightLibrarySnapshot(
                    target = libraryTarget(),
                    entries = listOf(profile)
                )
            )
        )
        val original = boundViewModel(libraryOperations = library)
        original.openLibraryProfile(profile.id)
        original.pointEditor.updateSelectedChannel(
            DeviceLightCustomChannelId.RED,
            INITIAL_RED
        )
        val restoredDraft = original.currentState.draft
        val restoredCheckpoint = requireNotNull(original.currentEditorCheckpoint)

        val restored = boundViewModel(
            restoredState = RestoredEditorState(
                draft = restoredDraft,
                dirty = true,
                checkpoint = restoredCheckpoint
            )
        )

        assertTrue(restored.currentState.hasUnsavedChanges)
        assertFalse(restored.currentState.hasUnappliedChanges)
        assertEquals(restoredDraft, restored.currentState.draft)
        assertEquals(restoredCheckpoint, restored.currentEditorCheckpoint)
    }

    @Test
    fun `apply to device sends authoritative revision and complete editor document`() {
        val custom = FakeCustomOperations(snapshot())
        val viewModel = boundViewModel(customOperations = custom)
        viewModel.pointEditor.updateSelectedChannel(DeviceLightCustomChannelId.RED, UPDATED_RED)
        viewModel.dayEditor.toggleWeekday(SUNDAY_INDEX)

        viewModel.applyToDevice()

        assertEquals(1, custom.applyCount)
        assertEquals(4L, custom.appliedRevision)
        assertEquals(
            EVERY_DAY_MASK xor customWeekdayMask(SUNDAY_INDEX),
            custom.appliedWeekdaysMask
        )
        assertEquals(UPDATED_RED, custom.appliedPoints.single().scene.channels.getValue(
            DeviceLightCustomChannel.RED
        ))
        assertFalse(viewModel.currentState.hasUnsavedChanges)
        assertFalse(viewModel.currentState.hasUnappliedChanges)
        assertTrue(viewModel.currentState.deviceProgramInstalled)
    }

    @Test
    fun `stale apply refreshes device baseline but preserves editor draft`() {
        val custom = FakeCustomOperations(snapshot())
        val viewModel = boundViewModel(customOperations = custom)
        viewModel.pointEditor.updateSelectedChannel(DeviceLightCustomChannelId.RED, UPDATED_RED)
        val candidate = viewModel.currentState.draft

        custom.failNextWrite = DeviceLightCustomFailure.STALE_REVISION
        custom.updateSnapshot(
            snapshot().copy(
                revision = 5,
                points = snapshot().points.map { point ->
                    point.copy(
                        scene = DeviceLightCustomScene(
                            point.scene.channels.toMutableMap().apply {
                                this[DeviceLightCustomChannel.RED] = 11
                            }
                        )
                    )
                }
            )
        )

        viewModel.applyToDevice()

        assertEquals(candidate, viewModel.currentState.draft)
        assertTrue(viewModel.currentState.hasUnsavedChanges)
        assertTrue(viewModel.currentState.hasUnappliedChanges)
        assertEquals(1, custom.applyCount)
    }

    @Test
    fun `delete device program clears firmware state without touching profiles`() {
        val library = FakeLibraryOperations()
        val custom = FakeCustomOperations(snapshot())
        val viewModel = boundViewModel(
            customOperations = custom,
            libraryOperations = library
        )

        viewModel.clearDeviceProgram()

        assertEquals(1, custom.clearCount)
        assertEquals(4L, custom.clearedRevision)
        assertFalse(viewModel.currentState.deviceProgramInstalled)
        assertTrue(viewModel.currentState.draft.points.isEmpty())
        assertFalse(viewModel.currentState.hasUnsavedChanges)
        assertFalse(viewModel.currentState.hasUnappliedChanges)
        assertEquals(0, library.deleteCount)
    }

    @Test
    fun `rgb product never exposes or persists white`() {
        val channels = WRGB_CHANNELS.dropLast(1)
        val library = FakeLibraryOperations()
        val viewModel = boundViewModel(
            customOperations = FakeCustomOperations(snapshot(channels)),
            libraryOperations = library
        )
        viewModel.addInitialPoint()

        viewModel.saveAs("RGB curve")

        assertFalse(DeviceLightCustomChannelId.WHITE in viewModel.currentState.channels)
        assertFalse(
            library.savedPoints.single().scene.channels.keys.any { channel ->
                channel.sceneKey == DeviceLightCustomChannel.WHITE.sceneKey
            }
        )
    }

    @Test
    fun `preview sends the complete editor draft without installing it`() {
        val custom = FakeCustomOperations(snapshot())
        val viewModel = boundViewModel(customOperations = custom)
        viewModel.pointEditor.updatePlayhead(PREVIEW_TIME_MS)
        viewModel.pointEditor.updateSelectedChannel(DeviceLightCustomChannelId.BLUE, UPDATED_BLUE)

        viewModel.preview()

        assertEquals(
            viewModel.currentState.draft.points.map { point ->
                DeviceLightCustomPoint(
                    timeMs = point.timeMs,
                    scene = DeviceLightCustomScene(
                        point.channels.mapKeys { (channel, _) -> channel.toApplicationChannelForTest() }
                    )
                )
            },
            custom.previewPoints
        )
        viewModel.clearPreview()
    }

    @Test
    fun `custom day preview clock maps real playback to the full virtual day`() {
        val realHourMs = CUSTOM_DAY_PREVIEW_DURATION_MS / HOURS_PER_PREVIEW_DAY

        assertEquals(MILLIS_PER_HOUR, customDayPreviewVirtualTimeMs(realHourMs))
        assertEquals(
            MILLIS_PER_DAY / 2,
            customDayPreviewVirtualTimeMs(CUSTOM_DAY_PREVIEW_DURATION_MS / 2)
        )
        assertEquals(
            MILLIS_PER_DAY,
            customDayPreviewVirtualTimeMs(CUSTOM_DAY_PREVIEW_DURATION_MS)
        )
    }

    @Test
    fun `preview never opens blocking loading`() = runTest {
        val previewGate = CompletableDeferred<Unit>()
        val custom = FakeCustomOperations(snapshot(), previewGate)
        val viewModel = boundViewModel(customOperations = custom)

        viewModel.preview()

        assertTrue(viewModel.currentState.operationInProgress)
        assertFalse(viewModel.currentState.showGlobalLoading)

        previewGate.complete(Unit)
        viewModel.clearPreview()

        assertFalse(viewModel.currentState.operationInProgress)
        assertFalse(viewModel.currentState.showGlobalLoading)
    }

    @Test
    fun `save opens blocking loading until persistence completes`() {
        val saveGate = CompletableDeferred<Unit>()
        val library = FakeLibraryOperations(saveGate)
        val viewModel = boundViewModel(libraryOperations = library)
        viewModel.addInitialPoint()

        viewModel.saveAs("Loading contract")

        assertTrue(viewModel.currentState.operationInProgress)
        assertTrue(viewModel.currentState.showGlobalLoading)

        saveGate.complete(Unit)

        assertFalse(viewModel.currentState.operationInProgress)
        assertFalse(viewModel.currentState.showGlobalLoading)
    }

    @Test
    fun `adding beyond firmware point limit shows warning and preserves draft`() = runTest {
        val restoredDraft = DeviceLightCustomDraft(
            points = List(MAX_POINT_CAPACITY) { index ->
                DeviceLightCustomPointUiState(
                    timeMs = index * MILLIS_PER_MINUTE,
                    channels = DeviceLightCustomChannelId.entries.associateWith { channel ->
                        channel.ordinal
                    }
                )
            }
        )
        val viewModel = boundViewModel(
            restoredState = RestoredEditorState(
                draft = restoredDraft,
                dirty = true
            )
        )
        val effect = async(start = CoroutineStart.UNDISPATCHED) { viewModel.effects.first() }

        viewModel.pointEditor.updatePlayhead(PREVIEW_TIME_MS)
        viewModel.pointEditor.requestPlayheadTime(editSelected = false)

        assertEquals(
            DeviceLightCustomCurveEffect.ShowPointLimit(MAX_POINT_CAPACITY),
            effect.await()
        )
        assertEquals(MAX_POINT_CAPACITY, viewModel.currentState.draft.points.size)
    }

    @Test
    fun `graph selection never creates a point`() {
        val secondTimeMs = INITIAL_TIME_MS + 2 * MILLIS_PER_HOUR
        val viewModel = boundViewModel()
        viewModel.addInitialPoint()
        viewModel.pointEditor.addOrMovePoint(null, secondTimeMs)

        viewModel.pointEditor.selectGraphPoint(secondTimeMs)
        viewModel.pointEditor.selectGraphPoint(INITIAL_TIME_MS + MILLIS_PER_HOUR)

        assertEquals(2, viewModel.currentState.draft.points.size)
        assertEquals(secondTimeMs, viewModel.currentState.selectedTimeMs)
        assertEquals(secondTimeMs, viewModel.currentState.previewTimeMs)
    }

    @Test
    fun `last remaining point cannot be deleted`() {
        val viewModel = boundViewModel()
        viewModel.addInitialPoint()

        viewModel.pointEditor.deletePoint(INITIAL_TIME_MS)

        assertEquals(1, viewModel.currentState.draft.points.size)
        assertFalse(viewModel.currentState.canDeleteSelectedPoint)
    }

    @Test
    fun `releasing playhead requests exact dragged time`() = runTest {
        val viewModel = boundViewModel()
        val effect = async(start = CoroutineStart.UNDISPATCHED) { viewModel.effects.first() }

        viewModel.pointEditor.updatePlayhead(PREVIEW_TIME_MS)
        viewModel.pointEditor.requestPlayheadTime(editSelected = false)

        assertEquals(
            DeviceLightCustomCurveEffect.OpenTimePicker(
                DeviceLightCustomTimePickerPurpose.Add(PREVIEW_TIME_MS)
            ),
            effect.await()
        )
    }

    @Test
    fun `moving playhead does not change draft before time confirmation`() {
        val viewModel = boundViewModel()

        viewModel.pointEditor.updatePlayhead(PREVIEW_TIME_MS)

        assertEquals(1, viewModel.currentState.draft.points.size)
        assertFalse(viewModel.currentState.hasUnsavedChanges)
        assertEquals(PREVIEW_TIME_MS, viewModel.currentState.previewTimeMs)
        assertEquals(DeviceLightCustomPlayheadMode.EDIT, viewModel.currentState.playheadMode)
    }

    @Test
    fun `authoritative refresh preserves a user positioned edit playhead`() {
        val viewModel = boundViewModel()
        viewModel.pointEditor.updatePlayhead(PREVIEW_TIME_MS)

        viewModel.refreshFromDevice()

        assertEquals(PREVIEW_TIME_MS, viewModel.currentState.previewTimeMs)
        assertEquals(DeviceLightCustomPlayheadMode.EDIT, viewModel.currentState.playheadMode)
    }

    @Test
    fun `clearing preview returns playhead ownership to device clock`() {
        val viewModel = boundViewModel()
        viewModel.pointEditor.updatePlayhead(PREVIEW_TIME_MS)

        viewModel.clearPreview()

        val state = viewModel.currentState
        assertEquals(DeviceLightCustomPlayheadMode.CLOCK, state.playheadMode)
        assertEquals(state.deviceTimeMs, state.previewTimeMs)
    }

    @Test
    fun `cancelling add returns playhead to selected firmware point`() {
        val viewModel = boundViewModel()
        viewModel.pointEditor.updatePlayhead(PREVIEW_TIME_MS)

        viewModel.pointEditor.cancelTimeSelection(
            DeviceLightCustomTimePickerPurpose.Add(PREVIEW_TIME_MS)
        )

        assertEquals(INITIAL_TIME_MS, viewModel.currentState.previewTimeMs)
        assertEquals(INITIAL_TIME_MS, viewModel.currentState.selectedTimeMs)
    }

    @Test
    fun `long press exposes contextual point actions without changing draft`() = runTest {
        val viewModel = boundViewModel()
        viewModel.addInitialPoint()
        val effect = async(start = CoroutineStart.UNDISPATCHED) { viewModel.effects.first() }

        viewModel.pointEditor.requestPointActions(INITIAL_TIME_MS)

        assertEquals(
            DeviceLightCustomCurveEffect.OpenPointActions(INITIAL_TIME_MS),
            effect.await()
        )
        assertEquals(1, viewModel.currentState.draft.points.size)
    }

    private fun boundViewModel(
        customOperations: FakeCustomOperations = FakeCustomOperations(snapshot()),
        libraryOperations: FakeLibraryOperations = FakeLibraryOperations(),
        restoredState: RestoredEditorState = RestoredEditorState()
    ) = DeviceLightCustomCurveViewModel(customOperations, libraryOperations, FakeLightDeviceRootOperations()).apply {
        bind(
            deviceUidText = DEVICE_UID,
            restoredDraft = restoredState.draft,
            restoredDirty = restoredState.dirty,
            restoredUnapplied = restoredState.unapplied,
            restoredCheckpoint = restoredState.checkpoint
        )
    }

    private data class RestoredEditorState(
        val draft: DeviceLightCustomDraft? = null,
        val dirty: Boolean = false,
        val unapplied: Boolean = false,
        val checkpoint: DeviceLightCustomDraft? = null
    )

    private fun DeviceLightCustomCurveViewModel.addInitialPoint() {
        pointEditor.addOrMovePoint(null, INITIAL_TIME_MS)
    }

    private class FakeCustomOperations(
        snapshot: DeviceLightCustomSnapshot,
        private val previewGate: CompletableDeferred<Unit>? = null
    ) : DeviceLightCustomOperations {
        private var authoritativeSnapshot = snapshot
        var previewPoints: List<DeviceLightCustomPoint>? = null
        var applyCount = 0
        var clearCount = 0
        var appliedRevision: Long? = null
        var appliedWeekdaysMask: Int? = null
        var appliedPoints: List<DeviceLightCustomPoint> = emptyList()
        var clearedRevision: Long? = null
        var failNextWrite: DeviceLightCustomFailure? = null

        fun updateSnapshot(snapshot: DeviceLightCustomSnapshot) {
            authoritativeSnapshot = snapshot
        }

        override fun observe(deviceUid: String): Flow<DeviceLightCustomReadResult> = emptyFlow()

        override fun current(deviceUid: String) =
            DeviceLightCustomReadResult.Available(authoritativeSnapshot)

        override suspend fun read(deviceUid: String) =
            DeviceLightCustomReadResult.Available(authoritativeSnapshot)

        override suspend fun applyToDevice(
            deviceUid: String,
            expectedRevision: Long,
            weekdaysMask: Int,
            points: List<DeviceLightCustomPoint>
        ): DeviceLightCustomWriteResult {
            applyCount += 1
            appliedRevision = expectedRevision
            appliedWeekdaysMask = weekdaysMask
            appliedPoints = points
            failNextWrite?.let { failure ->
                failNextWrite = null
                return DeviceLightCustomWriteResult.Failed(failure)
            }
            authoritativeSnapshot = authoritativeSnapshot.copy(
                revision = authoritativeSnapshot.revision + 1,
                installed = true,
                weekdaysMask = weekdaysMask,
                points = points
            )
            return DeviceLightCustomWriteResult.Success(authoritativeSnapshot)
        }

        override suspend fun clearDeviceProgram(
            deviceUid: String,
            expectedRevision: Long
        ): DeviceLightCustomWriteResult {
            clearCount += 1
            clearedRevision = expectedRevision
            failNextWrite?.let { failure ->
                failNextWrite = null
                return DeviceLightCustomWriteResult.Failed(failure)
            }
            authoritativeSnapshot = authoritativeSnapshot.copy(
                revision = authoritativeSnapshot.revision + 1,
                installed = false,
                weekdaysMask = EVERY_DAY_MASK,
                points = emptyList()
            )
            return DeviceLightCustomWriteResult.Success(authoritativeSnapshot)
        }

        override suspend fun preview(
            deviceUid: String,
            points: List<DeviceLightCustomPoint>
        ): DeviceLightCustomMutationResult {
            previewPoints = points
            previewGate?.await()
            return DeviceLightCustomMutationResult.Success
        }

        override suspend fun clearPreview(deviceUid: String) =
            DeviceLightCustomMutationResult.Success
    }

    private class FakeLibraryOperations(
        private val saveGate: CompletableDeferred<Unit>? = null,
        private val libraryResult: DeviceLightLibraryResult? = null
    ) : DeviceLightLibraryOperations {
        var savedName: String? = null
        var savedWeekdaysMask: Int? = null
        var savedPoints: List<DeviceLightLibraryCustomPoint> = emptyList()
        var deleteCount = 0

        override fun observeLibrary(deviceUid: String): Flow<DeviceLightLibraryResult> =
            libraryResult?.let(::flowOf) ?: emptyFlow()
        override suspend fun usedNames(kind: DeviceLightLibraryKind): List<String> = emptyList()
        override suspend fun saveManual(
            deviceUid: String,
            name: String,
            scene: DeviceLightLibraryScene
        ) = DeviceLightLibraryMutationResult.Success()

        override suspend fun saveCustom(
            deviceUid: String,
            name: String,
            weekdaysMask: Int,
            points: List<DeviceLightLibraryCustomPoint>
        ): DeviceLightLibraryMutationResult {
            savedName = name
            savedWeekdaysMask = weekdaysMask
            savedPoints = points
            saveGate?.await()
            return DeviceLightLibraryMutationResult.Success("custom")
        }

        override suspend fun rename(entryId: String, name: String) =
            DeviceLightLibraryMutationResult.Success(entryId)
        override suspend fun delete(entryId: String): DeviceLightLibraryMutationResult {
            deleteCount += 1
            return DeviceLightLibraryMutationResult.Success(entryId)
        }
    }

    private fun customLibraryEntry(
        name: String,
        red: Int
    ): DeviceLightLibraryEntry {
        val channels = DeviceLightLibraryChannel.entries
        return DeviceLightLibraryEntry(
            id = "profile-1",
            name = name,
            productKey = "LIGHT_WRGB_PRO_ELITE",
            channels = channels,
            payload = DeviceLightLibraryPayload.Custom(
                weekdaysMask = EVERY_DAY_MASK,
                points = listOf(
                    DeviceLightLibraryCustomPoint(
                        timeMs = INITIAL_TIME_MS,
                        scene = DeviceLightLibraryScene(
                            channels.associateWith { channel ->
                                when (channel) {
                                    DeviceLightLibraryChannel.RED -> red
                                    DeviceLightLibraryChannel.GREEN -> 40
                                    DeviceLightLibraryChannel.BLUE -> 60
                                    DeviceLightLibraryChannel.WHITE -> 85
                                }
                            }
                        )
                    )
                )
            ),
            createdAtMillis = 1L,
            updatedAtMillis = 1L
        )
    }

    private fun libraryTarget(): DeviceLightLibraryTarget {
        val channels = DeviceLightLibraryChannel.entries
        return DeviceLightLibraryTarget(
            deviceUid = DEVICE_UID,
            productKey = "LIGHT_WRGB_PRO_ELITE",
            channelDescriptors = channels.mapIndexed { index, channel ->
                DeviceLightLibraryChannelDescriptor(
                    channel = channel,
                    key = channel.name.lowercase(),
                    displayName = channel.name,
                    displayColorRgb = when (channel) {
                        DeviceLightLibraryChannel.RED -> 0xFF0000
                        DeviceLightLibraryChannel.GREEN -> 0x00FF00
                        DeviceLightLibraryChannel.BLUE -> 0x0000FF
                        DeviceLightLibraryChannel.WHITE -> 0xFFFFFF
                    },
                    order = index
                )
            },
            estimatedPowerWatts = null
        )
    }

    private fun DeviceLightCustomChannelId.toApplicationChannelForTest() = when (this) {
        DeviceLightCustomChannelId.RED -> DeviceLightCustomChannel.RED
        DeviceLightCustomChannelId.GREEN -> DeviceLightCustomChannel.GREEN
        DeviceLightCustomChannelId.BLUE -> DeviceLightCustomChannel.BLUE
        DeviceLightCustomChannelId.WHITE -> DeviceLightCustomChannel.WHITE
    }

    class MainDispatcherRule(
        private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
    ) : TestWatcher() {
        override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
        override fun finished(description: Description) = Dispatchers.resetMain()
    }

    private companion object {
        const val DEVICE_UID = "custom-light"
        const val INITIAL_TIME_MS = 12 * 60 * 60_000L
        const val PREVIEW_TIME_MS = 16 * 60 * 60_000L
        const val CLOCK_TIME_MS = 27 * 60_000L + 30_000L
        const val CLOCK_TICK_TOLERANCE_MS = 5_000L
        const val UPDATED_BLUE = 73
        const val UPDATED_RED = 64
        const val INITIAL_RED = 20
        const val SUNDAY_INDEX = 6
        const val HOURS_PER_PREVIEW_DAY = 24L
        val WRGB_CHANNELS = listOf(
            DeviceLightCustomChannel.RED,
            DeviceLightCustomChannel.GREEN,
            DeviceLightCustomChannel.BLUE,
            DeviceLightCustomChannel.WHITE
        )

        fun snapshot(
            channels: List<DeviceLightCustomChannel> = WRGB_CHANNELS,
            points: List<DeviceLightCustomPoint> = listOf(
                DeviceLightCustomPoint(
                    timeMs = INITIAL_TIME_MS,
                    scene = DeviceLightCustomScene(
                        channels.associateWith { channel ->
                            when (channel) {
                                DeviceLightCustomChannel.RED -> 20
                                DeviceLightCustomChannel.GREEN -> 40
                                DeviceLightCustomChannel.BLUE -> 60
                                DeviceLightCustomChannel.WHITE -> 85
                            }
                        }
                    )
                )
            )
        ): DeviceLightCustomSnapshot = DeviceLightCustomSnapshot(
            deviceUid = DEVICE_UID,
            productKey = if (channels.size == 4) "LIGHT_WRGB_PRO_ELITE" else "LIGHT_RGB_PRO_SLIM",
            revision = 4,
            installed = true,
            weekdaysMask = EVERY_DAY_MASK,
            maxPoints = MAX_POINT_CAPACITY,
            timeStepMs = MILLIS_PER_MINUTE,
            currentTimeMs = CLOCK_TIME_MS,
            channels = channels,
            points = points,
            firmwareWriteAuthoritative = true
        )
    }
}
