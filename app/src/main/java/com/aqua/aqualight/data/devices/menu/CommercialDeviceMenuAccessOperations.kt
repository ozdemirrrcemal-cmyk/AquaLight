package com.aqua.aqualight.data.devices.menu

import com.aqua.aqualight.application.devices.DeviceAccessDecision
import com.aqua.aqualight.application.devices.DeviceAccessPolicy
import com.aqua.aqualight.application.devices.DeviceCompatibilityOperations
import com.aqua.aqualight.application.devices.DeviceMenuAccessOperations
import com.aqua.aqualight.application.devices.DeviceMenuAccessResult

internal class CommercialDeviceMenuAccessOperations(
    private val livenessOperations: DeviceMenuAccessOperations,
    private val compatibilityOperations: DeviceCompatibilityOperations,
    private val accessPolicy: DeviceAccessPolicy
) : DeviceMenuAccessOperations {

    override suspend fun resolve(deviceUid: String): DeviceMenuAccessResult =
        when (val liveness = livenessOperations.resolve(deviceUid)) {
            is DeviceMenuAccessResult.Unavailable -> liveness
            is DeviceMenuAccessResult.Available -> validateCompatibility(liveness)
        }

    private fun validateCompatibility(
        liveness: DeviceMenuAccessResult.Available
    ): DeviceMenuAccessResult =
        when (
            val decision = accessPolicy.evaluateRoot(
                compatibilityOperations.current(liveness.deviceUid)
            )
        ) {
            DeviceAccessDecision.Allowed -> liveness.copy(
                family = compatibilityOperations.current(liveness.deviceUid).family
            )
            is DeviceAccessDecision.Blocked -> DeviceMenuAccessResult.Unavailable(
                title = liveness.title,
                reason = decision.reason
            )
        }
}
