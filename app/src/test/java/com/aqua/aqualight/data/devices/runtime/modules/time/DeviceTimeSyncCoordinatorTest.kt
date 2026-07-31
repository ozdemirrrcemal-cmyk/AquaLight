package com.aqua.aqualight.data.devices.runtime.modules.time

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceTimeSyncCoordinatorTest {

    @Test
    fun `one exact successful phone sync is recorded per device session`() = runBlocking {
        val calls = AtomicInteger(0)
        val coordinator = DeviceTimeSyncCoordinator(
            scope = this,
            syncPhoneNow = { deviceUid ->
                calls.incrementAndGet()
                success(deviceUid)
            }
        )
        val deviceUid = DeviceUid("device-time-once")

        assertEquals(
            DeviceTimeSyncScheduleResult.SCHEDULED,
            coordinator.syncPhoneNowIfNeeded(deviceUid)
        )
        awaitIdle(coordinator, deviceUid)
        assertTrue(coordinator.isSynchronized(deviceUid))
        assertEquals(
            DeviceTimeSyncScheduleResult.SKIPPED,
            coordinator.syncPhoneNowIfNeeded(deviceUid)
        )
        assertEquals(1, calls.get())
    }

    @Test
    fun `force never duplicates an in-flight sync but reruns a completed sync`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calls = AtomicInteger(0)
        val coordinator = DeviceTimeSyncCoordinator(
            scope = this,
            syncPhoneNow = { deviceUid ->
                val call = calls.incrementAndGet()
                if (call == 1) {
                    entered.complete(Unit)
                    release.await()
                }
                success(deviceUid)
            }
        )
        val deviceUid = DeviceUid("device-time-force")

        assertEquals(
            DeviceTimeSyncScheduleResult.SCHEDULED,
            coordinator.syncPhoneNowIfNeeded(deviceUid)
        )
        entered.await()
        assertEquals(
            DeviceTimeSyncScheduleResult.SKIPPED,
            coordinator.syncPhoneNowIfNeeded(deviceUid, force = true)
        )
        release.complete(Unit)
        awaitIdle(coordinator, deviceUid)

        assertEquals(
            DeviceTimeSyncScheduleResult.SCHEDULED,
            coordinator.syncPhoneNowIfNeeded(deviceUid, force = true)
        )
        awaitIdle(coordinator, deviceUid)
        assertEquals(2, calls.get())
    }

    @Test
    fun `firmware failure does not mark the session and allows retry`() = runBlocking {
        val calls = AtomicInteger(0)
        val coordinator = DeviceTimeSyncCoordinator(
            scope = this,
            syncPhoneNow = { deviceUid ->
                calls.incrementAndGet()
                DeviceRuntimeCommandOutcome.FirmwareError(
                    deviceUid = deviceUid,
                    module = DeviceTimeRuntimeContract.MODULE,
                    action = DeviceTimeRuntimeContract.Action.PHONE_SYNC,
                    messageId = "time-failure",
                    generation = GENERATION,
                    statusCode = 503,
                    code = "unavailable",
                    field = "time",
                    message = "Time service unavailable."
                )
            }
        )
        val deviceUid = DeviceUid("device-time-retry")

        assertEquals(
            DeviceTimeSyncScheduleResult.SCHEDULED,
            coordinator.syncPhoneNowIfNeeded(deviceUid)
        )
        awaitIdle(coordinator, deviceUid)
        assertFalse(coordinator.isSynchronized(deviceUid))
        assertEquals(
            DeviceTimeSyncScheduleResult.SCHEDULED,
            coordinator.syncPhoneNowIfNeeded(deviceUid)
        )
        awaitIdle(coordinator, deviceUid)
        assertEquals(2, calls.get())
    }

    @Test
    fun `clearing session memory prevents a stale success from publishing`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val coordinator = DeviceTimeSyncCoordinator(
            scope = this,
            syncPhoneNow = { deviceUid ->
                withContext(NonCancellable) {
                    entered.complete(Unit)
                    release.await()
                    success(deviceUid)
                }
            }
        )
        val deviceUid = DeviceUid("device-time-clear")

        coordinator.syncPhoneNowIfNeeded(deviceUid)
        entered.await()
        coordinator.clearSessionMemory(deviceUid)
        release.complete(Unit)
        awaitIdle(coordinator, deviceUid)

        assertFalse(coordinator.isSynchronized(deviceUid))
        assertEquals(
            DeviceTimeSyncScheduleResult.SCHEDULED,
            coordinator.syncPhoneNowIfNeeded(deviceUid)
        )
    }

    private suspend fun awaitIdle(
        coordinator: DeviceTimeSyncCoordinator,
        deviceUid: DeviceUid
    ) {
        withTimeout(1_000L) {
            while (coordinator.isSyncing(deviceUid)) delay(1L)
        }
    }

    private fun success(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome.Success<DeviceTimeSyncResult> =
        DeviceRuntimeCommandOutcome.Success(
            deviceUid = deviceUid,
            module = DeviceTimeRuntimeContract.MODULE,
            action = DeviceTimeRuntimeContract.Action.PHONE_SYNC,
            messageId = "time-success",
            generation = GENERATION,
            statusCode = 200,
            value = DeviceTimeSyncResult(
                operation = "phoneSync",
                synced = true,
                saved = false,
                saveRequested = false,
                event = DeviceTimeRuntimeContract.Event.STATUS_CHANGED,
                status = status()
            )
        )

    private fun status(): DeviceTimeStatus = DeviceTimeStatus(
        timeSet = true,
        timeString = "12:00:00 31.07.2026 W5",
        uptime = "D0 01:00:00; sec=3600; millis()=3600000; Count=0",
        uptimeMs = 3_600_000L,
        millisStartDay = 43_200_000L,
        timeZoneHours = 3,
        utcOffsetMinutes = 180,
        timezoneId = "Europe/Istanbul",
        posixTimeZone = "TRT-3",
        autoSyncNtpEnabled = true,
        autoSyncGadgetEnabled = true,
        ntpServerPrimary = "pool.ntp.org",
        ntpServerSecondary = "time.nist.gov",
        lastSyncSource = "phone",
        lastSyncEpochMillis = 1_754_000_000_000L,
        lastSyncUptimeMs = 3_600_000L,
        parts = DeviceTimeParts(2026, 7, 31, 6, 12, 0, 0),
        runtime = DeviceTimeRuntimeCapabilities(
            module = "time",
            readOnly = false,
            supportsConfigApply = true,
            supportsPhoneSync = true,
            supportsNtpSync = true,
            supportsRtcSet = true
        )
    )

    private companion object {
        val GENERATION = DeviceRuntimeConnectionGeneration(3L)
    }
}
