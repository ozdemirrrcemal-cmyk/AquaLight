package com.aqua.aqualight.data.devices.runtime.modules.timer

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome

internal suspend fun DeviceTimerRuntimeRepository.reconcileConfigMutation(
    deviceUid: DeviceUid,
    payload: DeviceTimerConfigApplyPayload,
    outcome: DeviceRuntimeCommandOutcome<DeviceTimerConfigApplyResult>
): DeviceRuntimeCommandOutcome<DeviceTimerConfigApplyResult> = reconcileMutation(
    deviceUid = deviceUid,
    channelKey = payload.normalizedChannelKey,
    outcome = outcome,
    reconciliation = TimerMutationReconciliation(
        channel = DeviceTimerConfigApplyResult::channel,
        revision = DeviceTimerConfigApplyResult::revision,
        confirms = { _, status ->
            val channel = status.channels.singleOrNull()
            val displayNameConfirmed = when (val update = payload.displayName) {
                DeviceTimerDisplayNameUpdate.Omitted -> true
                DeviceTimerDisplayNameUpdate.Clear -> channel?.displayName == channel?.name
                is DeviceTimerDisplayNameUpdate.Value ->
                    channel?.displayName == update.normalizedDisplayName
            }
            val schedulesConfirmed = payload.schedules
                ?.matchesTimerSchedules(status.schedules) ?: true
            displayNameConfirmed && schedulesConfirmed
        }
    )
)

internal suspend fun DeviceTimerRuntimeRepository.reconcileChannelMutation(
    deviceUid: DeviceUid,
    payload: DeviceTimerChannelSetPayload,
    outcome: DeviceRuntimeCommandOutcome<DeviceTimerChannelSetResult>
): DeviceRuntimeCommandOutcome<DeviceTimerChannelSetResult> = reconcileMutation(
    deviceUid = deviceUid,
    channelKey = payload.normalizedChannelKey,
    outcome = outcome,
    reconciliation = TimerMutationReconciliation(
        channel = DeviceTimerChannelSetResult::channel,
        revision = DeviceTimerChannelSetResult::revision,
        confirms = { _, status ->
            val channel = status.channels.singleOrNull()
            if (payload.durationMs == null) {
                channel?.regime == payload.regime
            } else {
                channel?.temporaryOverrideActive == true &&
                    channel.operatingState == payload.regime.toTimerOperatingStateOrNull()
            }
        }
    )
)

/**
 * ACK and rejection reconciliation stays inside the central Timer facade. There is no command
 * replay: a successful mutation must be confirmed by a same-generation scoped readback, while
 * failures that can leave cached state uncertain revoke reads until one fresh snapshot lands.
 */
private suspend fun <T> DeviceTimerRuntimeRepository.reconcileMutation(
    deviceUid: DeviceUid,
    channelKey: String,
    outcome: DeviceRuntimeCommandOutcome<T>,
    reconciliation: TimerMutationReconciliation<T>
): DeviceRuntimeCommandOutcome<T> {
    if (outcome !is DeviceRuntimeCommandOutcome.Success) {
        reconcileRejectedMutation(deviceUid, channelKey, outcome)
        return outcome
    }
    val mutation = outcome
    val value = mutation.value
    stateStore.recordMutationChannel(
        deviceUid,
        mutation.generation,
        reconciliation.channel(value),
        reconciliation.revision(value)
    )
    return when (val readback = requestStatus(deviceUid, channelKey)) {
        is DeviceRuntimeCommandOutcome.Success -> if (
            isConfirmedBy(mutation, deviceUid, channelKey, readback, reconciliation)
        ) {
            mutation
        } else {
            stateStore.invalidate(deviceUid, mutation.generation)
            mutation.timerReconciliationFailure()
        }
        else -> {
            stateStore.invalidate(deviceUid, mutation.generation)
            readback.asTimerFailure()
        }
    }
}

private suspend fun DeviceTimerRuntimeRepository.reconcileRejectedMutation(
    deviceUid: DeviceUid,
    channelKey: String,
    outcome: DeviceRuntimeCommandOutcome<*>
) {
    val failure = outcome as? DeviceRuntimeCommandOutcome.FirmwareError ?: return
    if (!failure.requiresTimerReconciliation()) return
    stateStore.requireStatusRefresh(deviceUid, failure.generation)
    val global = requestStatus(deviceUid)
    if (
        global is DeviceRuntimeCommandOutcome.Success &&
        global.generation == failure.generation &&
        global.value.channels.any { channel -> channel.key == channelKey }
    ) {
        requestStatus(deviceUid, channelKey)
    }
}

private fun <T> DeviceTimerRuntimeRepository.isConfirmedBy(
    mutation: DeviceRuntimeCommandOutcome.Success<T>,
    deviceUid: DeviceUid,
    channelKey: String,
    readback: DeviceRuntimeCommandOutcome.Success<DeviceTimerStatus>,
    reconciliation: TimerMutationReconciliation<T>
): Boolean {
    val accepted = stateStore.currentAuthoritativeState(deviceUid)
    return readback.generation == mutation.generation &&
        readback.value.channelScoped &&
        readback.value.selectedChannelKey == channelKey &&
        readback.value.revision == reconciliation.revision(mutation.value) &&
        accepted != null &&
        accepted.connectionGeneration == readback.generation &&
        accepted.channelDetails[channelKey] == readback.value &&
        reconciliation.confirms(mutation.value, readback.value)
}

private data class TimerMutationReconciliation<T>(
    val channel: (T) -> DeviceTimerChannelStatus,
    val revision: (T) -> Long,
    val confirms: (T, DeviceTimerStatus) -> Boolean
)
