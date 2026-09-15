package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom

import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomChannel
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomMutationResult
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomOperations
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomPoint
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomReadResult
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomScene
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomSnapshot
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryCustomPoint
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryKind
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryMutationResult
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryOperations
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryResult
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryScene
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
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
    fun `fresh editor starts with an empty clean draft`() {
        val viewModel = boundViewModel()

        assertFalse(viewModel.currentState.initialLoading)
        assertFalse(viewModel.currentState.hasUnsavedChanges)
        assertEquals(EVERY_DAY_MASK, viewModel.currentState.draft.weekdaysMask)
        assertTrue(viewModel.currentState.draft.points.isEmpty())
        assertEquals(null, viewModel.currentState.selectedPoint)
        assertEquals(INITIAL_TIME_MS, viewModel.currentState.previewTimeMs)
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
    fun `preview uses firmware virtual time without installing draft`() {
        val custom = FakeCustomOperations(snapshot())
        val viewModel = boundViewModel(customOperations = custom)
        viewModel.pointEditor.updatePlayhead(PREVIEW_TIME_MS)

        viewModel.preview()

        assertEquals(PREVIEW_TIME_MS, custom.previewTimeMs)
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
            restoredDraft = restoredDraft,
            restoredDirty = true
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

        assertEquals(0, viewModel.currentState.draft.points.size)
        assertFalse(viewModel.currentState.hasUnsavedChanges)
        assertEquals(PREVIEW_TIME_MS, viewModel.currentState.previewTimeMs)
    }

    @Test
    fun `cancelling first point time keeps playhead at dragged time`() {
        val viewModel = boundViewModel()
        viewModel.pointEditor.updatePlayhead(PREVIEW_TIME_MS)

        viewModel.pointEditor.cancelTimeSelection(
            DeviceLightCustomTimePickerPurpose.Add(PREVIEW_TIME_MS)
        )

        assertEquals(PREVIEW_TIME_MS, viewModel.currentState.previewTimeMs)
        assertEquals(null, viewModel.currentState.selectedTimeMs)
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
        restoredDraft: DeviceLightCustomDraft? = null,
        restoredDirty: Boolean = false
    ) = DeviceLightCustomCurveViewModel(customOperations, libraryOperations).apply {
        bind(DEVICE_UID, restoredDraft, restoredDirty)
    }

    private fun DeviceLightCustomCurveViewModel.addInitialPoint() {
        pointEditor.addOrMovePoint(null, INITIAL_TIME_MS)
    }

    private class FakeCustomOperations(
        private val snapshot: DeviceLightCustomSnapshot
    ) : DeviceLightCustomOperations {
        var previewTimeMs: Long? = null

        override suspend fun read(deviceUid: String) = DeviceLightCustomReadResult.Available(snapshot)

        override suspend fun preview(deviceUid: String, virtualTimeMs: Long):
            DeviceLightCustomMutationResult {
            previewTimeMs = virtualTimeMs
            return DeviceLightCustomMutationResult.Success
        }

        override suspend fun clearPreview(deviceUid: String) =
            DeviceLightCustomMutationResult.Success
    }

    private class FakeLibraryOperations : DeviceLightLibraryOperations {
        var savedName: String? = null
        var savedWeekdaysMask: Int? = null
        var savedPoints: List<DeviceLightLibraryCustomPoint> = emptyList()

        override fun observeLibrary(deviceUid: String): Flow<DeviceLightLibraryResult> = emptyFlow()
        override suspend fun usedNames(kind: DeviceLightLibraryKind): List<String> = emptyList()
        override suspend fun refreshInstalledCustom(deviceUid: String) = Unit
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
            return DeviceLightLibraryMutationResult.Success("custom")
        }

        override suspend fun rename(entryId: String, name: String) =
            DeviceLightLibraryMutationResult.Success(entryId)
        override suspend fun delete(entryId: String) =
            DeviceLightLibraryMutationResult.Success(entryId)
        override suspend fun load(deviceUid: String, entryId: String) =
            DeviceLightLibraryMutationResult.Success(entryId)
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
        const val UPDATED_BLUE = 73
        const val UPDATED_RED = 64
        const val SUNDAY_INDEX = 6
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
            currentTimeMs = INITIAL_TIME_MS,
            channels = channels,
            points = points
        )
    }
}
