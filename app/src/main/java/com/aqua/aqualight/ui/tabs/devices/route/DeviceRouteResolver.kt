package com.aqua.aqualight.ui.tabs.devices.route

import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceMenuAccessResult
import com.aqua.aqualight.application.devices.OwnerDeviceFamily

/**
 * Maps an application-approved device-menu decision to a UI navigation destination.
 *
 * Liveness, catalog closure and first-frame preparation are resolved before this mapper is called.
 * UI routing depends only on application-approved family metadata and readiness.
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
                target = DeviceRouteTarget.LIGHT_ROOT,
                presentationPrepared = access.presentationPrepared
            )
            OwnerDeviceFamily.DOSING -> DeviceRoute(
                deviceUid = deviceUid,
                title = title,
                target = DeviceRouteTarget.DOSING_ROOT,
                presentationPrepared = access.presentationPrepared
            )
            OwnerDeviceFamily.TIMER -> DeviceRoute(
                deviceUid = deviceUid,
                title = title,
                target = DeviceRouteTarget.TIMER_ROOT,
                presentationPrepared = access.presentationPrepared
            )
            OwnerDeviceFamily.COOLING -> DeviceRoute(
                deviceUid = deviceUid,
                title = title,
                target = DeviceRouteTarget.COOLING_ROOT,
                presentationPrepared = access.presentationPrepared
            )
            OwnerDeviceFamily.UNKNOWN -> DeviceRoute(
                deviceUid = deviceUid,
                title = title,
                target = DeviceRouteTarget.UNSUPPORTED,
                presentationPrepared = access.presentationPrepared,
                messageRes = R.string.device_unsupported_family_message
            )
        }
    }
}
