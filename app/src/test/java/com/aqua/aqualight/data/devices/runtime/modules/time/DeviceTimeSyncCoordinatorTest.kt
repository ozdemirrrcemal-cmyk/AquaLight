package com.aqua.aqualight.data.devices.runtime.modules.time

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
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
        val generation = AtomicReference(GENERATION_ONE)
        val coordinator = coordinator(
            generation = generation,
            requestStatus = { uid, expectedGeneration ->
                statusCalls.incrementAndGet()
                statusSuccess(uid, status(), expectedGeneration)
            },
            syncPhoneNow = { uid, expectedGeneration ->
                syncCalls.incrementAndGet()
                syncSuccess(uid, expectedGeneration)
            }
        )

        val decision = coordinator.syncPhoneNowIfNeeded(deviceUid, GENERATION_ONE)

        assertTrue(decision is DeviceTimeSyncDecision.Skipped)
        assertEquals(1, statusCalls.get())
        assertEquals(0, syncCalls.get())
    }

    @Test
    fun `rtc not ready uses existing phone sync without persistent save`() = runBlocking {
        val syncCalls = AtomicInteger(0)
        val deviceUid = DeviceUid("device-time-rtc-recovery")
        val generation = AtomicReference(GENERATION_ONE)
        val coordinator = coordinator(
            generation = generation,
            requestStatus = { uid, expectedGeneration ->
                statusSuccess(uid, status().copy(timeSet = false), expectedGeneration)
            },
            syncPhoneNow = { uid, expectedGeneration ->
                syncCalls.incrementAndGet()
                syncSuccess(uid, expectedGeneration)
            }
        )

        val decision = coordinator.syncPhoneNowIfNeeded(deviceUid, GENERATION_ONE)

        assertTrue(decision is DeviceTimeSyncDecision.Attempted)
        assertEquals(1, syncCalls.get())
    }

    @Test
    fun `timezone or auto sync policy drift uses existing phone sync`() = runBlocking {
        val syncCalls = AtomicInteger(0)
        val deviceUid = DeviceUid("device-time-zone-drift")
        val generation = AtomicReference(GENERATION_ONE)
        val coordinator = coordinator(
            generation = generation,
            requestStatus = { uid, expectedGeneration ->
                statusSuccess(
                    uid,
                    status().copy(
                        timezoneId = "UTC",
                        posixTimeZone = "UTC0",
                        utcOffsetMinutes = 0,
                        autoSyncNtpEnabled = false
                    ),
                    expectedGeneration
                )
            },
            syncPhoneNow = { uid, expectedGeneration ->
                syncCalls.incrementAndGet()
                syncSuccess(uid, expectedGeneration)
            }
        )

        assertTrue(
            coordinator.syncPhoneNowIfNeeded(
                deviceUid,
                GENERATION_ONE
            ) is DeviceTimeSyncDecision.Attempted
        )
        assertEquals(1, syncCalls.get())
    }

    @Test
    fun `transient status failure retries status first within the same generation`() =
        runBlocking {
            val statusCalls = AtomicInteger(0)
            val syncCalls = AtomicInteger(0)
            val delayCalls = AtomicInteger(0)
            val deviceUid = DeviceUid("device-time-status-retry")
            val generation = AtomicReference(GENERATION_ONE)
            val coordinator = coordinator(
                generation = generation,
                requestStatus = { uid, expectedGeneration ->
                    if (statusCalls.incrementAndGet() == 1) {
                        DeviceRuntimeCommandOutcome.NotConnected(
                            deviceUid = uid,
                            module = DeviceTimeRuntimeContract.MODULE,
                            action = DeviceTimeRuntimeContract.Action.STATUS_GET
                        )
                    } else {
                        statusSuccess(
                            uid,
                            status().copy(timeSet = false),
                            expectedGeneration
                        )
                    }
                },
                syncPhoneNow = { uid, expectedGeneration ->
                    syncCalls.incrementAndGet()
                    syncSuccess(uid, expectedGeneration)
                },
                retryDelay = { delayCalls.incrementAndGet() }
            )

            val decision = coordinator.syncPhoneNowIfNeeded(deviceUid, GENERATION_ONE)

            assertTrue(decision is DeviceTimeSyncDecision.Attempted)
            assertEquals(2, statusCalls.get())
            assertEquals(1, syncCalls.get())
            assertEquals(1, delayCalls.get())
        }

    @Test
    fun `transient sync failure rechecks status before retrying mutation`() = runBlocking {
        val calls = mutableListOf<String>()
        val syncCalls = AtomicInteger(0)
        val deviceUid = DeviceUid("device-time-sync-retry")
        val generation = AtomicReference(GENERATION_ONE)
        val coordinator = coordinator(
            generation = generation,
            requestStatus = { uid, expectedGeneration ->
                calls += "status"
                statusSuccess(uid, status().copy(timeSet = false), expectedGeneration)
            },
            syncPhoneNow = { uid, expectedGeneration ->
                calls += "sync"
                if (syncCalls.incrementAndGet() == 1) {
                    DeviceRuntimeCommandOutcome.SendFailed(
                        deviceUid = uid,
                        module = DeviceTimeRuntimeContract.MODULE,
                        action = DeviceTimeRuntimeContract.Action.PHONE_SYNC,
                        messageId = "failed-sync-message-id",
                        generation = expectedGeneration
                    )
                } else {
                    syncSuccess(uid, expectedGeneration)
                }
            },
            retryDelay = { }
        )

        val decision = coordinator.syncPhoneNowIfNeeded(deviceUid, GENERATION_ONE)

        assertTrue(decision is DeviceTimeSyncDecision.Attempted)
        assertEquals(listOf("status", "sync", "status", "sync"), calls)
    }

    @Test
    fun `non transient firmware sync failure is not retried`() = runBlocking {
        val statusCalls = AtomicInteger(0)
        val syncCalls = AtomicInteger(0)
        val delayCalls = AtomicInteger(0)
        val deviceUid = DeviceUid("device-time-hard-sync-failure")
        val generation = AtomicReference(GENERATION_ONE)
        val coordinator = coordinator(
            generation = generation,
            requestStatus = { uid, expectedGeneration ->
                statusCalls.incrementAndGet()
                statusSuccess(uid, status().copy(timeSet = false), expectedGeneration)
            },
            syncPhoneNow = { uid, expectedGeneration ->
                syncCalls.incrementAndGet()
                DeviceRuntimeCommandOutcome.FirmwareError(
                    deviceUid = uid,
                    module = DeviceTimeRuntimeContract.MODULE,
                    action = DeviceTimeRuntimeContract.Action.PHONE_SYNC,
                    messageId = "firmware-sync-error-message-id",
                    generation = expectedGeneration,
                    statusCode = 503,
                    code = "rtcUnavailable",
                    field = "rtc",
                    message = "RTC is unavailable."
                )
            },
            retryDelay = { delayCalls.incrementAndGet() }
        )

        val decision = coordinator.syncPhoneNowIfNeeded(deviceUid, GENERATION_ONE)

        assertTrue(decision is DeviceTimeSyncDecision.Attempted)
        assertEquals(1, statusCalls.get())
        assertEquals(1, syncCalls.get())
        assertEquals(0, delayCalls.get())
    }

    @Test
    fun `non transient firmware status failure is not retried`() = runBlocking {
        val statusCalls = AtomicInteger(0)
        val syncCalls = AtomicInteger(0)
        val delayCalls = AtomicInteger(0)
        val deviceUid = DeviceUid("device-time-hard-failure")
        val generation = AtomicReference(GENERATION_ONE)
        val coordinator = coordinator(
            generation = generation,
            requestStatus = { uid, expectedGeneration ->
                statusCalls.incrementAndGet()
                DeviceRuntimeCommandOutcome.FirmwareError(
                    deviceUid = uid,
                    module = DeviceTimeRuntimeContract.MODULE,
                    action = DeviceTimeRuntimeContract.Action.STATUS_GET,
                    messageId = "firmware-error-message-id",
                    generation = expectedGeneration,
                    statusCode = 503,
                    code = "rtcUnavailable",
                    field = "rtc",
                    message = "RTC is unavailable."
                )
            },
            syncPhoneNow = { uid, expectedGeneration ->
                syncCalls.incrementAndGet()
                syncSuccess(uid, expectedGeneration)
            },
            retryDelay = { delayCalls.incrementAndGet() }
        )

        val decision = coordinator.syncPhoneNowIfNeeded(deviceUid, GENERATION_ONE)

        assertTrue(decision is DeviceTimeSyncDecision.Skipped)
        assertEquals(1, statusCalls.get())
        assertEquals(0, syncCalls.get())
        assertEquals(0, delayCalls.get())
    }

    @Test
    fun `transient status retry is bounded to three status reads`() = runBlocking {
        val statusCalls = AtomicInteger(0)
        val delayCalls = AtomicInteger(0)
        val deviceUid = DeviceUid("device-time-bounded-retry")
        val generation = AtomicReference(GENERATION_ONE)
        val coordinator = coordinator(
            generation = generation,
            requestStatus = { uid, _ ->
                statusCalls.incrementAndGet()
                DeviceRuntimeCommandOutcome.NotConnected(
                    deviceUid = uid,
                    module = DeviceTimeRuntimeContract.MODULE,
                    action = DeviceTimeRuntimeContract.Action.STATUS_GET
                )
            },
            syncPhoneNow = { uid, expectedGeneration ->
                syncSuccess(uid, expectedGeneration)
            },
            retryDelay = { delayCalls.incrementAndGet() }
        )

        val decision = coordinator.syncPhoneNowIfNeeded(deviceUid, GENERATION_ONE)

        assertTrue(decision is DeviceTimeSyncDecision.Skipped)
        assertEquals(3, statusCalls.get())
        assertEquals(2, delayCalls.get())
    }

    @Test
    fun `concurrent duplicate is skipped for the same generation`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val statusCalls = AtomicInteger(0)
        val syncCalls = AtomicInteger(0)
        val deviceUid = DeviceUid("device-time-race")
        val generation = AtomicReference(GENERATION_ONE)
        val coordinator = coordinator(
            generation = generation,
            requestStatus = { uid, expectedGeneration ->
                statusCalls.incrementAndGet()
                entered.complete(Unit)
                release.await()
                statusSuccess(uid, status().copy(timeSet = false), expectedGeneration)
            },
            syncPhoneNow = { uid, expectedGeneration ->
                syncCalls.incrementAndGet()
                syncSuccess(uid, expectedGeneration)
            }
        )

        val first = async {
            coordinator.syncPhoneNowIfNeeded(deviceUid, GENERATION_ONE)
        }
        entered.await()
        val second = coordinator.syncPhoneNowIfNeeded(deviceUid, GENERATION_ONE)
        release.complete(Unit)

        assertTrue(second is DeviceTimeSyncDecision.Skipped)
        assertTrue(first.await() is DeviceTimeSyncDecision.Attempted)
        assertEquals(1, statusCalls.get())
        assertEquals(1, syncCalls.get())
    }

    @Test
    fun `new generation is not blocked and stale status cannot sync it`() = runBlocking {
        val oldStatusEntered = CompletableDeferred<Unit>()
        val releaseOldStatus = CompletableDeferred<Unit>()
        val syncedGenerations = mutableListOf<DeviceRuntimeConnectionGeneration>()
        val deviceUid = DeviceUid("device-time-generation-change")
        val generation = AtomicReference(GENERATION_ONE)
        val coordinator = coordinator(
            generation = generation,
            requestStatus = { uid, expectedGeneration ->
                if (expectedGeneration == GENERATION_ONE) {
                    oldStatusEntered.complete(Unit)
                    releaseOldStatus.await()
                }
                statusSuccess(
                    uid,
                    status().copy(timeSet = false),
                    expectedGeneration
                )
            },
            syncPhoneNow = { uid, expectedGeneration ->
                syncedGenerations += expectedGeneration
                syncSuccess(uid, expectedGeneration)
            }
        )

        val oldDecision = async {
            coordinator.syncPhoneNowIfNeeded(deviceUid, GENERATION_ONE)
        }
        oldStatusEntered.await()
        generation.set(GENERATION_TWO)

        val newDecision = coordinator.syncPhoneNowIfNeeded(deviceUid, GENERATION_TWO)
        releaseOldStatus.complete(Unit)

        assertTrue(newDecision is DeviceTimeSyncDecision.Attempted)
        assertTrue(oldDecision.await() is DeviceTimeSyncDecision.Skipped)
        assertEquals(listOf(GENERATION_TWO), syncedGenerations)
    }

    private fun coordinator(
        generation: AtomicReference<DeviceRuntimeConnectionGeneration>,
        requestStatus: suspend (
            DeviceUid,
            DeviceRuntimeConnectionGeneration
        ) -> DeviceRuntimeCommandOutcome<DeviceTimeStatus>?,
        syncPhoneNow: suspend (
            DeviceUid,
            DeviceRuntimeConnectionGeneration
        ) -> DeviceRuntimeCommandOutcome<DeviceTimeMutationResult>?,
        retryDelay: suspend (Long) -> Unit = { }
    ): DeviceTimeSyncCoordinator = DeviceTimeSyncCoordinator(
        requestStatus = requestStatus,
        syncPhoneNow = syncPhoneNow,
        currentConnectionGeneration = { generation.get() },
        currentTimeZoneSnapshot = {
            DeviceTimeZoneSnapshot(
                timezoneId = "Europe/Istanbul",
                posixTimeZone = "TRT-3",
                utcOffsetMinutes = 180
            )
        },
        retryDelay = retryDelay
    )

    private fun statusSuccess(
        deviceUid: DeviceUid,
        status: DeviceTimeStatus,
        generation: DeviceRuntimeConnectionGeneration
    ): DeviceRuntimeCommandOutcome.Success<DeviceTimeStatus> =
        DeviceRuntimeCommandOutcome.Success(
            deviceUid = deviceUid,
            module = DeviceTimeRuntimeContract.MODULE,
            action = DeviceTimeRuntimeContract.Action.STATUS_GET,
            messageId = "status-message-id",
            generation = generation,
            statusCode = 200,
            value = status
        )

    private fun syncSuccess(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): DeviceRuntimeCommandOutcome.Success<DeviceTimeMutationResult> =
        DeviceRuntimeCommandOutcome.Success(
            deviceUid = deviceUid,
            module = DeviceTimeRuntimeContract.MODULE,
            action = DeviceTimeRuntimeContract.Action.PHONE_SYNC,
            messageId = "sync-message-id",
            generation = generation,
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
        lastSyncSource = "rtc",
        lastSyncEpochMillis = 0L,
        lastSyncUptimeMs = 5_000L
    )

    private companion object {
        val GENERATION_ONE = DeviceRuntimeConnectionGeneration(1L)
        val GENERATION_TWO = DeviceRuntimeConnectionGeneration(2L)
    }
}
