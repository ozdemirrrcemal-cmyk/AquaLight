package com.aqua.aqualight.data.devices.light.programs.validation

sealed interface LightProgramValidationResult {

    data object Valid : LightProgramValidationResult

    data class Invalid(
        val message: String
    ) : LightProgramValidationResult
}