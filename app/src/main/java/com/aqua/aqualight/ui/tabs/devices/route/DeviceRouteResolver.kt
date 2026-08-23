package com.aqua.aqualight.ui.tabs.devices.route

import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceMenuAccessResult
import com.aqua.aqualight.application.devices.OwnerDeviceFamily

/**
 * Maps an application-approved device-menu decision to a UI navigation destination.
 *
 * Liveness and repository access are resolved before this mapper is called. Supported roots carry
 * only stable identity plus the resolved family target; their title remains repository-owned.
 */
class DeviceRouteResolver {

    fun resolve(
        access: DeviceMenuAccessResult.Available
    ): DeviceRoute {
        val deviceUid = access.deviceUid

        return when (access.family) {
            OwnerDeviceFamily.LIGHT -> DeviceRoute(
                deviceUid = deviceUid,
                target = DeviceRouteTarget.LIGHT_ROOT
            )
            OwnerDeviceFamily.DOSING -> DeviceRoute(
                deviceUid = deviceUid,
                target = DeviceRouteTarget.DOSING_ROOT
            )
            OwnerDeviceFamily.TIMER -> DeviceRoute(
                deviceUid = deviceUid,
                target = DeviceRouteTarget.TIMER_ROOT
            )
            OwnerDeviceFamily.COOLING -> DeviceRoute(
                deviceUid = deviceUid,
                target = DeviceRouteTarget.COOLING_ROOT
            )
            OwnerDeviceFamily.UNKNOWN -> DeviceRoute(
                deviceUid = deviceUid,
                target = DeviceRouteTarget.UNSUPPORTED,
                unsupportedTitle = access.title.ifBlank { deviceUid },
                messageRes = R.string.device_unsupported_family_message
            )
        }
    }
}
