package com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model

import java.io.Serializable

data class GeneratedQuickSetupProgramDraft(
    val deviceId: Long,
    val programName: String,
    val repeatDays: Set<Int>,
    val rampMinutes: Int,
    val peakIntensityPercent: Int,
    val balancePreset: QuickSetupChannelBalancePreset,
    val balance: WrgbChannelBalance,
    val curvePoints: List<GeneratedQuickSetupCurvePoint>
) : Serializable {

    companion object {
        const val ARG_QUICK_SETUP_GENERATED_DRAFT = "quickSetupGeneratedDraft"
    }
}

data class GeneratedQuickSetupCurvePoint(
    val kind: GeneratedQuickSetupCurvePointKind,
    val timeMinutes: Int,
    val masterPercent: Int,
    val channelOutput: WrgbChannelOutput
) : Serializable

enum class GeneratedQuickSetupCurvePointKind : Serializable {
    START,
    PEAK_START,
    PEAK_END,
    END
}