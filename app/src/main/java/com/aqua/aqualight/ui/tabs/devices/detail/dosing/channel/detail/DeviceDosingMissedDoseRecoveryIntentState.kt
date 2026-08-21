package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.detail

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelCommittedResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelRejection
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot

/**
 * Read-only projection of the latest central Dosing snapshot needed by the switch UI.
 *
 * This is not a device state owner. The central Dosing state owner remains authoritative; this
 * value is replaced whenever a new authoritative channel snapshot is observed.
 */
internal data class DeviceDosingMissedDoseRecoveryAuthority(
    val enabled: Boolean = false,
    val editable: Boolean = false,
    val revision: Long = 0L
)

internal sealed interface DeviceDosingMissedDoseRecoveryAction {
    data object Idle : DeviceDosingMissedDoseRecoveryAction
    data class Write(val targetEnabled: Boolean) : DeviceDosingMissedDoseRecoveryAction
    data class Fail(val failure: DeviceDosingChannelDetailFailure) : DeviceDosingMissedDoseRecoveryAction
}

internal data class DeviceDosingMissedDoseRecoveryResolution(
    val snapshot: DeviceDosingChannelSnapshot? = null,
    val failure: DeviceDosingChannelDetailFailure? = null
)

private data class InFlightMissedDoseRecovery(
    val targetEnabled: Boolean,
    val committedRevision: Long? = null
)

/**
 * Pure UI-intent state machine for the missed-dose recovery switch.
 *
 * It owns only transient user intent and in-flight command metadata. It never stores or invents an
 * authoritative device snapshot, and therefore cannot become a second Dosing source of truth.
 */
internal class DeviceDosingMissedDoseRecoveryIntentState {
    private var desiredEnabled: Boolean? = null
    private var inFlight: InFlightMissedDoseRecovery? = null

    fun reset() {
        desiredEnabled = null
        inFlight = null
    }

    fun request(enabled: Boolean) {
        desiredEnabled = enabled
    }

    fun nextAction(
        authority: DeviceDosingMissedDoseRecoveryAuthority
    ): DeviceDosingMissedDoseRecoveryAction = when {
        inFlight != null -> DeviceDosingMissedDoseRecoveryAction.Idle
        desiredEnabled == null -> DeviceDosingMissedDoseRecoveryAction.Idle
        desiredEnabled == authority.enabled -> {
            desiredEnabled = null
            DeviceDosingMissedDoseRecoveryAction.Idle
        }
        !authority.editable -> {
            desiredEnabled = null
            DeviceDosingMissedDoseRecoveryAction.Fail(DeviceDosingChannelDetailFailure.NOT_EDITABLE)
        }
        else -> DeviceDosingMissedDoseRecoveryAction.Write(
            targetEnabled = requireNotNull(desiredEnabled).also { targetEnabled ->
                inFlight = InFlightMissedDoseRecovery(targetEnabled)
            }
        )
    }

    fun onOperationResult(
        targetEnabled: Boolean,
        result: DeviceDosingChannelOperationResult,
        authority: DeviceDosingMissedDoseRecoveryAuthority
    ): DeviceDosingMissedDoseRecoveryResolution = when (result) {
        is DeviceDosingChannelOperationResult.Success -> DeviceDosingMissedDoseRecoveryResolution(
            snapshot = result.snapshot,
            failure = markCommitted(targetEnabled, result.snapshot.revision, authority)
        )
        is DeviceDosingChannelCommittedResult -> DeviceDosingMissedDoseRecoveryResolution(
            failure = markCommitted(targetEnabled, result.revision, authority)
        )
        is DeviceDosingChannelOperationResult.Rejected -> DeviceDosingMissedDoseRecoveryResolution(
            failure = onFailure(targetEnabled, result.reason.toDetailFailure(), authority)
        )
        DeviceDosingChannelOperationResult.Unavailable -> DeviceDosingMissedDoseRecoveryResolution(
            failure = onFailure(
                targetEnabled,
                DeviceDosingChannelDetailFailure.UNAVAILABLE,
                authority
            )
        )
        DeviceDosingChannelOperationResult.Failed -> DeviceDosingMissedDoseRecoveryResolution(
            failure = onFailure(targetEnabled, DeviceDosingChannelDetailFailure.TRY_AGAIN, authority)
        )
    }

