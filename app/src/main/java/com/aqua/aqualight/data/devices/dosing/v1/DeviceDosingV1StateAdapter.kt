package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelRejection
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeLifecycleEvent
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeTypedEvent
import kotlinx.coroutines.CoroutineScope

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
    data class Success<T>(
        val value: T,
        val state: DeviceDosingV1AuthoritativeState
    ) : DeviceDosingV1MutationResult<T>

    /**
     * Firmware durably accepted a persisted mutation, but authoritative readback is still pending.
     * No snapshot is carried because the central state owner must remain fail-closed until a full
     * global/channel/progress join succeeds.
     */
    data class Committed(val revision: Long) : DeviceDosingV1MutationResult<Nothing>

    /** A failed/contended assignment was proven present by authoritative readback. */
    data class Reconciled(
        val state: DeviceDosingV1AuthoritativeState
    ) : DeviceDosingV1MutationResult<Nothing>

    data class Failed(val outcome: DeviceRuntimeCommandOutcome<*>) : DeviceDosingV1MutationResult<Nothing>
    data class LocallyRejected(val reason: DeviceDosingChannelRejection) : DeviceDosingV1MutationResult<Nothing>
    data object Conflict : DeviceDosingV1MutationResult<Nothing>
    data object RejectedStale : DeviceDosingV1MutationResult<Nothing>
    data object Malformed : DeviceDosingV1MutationResult<Nothing>
}

internal sealed interface DeviceDosingV1EventResult {
    data class Refreshed(val state: DeviceDosingV1AuthoritativeState) : DeviceDosingV1EventResult
    data object RefreshedAll : DeviceDosingV1EventResult
    data object Ignored : DeviceDosingV1EventResult
    data object Malformed : DeviceDosingV1EventResult
    data object RefreshFailed : DeviceDosingV1EventResult
}

/** Central Dosing facade; [DeviceDosingV1StateOwner] remains the only authoritative state owner. */
internal class DeviceDosingV1StateAdapter(
    internal val repository: DeviceDosingV1Repository,
    stateOwner: DeviceDosingV1StateOwner = DeviceDosingV1StateOwner(),
    internal val reconciliationScope: CoroutineScope? = null
) {
    internal val stateAccess = DeviceDosingV1StateAccess(stateOwner)
    private val operationGate = DeviceDosingV1ChannelOperationGate()
    internal val refreshCoordinator = DeviceDosingV1RefreshCoordinator(
        repository = repository,
        stateOwner = stateOwner,
        stateAccess = stateAccess,
        operationGate = operationGate
    )
    private val backgroundReconciliation = reconciliationScope?.let { scope ->
        DeviceDosingV1CommittedReconciliationScheduler(scope, refreshCoordinator)
    }
    internal val mutationCoordinator = DeviceDosingV1MutationCoordinator(
        stateOwner = stateOwner,
        stateAccess = stateAccess,
        refreshCoordinator = refreshCoordinator,
        operationGate = operationGate,
        scheduleBackgroundReconciliation = backgroundReconciliation?.let { scheduler ->
            scheduler::schedule
        },
        cancelBackgroundReconciliation = backgroundReconciliation?.let { scheduler ->
            scheduler::cancel
        }
    )
    private val eventCoordinator = DeviceDosingV1EventCoordinator(
        stateOwner = stateOwner,
        refreshCoordinator = refreshCoordinator
    )

    val channelOperations = DeviceDosingV1ChannelOperationsAdapter(this)
    val calibrationOperations = DeviceDosingV1CalibrationOperationsAdapter(this)

    suspend fun consume(event: DeviceRuntimeTypedEvent): DeviceDosingV1EventResult = eventCoordinator.consume(event)

    /** Socket lifecycle changes revoke authority without fabricating empty firmware state. */
    fun consume(event: DeviceRuntimeLifecycleEvent) { stateAccess.invalidateAll(event.deviceUid) }

    fun currentChannel(deviceUid: String, slotId: String): DeviceDosingChannelSnapshot? =
        stateAccess.currentChannel(deviceUid, slotId)

    fun currentCalibration(deviceUid: String, slotId: String): DeviceDosingCalibrationSnapshot? =
        stateAccess.currentCalibration(deviceUid, slotId)
}

internal class LocalDosingMutationRejection(val reason: DeviceDosingChannelRejection) : IllegalStateException()
