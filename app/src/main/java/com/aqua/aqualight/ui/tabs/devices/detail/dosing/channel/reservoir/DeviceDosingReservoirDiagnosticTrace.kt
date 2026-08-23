package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.reservoir

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelCommittedResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.base.diagnostics.AppDiagnosticTrace

internal object DeviceDosingReservoirDiagnosticTrace {
    fun trackingSwitch(
        slotId: String,
        targetEnabled: Boolean,
        state: DeviceDosingReservoirEditorState
    ) {
        val rejection = draftChangeRejection(
            state = state,
            unchanged = state.draft.trackingEnabled == targetEnabled
        )
        AppDiagnosticTrace.event(
            category = "ui",
            name = "dosing.reservoir.tracking.switch",
            "slot" to slotId.takeIf(String::isNotBlank),
            "target" to targetEnabled,
            "accepted" to (rejection == null),
            "reason" to rejection,
            "busy" to state.operationInProgress,
            "baseRevision" to state.baseRevision
        )
    }

    fun alertSwitch(
        slotId: String,
        targetEnabled: Boolean,
        state: DeviceDosingReservoirEditorState
    ) {
        val rejection = when {
            targetEnabled && !state.draft.trackingEnabled -> "tracking_disabled"
            else -> draftChangeRejection(
                state = state,
                unchanged = state.draft.lowLevelAlertEnabled == targetEnabled
            )
        }
        AppDiagnosticTrace.event(
            category = "ui",
            name = "dosing.reservoir.alert.switch",
            "slot" to slotId.takeIf(String::isNotBlank),
            "target" to targetEnabled,
            "accepted" to (rejection == null),
            "reason" to rejection,
            "busy" to state.operationInProgress,
            "baseRevision" to state.baseRevision
        )
    }

    fun saveAttempt(slotId: String, state: DeviceDosingReservoirEditorState) {
        AppDiagnosticTrace.event(
            category = "ui",
            name = "dosing.reservoir.save",
            "slot" to slotId.takeIf(String::isNotBlank),
            "accepted" to state.canSave,
            "canSave" to state.canSave,
            "busy" to state.operationInProgress,
            "operation" to state.operationName,
            "baseRevision" to state.baseRevision
        )
    }

    fun saveResult(
        slotId: String,
        requestedState: DeviceDosingReservoirEditorState,
        operationResult: DeviceDosingChannelOperationResult,
        event: DeviceDosingReservoirEvent,
        revision: Long?
    ) {
        val reason = (event as? DeviceDosingReservoirEvent.SaveRejected)?.reason?.name
        AppDiagnosticTrace.event(
            category = "ui",
            name = "dosing.reservoir.result",
            "slot" to slotId,
            "operation" to requestedState.operationName,
            "result" to event.diagnosticResult,
            "outcome" to operationResult.diagnosticOutcome,
            "reason" to reason,
            "baseRevision" to requestedState.baseRevision,
            "revision" to revision
        )
    }

    private fun draftChangeRejection(
        state: DeviceDosingReservoirEditorState,
        unchanged: Boolean
    ): String? = when {
        !state.editable -> "not_editable"
        state.operationInProgress -> "busy"
        unchanged -> "unchanged"
        else -> null
    }

    private val DeviceDosingReservoirEditorState.operationName: String
        get() = if (firmwareConfigDirty) "firmware_config" else "alert_preference"

    private val DeviceDosingReservoirEvent.diagnosticResult: String
        get() = when (this) {
            DeviceDosingReservoirEvent.Saved -> "saved"
            is DeviceDosingReservoirEvent.SaveRejected -> "rejected"
            DeviceDosingReservoirEvent.SaveFailed -> "failed"
            DeviceDosingReservoirEvent.Refilled -> "refilled"
            DeviceDosingReservoirEvent.RefillFailed -> "refill_failed"
        }

    private val DeviceDosingChannelOperationResult.diagnosticOutcome: String
        get() = when (this) {
            is DeviceDosingChannelOperationResult.Success -> "success"
            is DeviceDosingChannelCommittedResult -> "committed"
            is DeviceDosingChannelOperationResult.Rejected -> "rejected"
            DeviceDosingChannelOperationResult.Unavailable -> "unavailable"
            DeviceDosingChannelOperationResult.Failed -> "failed"
        }
}
