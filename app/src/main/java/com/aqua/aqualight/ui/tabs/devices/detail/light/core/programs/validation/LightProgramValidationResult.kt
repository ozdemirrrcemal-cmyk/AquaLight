package com.aqua.aqualight.ui.tabs.devices.detail.light.core.programs.validation

sealed interface LightProgramValidationResult {

    data object Valid : LightProgramValidationResult

    data class Invalid(
        val message: String
    ) : LightProgramValidationResult
}