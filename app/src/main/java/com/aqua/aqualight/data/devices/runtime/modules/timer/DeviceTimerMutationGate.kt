package com.aqua.aqualight.data.devices.runtime.modules.timer

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes Timer mutations per device because firmware owns one device-wide revision. */
internal class DeviceTimerMutationGate {
    private val locks = ConcurrentHashMap<DeviceUid, Mutex>()

    suspend fun <T> withDevice(deviceUid: DeviceUid, block: suspend () -> T): T =
        lock(deviceUid).withLock { block() }

    private fun lock(deviceUid: DeviceUid): Mutex =
        locks.computeIfAbsent(deviceUid) { Mutex() }
}

internal fun List<DeviceTimerScheduleConfig>.matchesTimerSchedules(
    statuses: List<DeviceTimerScheduleStatus>
): Boolean {
    if (size != statuses.size) return false
    val bySlotId = statuses.associateBy(DeviceTimerScheduleStatus::slotId)
    return all { schedule ->
        val status = bySlotId[schedule.slotId] ?: return@all false
        status.enabled == schedule.enabled &&
            status.name == schedule.normalizedName &&
            status.weekdays == schedule.weekdays &&
            status.startTimeMs == schedule.startTimeMs &&
            status.endTimeMs == schedule.endTimeMs &&
            status.spansMidnight == schedule.spansMidnight
    }
}

internal fun DeviceTimerRegime.toTimerOperatingStateOrNull(): DeviceTimerOperatingState? =
    when (this) {
        DeviceTimerRegime.ON -> DeviceTimerOperatingState.ON
        DeviceTimerRegime.OFF -> DeviceTimerOperatingState.OFF
        DeviceTimerRegime.AUTO -> null
    }

internal fun DeviceRuntimeCommandOutcome.FirmwareError.requiresTimerReconciliation(): Boolean =
    code == DeviceTimerRuntimeContract.Error.CONFLICT ||
        code == DeviceTimerRuntimeContract.Error.NOT_FOUND ||
        code == DeviceTimerRuntimeContract.Error.HARDWARE_ERROR ||
        code == DeviceTimerRuntimeContract.Error.STORAGE_ERROR

internal fun <T> DeviceRuntimeCommandOutcome.Success<T>.timerReconciliationFailure():
    DeviceRuntimeCommandOutcome.ProtocolError = DeviceRuntimeCommandOutcome.ProtocolError(
        deviceUid = deviceUid,
        module = module,
        action = action,
        messageId = messageId,
        generation = generation,
        reason = "Timer mutation ACK was not confirmed by authoritative channel readback."
    )
