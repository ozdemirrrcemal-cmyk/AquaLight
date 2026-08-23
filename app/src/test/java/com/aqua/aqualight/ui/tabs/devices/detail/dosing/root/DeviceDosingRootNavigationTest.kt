package com.aqua.aqualight.ui.tabs.devices.detail.dosing.root

import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelDestination
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelNavigationOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelNavigationTarget
import com.aqua.aqualight.data.devices.dosing.UnavailableDeviceDosingChannelOperations
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
            channelOperations = UnavailableDeviceDosingChannelOperations
        )
        viewModel.bind(deviceUidText = DEVICE_UID, fallbackTitle = "Dose Pro")

        viewModel.openChannel(" $SLOT_ID ")

        assertEquals(target, viewModel.navigationEvents.first())
        assertEquals(DEVICE_UID, navigationOperations.requestedDeviceUid)
        assertEquals(SLOT_ID, navigationOperations.requestedSlotId)
    }

    @Test
    fun `first header frame uses current canonical root title instead of stale navigation title`() =
        runTest(dispatcher) {
            val rootOperations = FakeRootOperations(initialTitle = "Dose Pro 4")
            val viewModel = DeviceDosingRootViewModel(
                operations = rootOperations,
                channelNavigationOperations = FakeChannelNavigationOperations(null),
                channelOperations = UnavailableDeviceDosingChannelOperations
            )

            viewModel.bind(
                deviceUidText = DEVICE_UID,
                fallbackTitle = "Legacy dosing title"
            )

            assertEquals("Dose Pro 4", viewModel.uiState.value.title)

            rootOperations.publishTitle("Dose Pro 4")

            assertEquals("Dose Pro 4", viewModel.uiState.value.title)
        }

    @Test
    fun `runtime title changes keep using the same canonical title contract`() = runTest(dispatcher) {
        val rootOperations = FakeRootOperations(initialTitle = "Dose Pro 4")
        val viewModel = DeviceDosingRootViewModel(
            operations = rootOperations,
            channelNavigationOperations = FakeChannelNavigationOperations(null),
            channelOperations = UnavailableDeviceDosingChannelOperations
        )

        viewModel.bind(
            deviceUidText = DEVICE_UID,
            fallbackTitle = "Legacy dosing title"
        )
        rootOperations.publishTitle("My Doser")

        assertEquals("My Doser", viewModel.uiState.value.title)
    }

    @Test
    fun `navigation title is canonical fallback when root snapshot has no title`() = runTest(dispatcher) {
        val rootOperations = FakeRootOperations(initialTitle = "")
        val viewModel = DeviceDosingRootViewModel(
            operations = rootOperations,
            channelNavigationOperations = FakeChannelNavigationOperations(null),
            channelOperations = UnavailableDeviceDosingChannelOperations
        )

        viewModel.bind(
            deviceUidText = DEVICE_UID,
            fallbackTitle = "Dose Pro 4"
        )

        assertEquals("Dose Pro 4", viewModel.uiState.value.title)
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
            availability = OwnerDeviceAvailability.REACHABLE
        )
    }

    private companion object {
        const val DEVICE_UID = "device-1"
        const val SLOT_ID = "dosing:channel1"
    }
}
