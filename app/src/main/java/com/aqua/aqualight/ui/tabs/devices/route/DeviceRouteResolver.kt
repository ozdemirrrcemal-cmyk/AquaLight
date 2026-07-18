package com.aqua.aqualight.ui.tabs.devices.route

import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceMenuAccessResult
import com.aqua.aqualight.application.devices.OwnerDeviceFamily

/**
 * Maps an application-approved device-menu decision to a UI navigation destination.
 *
 * Liveness and repository access are resolved before this mapper is called. UI routing depends only
 * on firmware-provided family metadata carried by the application result.
 */
class DeviceRouteResolver {

    fun resolve(
        access: DeviceMenuAccessResult.Available
    ): DeviceRoute {
        val deviceUid = access.deviceUid
        val title = access.title.ifBlank { deviceUid }

        return when (access.family) {
            OwnerDeviceFamily.LIGHT -> DeviceRoute(
                deviceUid = deviceUid,
                title = title,
                target = DeviceRouteTarget.LIGHT_ROOT
            )
            OwnerDeviceFamily.DOSING -> DeviceRoute(
                deviceUid = deviceUid,
                title = title,
                target = DeviceRouteTarget.DOSING_ROOT
            )
            OwnerDeviceFamily.TIMER -> DeviceRoute(
                deviceUid = deviceUid,
                title = title,
                target = DeviceRouteTarget.TIMER_ROOT
            )
            OwnerDeviceFamily.COOLING -> DeviceRoute(
                deviceUid = deviceUid,
                title = title,
                target = DeviceRouteTarget.COOLING_ROOT
            )
            OwnerDeviceFamily.UNKNOWN -> DeviceRoute(
                deviceUid = deviceUid,
                title = title,
                target = DeviceRouteTarget.UNSUPPORTED,
                messageRes = R.string.device_unsupported_family_message
            )
        }
    }
}
