package com.aqua.aqualight.ui.tabs.devices.route

import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceSnapshot

/**
 * Resolves device detail destinations from firmware-provided metadata.
 *
 * Commercial rule: Android does not infer device type from display name, SSID, IP, SKU text or a
 * hard-coded local catalog. The route is derived from product.family first, then each destination
 * can decide visible controls from capabilities, limits and supportedScreens.
 */
class DeviceRouteResolver {

    fun resolve(
        snapshot: DeviceSnapshot?,
        requestedDeviceUid: String
    ): DeviceRoute {
        val deviceUid = snapshot?.deviceUid?.value ?: requestedDeviceUid
        val title = snapshot?.title
            ?.ifBlank { deviceUid }
            ?: deviceUid.ifBlank { DEFAULT_DEVICE_TITLE }

        if (snapshot == null) {
            return DeviceRoute(
                deviceUid = deviceUid,
                title = title,
                target = DeviceRouteTarget.UNSUPPORTED,
                message = "Device information is not available yet. Refresh discovery and try again."
            )
        }

        return when (snapshot.product.family) {
            DeviceFamily.LIGHT -> DeviceRoute(
                deviceUid = deviceUid,
                title = title,
                target = DeviceRouteTarget.LIGHT_ROOT
            )
            DeviceFamily.DOSING -> DeviceRoute(
                deviceUid = deviceUid,
                title = title,
                target = DeviceRouteTarget.DOSING_ROOT
            )
            DeviceFamily.TIMER -> DeviceRoute(
                deviceUid = deviceUid,
                title = title,
                target = DeviceRouteTarget.TIMER_ROOT
            )
            DeviceFamily.COOLING -> DeviceRoute(
                deviceUid = deviceUid,
                title = title,
                target = DeviceRouteTarget.COOLING_ROOT
            )
            DeviceFamily.UNKNOWN -> DeviceRoute(
                deviceUid = deviceUid,
                title = title,
                target = DeviceRouteTarget.UNSUPPORTED,
                message = "Unsupported AquaLight device family. Firmware did not provide a known product.family value."
            )
        }
    }

    companion object {
        private const val DEFAULT_DEVICE_TITLE = "Device"
    }
}
