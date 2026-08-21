package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.detail

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelCommittedResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingMutationReconciliation
import com.aqua.aqualight.application.devices.dosing.applyLatestIntentWithReconciliation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Read-only projection of the latest central Dosing snapshot needed by the switch UI.
 *
 * This is not a device state owner. The central Dosing state owner remains authoritative; this
 * value is replaced only from authoritative channel snapshots.
 */
internal data class DeviceDosingMissedDoseRecoveryAuthority(
    val enabled: Boolean = false,
    val editable: Boolean = false,
    val revision: Long = 0L
)

internal sealed interface DeviceDosingMissedDoseRecoveryAction {
    data object Idle : DeviceDosingMissedDoseRecoveryAction
    data class Write(val targetEnabled: Boolean) : DeviceDosingMissedDoseRecoveryAction
    data class Reconcile(val minimumRevision: Long) : DeviceDosingMissedDoseRecoveryAction
    data class Fail(val failure: DeviceDosingChannelDetailFailure) : DeviceDosingMissedDoseRecoveryAction
}

internal sealed interface DeviceDosingMissedDoseRecoveryFeedback {
    data object None : DeviceDosingMissedDoseRecoveryFeedback
    data object Saved : DeviceDosingMissedDoseRecoveryFeedback
    data class Failed(
        val failure: DeviceDosingChannelDetailFailure
    ) : DeviceDosingMissedDoseRecoveryFeedback
}

private data class InFlightMissedDoseRecovery(
    val targetEnabled: Boolean
)

/**
 * Durable firmware ACK projected temporarily while the central state owner is invalidated awaiting
 * readback. It expires as soon as an equal/newer authoritative snapshot arrives and is never used as
 * a replacement device state owner.
 */
private data class AcknowledgedMissedDoseRecovery(
    val targetEnabled: Boolean,
    val revision: Long
)

/**
 * Pure UI-intent state machine for the missed-dose recovery switch.
 *
 * It owns only transient user intent and mutation metadata. Authoritative device state still comes
 * exclusively from the central Dosing state owner.
 */
internal class DeviceDosingMissedDoseRecoveryIntentState {
    private var desiredEnabled: Boolean? = null
    private var inFlight: InFlightMissedDoseRecovery? = null
    private var acknowledged: AcknowledgedMissedDoseRecovery? = null
    private var reconciliationInFlight: Boolean = false

    fun reset() {
        desiredEnabled = null
        inFlight = null
        acknowledged = null
        reconciliationInFlight = false
    }

    fun request(enabled: Boolean) {
        desiredEnabled = enabled
    }

    fun nextAction(
        authority: DeviceDosingMissedDoseRecoveryAuthority
    ): DeviceDosingMissedDoseRecoveryAction {
        val persistedEnabled = acknowledged?.targetEnabled ?: authority.enabled
        return when {
            inFlight != null -> DeviceDosingMissedDoseRecoveryAction.Idle
            desiredEnabled == null -> DeviceDosingMissedDoseRecoveryAction.Idle
            desiredEnabled == persistedEnabled -> {
                desiredEnabled = null
                DeviceDosingMissedDoseRecoveryAction.Idle
            }
            reconciliationInFlight -> DeviceDosingMissedDoseRecoveryAction.Idle
            acknowledged != null -> DeviceDosingMissedDoseRecoveryAction.Reconcile(
                minimumRevision = requireNotNull(acknowledged).revision.also {
                    reconciliationInFlight = true
                }
            )
            !authority.editable -> {
                desiredEnabled = null
                DeviceDosingMissedDoseRecoveryAction.Fail(
                    DeviceDosingChannelDetailFailure.NOT_EDITABLE
                )
            }
            else -> DeviceDosingMissedDoseRecoveryAction.Write(
                targetEnabled = requireNotNull(desiredEnabled).also { targetEnabled ->
                    inFlight = InFlightMissedDoseRecovery(targetEnabled)
                }
            )
        }
    }

