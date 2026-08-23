package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.plan

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgram

private data class RecoveredDosingPlanDraft(
    val draft: DosingPlanDraft,
    val baseRevision: Long?,
    val baseProgram: DeviceDosingProgram?,
    val baseProgramKnown: Boolean,
    val draftDirty: Boolean
)

internal fun reduceDosingPlanSnapshot(
    current: DeviceDosingPlanEditorState,
    snapshot: DeviceDosingChannelSnapshot,
    restoredState: DosingPlanRestoreState
): DeviceDosingPlanEditorState {
    val recovered = recoverDosingPlanDraft(current, snapshot, restoredState)
    return current.copy(
        draft = recovered.draft,
        scheduling = snapshot.scheduling,
        supportedModes = snapshot.scheduling.supportedModes.mapTo(linkedSetOf()) { mode ->
            mode.toPlanMode()
        },
        missedDoseRecoveryEnabled = snapshot.program?.missedDoseRecoveryEnabled ?: false,
        editable = snapshot.calibrated && snapshot.controls.programEditable,
        initialized = true,
        baseRevision = recovered.baseRevision,
        baseProgram = recovered.baseProgram,
        baseProgramKnown = recovered.baseProgramKnown,
        draftDirty = recovered.draftDirty
    )
}

private fun recoverDosingPlanDraft(
    current: DeviceDosingPlanEditorState,
    snapshot: DeviceDosingChannelSnapshot,
    restoredState: DosingPlanRestoreState
): RecoveredDosingPlanDraft = when {
    !current.initialized -> recoverInitialDosingPlanDraft(snapshot, restoredState)
    current.draftDirty -> recoverDirtyDosingPlanDraft(current, snapshot)
    else -> snapshot.toRecoveredDosingPlanDraft()
}

private fun recoverInitialDosingPlanDraft(
    snapshot: DeviceDosingChannelSnapshot,
    restoredState: DosingPlanRestoreState
): RecoveredDosingPlanDraft {
    if (restoredState.requiresAuthoritativeReload(snapshot.revision)) {
        // Legacy/incomplete process state cannot safely identify the editor's CAS origin.
        // Reload authoritative data instead of leaving an editable draft that can never save.
        return snapshot.toRecoveredDosingPlanDraft()
    }
    return RecoveredDosingPlanDraft(
        draft = restoredState.draft ?: snapshot.authoritativePlanDraft(),
        baseRevision = restoredState.baseRevision ?: snapshot.revision,
        baseProgram = if (restoredState.hasCompleteDirtyOrigin) {
            restoredState.baseProgram
        } else {
            snapshot.program
        },
        baseProgramKnown = true,
        draftDirty = restoredState.hasDirtyDraft
    )
}

private fun DosingPlanRestoreState.requiresAuthoritativeReload(snapshotRevision: Long): Boolean {
    if (!hasDirtyDraft || hasCompleteDirtyOrigin) return false
    return (baseRevision ?: snapshotRevision) != snapshotRevision
}

private fun recoverDirtyDosingPlanDraft(
    current: DeviceDosingPlanEditorState,
    snapshot: DeviceDosingChannelSnapshot
): RecoveredDosingPlanDraft {
    val baselineStillCurrent = current.baseProgramKnown &&
        current.baseProgram.hasSamePlanMutationDomain(snapshot.program)
    return RecoveredDosingPlanDraft(
        draft = current.draft,
        baseRevision = if (baselineStillCurrent) snapshot.revision else current.baseRevision,
        baseProgram = if (baselineStillCurrent) snapshot.program else current.baseProgram,
        baseProgramKnown = current.baseProgramKnown,
        draftDirty = true
    )
}

private fun DeviceDosingChannelSnapshot.toRecoveredDosingPlanDraft() =
    RecoveredDosingPlanDraft(
        draft = authoritativePlanDraft(),
        baseRevision = revision,
        baseProgram = program,
        baseProgramKnown = true,
        draftDirty = false
    )

private fun DeviceDosingChannelSnapshot.authoritativePlanDraft() =
    program?.toPlanDraft() ?: defaultDraft()

private fun defaultDraft() = DosingPlanDraft(
    distributedDailyDoseMicroliters = DEFAULT_DAILY_DOSE_MICROLITERS,
    singleDoseStartTimeMs = DEFAULT_START_TIME_MILLIS
)

private const val DEFAULT_DAILY_DOSE_MICROLITERS = 3_000L
private const val DEFAULT_START_TIME_MILLIS = 8 * 60 * 60 * 1_000L
