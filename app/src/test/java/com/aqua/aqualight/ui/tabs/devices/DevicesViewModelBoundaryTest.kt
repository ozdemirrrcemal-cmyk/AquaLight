package com.aqua.aqualight.ui.tabs.devices

import com.aqua.aqualight.application.devices.DeleteOwnerDevicesResult
import com.aqua.aqualight.application.devices.DeviceMenuAccessOperations
import com.aqua.aqualight.application.devices.DeviceMenuAccessResult
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.OwnerDeviceListItem
import com.aqua.aqualight.application.devices.OwnerDevicesOperations
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactStatusStyle
import com.aqua.aqualight.ui.tabs.devices.route.DeviceMenuPresentationState
import com.aqua.aqualight.ui.tabs.devices.route.DeviceRouteResolver
import com.aqua.aqualight.ui.tabs.devices.route.DeviceRouteTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
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

@OptIn(ExperimentalCoroutinesApi::class)
class DevicesViewModelBoundaryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `device cards are rendered from one application boundary`() {
        val operations = FakeOwnerDevicesOperations(
            initialDevices = listOf(
                OwnerDeviceListItem(
                    deviceUid = "device-1",
                    displayName = "AquaLight One",
                    serialText = "AQL-0001",
                    family = OwnerDeviceFamily.LIGHT,
                    availability = OwnerDeviceAvailability.REACHABLE,
                    assignedTankName = "Tank 1"
                )
            )
        )

        val viewModel = createViewModel(operations)
        val state = viewModel.uiState.value

