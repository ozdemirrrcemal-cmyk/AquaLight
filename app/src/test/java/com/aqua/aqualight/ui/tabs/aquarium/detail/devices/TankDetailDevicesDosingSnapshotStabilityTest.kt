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
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCardChannelSummary
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCardOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCardState
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCardSummary
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactStatusStyle
import com.aqua.aqualight.ui.tabs.devices.route.DeviceRouteResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class TankDetailDevicesDosingSnapshotStabilityTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `retained dosing snapshot stays visible while device is unreachable`() {
        val assignments = FakeTankDeviceAssignmentOperations(
            assigned = listOf(dosingDevice(OwnerDeviceAvailability.REACHABLE))
        )
        val cardOperations = FakeDeviceDosingCardOperations().apply {
            updateState(DEVICE_UID, readyState(RETAINED_CHANNEL_TITLE))
        }
        val viewModel = createViewModel(assignments, cardOperations)

        viewModel.bind(TANK_ID)
        assertReadyCard(viewModel, DeviceCompactStatusStyle.ONLINE)

        assignments.updateAssigned(
            listOf(dosingDevice(OwnerDeviceAvailability.UNREACHABLE))
        )

        assertReadyCard(viewModel, DeviceCompactStatusStyle.OFFLINE)
        assertEquals(listOf(DEVICE_UID), cardOperations.observeRequests)

        assignments.updateAssigned(
            listOf(dosingDevice(OwnerDeviceAvailability.REACHABLE))
        )

        assertReadyCard(viewModel, DeviceCompactStatusStyle.ONLINE)
        assertEquals(listOf(DEVICE_UID, DEVICE_UID), cardOperations.observeRequests)
    }

    @Test
    fun `removing assignment clears retained dosing presentation`() {
        val assignments = FakeTankDeviceAssignmentOperations(
            assigned = listOf(dosingDevice(OwnerDeviceAvailability.REACHABLE))
        )
        val cardOperations = FakeDeviceDosingCardOperations().apply {
            updateState(DEVICE_UID, readyState(RETAINED_CHANNEL_TITLE))
        }
        val viewModel = createViewModel(assignments, cardOperations)

        viewModel.bind(TANK_ID)
        assertReadyCard(viewModel, DeviceCompactStatusStyle.ONLINE)

        assignments.updateAssigned(emptyList())
        assertTrue(viewModel.uiState.value.devices.isEmpty())

        assignments.updateAssigned(
            listOf(dosingDevice(OwnerDeviceAvailability.UNREACHABLE))
        )

        val dosingCard = requireNotNull(viewModel.uiState.value.devices.single().dosingCard)
        assertEquals(DosingDeviceSpotlightContentState.PREPARING, dosingCard.contentState)
        assertNull(dosingCard.summary)
        assertNull(dosingCard.selectedChannel)
        assertEquals(listOf(DEVICE_UID), cardOperations.observeRequests)
    }

    private fun createViewModel(
        assignments: TankDeviceAssignmentOperations,
        cardOperations: DeviceDosingCardOperations
    ): TankDetailDevicesViewModel = TankDetailDevicesViewModel(
        assignmentOperations = assignments,
        menuOpenUseCase = DeviceMenuOpenUseCase(
            menuAccessOperations = FakeMenuAccessOperations(),
            controlSurfacePreparationOperations = FakePreparationOperations()
        ),
        routeResolver = DeviceRouteResolver(),
        dosingCardOperations = cardOperations
    )

    private fun assertReadyCard(
        viewModel: TankDetailDevicesViewModel,
        expectedStatus: DeviceCompactStatusStyle
    ) {
        val item = viewModel.uiState.value.devices.single()
        val dosingCard = requireNotNull(item.dosingCard)
        assertEquals(expectedStatus, item.card.statusStyle)
        assertEquals(DosingDeviceSpotlightContentState.READY, dosingCard.contentState)
        assertEquals(RETAINED_CHANNEL_TITLE, dosingCard.selectedChannel?.title)
    }

    private fun dosingDevice(availability: OwnerDeviceAvailability): TankDeviceListItem =
        TankDeviceListItem(
            deviceUid = DEVICE_UID,
            displayName = DEVICE_NAME,
            serialText = DEVICE_SERIAL,
            family = OwnerDeviceFamily.DOSING,
            availability = availability
        )

    private fun readyState(channelTitle: String): DeviceDosingCardState =
        DeviceDosingCardState.Ready(
            DeviceDosingCardSummary(
                deviceUid = DEVICE_UID,
                channelCount = CHANNEL_COUNT,
                activeChannelCount = ACTIVE_CHANNEL_COUNT,
                channels = listOf(
                    DeviceDosingCardChannelSummary(
                        channelNumber = CHANNEL_NUMBER,
                        title = channelTitle,
                        runtimeEnabled = true,
                        dailyDoseMicroliters = null,
                        nextDose = null,
                        reservoir = null
                    )
                )
            )
        )

    private class FakeTankDeviceAssignmentOperations(
        assigned: List<TankDeviceListItem>
    ) : TankDeviceAssignmentOperations {
        private val assignedFlow = MutableStateFlow(assigned)

        fun updateAssigned(devices: List<TankDeviceListItem>) {
            assignedFlow.value = devices
        }

        override fun start(scope: CoroutineScope): Job = Job().apply { complete() }

        override fun assignedDevices(tankId: Long): Flow<List<TankDeviceListItem>> = assignedFlow

        override fun availableDevices(tankId: Long): Flow<AvailableTankDevicesSnapshot> = flowOf(
            AvailableTankDevicesSnapshot(
                devices = emptyList(),
                hasRegisteredDevices = false
            )
        )

        override suspend fun assignDevice(
            tankId: Long,
            deviceUid: String
        ): AssignDeviceToTankResult = AssignDeviceToTankResult.Assigned

        override suspend fun removeDevice(
            tankId: Long,
            deviceUid: String
        ): RemoveDeviceFromTankResult = RemoveDeviceFromTankResult.REMOVED
    }

    private class FakeDeviceDosingCardOperations : DeviceDosingCardOperations {
        val observeRequests = mutableListOf<String>()
        private val states = mutableMapOf<String, MutableStateFlow<DeviceDosingCardState>>()

        fun updateState(deviceUid: String, state: DeviceDosingCardState) {
            stateFlow(deviceUid).value = state
        }

        override fun observe(deviceUid: String): Flow<DeviceDosingCardState> {
            observeRequests += deviceUid
            return stateFlow(deviceUid)
        }

        private fun stateFlow(deviceUid: String): MutableStateFlow<DeviceDosingCardState> =
            states.getOrPut(deviceUid) {
                MutableStateFlow(DeviceDosingCardState.Preparing)
            }
    }

    private class FakeMenuAccessOperations : DeviceMenuAccessOperations {
        override suspend fun resolve(deviceUid: String): DeviceMenuAccessResult =
            DeviceMenuAccessResult.Unavailable(
                title = DEVICE_NAME,
                reason = DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN
            )
    }

    private class FakePreparationOperations : DeviceControlSurfacePreparationOperations {
        override suspend fun prepare(
            request: DeviceControlSurfacePreparationRequest
        ): DeviceControlSurfacePreparationResult = DeviceControlSurfacePreparationResult.Ready

        override fun discardFreshPreparation(
            deviceUid: String,
            family: OwnerDeviceFamily
        ) = Unit
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

    private companion object {
        const val TANK_ID = 10L
        const val DEVICE_UID = "dose-1"
        const val DEVICE_NAME = "Dose Pro 4"
        const val DEVICE_SERIAL = "AQL-dose-1"
        const val RETAINED_CHANNEL_TITLE = "Nitrate"
        const val CHANNEL_COUNT = 1
        const val ACTIVE_CHANNEL_COUNT = 1
        const val CHANNEL_NUMBER = 1
    }
}
