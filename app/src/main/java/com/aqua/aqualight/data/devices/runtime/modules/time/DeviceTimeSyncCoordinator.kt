package com.aqua.aqualight.data.devices.runtime.modules.time

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class DeviceTimeSyncScheduleResult {
    SCHEDULED,
    SKIPPED
}

/** One exact phone-time synchronization per authenticated device session. */
class DeviceTimeSyncCoordinator internal constructor(
    private val scope: CoroutineScope,
    private val syncPhoneNow:
        suspend (DeviceUid) -> DeviceRuntimeCommandOutcome<DeviceTimeSyncResult>
) {
    constructor(
        scope: CoroutineScope,
        repository: DeviceTimeRuntimeRepository
    ) : this(
        scope = scope,
        syncPhoneNow = { deviceUid ->
            repository.syncPhoneNow(deviceUid = deviceUid, save = false)
        }
    )

    private val lock = Any()
    private val sessionEpochs = mutableMapOf<String, Long>()
    private val syncedEpochs = mutableMapOf<String, Long>()
    private val jobs = mutableMapOf<String, Job>()

    fun syncPhoneNowIfNeeded(
        deviceUid: DeviceUid,
        force: Boolean = false
    ): DeviceTimeSyncScheduleResult {
        val key = deviceUid.value
        val epoch: Long
        val job: Job
        synchronized(lock) {
            epoch = sessionEpochs.getOrPut(key) { FIRST_SESSION_EPOCH }
            if (jobs[key]?.isActive == true || (!force && syncedEpochs[key] == epoch)) {
                return DeviceTimeSyncScheduleResult.SKIPPED
            }

            job = scope.launch(start = CoroutineStart.LAZY) {
                val success = try {
                    val outcome = syncPhoneNow(deviceUid)
                    outcome is DeviceRuntimeCommandOutcome.Success &&
                        outcome.value.saved == false &&
                        outcome.value.saveRequested == false
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    false
                }

                synchronized(lock) {
                    if (sessionEpochs[key] == epoch && jobs[key] === coroutineContext[Job]) {
                        jobs.remove(key)
                        if (success) syncedEpochs[key] = epoch
                    }
                }
            }
            jobs[key] = job
        }

        job.invokeOnCompletion {
            synchronized(lock) {
                if (jobs[key] === job) jobs.remove(key)
            }
        }
        job.start()
        return DeviceTimeSyncScheduleResult.SCHEDULED
    }

    fun clearSessionMemory(deviceUid: DeviceUid) {
        val key = deviceUid.value
        val job = synchronized(lock) {
            val current = sessionEpochs[key] ?: FIRST_SESSION_EPOCH
            sessionEpochs[key] = current + 1L
            syncedEpochs.remove(key)
            jobs.remove(key)
        }
        job?.cancel()
    }

    internal fun isSynchronized(deviceUid: DeviceUid): Boolean = synchronized(lock) {
        syncedEpochs[deviceUid.value] == sessionEpochs[deviceUid.value]
    }

    internal fun isSyncing(deviceUid: DeviceUid): Boolean = synchronized(lock) {
        jobs[deviceUid.value]?.isActive == true
    }

    private companion object {
        const val FIRST_SESSION_EPOCH = 1L
    }
}
