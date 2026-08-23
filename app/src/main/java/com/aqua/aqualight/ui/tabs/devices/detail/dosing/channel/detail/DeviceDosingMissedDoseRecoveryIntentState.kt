package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.detail

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelCommittedResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Read-only projection of the latest central Dosing snapshot needed by the switch UI. */
internal data class DeviceDosingMissedDoseRecoveryAuthority(
    val enabled: Boolean = false,
    val editable: Boolean = false,
    val revision: Long = 0L
)

internal sealed interface DeviceDosingMissedDoseRecoveryFeedback {
    data object None : DeviceDosingMissedDoseRecoveryFeedback
    data object Saved : DeviceDosingMissedDoseRecoveryFeedback
    data class Failed(
        val failure: DeviceDosingChannelDetailFailure
    ) : DeviceDosingMissedDoseRecoveryFeedback
}

/** Durable firmware ACK retained only until an equal/newer central presentation arrives. */
private data class AcknowledgedMissedDoseRecovery(
    val targetEnabled: Boolean,
    val revision: Long
)

/**
 * Transient switch presentation state.
 *
 * Persistence ordering and latest-intent coalescing belong to the central data coordinator. This
 * class never refreshes firmware, never owns revision authority and never replays a mutation.
 */
internal class DeviceDosingMissedDoseRecoveryIntentState {
    private var desiredEnabled: Boolean? = null
    private var acknowledged: AcknowledgedMissedDoseRecovery? = null
    private var latestRequestId: Long = 0L

    fun reset() {
        latestRequestId += 1L
        desiredEnabled = null
        acknowledged = null
    }

    fun request(enabled: Boolean): Long {
        desiredEnabled = enabled
        latestRequestId += 1L
        return latestRequestId
    }

    fun isLatest(requestId: Long): Boolean = requestId == latestRequestId

    fun onSuccessfulReadback(
        requestId: Long,
        targetEnabled: Boolean,
        authority: DeviceDosingMissedDoseRecoveryAuthority
    ): DeviceDosingMissedDoseRecoveryFeedback {
        if (requestId != latestRequestId) return DeviceDosingMissedDoseRecoveryFeedback.None
        desiredEnabled = null
        acknowledged = null
        return if (authority.enabled == targetEnabled) {
            DeviceDosingMissedDoseRecoveryFeedback.Saved
        } else {
            DeviceDosingMissedDoseRecoveryFeedback.Failed(
                DeviceDosingChannelDetailFailure.STATE_CHANGED
            )
        }
    }

    fun onCommitted(
        requestId: Long,
        targetEnabled: Boolean,
        committedRevision: Long
    ): DeviceDosingMissedDoseRecoveryFeedback {
        if (requestId != latestRequestId) return DeviceDosingMissedDoseRecoveryFeedback.None
        desiredEnabled = null
        acknowledged = AcknowledgedMissedDoseRecovery(targetEnabled, committedRevision)
        return DeviceDosingMissedDoseRecoveryFeedback.Saved
    }

    fun onFailure(
        requestId: Long,
        failure: DeviceDosingChannelDetailFailure
    ): DeviceDosingMissedDoseRecoveryFeedback {
        if (requestId != latestRequestId) return DeviceDosingMissedDoseRecoveryFeedback.None
        desiredEnabled = null
        return DeviceDosingMissedDoseRecoveryFeedback.Failed(failure)
    }

    fun onAuthorityChanged(authority: DeviceDosingMissedDoseRecoveryAuthority) {
        val accepted = acknowledged ?: return
        if (authority.revision >= accepted.revision) acknowledged = null
    }

