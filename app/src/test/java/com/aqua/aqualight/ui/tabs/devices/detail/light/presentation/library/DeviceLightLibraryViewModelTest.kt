package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.library

import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootRoute
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryChannel
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryChannelDescriptor
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryCustomPoint
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryEntry
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryFailure
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryKind
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryMutationResult
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryOperations
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryPayload
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryResult
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryScene
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibrarySnapshot
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryTarget
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class DeviceLightLibraryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `refresh failure and offline presence retain the last library presentation`() {
        val library = FakeLibraryOperations()
        val root = FakeRootOperations(onlineRoot())
        val viewModel = DeviceLightLibraryViewModel(library, root).apply { bind(DEVICE_UID) }

        assertEquals(listOf(ENTRY_ID), viewModel.uiState.value.entries.map { entry -> entry.id })
        assertEquals(DeviceConnectionVisualState.ONLINE, viewModel.uiState.value.connectionVisualState)
        assertTrue(viewModel.uiState.value.firmwareWritesEnabled)

        library.publish(DeviceLightLibraryResult.Failed(DeviceLightLibraryFailure.UNAVAILABLE))
        root.publish(onlineRoot(OwnerDeviceAvailability.UNREACHABLE))

        val retained = viewModel.uiState.value
        assertEquals(listOf(ENTRY_ID), retained.entries.map { entry -> entry.id })
        assertNotNull(retained.target)
        assertNotNull(retained.readError)
        assertFalse(retained.initialLoading)
        assertEquals(DeviceConnectionVisualState.OFFLINE, retained.connectionVisualState)
        assertFalse(retained.firmwareWritesEnabled)

        viewModel.load(ENTRY_ID)
        viewModel.rename(ENTRY_ID, "Renamed")
        viewModel.delete(ENTRY_ID)

        assertEquals(0, library.loadCalls)
        assertEquals(1, library.renameCalls)
        assertEquals(1, library.deleteCalls)
    }

    @Test
    fun `successful load remains pending until firmware confirms the loaded entry`() {
        val library = FakeLibraryOperations()
        val viewModel = DeviceLightLibraryViewModel(
            operations = library,
            rootOperations = FakeRootOperations(onlineRoot())
        ).apply { bind(DEVICE_UID) }

        viewModel.load(ENTRY_ID)

        assertEquals(ENTRY_ID, viewModel.uiState.value.activeLoadEntryId)
        assertEquals(1, library.loadCalls)

        library.publish(availableResult(isLoaded = true))

        assertNull(viewModel.uiState.value.activeLoadEntryId)
        assertTrue(viewModel.uiState.value.entries.single().isLoaded)
    }

    @Test
    fun `central presence updates refresh data without reconnecting from Library`() {
        val library = FakeLibraryOperations()
        val root = FakeRootOperations(onlineRoot())
        val viewModel = DeviceLightLibraryViewModel(library, root).apply { bind(DEVICE_UID) }

        assertEquals(0, root.connectCalls)
        assertEquals(1, library.refreshCalls)

        root.publish(onlineRoot(OwnerDeviceAvailability.UNREACHABLE))
        root.publish(onlineRoot())

        assertEquals(2, library.refreshCalls)
        assertEquals(0, root.connectCalls)
        assertEquals(listOf(ENTRY_ID), viewModel.uiState.value.entries.map { entry -> entry.id })
    }

    @Test
    fun `presentation remains visible while current generation write authority is pending`() {
        val library = FakeLibraryOperations(availableResult(firmwareWriteAuthoritative = false))
        val viewModel = DeviceLightLibraryViewModel(
            operations = library,
            rootOperations = FakeRootOperations(onlineRoot())
        ).apply { bind(DEVICE_UID) }

        assertEquals(DeviceConnectionVisualState.ONLINE, viewModel.uiState.value.connectionVisualState)
        assertEquals(listOf(ENTRY_ID), viewModel.uiState.value.entries.map { entry -> entry.id })
        assertFalse(viewModel.uiState.value.firmwareWritesEnabled)

        viewModel.load(ENTRY_ID)
        assertEquals(0, library.loadCalls)

        library.publish(availableResult(firmwareWriteAuthoritative = true))
        viewModel.load(ENTRY_ID)

        assertEquals(1, library.loadCalls)
    }

    @Test
    fun `unknown central presence does not expose a transient cold error`() {
        val library = FakeLibraryOperations(
            DeviceLightLibraryResult.Failed(DeviceLightLibraryFailure.NOT_CONNECTED)
        )
        val root = FakeRootOperations(null)
        val viewModel = DeviceLightLibraryViewModel(library, root).apply { bind(DEVICE_UID) }

        assertTrue(viewModel.uiState.value.initialLoading)

        root.publish(onlineRoot(OwnerDeviceAvailability.UNREACHABLE))

        assertFalse(viewModel.uiState.value.initialLoading)
        assertNotNull(viewModel.uiState.value.readError)
    }

    private class FakeLibraryOperations(
        initial: DeviceLightLibraryResult = availableResult()
    ) : DeviceLightLibraryOperations {
        private val results = MutableStateFlow(initial)
        var refreshCalls = 0
        var loadCalls = 0
        var renameCalls = 0
        var deleteCalls = 0

        override fun observeLibrary(deviceUid: String): Flow<DeviceLightLibraryResult> = results

        override suspend fun usedNames(kind: DeviceLightLibraryKind): List<String> = emptyList()

        override suspend fun refreshInstalledCustom(deviceUid: String) {
            refreshCalls += 1
        }

        override suspend fun saveManual(
            deviceUid: String,
            name: String,
            scene: DeviceLightLibraryScene
        ) = DeviceLightLibraryMutationResult.Success("manual")

        override suspend fun saveCustom(
            deviceUid: String,
            name: String,
            weekdaysMask: Int,
            points: List<DeviceLightLibraryCustomPoint>
        ) = DeviceLightLibraryMutationResult.Success("custom")

        override suspend fun rename(
            entryId: String,
            name: String
        ): DeviceLightLibraryMutationResult.Success {
            renameCalls += 1
            return DeviceLightLibraryMutationResult.Success(entryId)
        }

        override suspend fun delete(entryId: String): DeviceLightLibraryMutationResult.Success {
            deleteCalls += 1
            return DeviceLightLibraryMutationResult.Success(entryId)
        }

        override suspend fun load(
            deviceUid: String,
            entryId: String
        ): DeviceLightLibraryMutationResult.Success {
            loadCalls += 1
            return DeviceLightLibraryMutationResult.Success(entryId)
        }

        fun publish(result: DeviceLightLibraryResult) {
            results.value = result
        }
    }

    private class FakeRootOperations(initial: DeviceRootSnapshot?) : DeviceRootOperations {
        private val snapshots = MutableStateFlow(initial)
        var connectCalls = 0

        override fun observe(deviceUid: String): Flow<DeviceRootSnapshot?> = snapshots

        override fun current(deviceUid: String): DeviceRootSnapshot? = snapshots.value

        override fun connect(deviceUid: String): Result<Unit> {
            connectCalls += 1
            return Result.success(Unit)
        }

        override fun authorizeRoute(deviceUid: String, route: DeviceRootRoute): Boolean = true

        fun publish(snapshot: DeviceRootSnapshot?) {
            snapshots.value = snapshot
        }
    }

    class MainDispatcherRule(
        private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
    ) : TestWatcher() {
        override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
        override fun finished(description: Description) = Dispatchers.resetMain()
    }

    private companion object {
        const val DEVICE_UID = "library-light"
        const val PRODUCT_KEY = "LIGHT_WRGB_PRO_ELITE"
        const val ENTRY_ID = "manual-entry"

        fun onlineRoot(
            availability: OwnerDeviceAvailability = OwnerDeviceAvailability.REACHABLE
        ) = DeviceRootSnapshot(
            deviceUid = DEVICE_UID,
            title = "Library Light",
            availability = availability,
            family = OwnerDeviceFamily.LIGHT,
            catalogState = DeviceRootCatalogState.VALID,
            productKey = PRODUCT_KEY
        )

        fun availableResult(
            isLoaded: Boolean = false,
            firmwareWriteAuthoritative: Boolean = true
        ): DeviceLightLibraryResult.Available {
            val channels = DeviceLightLibraryChannel.entries
            val scene = DeviceLightLibraryScene(channels.associateWith { channel -> channel.ordinal * 10 })
            return DeviceLightLibraryResult.Available(
                DeviceLightLibrarySnapshot(
                    target = DeviceLightLibraryTarget(
                        deviceUid = DEVICE_UID,
                        productKey = PRODUCT_KEY,
                        channelDescriptors = channels.mapIndexed { index, channel ->
                            DeviceLightLibraryChannelDescriptor(
                                channel = channel,
                                key = channel.name.lowercase(),
                                displayName = "Firmware ${channel.name}",
                                displayColorRgb = index,
                                order = index
                            )
                        },
                        estimatedPowerWatts = 80
                    ),
                    entries = listOf(
                        DeviceLightLibraryEntry(
                            id = ENTRY_ID,
                            name = "Manual scene",
                            productKey = PRODUCT_KEY,
                            channels = channels,
                            payload = DeviceLightLibraryPayload.Manual(scene),
                            createdAtMillis = 1L,
                            updatedAtMillis = 1L,
                            isLoaded = isLoaded
                        )
                    ),
                    firmwareWriteAuthoritative = firmwareWriteAuthoritative
                )
            )
        }
    }
}
