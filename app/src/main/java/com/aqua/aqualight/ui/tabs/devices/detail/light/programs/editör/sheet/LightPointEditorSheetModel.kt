package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.sheet

import androidx.annotation.StringRes

data class LightPointEditorSheetModel(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @StringRes val saveButtonTextRes: Int,
    val pointName: String = "",
    val timeLabel: String = "",
    val intensityPercent: Int? = null,
    val canRename: Boolean = true,
    val canDelete: Boolean = false
)