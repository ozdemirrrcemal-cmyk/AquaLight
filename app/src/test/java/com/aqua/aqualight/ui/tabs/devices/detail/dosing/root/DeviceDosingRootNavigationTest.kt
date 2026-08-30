package com.aqua.aqualight.ui.tabs.devices.detail.dosing.root

import com.aqua.aqualight.application.devices.DeviceChannelSlots
import com.aqua.aqualight.application.devices.DeviceChannelWireKey
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationOperations
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationRequest
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationResult
import com.aqua.aqualight.application.devices.DeviceDosingChannelSlot
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootRoute
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.DeviceSlotIndex
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelDestination
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelNavigationOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelNavigationTarget
import com.aqua.aqualight.data.devices.dosing.UnavailableDeviceDosingChannelOperations
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.sampleDosingChannelSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceDosingRootNavigationTest {

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
    fun `channel click emits only the centrally resolved target`() = runTest(dispatcher) {
        val target = DeviceDosingChannelNavigationTarget(
            deviceUid = DEVICE_UID,
            slotId = SLOT_ID,
            pumpCount = 2,
            channelNumber = 1,
            lastCalibratedAtEpochSeconds = 1_786_320_000L,
            destination = DeviceDosingChannelDestination.DETAIL
        )
        val navigationOperations = FakeChannelNavigationOperations(target)
        val viewModel = DeviceDosingRootViewModel(
            operations = FakeRootOperations(),
            channelNavigationOperations = navigationOperations,
            channelOperations = FakeChannelOperations(),
            controlSurfacePreparationOperations = FakePreparationOperations()
        )
        viewModel.bind(deviceUidText = DEVICE_UID)

        viewModel.openChannel(" $SLOT_ID ")

        assertEquals(target, viewModel.navigationEvents.first())
        assertEquals(DEVICE_UID, navigationOperations.requestedDeviceUid)
        assertEquals(SLOT_ID, navigationOperations.requestedSlotId)
    }

    @Test
    fun `dosing header starts from and follows dynamic device title`() = runTest(dispatcher) {
        val rootOperations = FakeRootOperations(initialTitle = "Dose Pro 4")
        val viewModel = DeviceDosingRootViewModel(
            operations = rootOperations,
            channelNavigationOperations = FakeChannelNavigationOperations(null),
            channelOperations = FakeChannelOperations(),
            controlSurfacePreparationOperations = FakePreparationOperations()
        )

        viewModel.bind(deviceUidText = DEVICE_UID)

        assertEquals("Dose Pro 4", viewModel.uiState.value.title)

        rootOperations.publishTitle("My Doser")

        assertEquals("My Doser", viewModel.uiState.value.title)
    }

    private class FakePreparationOperations : DeviceControlSurfacePreparationOperations {
        private var fresh = true

        override suspend fun prepare(
            request: DeviceControlSurfacePreparationRequest
        ): DeviceControlSurfacePreparationResult = DeviceControlSurfacePreparationResult.Ready

        override fun consumeFreshPreparation(
            deviceUid: String,
            family: OwnerDeviceFamily
        ): Boolean = fresh.also { fresh = false }
    }

    private class FakeChannelOperations :
        DeviceDosingChannelOperations by UnavailableDeviceDosingChannelOperations {
        private val snapshots = listOf(
            sampleDosingChannelSnapshot().copy(
                slotId = SLOT_ID,
                channelNumber = 1,
                channelTitle = "Channel 1"
            ),
            sampleDosingChannelSnapshot()
        )
        private val stream = MutableStateFlow(snapshots)

        override fun observeAll(deviceUid: String): Flow<List<DeviceDosingChannelSnapshot>> =
            stream

        override fun current(deviceUid: String, slotId: String): DeviceDosingChannelSnapshot? =
            snapshots.singleOrNull { snapshot ->
                snapshot.deviceUid == deviceUid && snapshot.slotId == slotId
            }
    }

    private class FakeChannelNavigationOperations(
        private val target: DeviceDosingChannelNavigationTarget?
    ) : DeviceDosingChannelNavigationOperations {
        var requestedDeviceUid: String = ""
        var requestedSlotId: String = ""

        override suspend fun resolve(
            deviceUid: String,
            slotId: String
        ): DeviceDosingChannelNavigationTarget? {
            requestedDeviceUid = deviceUid
            requestedSlotId = slotId
            return target
        }
    }

    private class FakeRootOperations(
        initialTitle: String = "Dose Pro"
    ) : DeviceRootOperations {
        private val snapshots = MutableStateFlow<DeviceRootSnapshot?>(snapshot(initialTitle))

        override fun observe(deviceUid: String): Flow<DeviceRootSnapshot?> = snapshots

        override fun current(deviceUid: String): DeviceRootSnapshot? = snapshots.value

        override fun connect(deviceUid: String): Result<Unit> = Result.success(Unit)

        fun publishTitle(title: String) {
            snapshots.value = snapshot(title)
        }

        private fun snapshot(title: String) = DeviceRootSnapshot(
            deviceUid = DEVICE_UID,
            title = title,
            availability = OwnerDeviceAvailability.REACHABLE,
            family = OwnerDeviceFamily.DOSING,
            catalogState = DeviceRootCatalogState.VALID,
            dosingChannelCount = CHANNEL_COUNT,
            channelSlots = DeviceChannelSlots(
                lightChannels = emptyList(),
                timerChannels = emptyList(),
                dosingChannels = List(CHANNEL_COUNT) { index ->
                    DeviceDosingChannelSlot(
                        index = DeviceSlotIndex(index),
                        wireKey = DeviceChannelWireKey("channel${index + 1}"),
                        defaultDisplayName = "Channel ${index + 1}",
                        displayNameEditable = true
                    )
                },
                fanOutputs = emptyList(),
                temperatureSensors = emptyList()
            ),
            allowedRoutes = setOf(
                DeviceRootRoute.DOSING_CHANNELS,
                DeviceRootRoute.DOSING_CALIBRATION
            )
        )
    }

    private companion object {
        const val DEVICE_UID = "device-1"
        const val SLOT_ID = "dosing:channel1"
        const val CHANNEL_COUNT = 2
    }
}
