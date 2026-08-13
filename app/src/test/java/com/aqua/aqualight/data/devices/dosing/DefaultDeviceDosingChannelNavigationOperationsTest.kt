package com.aqua.aqualight.data.devices.dosing

import com.aqua.aqualight.application.devices.DeviceChannelSlots
import com.aqua.aqualight.application.devices.DeviceChannelWireKey
import com.aqua.aqualight.application.devices.DeviceDosingChannelSlot
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootRoute
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.DeviceSlotIndex
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelDestination
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.modules.dosing.DeviceDosingRuntimeFixtures
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingChannelStatus
import com.aqua.aqualight.data.devices.runtime.modules.dosing.parsers.DeviceDosingStatusParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultDeviceDosingChannelNavigationOperationsTest {

    @Test
    fun `uncalibrated channel requests only its firmware status and opens calibration`() = runTest {
        val port = fakePort(successfulStatus(calibrated = false))

        val target = DefaultDeviceDosingChannelNavigationOperations(port)
            .resolve(DEVICE_UID, CHANNEL_ONE_SLOT_ID)

        assertEquals(DeviceDosingChannelDestination.CALIBRATION, target?.destination)
        assertEquals(CHANNEL_ONE_SLOT_ID, target?.slotId)
        assertEquals(listOf(ROOT_CALL, statusCall(CHANNEL_ONE_KEY)), port.calls)
    }

    @Test
    fun `calibrated channel opens detail with firmware effective name`() = runTest {
        val port = fakePort(
            successfulStatus(calibrated = true, effectiveName = CUSTOM_CHANNEL_NAME)
        )

        val target = DefaultDeviceDosingChannelNavigationOperations(port)
            .resolve(DEVICE_UID, CHANNEL_ONE_SLOT_ID)

        assertEquals(DeviceDosingChannelDestination.DETAIL, target?.destination)
        assertEquals(CUSTOM_CHANNEL_NAME, target?.channelTitle)
        assertEquals(CALIBRATED_AT, target?.lastCalibratedAtEpochSeconds)
    }

    @Test
    fun `expired session uses central recovery once before channel status retry`() = runTest {
        val port = fakePort(
            notAuthenticated(),
            successfulStatus(calibrated = false)
        )

        val target = DefaultDeviceDosingChannelNavigationOperations(port)
            .resolve(DEVICE_UID, CHANNEL_ONE_SLOT_ID)

        assertEquals(DeviceDosingChannelDestination.CALIBRATION, target?.destination)
        assertEquals(
            listOf(
                ROOT_CALL,
                statusCall(CHANNEL_ONE_KEY),
                PREPARE_CALL,
                statusCall(CHANNEL_ONE_KEY)
            ),
            port.calls
        )
    }

    @Test
    fun `failed central recovery does not send second channel request`() = runTest {
        val port = fakePort(notAuthenticated(), prepared = false)

        val target = DefaultDeviceDosingChannelNavigationOperations(port)
            .resolve(DEVICE_UID, CHANNEL_ONE_SLOT_ID)

        assertNull(target)
        assertEquals(
            listOf(ROOT_CALL, statusCall(CHANNEL_ONE_KEY), PREPARE_CALL),
            port.calls
        )
    }

    @Test
    fun `current route uses already published channel status without request`() = runTest {
        val current = channelStatus(calibrated = true, effectiveName = CUSTOM_CHANNEL_NAME)
        val port = fakePort(currentStatus = current)

        val target = DefaultDeviceDosingChannelNavigationOperations(port)
            .resolveCurrent(DEVICE_UID, CHANNEL_ONE_SLOT_ID)

        assertEquals(DeviceDosingChannelDestination.DETAIL, target?.destination)
        assertEquals(CUSTOM_CHANNEL_NAME, target?.channelTitle)
        assertEquals(
            listOf(ROOT_CALL, currentStatusCall(CHANNEL_ONE_KEY)),
            port.calls
        )
    }

    @Test
    fun `protocol error is not hidden by blind runtime recovery`() = runTest {
        val port = fakePort(protocolError())

        val target = DefaultDeviceDosingChannelNavigationOperations(port)
            .resolve(DEVICE_UID, CHANNEL_ONE_SLOT_ID)

        assertNull(target)
        assertEquals(listOf(ROOT_CALL, statusCall(CHANNEL_ONE_KEY)), port.calls)
    }

    private fun fakePort(
        vararg outcomes: DeviceRuntimeCommandOutcome<DeviceDosingChannelStatus>,
        prepared: Boolean = true,
        currentStatus: DeviceDosingChannelStatus? = null
    ) = FakeRuntimePort(
        prepared = prepared,
        root = dosingRootSnapshot(),
        outcomes = outcomes.toList(),
        currentStatus = currentStatus
    )

    private fun successfulStatus(
        calibrated: Boolean,
        effectiveName: String = DEFAULT_CHANNEL_NAME
    ) = DeviceRuntimeCommandOutcome.Success(
        deviceUid = DeviceUid(DEVICE_UID),
        module = DOSING_MODULE,
        action = STATUS_ACTION,
        messageId = MESSAGE_ID,
        generation = DeviceRuntimeConnectionGeneration(RUNTIME_GENERATION),
        statusCode = SUCCESS_STATUS_CODE,
        value = channelStatus(calibrated, effectiveName)
    )

    private fun channelStatus(
        calibrated: Boolean,
        effectiveName: String = DEFAULT_CHANNEL_NAME
    ): DeviceDosingChannelStatus = DeviceDosingStatusParser.parseChannel(
        DeviceDosingRuntimeFixtures.channelStatus(
            displayName = effectiveName,
            program = if (calibrated) {
                DeviceDosingRuntimeFixtures.singleProgram()
            } else {
                null
            },
            lastCalibratedAt = if (calibrated) CALIBRATED_AT else 0L,
            doseMsPerMl = if (calibrated) 1_000L else 0L
        )
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
        reason = "Invalid dosing channel status payload."
    )

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
        outcomes: List<DeviceRuntimeCommandOutcome<DeviceDosingChannelStatus>>,
        private val currentStatus: DeviceDosingChannelStatus?
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

        override fun observeStatuses(
            deviceUid: DeviceUid
        ): Flow<Map<String, DeviceDosingChannelStatus>> = flowOf(
            currentStatus?.let { mapOf(CHANNEL_ONE_KEY to it) }.orEmpty()
        )

        override fun currentStatus(
            deviceUid: DeviceUid,
            channelKey: String
        ): DeviceDosingChannelStatus? {
            calls += currentStatusCall(channelKey)
            return currentStatus
        }

        override suspend fun requestStatus(
            deviceUid: DeviceUid,
            channelKey: String
        ): DeviceRuntimeCommandOutcome<DeviceDosingChannelStatus> {
            calls += statusCall(channelKey)
            return outcomes.removeFirst()
        }
    }

    private companion object {
        const val DEVICE_UID = "device-1"
        const val CHANNEL_ONE_KEY = "channel1"
        const val CHANNEL_ONE_SLOT_ID = "dosing:channel1"
        const val DEFAULT_CHANNEL_NAME = "Channel 1"
        const val CUSTOM_CHANNEL_NAME = "Nutrients"
        const val CALIBRATED_AT = 1_786_320_000L
        const val RUNTIME_GENERATION = 1L
        const val SUCCESS_STATUS_CODE = 200
        const val DOSING_MODULE = "dosing"
        const val STATUS_ACTION = "status.get"
        const val MESSAGE_ID = "message-1"
        const val PREPARE_CALL = "prepare"
        const val ROOT_CALL = "root"

        fun statusCall(channelKey: String) = "status:$channelKey"
        fun currentStatusCall(channelKey: String) = "currentStatus:$channelKey"
    }
}
