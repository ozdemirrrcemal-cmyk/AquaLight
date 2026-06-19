package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model

enum class PreviewSpeed(
    val label: String,
    val durationMinutes: Int
) {
    ONE_MINUTE("1 min", 1),
    THREE_MINUTES("3 min", 3),
    FIVE_MINUTES("5 min", 5)
}