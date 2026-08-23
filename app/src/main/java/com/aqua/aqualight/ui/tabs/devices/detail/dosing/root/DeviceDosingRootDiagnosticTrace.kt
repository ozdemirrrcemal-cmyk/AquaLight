package com.aqua.aqualight.ui.tabs.devices.detail.dosing.root

import com.aqua.aqualight.base.diagnostics.AppDiagnosticTrace

internal object DeviceDosingRootDiagnosticTrace {
    fun open(slotId: String, accepted: Boolean) {
        AppDiagnosticTrace.event(
            category = "ui",
            name = "dosing.root.channel.open",
            "slot" to slotId.takeIf(String::isNotBlank),
            "accepted" to accepted,
            "reason" to if (accepted) null else "invalid_binding"
        )
    }

    fun result(
        slotId: String,
        result: String,
        reason: String? = null,
        route: String? = null
    ) {
        AppDiagnosticTrace.event(
            category = "ui",
            name = "dosing.root.channel.result",
            "slot" to slotId,
            "result" to result,
            "reason" to reason,
            "route" to route
        )
    }
}
