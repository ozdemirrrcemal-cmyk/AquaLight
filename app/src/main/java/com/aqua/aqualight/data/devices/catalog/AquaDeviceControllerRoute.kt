package com.aqua.aqualight.data.devices.catalog

enum class AquaDeviceControllerRoute {
    LIGHT,
    DOSING,
    TIMER,
    COOLING,
    UNSUPPORTED
}

fun AquaDeviceControllerType.toControllerRoute(): AquaDeviceControllerRoute {
    return when (this) {
        AquaDeviceControllerType.GENERIC_LIGHT,
        AquaDeviceControllerType.CUSTOM_LIGHT_ADVANCED,
        AquaDeviceControllerType.CUSTOM_LIGHT_MATRIX -> AquaDeviceControllerRoute.LIGHT

        AquaDeviceControllerType.GENERIC_DOSING,
        AquaDeviceControllerType.CUSTOM_DOSING_4CH -> AquaDeviceControllerRoute.DOSING

        AquaDeviceControllerType.GENERIC_TIMER,
        AquaDeviceControllerType.CUSTOM_TIMER_MULTI_CONTROL,
        AquaDeviceControllerType.CUSTOM_TIMER_SCENE_PRO -> AquaDeviceControllerRoute.TIMER

        AquaDeviceControllerType.GENERIC_COOLING,
        AquaDeviceControllerType.CUSTOM_COOLING_ADVANCED -> AquaDeviceControllerRoute.COOLING

        AquaDeviceControllerType.FULL_CONTROLLER,
        AquaDeviceControllerType.UNSUPPORTED -> AquaDeviceControllerRoute.UNSUPPORTED
    }
}

fun AquaDeviceCategory.toControllerRoute(): AquaDeviceControllerRoute {
    return when (this) {
        AquaDeviceCategory.LIGHT -> AquaDeviceControllerRoute.LIGHT
        AquaDeviceCategory.DOSING -> AquaDeviceControllerRoute.DOSING
        AquaDeviceCategory.TIMER -> AquaDeviceControllerRoute.TIMER
        AquaDeviceCategory.COOLING -> AquaDeviceControllerRoute.COOLING
        AquaDeviceCategory.CONTROLLER,
        AquaDeviceCategory.UNKNOWN -> AquaDeviceControllerRoute.UNSUPPORTED
    }
}
