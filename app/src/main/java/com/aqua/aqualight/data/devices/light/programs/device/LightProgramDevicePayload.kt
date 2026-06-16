package com.aqua.aqualight.data.devices.light.programs.device

import com.aqua.aqualight.data.devices.api.light.LightProgramWriteRequest

/**
 * Compiled device upload package for one saved Light program.
 *
 * The checksum is calculated from the exact channel indexes and LP points that
 * will be sent to the controller. It lets local storage later prove whether the
 * active controller schedule still matches a saved program.
 */
data class LightProgramDevicePayload(
    val request: LightProgramWriteRequest,
    val checksum: String
) {
    val totalPointCount: Int
        get() = request.totalPointCount
}
