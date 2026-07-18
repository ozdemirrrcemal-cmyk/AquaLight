package com.aqua.aqualight.ui.tabs.devices.route

import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceMenuAccessResult
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.text.AppTextResolver

/**
 * Maps an application-approved device-menu decision to a UI navigation destination.
 *
 * Liveness and repository access are resolved before this mapper is called. UI routing depends only
 * on firmware-provided family metadata carried by the application result.
 */
class DeviceRouteResolver(
    private val textResolver: AppTextResolver
) {

    fun resolve(
        access: DeviceMenuAccessResult.Available
    ): DeviceRoute {
        val deviceUid = access.deviceUid
        val title = access.title.ifBlank {
            deviceUid.ifBlank { textResolver.get(R.string.device_menu_default_title) }
        }

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
                message = textResolver.get(R.string.device_route_unsupported_family)
            )
        }
    }
}