    fun onAuthorityChanged(
        authority: DeviceDosingMissedDoseRecoveryAuthority
    ): DeviceDosingChannelDetailFailure? = reconcile(authority)

    fun present(
        draft: DeviceDosingChannelDetailDraft,
        authority: DeviceDosingMissedDoseRecoveryAuthority
    ): DeviceDosingChannelDetailDraft {
        val syncing = desiredEnabled != null || inFlight != null
        val operationInProgress = when {
            syncing -> true
            draft.missedDoseRecoverySyncing -> false
            else -> draft.operationInProgress
        }
        return draft.copy(
            missedDoseRecoveryEnabled = desiredEnabled ?: authority.enabled,
            missedDoseRecoveryEditable = authority.editable,
            missedDoseRecoverySyncing = syncing,
            operationInProgress = operationInProgress
        )
    }

    private fun markCommitted(
        targetEnabled: Boolean,
        committedRevision: Long,
        authority: DeviceDosingMissedDoseRecoveryAuthority
    ): DeviceDosingChannelDetailFailure? {
        val active = inFlight
        if (active == null || active.targetEnabled != targetEnabled) return null
        inFlight = active.copy(committedRevision = committedRevision)
        return reconcile(authority)
    }

    private fun onFailure(
        failedTargetEnabled: Boolean,
        failure: DeviceDosingChannelDetailFailure,
        authority: DeviceDosingMissedDoseRecoveryAuthority
    ): DeviceDosingChannelDetailFailure? {
        val active = inFlight
        if (active == null || active.targetEnabled != failedTargetEnabled) return null
        inFlight = null
        val latestIntent = desiredEnabled
        return when {
            latestIntent == authority.enabled -> {
                desiredEnabled = null
                null
            }
            latestIntent != null && latestIntent != failedTargetEnabled -> null
            else -> {
                desiredEnabled = null
                failure
            }
        }
    }

    private fun reconcile(
        authority: DeviceDosingMissedDoseRecoveryAuthority
    ): DeviceDosingChannelDetailFailure? {
        val active = inFlight
        val committedRevision = active?.committedRevision
        return when {
            active == null || committedRevision == null -> null
            authority.revision < committedRevision -> null
            authority.enabled != active.targetEnabled && desiredEnabled == active.targetEnabled -> {
                inFlight = null
                desiredEnabled = null
                DeviceDosingChannelDetailFailure.STATE_CHANGED
            }
            else -> {
                inFlight = null
                if (desiredEnabled == authority.enabled) desiredEnabled = null
                null
            }
        }
    }
}

internal fun DeviceDosingChannelRejection.toDetailFailure(): DeviceDosingChannelDetailFailure =
    when (this) {
        DeviceDosingChannelRejection.INVALID_DRAFT -> DeviceDosingChannelDetailFailure.INVALID_INPUT
        DeviceDosingChannelRejection.NOT_EDITABLE -> DeviceDosingChannelDetailFailure.NOT_EDITABLE
        DeviceDosingChannelRejection.NOT_CALIBRATED ->
            DeviceDosingChannelDetailFailure.CALIBRATION_REQUIRED
        DeviceDosingChannelRejection.BUSY -> DeviceDosingChannelDetailFailure.BUSY
        DeviceDosingChannelRejection.CONFLICT -> DeviceDosingChannelDetailFailure.STATE_CHANGED
        DeviceDosingChannelRejection.UNSAFE -> DeviceDosingChannelDetailFailure.SAFETY_BLOCKED
        DeviceDosingChannelRejection.UNKNOWN -> DeviceDosingChannelDetailFailure.TRY_AGAIN
    }