    fun present(
        draft: DeviceDosingChannelDetailDraft,
        authority: DeviceDosingMissedDoseRecoveryAuthority
    ): DeviceDosingChannelDetailDraft = draft.copy(
        missedDoseRecoveryEnabled = desiredEnabled
            ?: acknowledged?.targetEnabled
            ?: authority.enabled,
        missedDoseRecoveryEditable = authority.editable,
        missedDoseRecoverySyncing = desiredEnabled != null
    )
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

/** UI facade over the central latest-intent mutation queue. */
internal class DeviceDosingMissedDoseRecoveryController(
    private val operations: DeviceDosingChannelOperations,
    private val scope: CoroutineScope,
    private val hooks: DeviceDosingMissedDoseRecoveryHooks
) {
    private val intent = DeviceDosingMissedDoseRecoveryIntentState()
    private var authority = DeviceDosingMissedDoseRecoveryAuthority()

    fun reset() {
        intent.reset()
        authority = DeviceDosingMissedDoseRecoveryAuthority()
    }

    fun request(enabled: Boolean) {
        val requestId = intent.request(enabled)
        val binding = hooks.currentBinding()
        DeviceDosingMissedDoseRecoveryDiagnosticTrace.save(
            binding = binding,
            targetEnabled = enabled,
            baseRevision = authority.revision
        )
        present()
        scope.launch {
            val result = persist(binding, enabled)
            if (hooks.currentBinding() != binding) {
                DeviceDosingMissedDoseRecoveryDiagnosticTrace.discarded(
                    slotId = binding.slotId,
                    targetEnabled = enabled,
                    reason = "binding_changed"
                )
                return@launch
            }
            handleResult(requestId, enabled, result)
        }
    }

    fun onSnapshot(snapshot: DeviceDosingChannelSnapshot) {
        val nextAuthority = DeviceDosingMissedDoseRecoveryAuthority(
            enabled = snapshot.program?.missedDoseRecoveryEnabled == true,
            editable = snapshot.program != null &&
                snapshot.scheduling.supportsMissedDoseRecovery &&
                snapshot.controls.programEditable,
            revision = snapshot.revision
        )
        if (nextAuthority.revision >= authority.revision) {
            authority = nextAuthority
            intent.onAuthorityChanged(authority)
        }
        present()
    }

    private suspend fun persist(
        binding: DeviceDosingChannelDetailBinding,
        enabled: Boolean
    ): DeviceDosingChannelOperationResult = if (
        binding.deviceUid.isBlank() || binding.slotId.isBlank()
    ) {
        DeviceDosingChannelOperationResult.Unavailable
    } else {
        try {
            operations.setMissedDoseRecoveryEnabled(
                binding.deviceUid,
                binding.slotId,
                enabled
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            DeviceDosingChannelOperationResult.Failed
        }
    }

    private fun handleResult(
        requestId: Long,
        targetEnabled: Boolean,
        result: DeviceDosingChannelOperationResult
    ) {
        if (!intent.isLatest(requestId)) {
            DeviceDosingMissedDoseRecoveryDiagnosticTrace.discarded(
                slotId = hooks.currentBinding().slotId,
                targetEnabled = targetEnabled,
                reason = "superseded"
            )
            return
        }
        DeviceDosingMissedDoseRecoveryDiagnosticTrace.result(
            binding = hooks.currentBinding(),
            targetEnabled = targetEnabled,
            result = result
        )
        val feedback = when (result) {
            is DeviceDosingChannelOperationResult.Success -> {
                hooks.applySnapshot(result.snapshot)
                intent.onSuccessfulReadback(requestId, targetEnabled, authority)
            }
            is DeviceDosingChannelCommittedResult -> intent.onCommitted(
                requestId = requestId,
                targetEnabled = targetEnabled,
                committedRevision = result.revision
            )
            is DeviceDosingChannelOperationResult.Rejected -> intent.onFailure(
                requestId,
                result.reason.toDetailFailure()
            )
            DeviceDosingChannelOperationResult.Unavailable -> intent.onFailure(
                requestId,
                DeviceDosingChannelDetailFailure.UNAVAILABLE
            )
            DeviceDosingChannelOperationResult.Failed -> intent.onFailure(
                requestId,
                DeviceDosingChannelDetailFailure.TRY_AGAIN
            )
        }
        present()
        publish(feedback)
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
