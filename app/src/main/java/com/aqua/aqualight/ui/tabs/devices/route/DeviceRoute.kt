package com.aqua.aqualight.ui.tabs.devices.route

/**
 * Navigation decision produced from firmware supplied product family/capabilities.
 *
 * UI must route by deviceUid and resolved firmware metadata, not by local numeric ids or model-name
 * guesses. Real runtime root screens are connected gradually; this route object is the stable
 * boundary between the Devices list and the Navigation graph.
 */
data class DeviceRoute(
    val deviceUid: String,
    val title: String,
    val target: DeviceRouteTarget,
    val message: String = ""
)

enum class DeviceRouteTarget {
    LIGHT_ROOT,
    DOSING_ROOT,
    TIMER_ROOT,
    COOLING_ROOT,
    UNSUPPORTED
}
