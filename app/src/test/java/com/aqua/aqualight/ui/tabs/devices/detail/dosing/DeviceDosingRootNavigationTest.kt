package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelDestination
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelNavigationOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelNavigationTarget
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
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
            channelTitle = "Nutrients",
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

    private class FakeRootOperations : DeviceRootOperations {
        private val snapshot = DeviceRootSnapshot(
            deviceUid = DEVICE_UID,
            title = "Dose Pro",
            availability = OwnerDeviceAvailability.REACHABLE
        )
        private val snapshots = MutableStateFlow<DeviceRootSnapshot?>(snapshot)

        override fun observe(deviceUid: String): Flow<DeviceRootSnapshot?> = snapshots

        override fun current(deviceUid: String): DeviceRootSnapshot = snapshot

        override fun connect(deviceUid: String): Result<Unit> = Result.success(Unit)
    }

    private companion object {
        const val DEVICE_UID = "device-1"
        const val SLOT_ID = "dosing:channel1"
    }
}
