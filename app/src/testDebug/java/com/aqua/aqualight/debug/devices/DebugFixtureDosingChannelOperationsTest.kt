package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgram
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramMode
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramSchedule
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirSettings
import com.aqua.aqualight.application.devices.dosing.DeviceDosingRunSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugFixtureDosingChannelOperationsTest {

    @Test
    fun `dose pro 4 fixture covers all progress modes and conditional reservoir state`() {
        val fixtures = DebugDeviceFixtureCatalog()
        val root = fixtures.snapshots
            .first { snapshot -> snapshot.limits.dosingChannelCount == 4 }
            .let { snapshot -> checkNotNull(fixtures.rootSnapshot(snapshot.deviceUid.value)) }
        val store = DebugFixtureDosingStateStore(fixtures)
        val channels = root.channelSlots.dosingChannels.map { slot ->
            checkNotNull(store.currentChannel(root.deviceUid, slot.id.value))
        }

        assertEquals(
            listOf(
                DeviceDosingProgramMode.SINGLE,
                DeviceDosingProgramMode.HOURLY_24,
                DeviceDosingProgramMode.CUSTOM_PERIODS,
                DeviceDosingProgramMode.TIMER
            ),
            channels.map { channel -> channel.program?.schedule?.mode }
        )
        assertEquals(10_000L, channels[0].usageToday.manualDeliveredMicroliters)
        assertFalse(channels[2].reservoir.trackingEnabled)
        assertTrue(channels[3].reservoir.trackingEnabled)
        assertTrue(channels[3].activeRun.active)
    }

    @Test
    fun `fixture owns channel status and all ui mutations without a runtime`() = runTest {
        val fixtures = DebugDeviceFixtureCatalog()
        val root = fixtures.snapshots
            .first { snapshot -> snapshot.capabilities.dosing }
            .let { snapshot -> checkNotNull(fixtures.rootSnapshot(snapshot.deviceUid.value)) }
        val store = DebugFixtureDosingStateStore(fixtures)
        val calibratedSlot = root.channelSlots.dosingChannels[1]
        val deviceUid = root.deviceUid
        val slotId = calibratedSlot.id.value

        val initial = store.observeChannel(deviceUid, slotId).first()
        assertNotNull(initial)
        assertTrue(checkNotNull(initial).calibrated)
        assertNotNull(initial.program)

        val program = DeviceDosingProgram(
            enabled = true,
            weekdays = listOf(true, true, true, true, true, false, false),
            schedule = DeviceDosingProgramSchedule.Single(
                dailyDoseMicroliters = 3_000L,
                startTimeMillis = 28_800_000L
            ),
            missedDoseRecoveryEnabled = false
        )
        assertTrue(store.applyProgram(deviceUid, slotId, program).isSuccess())
        assertEquals(program, store.currentChannel(deviceUid, slotId)?.program)

        assertTrue(
            store.applyReservoirSettings(
                deviceUid = deviceUid,
                slotId = slotId,
                settings = DeviceDosingReservoirSettings(
                    trackingEnabled = true,
                    capacityMicroliters = 600_000L,
                    lowLevelAlertEnabled = false
                )
            ).isSuccess()
        )
        assertEquals(
            600_000L,
            store.currentChannel(deviceUid, slotId)?.reservoir?.capacityMicroliters
        )

        assertTrue(store.doseNow(deviceUid, slotId, 2_500L).isSuccess())
        assertEquals(
            DeviceDosingRunSource.MANUAL,
            store.currentChannel(deviceUid, slotId)?.activeRun?.source
        )
        assertTrue(store.doseStop(deviceUid, slotId).isSuccess())
        assertFalse(checkNotNull(store.currentChannel(deviceUid, slotId)).activeRun.active)

        assertTrue(store.resetChannel(deviceUid, slotId).isSuccess())
        assertFalse(checkNotNull(store.currentChannel(deviceUid, slotId)).calibrated)
        assertFalse(checkNotNull(store.current(deviceUid, slotId)).calibrated)
    }

    private fun DeviceDosingChannelOperationResult.isSuccess(): Boolean =
        this is DeviceDosingChannelOperationResult.Success
}