        assertEquals(1, operations.startCalls)
        assertEquals(1, operations.refreshCalls)
        assertFalse(state.isEmpty)
        assertEquals(1, state.devices.size)
        assertEquals("device-1", state.devices.single().deviceUid)
        assertEquals("AquaLight One", state.devices.single().card.displayName)
        assertEquals("AQL-0001", state.devices.single().card.serialText)
        assertEquals("Tank 1", state.devices.single().card.supportingText)
        assertEquals(DeviceCompactStatusStyle.ONLINE, state.devices.single().card.statusStyle)
    }

    @Test
    fun `available device menu result is mapped to UI route`() = runTest {
        val menuOperations = FakeDeviceMenuAccessOperations(
            result = DeviceMenuAccessResult.Available(
                deviceUid = "device-1",
                title = "AquaLight One",
                family = OwnerDeviceFamily.LIGHT,
                presentationPrepared = true
            )
        )
        val viewModel = createViewModel(
            operations = FakeOwnerDevicesOperations(listOf(device("device-1"))),
            menuOperations = menuOperations
        )

        viewModel.onDeviceClicked("device-1")
        val menuState = viewModel.uiState.first { state ->
            state.deviceMenuState is DeviceMenuPresentationState.Ready
        }.deviceMenuState as DeviceMenuPresentationState.Ready

        assertEquals("device-1", menuOperations.lastRequest)
        assertEquals("device-1", menuState.route.deviceUid)
        assertEquals("AquaLight One", menuState.route.title)
        assertEquals(DeviceRouteTarget.LIGHT_ROOT, menuState.route.target)
        assertTrue(menuState.route.presentationPrepared)
        assertTrue(viewModel.uiState.value.isOpeningDeviceMenu)

        viewModel.onDeviceMenuResultHandled(menuState.requestId)

        assertFalse(viewModel.uiState.value.isOpeningDeviceMenu)
        assertEquals(
            DeviceMenuPresentationState.Idle,
            viewModel.uiState.value.deviceMenuState
        )
    }

    @Test
    fun `available result without presentation proof is rejected before navigation`() = runTest {
        val viewModel = createViewModel(
            operations = FakeOwnerDevicesOperations(listOf(device("device-1"))),
            menuOperations = FakeDeviceMenuAccessOperations(
                DeviceMenuAccessResult.Available(
                    deviceUid = "device-1",
                    title = "Dose Pro",
                    family = OwnerDeviceFamily.DOSING
                )
            )
        )

        viewModel.onDeviceClicked("device-1")
        val menuState = viewModel.uiState.first { state ->
            state.deviceMenuState is DeviceMenuPresentationState.Failure
        }.deviceMenuState as DeviceMenuPresentationState.Failure

        assertEquals("device-1", menuState.deviceUid)
        assertTrue(viewModel.uiState.value.isOpeningDeviceMenu)

        viewModel.onDeviceMenuResultHandled(menuState.requestId)

        assertFalse(viewModel.uiState.value.isOpeningDeviceMenu)
    }

    @Test
    fun `partial delete keeps only failed devices selected`() = runTest {
        val operations = FakeOwnerDevicesOperations(
            initialDevices = listOf(
                device(uid = "device-1"),
                device(uid = "device-2")
            )
        ).apply {
            deleteResult = DeleteOwnerDevicesResult(
                succeededDeviceUids = setOf("device-1"),
                failedDeviceUids = setOf("device-2")
            )
        }
        val viewModel = createViewModel(operations)

        viewModel.onDeviceLongClicked("device-1")
        viewModel.onDeviceLongClicked("device-2")
        viewModel.deleteSelectedDevices()

        val event = viewModel.events.first()
        val partial = event as DevicesEvent.ShowDeletePartialSuccess

        assertEquals(setOf("device-1", "device-2"), operations.lastDeleteRequest)
        assertEquals(1, partial.succeededCount)
        assertEquals(1, partial.failedCount)
        assertTrue(viewModel.uiState.value.selectionMode)
        assertEquals(1, viewModel.uiState.value.selectedCount)
        assertFalse(viewModel.uiState.value.devices.first { it.deviceUid == "device-1" }.isSelected)
        assertTrue(viewModel.uiState.value.devices.first { it.deviceUid == "device-2" }.isSelected)
    }

    private fun createViewModel(
        operations: OwnerDevicesOperations,
        menuOperations: DeviceMenuAccessOperations = FakeDeviceMenuAccessOperations()
    ): DevicesViewModel {
        return DevicesViewModel(
            operations = operations,
            menuAccessOperations = menuOperations,
            routeResolver = DeviceRouteResolver()
        )
    }

    private fun device(uid: String) = OwnerDeviceListItem(
        deviceUid = uid,
        displayName = uid,
        serialText = uid,
        family = OwnerDeviceFamily.UNKNOWN,
        availability = OwnerDeviceAvailability.UNREACHABLE
    )

    private class FakeOwnerDevicesOperations(
        initialDevices: List<OwnerDeviceListItem>
    ) : OwnerDevicesOperations {
        override val devices = MutableStateFlow(initialDevices)

        var startCalls = 0
        var refreshCalls = 0
        var lastDeleteRequest: Set<String> = emptySet()
        var deleteResult = DeleteOwnerDevicesResult(
            succeededDeviceUids = emptySet(),
            failedDeviceUids = emptySet()
        )

        override fun start(scope: CoroutineScope): Job {
            startCalls += 1
            return Job().apply { complete() }
        }

        override fun refreshVisibleDevices() {
            refreshCalls += 1
        }

        override suspend fun deleteDevices(
            deviceUids: Set<String>
        ): DeleteOwnerDevicesResult {
            lastDeleteRequest = deviceUids
            return deleteResult
        }
    }

    private class FakeDeviceMenuAccessOperations(
        var result: DeviceMenuAccessResult = DeviceMenuAccessResult.Unavailable(
            title = "",
            reason = DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN
        )
    ) : DeviceMenuAccessOperations {
        var lastRequest: String = ""

        override suspend fun resolve(deviceUid: String): DeviceMenuAccessResult {
            lastRequest = deviceUid
            return result
        }
    }

    class MainDispatcherRule(
        private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
    ) : TestWatcher() {
        override fun starting(description: Description) {
            Dispatchers.setMain(dispatcher)
        }

        override fun finished(description: Description) {
            Dispatchers.resetMain()
        }
    }
}
