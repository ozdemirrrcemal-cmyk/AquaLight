package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model

import androidx.annotation.ColorRes
import com.aqua.aqualight.R

enum class CurvePointKind(
    val sortOrder: Int
) {
    START(0),
    PEAK_START(1),
    PEAK_END(2),
    END(3),
    INTERMEDIATE(4)
}

data class CurvePointState(
    val id: String,
    val label: String,
    val time: String,
    val intensity: Int,
    val kind: CurvePointKind,
    val canDelete: Boolean = false
)

data class PreviewFrame(
    val time: String,
    val mainIntensity: Int,
    val red: Int,
    val green: Int,
    val blue: Int,
    val white: Int
)

enum class ProgramEditorMode {
    SIMPLE,
    PRO
}

data class ProgramSaveDraft(
    val name: String,
    val mode: ProgramEditorMode,
    val repeatMode: RepeatMode,
    val repeatDays: Set<Int>,
    val rampSmoothing: RampSmoothing,
    val simpleCurve: List<ProgramCurvePointDraft>,
    val proCurves: Map<ProChannel, List<ProgramCurvePointDraft>>,
    val channelBalance: ProgramChannelBalanceDraft,
    val acclimation: ProgramAcclimationDraft
)

data class ProgramCurvePointDraft(
    val id: String,
    val label: String,
    val time: String,
    val minute: Int,
    val intensity: Int,
    val kind: CurvePointKind,
    val canDelete: Boolean
)

data class ProgramChannelBalanceDraft(
    val red: Int,
    val green: Int,
    val blue: Int,
    val white: Int
)

data class ProgramAcclimationDraft(
    val enabled: Boolean,
    val durationDays: Int,
    val startIntensityPercent: Int
)

data class AcclimationState(
    val enabled: Boolean = false,
    val durationDays: Int = 7,
    val startIntensityPercent: Int = 40
)

data class ProgramValidationResult(
    val isValid: Boolean,
    val message: String?
) {
    companion object {
        fun valid(): ProgramValidationResult {
            return ProgramValidationResult(
                isValid = true,
                message = null
            )
        }

        fun invalid(
            message: String
        ): ProgramValidationResult {
            return ProgramValidationResult(
                isValid = false,
                message = message
            )
        }
    }
}

enum class ProChannel(
    val label: String,
    @ColorRes val colorRes: Int,
    val defaultPeak: Int
) {
    RED(
        label = "Red",
        colorRes = R.color.light_red,
        defaultPeak = 80
    ),
    GREEN(
        label = "Green",
        colorRes = R.color.light_green,
        defaultPeak = 72
    ),
    BLUE(
        label = "Blue",
        colorRes = R.color.light_blue,
        defaultPeak = 82
    ),
    WHITE(
        label = "White",
        colorRes = R.color.light_white,
        defaultPeak = 78
    )
}

enum class RepeatMode(
    val label: String
) {
    EVERY("Every day"),
    WEEKDAYS("Weekdays"),
    WEEKEND("Weekend"),
    CUSTOM("Custom")
}

enum class RampSmoothing(
    val label: String
) {
    LINEAR("Linear"),
    SOFT("Soft"),
    NATURAL("Natural")
}