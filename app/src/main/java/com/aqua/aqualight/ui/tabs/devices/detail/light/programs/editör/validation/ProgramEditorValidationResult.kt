package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.validation

import androidx.annotation.StringRes

sealed class ProgramEditorValidationResult {

    data object Valid : ProgramEditorValidationResult()

    data class Invalid(
        @StringRes val messageRes: Int
    ) : ProgramEditorValidationResult()
}