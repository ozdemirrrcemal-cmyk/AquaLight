package com.aqua.aqualight.ui.tabs.aquarium.detail.devices

import com.aqua.aqualight.application.devices.AssignDeviceToTankResult
import com.aqua.aqualight.application.devices.AvailableTankDevicesSnapshot
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationOperations
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationRequest
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationResult
import com.aqua.aqualight.application.devices.DeviceMenuAccessOperations
import com.aqua.aqualight.application.devices.DeviceMenuAccessResult
import com.aqua.aqualight.application.devices.DeviceMenuOpenUseCase
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.RemoveDeviceFromTankResult
import com.aqua.aqualight.application.devices.TankDeviceAssignmentOperations
import com.aqua.aqualight.application.devices.TankDeviceListItem
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactStatusStyle
import com.aqua.aqualight.ui.tabs.aquarium.detail.devices.select.TankDeviceSelectEmptyReason
import com.aqua.aqualight.ui.tabs.aquarium.detail.devices.select.TankDeviceSelectEvent
import com.aqua.aqualight.ui.tabs.aquarium.detail.devices.select.TankDeviceSelectViewModel
import com.aqua.aqualight.ui.tabs.devices.route.DeviceRouteResolver
import com.aqua.aqualight.ui.tabs.devices.route.DeviceRouteTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
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
class TankDeviceAssignmentViewModelBoundaryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `tank detail renders assigned devices from application boundary`() {
        val operations = FakeTankDeviceAssignmentOperations(
            assigned = listOf(device("device-1"))
        )
        val viewModel = createTankDetailViewModel(operations)

        viewModel.bind(10L)

