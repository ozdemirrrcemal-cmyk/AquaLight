package com.aqua.aqualight.data.devices

import com.aqua.aqualight.application.devices.DeviceChannelSlots
import com.aqua.aqualight.application.devices.DeviceChannelWireKey
import com.aqua.aqualight.application.devices.DeviceDosingChannelDestination
import com.aqua.aqualight.application.devices.DeviceDosingChannelSlot
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootRoute
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.DeviceSlotIndex
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.modules.dosing.DeviceDosingRuntimeFixtures
import com.aqua.aqualight.data.devices.runtime.modules.dosing.DeviceDosingStatus
import com.aqua.aqualight.data.devices.runtime.modules.dosing.DeviceDosingStatusParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultDeviceDosingChannelNavigationOperationsTest {

    @Test
    fun `uncalibrated firmware channel opens calibration after runtime preflight`() = runTest {
        val port = fakePort(status = firmwareStatus(calibrated = false))

        val target = operations(port).resolve(DEVICE_UID, CHANNEL_ONE_SLOT_ID)

        assertEquals(DeviceDosingChannelDestination.CALIBRATION, target?.destination)
        assertEquals(CHANNEL_ONE_SLOT_ID, target?.slotId)
        assertEquals(listOf(PREPARE_CALL, ROOT_CALL, STATUS_CALL), port.calls)
    }

    @Test
    fun `calibrated firmware channel opens detail with firmware display name`() = runTest {
        val port = fakePort(
            status = firmwareStatus(calibrated = true, displayName = CUSTOM_CHANNEL_NAME)
        )

        val target = operations(port).resolve(DEVICE_UID, CHANNEL_ONE_SLOT_ID)

        assertEquals(DeviceDosingChannelDestination.DETAIL, target?.destination)
        assertEquals(CUSTOM_CHANNEL_NAME, target?.channelTitle)
    }

    @Test
    fun `failed runtime preflight never requests dosing status`() = runTest {
        val port = fakePort(status = firmwareStatus(calibrated = false), prepared = false)

        val target = operations(port).resolve(DEVICE_UID, CHANNEL_ONE_SLOT_ID)

        assertNull(target)
        assertEquals(listOf(PREPARE_CALL), port.calls)
    }

    private fun operations(port: FakeRuntimePort) =
        DefaultDeviceDosingChannelNavigationOperations(port)

    private fun fakePort(
        status: DeviceDosingStatus,
        prepared: Boolean = true
    ) = FakeRuntimePort(
        prepared = prepared,
        root = dosingRootSnapshot(),
        status = status
    )

    private fun firmwareStatus(
        calibrated: Boolean,
        displayName: String = DEFAULT_CHANNEL_NAME
    ): DeviceDosingStatus {
        val status = DeviceDosingStatusParser.parse(
            DeviceDosingRuntimeFixtures.status(channelOneDisplayName = displayName)
        )
        val channel = status.channels.first()
        val dosing = channel.dosing.copy(
            doseMsPerMl = if (calibrated) CALIBRATED_DOSE_MS_PER_ML else 0L,
            lastCalibratedAt = if (calibrated) CALIBRATED_AT else 0L,
            calibrated = calibrated
        )
        return status.copy(
            channels = listOf(channel.copy(dosing = dosing)) + status.channels.drop(1)
        )
    }

    private fun dosingRootSnapshot() = DeviceRootSnapshot(
        deviceUid = DEVICE_UID,
        title = "Dose Pro 2",
        availability = OwnerDeviceAvailability.REACHABLE,
        family = OwnerDeviceFamily.DOSING,
        catalogState = DeviceRootCatalogState.VALID,
        channelSlots = DeviceChannelSlots.EMPTY.copy(
            dosingChannels = listOf(
                DeviceDosingChannelSlot(
                    index = DeviceSlotIndex(0),
                    wireKey = DeviceChannelWireKey(CHANNEL_ONE_KEY),
                    defaultDisplayName = DEFAULT_CHANNEL_NAME,
                    displayNameEditable = true
                )
            )
        ),
        allowedRoutes = setOf(
            DeviceRootRoute.DOSING_CHANNELS,
            DeviceRootRoute.DOSING_CALIBRATION
        )
    )

    private class FakeRuntimePort(
        private val prepared: Boolean,
        private val root: DeviceRootSnapshot,
        private val status: DeviceDosingStatus
    ) : DeviceDosingChannelNavigationRuntimePort {
        val calls = mutableListOf<String>()

        override suspend fun prepareRuntime(deviceUid: DeviceUid): Boolean {
            calls += PREPARE_CALL
            return prepared
        }

        override fun currentRootSnapshot(deviceUid: DeviceUid): DeviceRootSnapshot {
            calls += ROOT_CALL
            return root
        }

        override suspend fun requestStatus(deviceUid: DeviceUid): DeviceDosingStatus {
            calls += STATUS_CALL
            return status
        }
    }

    private companion object {
        const val DEVICE_UID = "device-1"
        const val CHANNEL_ONE_KEY = "channel1"
        const val CHANNEL_ONE_SLOT_ID = "dosing:channel1"
        const val DEFAULT_CHANNEL_NAME = "Channel 1"
        const val CUSTOM_CHANNEL_NAME = "Nutrients"
        const val CALIBRATED_DOSE_MS_PER_ML = 1_000L
        const val CALIBRATED_AT = 100L
        const val PREPARE_CALL = "prepare"
        const val ROOT_CALL = "root"
        const val STATUS_CALL = "status"
    }
}