    /** Handles a durable firmware ACK when its authoritative readback may still be pending. */
    fun onCommitted(
        targetEnabled: Boolean,
        committedRevision: Long,
        authority: DeviceDosingMissedDoseRecoveryAuthority
    ): DeviceDosingMissedDoseRecoveryFeedback {
        val active = inFlight
        if (active == null || active.targetEnabled != targetEnabled) {
            return DeviceDosingMissedDoseRecoveryFeedback.None
        }
        inFlight = null
        acknowledged = null
        return when {
            authority.revision < committedRevision -> {
                recordAcknowledged(targetEnabled, committedRevision)
                if (desiredEnabled == targetEnabled) {
                    desiredEnabled = null
                    DeviceDosingMissedDoseRecoveryFeedback.Saved
                } else {
                    DeviceDosingMissedDoseRecoveryFeedback.None
                }
            }
            authority.enabled == targetEnabled && desiredEnabled == targetEnabled -> {
                desiredEnabled = null
                DeviceDosingMissedDoseRecoveryFeedback.Saved
            }
            authority.enabled != targetEnabled && desiredEnabled == targetEnabled -> {
                desiredEnabled = null
                DeviceDosingMissedDoseRecoveryFeedback.Failed(
                    DeviceDosingChannelDetailFailure.STATE_CHANGED
                )
            }
            else -> DeviceDosingMissedDoseRecoveryFeedback.None
        }
    }

    /** Handles a successful mutation that already includes an authoritative readback snapshot. */
    fun onSuccessfulReadback(
        targetEnabled: Boolean,
        committedRevision: Long,
        authority: DeviceDosingMissedDoseRecoveryAuthority
    ): DeviceDosingMissedDoseRecoveryFeedback {
        val active = inFlight
        var feedback: DeviceDosingMissedDoseRecoveryFeedback =
            DeviceDosingMissedDoseRecoveryFeedback.None

        if (active != null && active.targetEnabled == targetEnabled) {
            inFlight = null
            acknowledged = null
            feedback = when {
                authority.revision < committedRevision -> {
                    recordAcknowledged(targetEnabled, committedRevision)
                    if (desiredEnabled == targetEnabled) {
                        desiredEnabled = null
                        DeviceDosingMissedDoseRecoveryFeedback.Saved
                    } else {
                        DeviceDosingMissedDoseRecoveryFeedback.None
                    }
                }
                authority.enabled == targetEnabled && desiredEnabled == targetEnabled -> {
                    desiredEnabled = null
                    DeviceDosingMissedDoseRecoveryFeedback.Saved
                }
                authority.enabled != targetEnabled && desiredEnabled == targetEnabled -> {
                    desiredEnabled = null
                    DeviceDosingMissedDoseRecoveryFeedback.Failed(
                        DeviceDosingChannelDetailFailure.STATE_CHANGED
                    )
                }
                else -> DeviceDosingMissedDoseRecoveryFeedback.None
            }
        }
        return feedback
    }

    fun onFailure(
        failedTargetEnabled: Boolean,
        failure: DeviceDosingChannelDetailFailure,
        authority: DeviceDosingMissedDoseRecoveryAuthority
    ): DeviceDosingMissedDoseRecoveryFeedback {
        val active = inFlight
        if (active == null || active.targetEnabled != failedTargetEnabled) {
            return DeviceDosingMissedDoseRecoveryFeedback.None
        }
        inFlight = null
        val persistedEnabled = acknowledged?.targetEnabled ?: authority.enabled
        val latestIntent = desiredEnabled
        return when {
            latestIntent == persistedEnabled -> {
                desiredEnabled = null
                DeviceDosingMissedDoseRecoveryFeedback.None
            }
            latestIntent != null && latestIntent != failedTargetEnabled ->
                DeviceDosingMissedDoseRecoveryFeedback.None
            else -> {
                desiredEnabled = null
                DeviceDosingMissedDoseRecoveryFeedback.Failed(failure)
            }
        }
    }

    fun onReconciliationCompleted(
        failure: DeviceDosingChannelDetailFailure?
    ): DeviceDosingMissedDoseRecoveryFeedback {
        val shouldFailLatestIntent = if (reconciliationInFlight) {
            reconciliationInFlight = false
            val pendingTarget = acknowledged?.targetEnabled
            val latestIntent = desiredEnabled
            val queuedReversal = pendingTarget != null && latestIntent != null &&
                latestIntent != pendingTarget
            failure != null && queuedReversal
        } else {
            false
        }
        if (shouldFailLatestIntent) desiredEnabled = null
        return if (shouldFailLatestIntent) {
            DeviceDosingMissedDoseRecoveryFeedback.Failed(requireNotNull(failure))
        } else {
            DeviceDosingMissedDoseRecoveryFeedback.None
        }
    }

