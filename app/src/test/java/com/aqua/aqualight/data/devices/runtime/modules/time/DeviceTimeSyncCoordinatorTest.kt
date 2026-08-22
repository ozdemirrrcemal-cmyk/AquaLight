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
    fun `ready rtc with matching phone policy is not rewritten`() = runBlocking {
        val statusCalls = AtomicInteger(0)
        val syncCalls = AtomicInteger(0)
        val deviceUid = DeviceUid("device-time-current")
        val coordinator = coordinator(
            requestStatus = { uid ->
                statusCalls.incrementAndGet()
                statusSuccess(uid, status())
            },
            syncPhoneNow = { uid ->
                syncCalls.incrementAndGet()
                syncSuccess(uid)
            }
        )

        val decision = coordinator.syncPhoneNowIfNeeded(deviceUid)

        assertTrue(decision is DeviceTimeSyncDecision.Skipped)
        assertEquals(1, statusCalls.get())
        assertEquals(0, syncCalls.get())
    }

    @Test
    fun `rtc not ready uses existing phone sync without persistent save`() = runBlocking {
        val syncCalls = AtomicInteger(0)
        val deviceUid = DeviceUid("device-time-rtc-recovery")
        val coordinator = coordinator(
            requestStatus = { uid -> statusSuccess(uid, status(timeSet = false)) },
            syncPhoneNow = { uid ->
                syncCalls.incrementAndGet()
                syncSuccess(uid)
            }
        )

        val decision = coordinator.syncPhoneNowIfNeeded(deviceUid)

        assertTrue(decision is DeviceTimeSyncDecision.Attempted)
        assertEquals(1, syncCalls.get())
    }

    @Test
    fun `timezone or auto sync policy drift uses existing phone sync`() = runBlocking {
        val syncCalls = AtomicInteger(0)
        val deviceUid = DeviceUid("device-time-zone-drift")
        val coordinator = coordinator(
            requestStatus = { uid ->
                statusSuccess(
                    uid,
                    status(
                        timezoneId = "UTC",
                        posixTimeZone = "UTC0",
                        utcOffsetMinutes = 0,
                        autoSyncNtpEnabled = false
                    )
                )
            },
            syncPhoneNow = { uid ->
                syncCalls.incrementAndGet()
                syncSuccess(uid)
            }
        )

        assertTrue(
            coordinator.syncPhoneNowIfNeeded(deviceUid) is DeviceTimeSyncDecision.Attempted
        )
        assertEquals(1, syncCalls.get())
    }

    @Test
    fun `status failure fails closed and next bootstrap can retry`() = runBlocking {
        val statusCalls = AtomicInteger(0)
        val syncCalls = AtomicInteger(0)
        val deviceUid = DeviceUid("device-time-status-retry")
        val coordinator = coordinator(
            requestStatus = { uid ->
                if (statusCalls.incrementAndGet() == 1) {
                    DeviceRuntimeCommandOutcome.NotConnected(
                        deviceUid = uid,
                        module = DeviceTimeRuntimeContract.MODULE,
                        action = DeviceTimeRuntimeContract.Action.STATUS_GET
                    )
                } else {
                    statusSuccess(uid, status(timeSet = false))
                }
            },
            syncPhoneNow = { uid ->
                syncCalls.incrementAndGet()
                syncSuccess(uid)
            }
        )

        val first = coordinator.syncPhoneNowIfNeeded(deviceUid)
        assertTrue(first is DeviceTimeSyncDecision.Skipped)
        assertEquals(0, syncCalls.get())

        val second = coordinator.syncPhoneNowIfNeeded(deviceUid)

        assertTrue(second is DeviceTimeSyncDecision.Attempted)
        assertEquals(2, statusCalls.get())
        assertEquals(1, syncCalls.get())
    }

    @Test
    fun `concurrent duplicate is skipped while status evaluation is pending`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val statusCalls = AtomicInteger(0)
        val syncCalls = AtomicInteger(0)
        val deviceUid = DeviceUid("device-time-race")
        val coordinator = coordinator(
            requestStatus = { uid ->
                statusCalls.incrementAndGet()
                entered.complete(Unit)
                release.await()
                statusSuccess(uid, status(timeSet = false))
            },
            syncPhoneNow = { uid ->
                syncCalls.incrementAndGet()
                syncSuccess(uid)
            }
        )

        val first = async { coordinator.syncPhoneNowIfNeeded(deviceUid) }
        entered.await()
        val second = coordinator.syncPhoneNowIfNeeded(deviceUid)
        release.complete(Unit)

        assertTrue(second is DeviceTimeSyncDecision.Skipped)
        assertTrue(first.await() is DeviceTimeSyncDecision.Attempted)
        assertEquals(1, statusCalls.get())
        assertEquals(1, syncCalls.get())
    }

    private fun coordinator(
        requestStatus: suspend (DeviceUid) -> DeviceRuntimeCommandOutcome<DeviceTimeStatus>,
        syncPhoneNow: suspend (DeviceUid) ->
            DeviceRuntimeCommandOutcome<DeviceTimeMutationResult>
    ): DeviceTimeSyncCoordinator = DeviceTimeSyncCoordinator(
        requestStatus = requestStatus,
        syncPhoneNow = syncPhoneNow,
        currentTimeZoneSnapshot = {
            DeviceTimeZoneSnapshot(
                timezoneId = "Europe/Istanbul",
                posixTimeZone = "TRT-3",
                utcOffsetMinutes = 180
            )
        }
    )

    private fun statusSuccess(
        deviceUid: DeviceUid,
        status: DeviceTimeStatus
    ): DeviceRuntimeCommandOutcome.Success<DeviceTimeStatus> =
        DeviceRuntimeCommandOutcome.Success(
            deviceUid = deviceUid,
            module = DeviceTimeRuntimeContract.MODULE,
            action = DeviceTimeRuntimeContract.Action.STATUS_GET,
            messageId = "status-message-id",
            generation = DeviceRuntimeConnectionGeneration(1L),
            statusCode = 200,
            value = status
        )

    private fun syncSuccess(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome.Success<DeviceTimeMutationResult> =
        DeviceRuntimeCommandOutcome.Success(
            deviceUid = deviceUid,
            module = DeviceTimeRuntimeContract.MODULE,
            action = DeviceTimeRuntimeContract.Action.PHONE_SYNC,
            messageId = "sync-message-id",
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

    private fun status(
        timeSet: Boolean = true,
        timezoneId: String = "Europe/Istanbul",
        posixTimeZone: String = "TRT-3",
        utcOffsetMinutes: Int = 180,
        autoSyncNtpEnabled: Boolean = true,
        autoSyncGadgetEnabled: Boolean = true
    ): DeviceTimeStatus = DeviceTimeStatus(
        timeSet = timeSet,
        timeString = if (timeSet) "2026-08-01 12:00:00" else "1970-01-01 00:00:00",
        timezoneId = timezoneId,
        posixTimeZone = posixTimeZone,
        utcOffsetMinutes = utcOffsetMinutes,
        autoSyncNtpEnabled = autoSyncNtpEnabled,
        autoSyncGadgetEnabled = autoSyncGadgetEnabled,
        ntpServerPrimary = "pool.ntp.org",
        ntpServerSecondary = "time.nist.gov",
        lastSyncSource = if (timeSet) "rtc" else "none",
        lastSyncEpochMillis = 0L,
        lastSyncUptimeMs = 5_000L
    )
}
