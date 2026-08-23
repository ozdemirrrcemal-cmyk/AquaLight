package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.plan

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelCommittedResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.base.diagnostics.AppDiagnosticTrace

internal object DeviceDosingPlanDiagnosticTrace {
    fun scheduleSwitch(
        slotId: String,
        targetEnabled: Boolean,
        state: DeviceDosingPlanEditorState
    ) {
        val rejection = when {
            !state.editable -> "not_editable"
            state.operationInProgress -> "busy"
            state.draft.scheduleEnabled == targetEnabled -> "unchanged"
            else -> null
        }
        AppDiagnosticTrace.event(
            category = "ui",
            name = "dosing.plan.program.switch",
            "slot" to slotId.takeIf(String::isNotBlank),
            "target" to targetEnabled,
            "accepted" to (rejection == null),
            "reason" to rejection,
            "busy" to state.operationInProgress,
            "baseRevision" to state.baseRevision
        )
    }

    fun saveAttempt(
        slotId: String,
        targetEnabled: Boolean,
        state: DeviceDosingPlanEditorState,
        accepted: Boolean
    ) {
        AppDiagnosticTrace.event(
            category = "ui",
            name = "dosing.plan.save",
            "slot" to slotId.takeIf(String::isNotBlank),
            "target" to targetEnabled,
            "accepted" to accepted,
            "canSave" to state.canSave,
            "busy" to state.operationInProgress,
            "baseRevision" to state.baseRevision
        )
    }

    fun validationRejected(
        slotId: String,
        targetEnabled: Boolean,
        state: DeviceDosingPlanEditorState,
        issue: DosingPlanValidationIssue
    ) {
        AppDiagnosticTrace.event(
            category = "ui",
            name = "dosing.plan.result",
            "slot" to slotId,
            "target" to targetEnabled,
            "result" to "validation_rejected",
            "reason" to issue.name,
            "baseRevision" to state.baseRevision
        )
    }

    fun saveResult(
        slotId: String,
        targetEnabled: Boolean,
        state: DeviceDosingPlanEditorState,
        result: DeviceDosingChannelOperationResult
    ) {
        val disposition = when (result) {
            is DeviceDosingChannelOperationResult.Success -> "success"
            is DeviceDosingChannelCommittedResult -> "committed"
            is DeviceDosingChannelOperationResult.Rejected -> "rejected"
            DeviceDosingChannelOperationResult.Unavailable -> "unavailable"
            DeviceDosingChannelOperationResult.Failed -> "failed"
        }
        val revision = when (result) {
            is DeviceDosingChannelOperationResult.Success -> result.snapshot.revision
            is DeviceDosingChannelCommittedResult -> result.revision
            else -> null
        }
        val reason = (result as? DeviceDosingChannelOperationResult.Rejected)?.reason?.name
        AppDiagnosticTrace.event(
            category = "ui",
            name = "dosing.plan.result",
            "slot" to slotId,
            "target" to targetEnabled,
            "result" to disposition,
            "baseRevision" to state.baseRevision,
            "revision" to revision,
            "reason" to reason
        )
    }
}
