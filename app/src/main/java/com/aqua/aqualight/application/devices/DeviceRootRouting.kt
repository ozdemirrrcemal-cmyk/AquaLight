package com.aqua.aqualight.application.devices

enum class DeviceRootCatalogState {
    VALID,
    INVALID
}

enum class DeviceRootRoute {
    LIGHT_MANUAL,
    LIGHT_QUICK_SETUP,
    LIGHT_PROGRAMS,
    LIGHT_PRESETS,
    LIGHT_SIMULATION,
    DOSING_CHANNELS,
    DOSING_CALIBRATION,
    DOSING_SCHEDULES,
    TIMER_CHANNELS,
    TIMER_SCHEDULES,
    COOLING_FANS,
    COOLING_TEMPERATURE,
    DEVICE_SETTINGS
}