        val state = viewModel.uiState.value
        assertEquals(1, operations.startCalls)
        assertFalse(state.isEmpty)
        assertEquals("device-1", state.devices.single().deviceUid)
        assertEquals("AquaLight device-1", state.devices.single().card.displayName)
        assertEquals(DeviceCompactStatusStyle.ONLINE, state.devices.single().card.statusStyle)
    }

    @Test
    fun `tank detail menu open uses shared preparation contract before route`() = runTest {
        val preparation = FakePreparationOperations()
        val viewModel = createTankDetailViewModel(
            operations = FakeTankDeviceAssignmentOperations(
                assigned = listOf(device("device-1"))
            ),
            menuAccess = FakeMenuAccessOperations(
                DeviceMenuAccessResult.Available(
                    deviceUid = "device-1",
                    title = "Dose Pro 4",
                    family = OwnerDeviceFamily.DOSING
                )
            ),
            preparation = preparation
        )
        viewModel.bind(10L)

        viewModel.onDeviceClicked("device-1")
        val event = viewModel.events.first() as TankDetailDevicesEvent.OpenDeviceRoute

        assertEquals("device-1", preparation.lastRequest?.deviceUid)
        assertEquals(OwnerDeviceFamily.DOSING, preparation.lastRequest?.family)
        assertEquals(DeviceRouteTarget.DOSING_ROOT, event.route.target)
        assertTrue(viewModel.uiState.value.isOpeningDeviceMenu)

        viewModel.onDeviceNavigationFinished("device-1", committed = false)

        assertEquals("device-1" to OwnerDeviceFamily.DOSING, preparation.lastDiscardRequest)
        assertFalse(viewModel.uiState.value.isOpeningDeviceMenu)
    }

    @Test
    fun `tank detail emits remove failure from typed application result`() = runTest {
        val operations = FakeTankDeviceAssignmentOperations(
            assigned = listOf(device("device-1"))
        ).apply {
            removeResult = RemoveDeviceFromTankResult.FAILURE
        }
        val viewModel = createTankDetailViewModel(operations)
        viewModel.bind(10L)

        viewModel.removeDeviceFromTank("device-1")

        assertEquals(TankDetailDevicesEvent.ShowRemoveFailed, viewModel.events.first())
        assertEquals(10L to "device-1", operations.lastRemoveRequest)
    }

    @Test
    fun `selection distinguishes all registered devices assigned`() {
        val operations = FakeTankDeviceAssignmentOperations(
            available = AvailableTankDevicesSnapshot(
                devices = emptyList(),
                hasRegisteredDevices = true
            )
        )
        val viewModel = TankDeviceSelectViewModel(operations)

        viewModel.bind(10L)

        val state = viewModel.uiState.value
        assertTrue(state.isEmpty)
        assertEquals(
            TankDeviceSelectEmptyReason.ALL_REGISTERED_DEVICES_ASSIGNED,
            state.emptyReason
        )
    }

    @Test
    fun `selection exposes assignment conflict tank id`() = runTest {
        val operations = FakeTankDeviceAssignmentOperations(
            available = AvailableTankDevicesSnapshot(
                devices = listOf(device("device-1")),
                hasRegisteredDevices = true
            )
        ).apply {
            assignResult = AssignDeviceToTankResult.Conflict(existingTankId = 42L)
        }
        val viewModel = TankDeviceSelectViewModel(operations)
        viewModel.bind(10L)

        viewModel.onDeviceClicked(viewModel.uiState.value.devices.single())

        val event = viewModel.events.first() as TankDeviceSelectEvent.ShowAssignmentConflict
        assertEquals(42L, event.existingTankId)
        assertEquals(10L to "device-1", operations.lastAssignRequest)
    }

    private fun createTankDetailViewModel(
        operations: TankDeviceAssignmentOperations,
        menuAccess: DeviceMenuAccessOperations = FakeMenuAccessOperations(),
        preparation: DeviceControlSurfacePreparationOperations = FakePreparationOperations()
    ): TankDetailDevicesViewModel = TankDetailDevicesViewModel(
        assignmentOperations = operations,
        menuOpenUseCase = DeviceMenuOpenUseCase(
            menuAccessOperations = menuAccess,
            controlSurfacePreparationOperations = preparation
        ),
        routeResolver = DeviceRouteResolver()
    )

    private fun device(uid: String) = TankDeviceListItem(
        deviceUid = uid,
        displayName = "AquaLight $uid",
        serialText = "AQL-$uid",
        family = OwnerDeviceFamily.LIGHT,
        availability = OwnerDeviceAvailability.REACHABLE
    )

    private class FakeTankDeviceAssignmentOperations(
        assigned: List<TankDeviceListItem> = emptyList(),
        available: AvailableTankDevicesSnapshot = AvailableTankDevicesSnapshot(
            devices = emptyList(),
            hasRegisteredDevices = false
        )
    ) : TankDeviceAssignmentOperations {
        private val assignedFlow = MutableStateFlow(assigned)
        private val availableFlow = MutableStateFlow(available)

        var startCalls: Int = 0
        var assignResult: AssignDeviceToTankResult = AssignDeviceToTankResult.Assigned
        var removeResult: RemoveDeviceFromTankResult = RemoveDeviceFromTankResult.REMOVED
        var lastAssignRequest: Pair<Long, String>? = null
        var lastRemoveRequest: Pair<Long, String>? = null

        override fun start(scope: CoroutineScope): Job {
            startCalls += 1
            return Job().apply { complete() }
        }

        override fun assignedDevices(tankId: Long): Flow<List<TankDeviceListItem>> =
            assignedFlow

        override fun availableDevices(tankId: Long): Flow<AvailableTankDevicesSnapshot> =
            availableFlow

        override suspend fun assignDevice(
            tankId: Long,
            deviceUid: String
        ): AssignDeviceToTankResult {
            lastAssignRequest = tankId to deviceUid
            return assignResult
        }

        override suspend fun removeDevice(
            tankId: Long,
            deviceUid: String
        ): RemoveDeviceFromTankResult {
            lastRemoveRequest = tankId to deviceUid
            return removeResult
        }
    }

    private class FakeMenuAccessOperations(
        private val result: DeviceMenuAccessResult = DeviceMenuAccessResult.Unavailable(
            title = "",
            reason = DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN
        )
    ) : DeviceMenuAccessOperations {
        override suspend fun resolve(deviceUid: String): DeviceMenuAccessResult = result
    }

    private class FakePreparationOperations : DeviceControlSurfacePreparationOperations {
        var lastRequest: DeviceControlSurfacePreparationRequest? = null
        var lastDiscardRequest: Pair<String, OwnerDeviceFamily>? = null

        override suspend fun prepare(
            request: DeviceControlSurfacePreparationRequest
        ): DeviceControlSurfacePreparationResult {
            lastRequest = request
            return DeviceControlSurfacePreparationResult.Ready
        }

        override fun discardFreshPreparation(
            deviceUid: String,
            family: OwnerDeviceFamily
        ) {
            lastDiscardRequest = deviceUid to family
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
