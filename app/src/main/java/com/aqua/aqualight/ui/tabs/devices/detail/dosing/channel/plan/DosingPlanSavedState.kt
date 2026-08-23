package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.plan

import android.os.Bundle

internal fun Bundle?.restoreDosingPlanState() = DosingPlanRestoreState(
    draft = this?.let(DosingPlanDraft::restore),
    baseRevision = this
        ?.takeIf { state -> state.containsKey(STATE_BASE_REVISION) }
        ?.getLong(STATE_BASE_REVISION),
    baseProgram = restoreBaseProgram(),
    baseProgramKnown = this?.getBoolean(STATE_BASE_PROGRAM_KNOWN, false) == true,
    draftDirty = this?.getBoolean(STATE_DRAFT_DIRTY, false) == true
)

internal fun DeviceDosingPlanEditorState.writeDosingPlanStateTo(outState: Bundle) {
    draft.writeTo(outState)
    baseRevision?.let { revision -> outState.putLong(STATE_BASE_REVISION, revision) }
    outState.putBoolean(STATE_BASE_PROGRAM_KNOWN, baseProgramKnown)
    if (baseProgramKnown) {
        outState.putBoolean(STATE_BASE_PROGRAM_PRESENT, baseProgram != null)
        baseProgram?.let { program ->
            program.toPlanDraft().writeTo(outState, BASE_PROGRAM_KEY_PREFIX)
            outState.putBoolean(
                STATE_BASE_PROGRAM_MISSED_DOSE_RECOVERY,
                program.missedDoseRecoveryEnabled
            )
        }
    }
    outState.putBoolean(STATE_DRAFT_DIRTY, draftDirty)
}

private fun Bundle?.restoreBaseProgram() = this
    ?.takeIf { state ->
        state.getBoolean(STATE_BASE_PROGRAM_KNOWN, false) &&
            state.getBoolean(STATE_BASE_PROGRAM_PRESENT, false)
    }
    ?.let { state ->
        DosingPlanDraft.restore(state, BASE_PROGRAM_KEY_PREFIX).toApplicationProgram(
            state.getBoolean(STATE_BASE_PROGRAM_MISSED_DOSE_RECOVERY, false)
        )
    }

private const val STATE_BASE_REVISION = "dosing_plan_base_revision"
private const val STATE_BASE_PROGRAM_KNOWN = "dosing_plan_base_program_known"
private const val STATE_BASE_PROGRAM_PRESENT = "dosing_plan_base_program_present"
private const val STATE_BASE_PROGRAM_MISSED_DOSE_RECOVERY =
    "dosing_plan_base_program_missed_dose_recovery"
private const val BASE_PROGRAM_KEY_PREFIX = "dosing_plan_base_program_"
private const val STATE_DRAFT_DIRTY = "dosing_plan_draft_dirty"
