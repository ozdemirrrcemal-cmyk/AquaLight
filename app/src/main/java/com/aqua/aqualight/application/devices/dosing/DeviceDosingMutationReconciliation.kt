package com.aqua.aqualight.application.devices.dosing

/**
 * Result of a bounded optimistic-concurrency reconciliation.
 *
 * [authoritativeSnapshot] is always supplied by [DeviceDosingChannelOperations.refresh]; it is
 * never synthesized from a mutation response. This keeps the central Dosing state owner as the
 * only authority while allowing long-lived editors to reconcile their transient drafts.
 */
internal data class DeviceDosingMutationReconciliation(
    val result: DeviceDosingChannelOperationResult,
    val authoritativeSnapshot: DeviceDosingChannelSnapshot? = null
)

internal data class DeviceDosingRevisionedIntent<Domain>(
    val deviceUid: String,
    val slotId: String,
    val baseRevision: Long,
    val baseDomain: Domain,
    val desiredDomain: Domain
)

/**
 * Applies a long-lived editor intent and safely absorbs an unrelated channel revision change.
 *
 * A conflict is followed by one authoritative read. The intent is considered complete when the
 * requested domain is already present, retried once when that domain is unchanged from the
 * editor's baseline, and otherwise left as a real conflict. There is deliberately no retry loop.
 */
internal suspend fun <Domain> DeviceDosingChannelOperations.applyRevisionedIntentWithReconciliation(
    intent: DeviceDosingRevisionedIntent<Domain>,
    domainFrom: (DeviceDosingChannelSnapshot) -> Domain,
    applyAtRevision: suspend (Long) -> DeviceDosingChannelOperationResult
): DeviceDosingMutationReconciliation {
    val initial = applyAtRevision(intent.baseRevision)
    return if (!initial.isRevisionConflict()) {
        initial.toReconciliation()
    } else {
        val refreshed = refresh(intent.deviceUid, intent.slotId).successSnapshotOrNull()
        when {
            refreshed == null -> DeviceDosingMutationReconciliation(initial)
            domainFrom(refreshed) == intent.desiredDomain -> DeviceDosingMutationReconciliation(
                result = DeviceDosingChannelOperationResult.Success(refreshed),
                authoritativeSnapshot = refreshed
            )
            domainFrom(refreshed) == intent.baseDomain ->
                applyAtRevision(refreshed.revision).toReconciliation(refreshed)
            else -> DeviceDosingMutationReconciliation(
                result = DeviceDosingChannelOperationResult.Rejected(
                    DeviceDosingChannelRejection.CONFLICT
                ),
                authoritativeSnapshot = refreshed
            )
        }
    }
}

/**
 * Applies an isolated idempotent intent against the latest channel state.
 *
 * This variant is for controls such as the missed-dose switch whose adapter rebuilds the full
 * firmware payload from the latest central snapshot. A firmware revision conflict is reconciled
 * and the exact scalar intent is reapplied at most once.
 */
internal suspend fun <Domain> DeviceDosingChannelOperations.applyLatestIntentWithReconciliation(
    deviceUid: String,
    slotId: String,
    desiredDomain: Domain,
    domainFrom: (DeviceDosingChannelSnapshot) -> Domain,
    apply: suspend () -> DeviceDosingChannelOperationResult
): DeviceDosingMutationReconciliation {
    val initial = apply()
    return if (!initial.isRevisionConflict()) {
        initial.toReconciliation()
    } else {
        val refreshed = refresh(deviceUid, slotId).successSnapshotOrNull()
        when {
            refreshed == null -> DeviceDosingMutationReconciliation(initial)
            domainFrom(refreshed) == desiredDomain -> DeviceDosingMutationReconciliation(
                result = DeviceDosingChannelOperationResult.Success(refreshed),
                authoritativeSnapshot = refreshed
            )
            else -> apply().toReconciliation(refreshed)
        }
    }
}

private fun DeviceDosingChannelOperationResult.isRevisionConflict(): Boolean =
    this is DeviceDosingChannelOperationResult.Rejected &&
        reason == DeviceDosingChannelRejection.CONFLICT

private fun DeviceDosingChannelOperationResult.successSnapshotOrNull():
    DeviceDosingChannelSnapshot? =
    (this as? DeviceDosingChannelOperationResult.Success)?.snapshot

private fun DeviceDosingChannelOperationResult.toReconciliation(
    fallbackSnapshot: DeviceDosingChannelSnapshot? = null
): DeviceDosingMutationReconciliation = DeviceDosingMutationReconciliation(
    result = this,
    authoritativeSnapshot = successSnapshotOrNull() ?: fallbackSnapshot
)
