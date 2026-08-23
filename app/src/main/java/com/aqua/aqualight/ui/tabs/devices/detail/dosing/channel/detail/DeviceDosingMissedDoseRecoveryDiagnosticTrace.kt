package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.detail

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelCommittedResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.base.diagnostics.AppDiagnosticTrace

internal object DeviceDosingMissedDoseRecoveryDiagnosticTrace {
    fun switch(
        slotId: String,
        targetEnabled: Boolean,
        state: DeviceDosingChannelDetailDraft
    ) {
        val rejection = when {
            !state.missedDoseRecoveryEditable -> "not_editable"
            state.operationInProgress -> "busy"
            state.missedDoseRecoveryEnabled == targetEnabled -> "unchanged"
            else -> null
        }
        AppDiagnosticTrace.event(
            category = "ui",
            name = "dosing.detail.missed_dose.switch",
            "slot" to slotId.takeIf(String::isNotBlank),
            "target" to targetEnabled,
            "accepted" to (rejection == null),
            "reason" to rejection,
            "busy" to state.operationInProgress
        )
    }

    fun save(
        binding: DeviceDosingChannelDetailBinding,
        targetEnabled: Boolean,
        baseRevision: Long
    ) {
        AppDiagnosticTrace.event(
            category = "ui",
            name = "dosing.detail.missed_dose.save",
            "slot" to binding.slotId.takeIf(String::isNotBlank),
            "target" to targetEnabled,
            "baseRevision" to baseRevision
        )
    }

    fun discarded(slotId: String, targetEnabled: Boolean, reason: String) {
        AppDiagnosticTrace.event(
            category = "ui",
            name = "dosing.detail.missed_dose.result",
            "slot" to slotId.takeIf(String::isNotBlank),
            "target" to targetEnabled,
            "result" to "discarded",
            "reason" to reason
        )
    }

    fun result(
        binding: DeviceDosingChannelDetailBinding,
        targetEnabled: Boolean,
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
            name = "dosing.detail.missed_dose.result",
            "slot" to binding.slotId.takeIf(String::isNotBlank),
            "target" to targetEnabled,
            "result" to disposition,
            "revision" to revision,
            "reason" to reason
        )
    }
}
