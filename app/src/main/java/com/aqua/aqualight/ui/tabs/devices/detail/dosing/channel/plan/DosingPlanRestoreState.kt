package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.plan

import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgram

/** Immutable process-death payload for the draft and its complete CAS origin. */
internal data class DosingPlanRestoreState(
    val draft: DosingPlanDraft? = null,
    val baseRevision: Long? = null,
    val baseProgram: DeviceDosingProgram? = null,
    val baseProgramKnown: Boolean = false,
    val draftDirty: Boolean = false
) {
    val hasDirtyDraft: Boolean
        get() = draft != null && draftDirty

    val hasCompleteDirtyOrigin: Boolean
        get() = hasDirtyDraft && baseRevision != null && baseProgramKnown
}
