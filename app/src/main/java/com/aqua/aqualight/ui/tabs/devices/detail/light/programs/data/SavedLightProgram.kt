package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model

data class SavedLightProgram(
    val id: String,
    val deviceId: Long,
    val title: String,
    val isEnabled: Boolean = true,
    val isActive: Boolean = true,
    val mode: SavedLightProgramMode = SavedLightProgramMode.SIMPLE,
    val repeatDays: Set<Int>,
    val rampMinutes: Int,
    val peakIntensityPercent: Int,
    val balance: SavedLightProgramBalance,
    val curvePoints: List<SavedLightProgramCurvePoint>,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

enum class SavedLightProgramMode {
    SIMPLE,
    PRO
}

data class SavedLightProgramBalance(
    val red: Int,
    val green: Int,
    val blue: Int,
    val white: Int
)

data class SavedLightProgramCurvePoint(
    val kind: SavedLightProgramCurvePointKind,
    val minuteOfDay: Int,
    val masterPercent: Int,
    val red: Int,
    val green: Int,
    val blue: Int,
    val white: Int
)

enum class SavedLightProgramCurvePointKind {
    START,
    PEAK_START,
    PEAK_END,
    END,
    CUSTOM
}