package com.aqua.aqualight.data.devices.menu

import com.aqua.aqualight.application.devices.DeviceAccessDecision
import com.aqua.aqualight.application.devices.DeviceAccessPolicy
import com.aqua.aqualight.application.devices.DeviceCompatibilityOperations
import com.aqua.aqualight.application.devices.DeviceMenuAccessOperations
import com.aqua.aqualight.application.devices.DeviceMenuAccessResult

/**
 * Commercial compatibility gate layered after current liveness.
 *
 * Product/domain compatibility never mutates device presence. The device may remain Online while
 * this gate blocks a control surface with a typed compatibility reason.
 */
internal class CommercialDeviceMenuAccessOperations(
    private val livenessOperations: DeviceMenuAccessOperations,
    private val compatibilityOperations: DeviceCompatibilityOperations,
    private val accessPolicy: DeviceAccessPolicy
) : DeviceMenuAccessOperations {

    override suspend fun resolve(deviceUid: String): DeviceMenuAccessResult {
        return when (val liveness = livenessOperations.resolve(deviceUid)) {
            is DeviceMenuAccessResult.Unavailable -> liveness
            is DeviceMenuAccessResult.Available -> validateCompatibility(liveness)
        }
    }

    private fun validateCompatibility(
        liveness: DeviceMenuAccessResult.Available
    ): DeviceMenuAccessResult {
        val compatibility = compatibilityOperations.current(liveness.deviceUid)
        return when (val decision = accessPolicy.evaluateRoot(compatibility)) {
            DeviceAccessDecision.Allowed -> liveness.copy(family = compatibility.family)
            is DeviceAccessDecision.Blocked -> DeviceMenuAccessResult.Unavailable(
                title = liveness.title,
                reason = decision.reason
            )
        }
    }
}
