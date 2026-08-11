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
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.modules.dosing.DeviceDosingRuntimeFixtures
import com.aqua.aqualight.data.devices.runtime.modules.dosing.DeviceDosingStatus
import com.aqua.aqualight.data.devices.runtime.modules.dosing.DeviceDosingStatusParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultDeviceDosingChannelNavigationOperationsTest {

    @Test
    fun `uncalibrated firmware channel opens calibration without redundant preflight`() = runTest {
        val port = fakePort(successfulStatus(calibrated = false))

        val target = DefaultDeviceDosingChannelNavigationOperations(port)
            .resolve(DEVICE_UID, CHANNEL_ONE_SLOT_ID)

        assertEquals(DeviceDosingChannelDestination.CALIBRATION, target?.destination)
        assertEquals(CHANNEL_ONE_SLOT_ID, target?.slotId)
        assertEquals(listOf(ROOT_CALL, STATUS_CALL), port.calls)
    }

    @Test
    fun `calibrated firmware channel opens detail with firmware display name`() = runTest {
        val port = fakePort(
            successfulStatus(calibrated = true, displayName = CUSTOM_CHANNEL_NAME)
        )

        val target = DefaultDeviceDosingChannelNavigationOperations(port)
            .resolve(DEVICE_UID, CHANNEL_ONE_SLOT_ID)

        assertEquals(DeviceDosingChannelDestination.DETAIL, target?.destination)
        assertEquals(CUSTOM_CHANNEL_NAME, target?.channelTitle)
        assertEquals(CALIBRATED_AT, target?.lastCalibratedAtEpochSeconds)
    }

    @Test
    fun `expired session uses central recovery once before retrying firmware status`() = runTest {
        val port = fakePort(
            notAuthenticated(),
            successfulStatus(calibrated = false)
        )

        val target = DefaultDeviceDosingChannelNavigationOperations(port)
            .resolve(DEVICE_UID, CHANNEL_ONE_SLOT_ID)

        assertEquals(DeviceDosingChannelDestination.CALIBRATION, target?.destination)
        assertEquals(listOf(ROOT_CALL, STATUS_CALL, PREPARE_CALL, STATUS_CALL), port.calls)
    }

    @Test
    fun `failed central recovery does not send a second firmware request`() = runTest {
        val port = fakePort(notAuthenticated(), prepared = false)

        val target = DefaultDeviceDosingChannelNavigationOperations(port)
            .resolve(DEVICE_UID, CHANNEL_ONE_SLOT_ID)

        assertNull(target)
        assertEquals(listOf(ROOT_CALL, STATUS_CALL, PREPARE_CALL), port.calls)
    }

    @Test
    fun `root refresh repairs an unauthenticated runtime before publishing channels`() = runTest {
        val port = fakePort(
            notAuthenticated(),
            successfulStatus(calibrated = false)
        )

        val refreshed = DefaultDeviceDosingChannelNavigationOperations(port)
            .refreshTargets(DEVICE_UID)

        assertEquals(true, refreshed)
        assertEquals(listOf(STATUS_CALL, PREPARE_CALL, STATUS_CALL), port.calls)
    }

    @Test
    fun `current route uses authoritative published status without another request`() = runTest {
        val port = fakePort(
            currentStatus = firmwareStatus(
                calibrated = true,
                displayName = CUSTOM_CHANNEL_NAME
            )
        )

        val target = DefaultDeviceDosingChannelNavigationOperations(port)
            .resolveCurrent(DEVICE_UID, CHANNEL_ONE_SLOT_ID)

        assertEquals(DeviceDosingChannelDestination.DETAIL, target?.destination)
        assertEquals(CUSTOM_CHANNEL_NAME, target?.channelTitle)
        assertEquals(listOf(ROOT_CALL, CURRENT_STATUS_CALL), port.calls)
    }

    @Test
    fun `missing current status recovers instead of assuming uncalibrated`() = runTest {
        val port = fakePort(
            notAuthenticated(),
            successfulStatus(calibrated = false)
        )

        val target = DefaultDeviceDosingChannelNavigationOperations(port)
            .resolveCurrent(DEVICE_UID, CHANNEL_ONE_SLOT_ID)

        assertEquals(DeviceDosingChannelDestination.CALIBRATION, target?.destination)
        assertEquals(
            listOf(
                ROOT_CALL,
                CURRENT_STATUS_CALL,
                STATUS_CALL,
                PREPARE_CALL,
                STATUS_CALL
            ),
            port.calls
        )
    }

    @Test
    fun `missing current status with invalid firmware payload fails closed`() = runTest {
        val port = fakePort(protocolError())

        val target = DefaultDeviceDosingChannelNavigationOperations(port)
            .resolveCurrent(DEVICE_UID, CHANNEL_ONE_SLOT_ID)

        assertNull(target)
        assertEquals(
            listOf(ROOT_CALL, CURRENT_STATUS_CALL, STATUS_CALL),
            port.calls
        )
    }

    @Test
    fun `firmware protocol failure is not hidden by a blind recovery retry`() = runTest {
        val port = fakePort(protocolError())

        val target = DefaultDeviceDosingChannelNavigationOperations(port)
            .resolve(DEVICE_UID, CHANNEL_ONE_SLOT_ID)

        assertNull(target)
        assertEquals(listOf(ROOT_CALL, STATUS_CALL), port.calls)
    }

    private fun fakePort(
        vararg outcomes: DeviceRuntimeCommandOutcome<DeviceDosingStatus>,
        prepared: Boolean = true,
        currentStatus: DeviceDosingStatus? = null
    ) = FakeRuntimePort(
        prepared = prepared,
        root = dosingRootSnapshot(),
        outcomes = outcomes.toList(),
        currentStatus = currentStatus
    )

    private fun successfulStatus(
        calibrated: Boolean,
        displayName: String = DEFAULT_CHANNEL_NAME
    ) = DeviceRuntimeCommandOutcome.Success(
        deviceUid = DeviceUid(DEVICE_UID),
        module = DOSING_MODULE,
        action = STATUS_ACTION,
        messageId = MESSAGE_ID,
        generation = DeviceRuntimeConnectionGeneration(RUNTIME_GENERATION),
        statusCode = SUCCESS_STATUS_CODE,
        value = firmwareStatus(calibrated, displayName)
    )

    private fun notAuthenticated() = DeviceRuntimeCommandOutcome.NotAuthenticated(
        deviceUid = DeviceUid(DEVICE_UID),
        module = DOSING_MODULE,
        action = STATUS_ACTION,
        generation = DeviceRuntimeConnectionGeneration(RUNTIME_GENERATION)
    )

    private fun protocolError() = DeviceRuntimeCommandOutcome.ProtocolError(
        deviceUid = DeviceUid(DEVICE_UID),
        module = DOSING_MODULE,
        action = STATUS_ACTION,
        messageId = MESSAGE_ID,
        generation = DeviceRuntimeConnectionGeneration(RUNTIME_GENERATION),
        reason = "Invalid dosing status payload."
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
        outcomes: List<DeviceRuntimeCommandOutcome<DeviceDosingStatus>>,
        private val currentStatus: DeviceDosingStatus?
    ) : DeviceDosingChannelNavigationRuntimePort {
        private val outcomes = ArrayDeque(outcomes)
        val calls = mutableListOf<String>()

        override suspend fun prepareRuntime(deviceUid: DeviceUid): Boolean {
            calls += PREPARE_CALL
            return prepared
        }

        override fun currentRootSnapshot(deviceUid: DeviceUid): DeviceRootSnapshot {
            calls += ROOT_CALL
            return root
        }

        override fun currentStatus(deviceUid: DeviceUid): DeviceDosingStatus? {
            calls += CURRENT_STATUS_CALL
            return currentStatus
        }

        override suspend fun requestStatus(
            deviceUid: DeviceUid
        ): DeviceRuntimeCommandOutcome<DeviceDosingStatus> {
            calls += STATUS_CALL
            return outcomes.removeFirst()
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
        const val RUNTIME_GENERATION = 1L
        const val SUCCESS_STATUS_CODE = 200
        const val DOSING_MODULE = "dosing"
        const val STATUS_ACTION = "status.get"
        const val MESSAGE_ID = "message-1"
        const val PREPARE_CALL = "prepare"
        const val ROOT_CALL = "root"
        const val CURRENT_STATUS_CALL = "currentStatus"
        const val STATUS_CALL = "status"
    }
}
