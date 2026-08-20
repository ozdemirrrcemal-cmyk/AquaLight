package com.aqua.aqualight.application.devices

enum class DeviceRootCatalogState {
    /** Exact commercial identity/topology is known, but current-session runtime metadata is pending. */
    PENDING,
    /** Exact identity, capabilities, limits, screens, features and modules were validated. */
    VALID,
    /** A complete exact commercial identity was reported but is not supported by this catalog. */
    UNSUPPORTED,
    /** Reported commercial data is malformed or conflicts with the pinned catalog contract. */
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
    ): DeviceRootRoute? = when (family) {
        OwnerDeviceFamily.LIGHT -> resolveLight(feature)
        OwnerDeviceFamily.TIMER -> resolveTimer(feature)
        OwnerDeviceFamily.DOSING -> resolveDosing(feature)
        OwnerDeviceFamily.COOLING -> resolveCooling(feature)
        OwnerDeviceFamily.UNKNOWN -> null
    }

    private fun resolveLight(feature: DeviceRootMenuFeature): DeviceRootRoute? = when (feature) {
        DeviceRootMenuFeature.LIGHT_MANUAL -> DeviceRootRoute.LIGHT_MANUAL
        DeviceRootMenuFeature.LIGHT_QUICK_SETUP -> DeviceRootRoute.LIGHT_QUICK_SETUP
        DeviceRootMenuFeature.LIGHT_PROGRAMS -> DeviceRootRoute.LIGHT_PROGRAMS
        DeviceRootMenuFeature.LIGHT_PRESETS -> DeviceRootRoute.LIGHT_PRESETS
        DeviceRootMenuFeature.LIGHT_SIMULATION -> DeviceRootRoute.LIGHT_SIMULATION
        DeviceRootMenuFeature.COOLING_FANS -> DeviceRootRoute.LIGHT_FAN_CONTROL
        DeviceRootMenuFeature.COOLING_TEMPERATURE ->
            DeviceRootRoute.LIGHT_TEMPERATURE_PROTECTION
        DeviceRootMenuFeature.DEVICE_SETTINGS -> DeviceRootRoute.DEVICE_SETTINGS
        else -> null
    }

    private fun resolveTimer(feature: DeviceRootMenuFeature): DeviceRootRoute? = when (feature) {
        DeviceRootMenuFeature.TIMER_CHANNELS -> DeviceRootRoute.TIMER_CHANNELS
        DeviceRootMenuFeature.TIMER_SCHEDULES -> DeviceRootRoute.TIMER_SCHEDULES
        DeviceRootMenuFeature.DEVICE_SETTINGS -> DeviceRootRoute.DEVICE_SETTINGS
        else -> null
    }

    private fun resolveDosing(feature: DeviceRootMenuFeature): DeviceRootRoute? = when (feature) {
        DeviceRootMenuFeature.DOSING_CHANNELS -> DeviceRootRoute.DOSING_CHANNELS
        DeviceRootMenuFeature.DOSING_CALIBRATION -> DeviceRootRoute.DOSING_CALIBRATION
        DeviceRootMenuFeature.DOSING_SCHEDULES -> DeviceRootRoute.DOSING_SCHEDULES
        DeviceRootMenuFeature.DEVICE_SETTINGS -> DeviceRootRoute.DEVICE_SETTINGS
        else -> null
    }

    private fun resolveCooling(feature: DeviceRootMenuFeature): DeviceRootRoute? = when (feature) {
        DeviceRootMenuFeature.COOLING_FANS -> DeviceRootRoute.COOLING_CONTROL
        DeviceRootMenuFeature.COOLING_TEMPERATURE -> DeviceRootRoute.COOLING_TEMPERATURE
        DeviceRootMenuFeature.DEVICE_SETTINGS -> DeviceRootRoute.DEVICE_SETTINGS
        else -> null
    }
}