    fun onAuthorityChanged(
        authority: DeviceDosingMissedDoseRecoveryAuthority
    ): DeviceDosingMissedDoseRecoveryFeedback {
        val pendingAck = acknowledged
        val feedback = when {
            pendingAck == null -> DeviceDosingMissedDoseRecoveryFeedback.None
            authority.revision < pendingAck.revision -> DeviceDosingMissedDoseRecoveryFeedback.None
            else -> {
                acknowledged = null
                when {
                    authority.enabled == pendingAck.targetEnabled || inFlight != null ->
                        DeviceDosingMissedDoseRecoveryFeedback.None
                    desiredEnabled == null ->
                        DeviceDosingMissedDoseRecoveryFeedback.Failed(
                            DeviceDosingChannelDetailFailure.STATE_CHANGED
                        )
                    desiredEnabled == pendingAck.targetEnabled -> {
                        desiredEnabled = null
                        DeviceDosingMissedDoseRecoveryFeedback.Failed(
                            DeviceDosingChannelDetailFailure.STATE_CHANGED
                        )
                    }
                    else -> DeviceDosingMissedDoseRecoveryFeedback.None
                }
            }
        }
        return feedback
    }

    fun present(
        draft: DeviceDosingChannelDetailDraft,
        authority: DeviceDosingMissedDoseRecoveryAuthority
    ): DeviceDosingChannelDetailDraft = draft.copy(
        missedDoseRecoveryEnabled = desiredEnabled
            ?: acknowledged?.targetEnabled
            ?: authority.enabled,
        missedDoseRecoveryEditable = authority.editable,
        missedDoseRecoverySyncing = desiredEnabled != null || inFlight != null ||
            reconciliationInFlight
    )

    private fun recordAcknowledged(targetEnabled: Boolean, revision: Long) {
        val current = acknowledged
        if (current == null || revision >= current.revision) {
            acknowledged = AcknowledgedMissedDoseRecovery(targetEnabled, revision)
        }
    }
}

internal data class DeviceDosingChannelDetailBinding(
    val deviceUid: String,
    val slotId: String
)

internal data class DeviceDosingMissedDoseRecoveryHooks(
    val currentBinding: () -> DeviceDosingChannelDetailBinding,
    val currentDraft: () -> DeviceDosingChannelDetailDraft,
    val updateDraft: (DeviceDosingChannelDetailDraft) -> Unit,
    val applySnapshot: (DeviceDosingChannelSnapshot) -> Unit,
    val publishEvent: suspend (DeviceDosingChannelDetailEvent) -> Unit
)

/**
 * Switch-specific UI coordinator.
 *
 * It owns only transient switch intent and its in-flight job. Firmware persistence and all
 * authoritative state remain behind [DeviceDosingChannelOperations] and the central Dosing owner.
 */
