package com.aqua.aqualight.application.devices

enum class DeviceRootCatalogState {
    VALID,
    INVALID
}

/** Exact family-specific application destinations. */
enum class DeviceRootRoute {
    LIGHT_MANUAL,
    LIGHT_QUICK_SETUP,
    LIGHT_PROGRAMS,
    LIGHT_PRESETS,
    LIGHT_SIMULATION,
    LIGHT_FAN_CONTROL,
    LIGHT_TEMPERATURE_PROTECTION,
    DOSING_CHANNELS,
    DOSING_CALIBRATION,
    DOSING_SCHEDULES,
    TIMER_CHANNELS,
    TIMER_SCHEDULES,
    COOLING_CONTROL,
    COOLING_TEMPERATURE,
    DEVICE_SETTINGS
}

/** Maps a validated family menu feature to one exact family destination. */
object DeviceRootRouteResolver {
    fun resolve(
        family: OwnerDeviceFamily,
        feature: DeviceRootMenuFeature
    ): DeviceRootRoute? = when (feature) {
        DeviceRootMenuFeature.LIGHT_MANUAL -> family.onlyLight(DeviceRootRoute.LIGHT_MANUAL)
        DeviceRootMenuFeature.LIGHT_QUICK_SETUP ->
            family.onlyLight(DeviceRootRoute.LIGHT_QUICK_SETUP)
        DeviceRootMenuFeature.LIGHT_PROGRAMS -> family.onlyLight(DeviceRootRoute.LIGHT_PROGRAMS)
        DeviceRootMenuFeature.LIGHT_PRESETS -> family.onlyLight(DeviceRootRoute.LIGHT_PRESETS)
        DeviceRootMenuFeature.LIGHT_SIMULATION ->
            family.onlyLight(DeviceRootRoute.LIGHT_SIMULATION)
        DeviceRootMenuFeature.DOSING_CHANNELS ->
            family.onlyDosing(DeviceRootRoute.DOSING_CHANNELS)
        DeviceRootMenuFeature.DOSING_CALIBRATION ->
            family.onlyDosing(DeviceRootRoute.DOSING_CALIBRATION)
        DeviceRootMenuFeature.DOSING_SCHEDULES ->
            family.onlyDosing(DeviceRootRoute.DOSING_SCHEDULES)
        DeviceRootMenuFeature.TIMER_CHANNELS ->
            family.onlyTimer(DeviceRootRoute.TIMER_CHANNELS)
        DeviceRootMenuFeature.TIMER_SCHEDULES ->
            family.onlyTimer(DeviceRootRoute.TIMER_SCHEDULES)
        DeviceRootMenuFeature.COOLING_FANS -> when (family) {
            OwnerDeviceFamily.LIGHT -> DeviceRootRoute.LIGHT_FAN_CONTROL
            OwnerDeviceFamily.COOLING -> DeviceRootRoute.COOLING_CONTROL
            else -> null
        }
        DeviceRootMenuFeature.COOLING_TEMPERATURE -> when (family) {
            OwnerDeviceFamily.LIGHT -> DeviceRootRoute.LIGHT_TEMPERATURE_PROTECTION
            OwnerDeviceFamily.COOLING -> DeviceRootRoute.COOLING_TEMPERATURE
            else -> null
        }
        DeviceRootMenuFeature.DEVICE_SETTINGS -> when (family) {
            OwnerDeviceFamily.LIGHT,
            OwnerDeviceFamily.TIMER,
            OwnerDeviceFamily.DOSING,
            OwnerDeviceFamily.COOLING -> DeviceRootRoute.DEVICE_SETTINGS
            OwnerDeviceFamily.UNKNOWN -> null
        }
    }

    private fun OwnerDeviceFamily.onlyLight(route: DeviceRootRoute): DeviceRootRoute? =
        route.takeIf { this == OwnerDeviceFamily.LIGHT }

    private fun OwnerDeviceFamily.onlyTimer(route: DeviceRootRoute): DeviceRootRoute? =
        route.takeIf { this == OwnerDeviceFamily.TIMER }

    private fun OwnerDeviceFamily.onlyDosing(route: DeviceRootRoute): DeviceRootRoute? =
        route.takeIf { this == OwnerDeviceFamily.DOSING }
}
