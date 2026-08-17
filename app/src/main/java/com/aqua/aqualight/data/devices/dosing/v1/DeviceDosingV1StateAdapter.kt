package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelRejection
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeLifecycleEvent
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeTypedEvent

internal data class DeviceDosingV1AuthoritativeState(
    val channel: DeviceDosingChannelSnapshot,
    val calibration: DeviceDosingCalibrationSnapshot
)

internal sealed interface DeviceDosingV1RefreshResult {
    data class Success(val state: DeviceDosingV1AuthoritativeState) : DeviceDosingV1RefreshResult
    data class Failed(val outcome: DeviceRuntimeCommandOutcome<*>) : DeviceDosingV1RefreshResult
    data object RejectedStale : DeviceDosingV1RefreshResult
    data object Malformed : DeviceDosingV1RefreshResult
}

internal sealed interface DeviceDosingV1MutationResult<out T> {
    data class Success<T>(val value: T, val state: DeviceDosingV1AuthoritativeState) : DeviceDosingV1MutationResult<T>
    data class Failed(val outcome: DeviceRuntimeCommandOutcome<*>) : DeviceDosingV1MutationResult<Nothing>
    data class LocallyRejected(val reason: DeviceDosingChannelRejection) : DeviceDosingV1MutationResult<Nothing>
    data object Conflict : DeviceDosingV1MutationResult<Nothing>
    data object RejectedStale : DeviceDosingV1MutationResult<Nothing>
    data object Malformed : DeviceDosingV1MutationResult<Nothing>
}

internal sealed interface DeviceDosingV1EventResult {
    data class Refreshed(val state: DeviceDosingV1AuthoritativeState) : DeviceDosingV1EventResult
    data object Ignored : DeviceDosingV1EventResult
    data object Malformed : DeviceDosingV1EventResult
    data object RefreshFailed : DeviceDosingV1EventResult
}

/** Central Dosing facade; [DeviceDosingV1StateOwner] remains the only authoritative state owner. */
internal class DeviceDosingV1StateAdapter(
    internal val repository: DeviceDosingV1Repository,
    stateOwner: DeviceDosingV1StateOwner = DeviceDosingV1StateOwner()
) {
    internal val stateAccess = DeviceDosingV1StateAccess(stateOwner)
    private val operationGate = DeviceDosingV1ChannelOperationGate()
    internal val refreshCoordinator = DeviceDosingV1RefreshCoordinator(
        repository = repository,
        stateOwner = stateOwner,
        stateAccess = stateAccess,
        operationGate = operationGate
    )
    internal val mutationCoordinator = DeviceDosingV1MutationCoordinator(
        stateOwner = stateOwner,
        stateAccess = stateAccess,
        refreshCoordinator = refreshCoordinator,
        operationGate = operationGate
    )
    private val eventCoordinator = DeviceDosingV1EventCoordinator(
        stateOwner = stateOwner,
        refreshCoordinator = refreshCoordinator,
        operationGate = operationGate
    )

    val channelOperations = DeviceDosingV1ChannelOperationsAdapter(this)
    val calibrationOperations = DeviceDosingV1CalibrationOperationsAdapter(this)

    suspend fun consume(event: DeviceRuntimeTypedEvent): DeviceDosingV1EventResult = eventCoordinator.consume(event)

    /** A reconnect or disconnect boundary must never retain a previous session's snapshots. */
    fun consume(event: DeviceRuntimeLifecycleEvent) { stateAccess.clear(event.deviceUid) }

    fun currentChannel(deviceUid: String, slotId: String): DeviceDosingChannelSnapshot? =
        stateAccess.currentChannel(deviceUid, slotId)

    fun currentCalibration(deviceUid: String, slotId: String): DeviceDosingCalibrationSnapshot? =
        stateAccess.currentCalibration(deviceUid, slotId)
}

internal class LocalDosingMutationRejection(val reason: DeviceDosingChannelRejection) : IllegalStateException()