internal class DeviceDosingMissedDoseRecoveryController(
    private val operations: DeviceDosingChannelOperations,
    private val scope: CoroutineScope,
    private val hooks: DeviceDosingMissedDoseRecoveryHooks
) {
    private val intent = DeviceDosingMissedDoseRecoveryIntentState()
    private var authority = DeviceDosingMissedDoseRecoveryAuthority()
    private var mutationJob: Job? = null

    fun reset() {
        mutationJob?.cancel()
        mutationJob = null
        intent.reset()
        authority = DeviceDosingMissedDoseRecoveryAuthority()
    }

    fun request(enabled: Boolean) {
        intent.request(enabled)
        present()
        drive()
    }

    fun onSnapshot(snapshot: DeviceDosingChannelSnapshot) {
        val nextAuthority = DeviceDosingMissedDoseRecoveryAuthority(
            enabled = snapshot.program?.missedDoseRecoveryEnabled == true,
            editable = snapshot.program != null &&
                snapshot.scheduling.supportsMissedDoseRecovery &&
                snapshot.controls.programEditable,
            revision = snapshot.revision
        )
        val feedback = if (nextAuthority.revision >= authority.revision) {
            authority = nextAuthority
            intent.onAuthorityChanged(authority)
        } else {
            DeviceDosingMissedDoseRecoveryFeedback.None
        }
        present()
        publish(feedback)
        drive()
    }

    private fun drive() {
        when (val action = intent.nextAction(authority)) {
            DeviceDosingMissedDoseRecoveryAction.Idle -> present()
            is DeviceDosingMissedDoseRecoveryAction.Fail -> {
                present()
                publish(DeviceDosingMissedDoseRecoveryFeedback.Failed(action.failure))
            }
            is DeviceDosingMissedDoseRecoveryAction.Reconcile -> reconcile(action)
            is DeviceDosingMissedDoseRecoveryAction.Write -> {
                val binding = hooks.currentBinding()
                present()
                mutationJob = scope.launch {
                    val reconciliation = if (
                        binding.deviceUid.isBlank() || binding.slotId.isBlank()
                    ) {
                        DeviceDosingMutationReconciliation(
                            DeviceDosingChannelOperationResult.Unavailable
                        )
                    } else {
                        try {
                            operations.applyLatestIntentWithReconciliation(
                                deviceUid = binding.deviceUid,
                                slotId = binding.slotId,
                                desiredDomain = MissedDoseRecoveryDomain(action.targetEnabled),
                                domainFrom = { snapshot ->
                                    MissedDoseRecoveryDomain(
                                        snapshot.program?.missedDoseRecoveryEnabled
                                    )
                                },
                                apply = {
                                    operations.setMissedDoseRecoveryEnabled(
                                        binding.deviceUid,
                                        binding.slotId,
                                        action.targetEnabled
                                    )
                                }
                            )
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (_: Exception) {
                            DeviceDosingMutationReconciliation(
                                DeviceDosingChannelOperationResult.Failed
                            )
                        }
                    }
                    if (hooks.currentBinding() != binding) return@launch
                    if (reconciliation.result !is DeviceDosingChannelOperationResult.Success) {
                        reconciliation.authoritativeSnapshot?.let(hooks.applySnapshot)
                    }
                    handleResult(action.targetEnabled, reconciliation.result)
                }
            }
        }
    }

    private fun reconcile(action: DeviceDosingMissedDoseRecoveryAction.Reconcile) {
        val binding = hooks.currentBinding()
        present()
        mutationJob = scope.launch {
            val result = if (binding.deviceUid.isBlank() || binding.slotId.isBlank()) {
                DeviceDosingChannelOperationResult.Unavailable
            } else {
                try {
                    operations.refresh(binding.deviceUid, binding.slotId)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    DeviceDosingChannelOperationResult.Failed
                }
            }
            if (hooks.currentBinding() != binding) return@launch

            if (
                result is DeviceDosingChannelOperationResult.Success &&
                result.snapshot.revision >= action.minimumRevision
            ) {
                intent.onReconciliationCompleted(failure = null)
                hooks.applySnapshot(result.snapshot)
            } else {
                val feedback = intent.onReconciliationCompleted(result.toReconciliationFailure())
                present()
                publish(feedback)
                drive()
            }
        }
    }

    private fun handleResult(
        targetEnabled: Boolean,
        result: DeviceDosingChannelOperationResult
    ) {
        val feedback = when (result) {
            is DeviceDosingChannelOperationResult.Success -> {
                hooks.applySnapshot(result.snapshot)
                intent.onSuccessfulReadback(
                    targetEnabled = targetEnabled,
                    committedRevision = result.snapshot.revision,
                    authority = authority
                )
            }
            is DeviceDosingChannelCommittedResult -> intent.onCommitted(
                targetEnabled = targetEnabled,
                committedRevision = result.revision,
                authority = authority
            )
            is DeviceDosingChannelOperationResult.Rejected -> intent.onFailure(
                failedTargetEnabled = targetEnabled,
                failure = result.reason.toDetailFailure(),
                authority = authority
            )
            DeviceDosingChannelOperationResult.Unavailable -> intent.onFailure(
                failedTargetEnabled = targetEnabled,
                failure = DeviceDosingChannelDetailFailure.UNAVAILABLE,
                authority = authority
            )
            DeviceDosingChannelOperationResult.Failed -> intent.onFailure(
                failedTargetEnabled = targetEnabled,
                failure = DeviceDosingChannelDetailFailure.TRY_AGAIN,
                authority = authority
            )
        }
        present()
        publish(feedback)
        drive()
    }

    private fun publish(feedback: DeviceDosingMissedDoseRecoveryFeedback) {
        val event = when (feedback) {
            DeviceDosingMissedDoseRecoveryFeedback.None -> null
            DeviceDosingMissedDoseRecoveryFeedback.Saved ->
                DeviceDosingChannelDetailEvent.MissedDoseRecoverySaved
            is DeviceDosingMissedDoseRecoveryFeedback.Failed ->
                DeviceDosingChannelDetailEvent.OperationFailed(feedback.failure)
        }
        event?.let { scope.launch { hooks.publishEvent(it) } }
    }

    private fun present() {
        hooks.updateDraft(intent.present(hooks.currentDraft(), authority))
    }
}

private data class MissedDoseRecoveryDomain(
    val enabled: Boolean?
)

private fun DeviceDosingChannelOperationResult.toReconciliationFailure():
    DeviceDosingChannelDetailFailure = when (this) {
    is DeviceDosingChannelOperationResult.Rejected -> reason.toDetailFailure()
    DeviceDosingChannelOperationResult.Unavailable -> DeviceDosingChannelDetailFailure.UNAVAILABLE
    is DeviceDosingChannelOperationResult.Success,
    is DeviceDosingChannelCommittedResult,
    DeviceDosingChannelOperationResult.Failed -> DeviceDosingChannelDetailFailure.TRY_AGAIN
}
