package com.aqua.aqualight.data.devices.light.programs.activation

import com.aqua.aqualight.data.devices.light.programs.model.SavedLightProgram

/**
 * Result of uploading a saved local program as the controller's active schedule.
 */
data class LightProgramActivationResult(
    val program: SavedLightProgram,
    val checksum: String,
    val uploadedToDevice: Boolean
)
