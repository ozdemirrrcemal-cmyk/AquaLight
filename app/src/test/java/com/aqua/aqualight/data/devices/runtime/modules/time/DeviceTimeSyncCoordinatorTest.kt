package com.aqua.aqualight.data.devices.runtime.modules.time

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceTimeSyncCoordinatorTest {
    @Test
    fun `synchronizes one confirmed phone snapshot per device session`() = runBlocking {
        val calls = AtomicInteger(0)
        val coordinator = DeviceTimeSyncCoordinator(
            syncPhoneNow = { deviceUid ->
                calls.incrementAndGet()
                success(deviceUid)
            }
        )
        val deviceUid = DeviceUid("device-time-once")

        val first = coordinator.syncPhoneNowIfNeeded(deviceUid)
        val second = coordinator.syncPhoneNowIfNeeded(deviceUid)

        assertTrue(first is DeviceTimeSyncDecision.Attempted)
        assertTrue(second is DeviceTimeSyncDecision.Skipped)
        assertEquals(1, calls.get())
    }

    @Test
    fun `failed outcome does not mark session synchronized and allows retry`() = runBlocking {
        val calls = AtomicInteger(0)
        val coordinator = DeviceTimeSyncCoordinator(
            syncPhoneNow = { deviceUid ->
                calls.incrementAndGet()
                DeviceRuntimeCommandOutcome.NotConnected(
                    deviceUid = deviceUid,
                    module = DeviceTimeRuntimeContract.MODULE,
                    action = DeviceTimeRuntimeContract.Action.PHONE_SYNC
                )
            }
        )
        val deviceUid = DeviceUid("device-time-retry")

        val first = coordinator.syncPhoneNowIfNeeded(deviceUid)
        val second = coordinator.syncPhoneNowIfNeeded(deviceUid)

        assertTrue(first is DeviceTimeSyncDecision.Attempted)
        assertTrue(second is DeviceTimeSyncDecision.Attempted)
        assertEquals(2, calls.get())
    }

    @Test
    fun `concurrent request is skipped while one broker command is pending`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calls = AtomicInteger(0)
        val deviceUid = DeviceUid("device-time-race")
        val coordinator = DeviceTimeSyncCoordinator(
            syncPhoneNow = { uid ->
                calls.incrementAndGet()
                entered.complete(Unit)
                release.await()
                success(uid)
            }
        )

        val first = async { coordinator.syncPhoneNowIfNeeded(deviceUid) }
        entered.await()
        val second = coordinator.syncPhoneNowIfNeeded(deviceUid, force = true)
        release.complete(Unit)

        assertTrue(second is DeviceTimeSyncDecision.Skipped)
        assertTrue(first.await() is DeviceTimeSyncDecision.Attempted)
        assertEquals(1, calls.get())
    }

    @Test
    fun `clearing session memory allows synchronization again`() = runBlocking {
        val calls = AtomicInteger(0)
        val deviceUid = DeviceUid("device-time-clear")
        val coordinator = DeviceTimeSyncCoordinator(
            syncPhoneNow = { uid ->
                calls.incrementAndGet()
                success(uid)
            }
        )

        coordinator.syncPhoneNowIfNeeded(deviceUid)
        coordinator.clearSessionMemory(deviceUid)
        coordinator.syncPhoneNowIfNeeded(deviceUid)

        assertEquals(2, calls.get())
    }

    private fun success(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome.Success<DeviceTimeMutationResult> =
        DeviceRuntimeCommandOutcome.Success(
            deviceUid = deviceUid,
            module = DeviceTimeRuntimeContract.MODULE,
            action = DeviceTimeRuntimeContract.Action.PHONE_SYNC,
            messageId = "message-id",
            generation = DeviceRuntimeConnectionGeneration(1L),
            statusCode = 200,
            value = DeviceTimeMutationResult(
                operation = "phoneSync",
                changed = null,
                synced = true,
                saved = false,
                saveRequested = false,
                status = status()
            )
        )

    private fun status(): DeviceTimeStatus = DeviceTimeStatus(
        timeSet = true,
        timeString = "2026-08-01 12:00:00",
        timezoneId = "Europe/Istanbul",
        posixTimeZone = "TRT-3",
        utcOffsetMinutes = 180,
        autoSyncNtpEnabled = true,
        autoSyncGadgetEnabled = true,
        ntpServerPrimary = "pool.ntp.org",
        ntpServerSecondary = "time.nist.gov",
        lastSyncSource = "phone",
        lastSyncEpochMillis = 1_754_041_600_000L,
        lastSyncUptimeMs = 5_000L
    )
}
