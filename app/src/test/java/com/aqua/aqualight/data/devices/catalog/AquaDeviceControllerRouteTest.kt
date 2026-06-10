package com.aqua.aqualight.data.devices.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

class AquaDeviceControllerRouteTest {

    @Test
    fun allControllerTypesHaveExplicitRoutes() {
        val routesByType = AquaDeviceControllerType.entries.associateWith { type ->
            type.toControllerRoute()
        }

        assertEquals(AquaDeviceControllerRoute.LIGHT, routesByType[AquaDeviceControllerType.GENERIC_LIGHT])
        assertEquals(AquaDeviceControllerRoute.LIGHT, routesByType[AquaDeviceControllerType.CUSTOM_LIGHT_ADVANCED])
        assertEquals(AquaDeviceControllerRoute.LIGHT, routesByType[AquaDeviceControllerType.CUSTOM_LIGHT_MATRIX])

        assertEquals(AquaDeviceControllerRoute.DOSING, routesByType[AquaDeviceControllerType.GENERIC_DOSING])
        assertEquals(AquaDeviceControllerRoute.DOSING, routesByType[AquaDeviceControllerType.CUSTOM_DOSING_4CH])

        assertEquals(AquaDeviceControllerRoute.TIMER, routesByType[AquaDeviceControllerType.GENERIC_TIMER])
        assertEquals(AquaDeviceControllerRoute.TIMER, routesByType[AquaDeviceControllerType.CUSTOM_TIMER_MULTI_CONTROL])
        assertEquals(AquaDeviceControllerRoute.TIMER, routesByType[AquaDeviceControllerType.CUSTOM_TIMER_SCENE_PRO])

        assertEquals(AquaDeviceControllerRoute.COOLING, routesByType[AquaDeviceControllerType.GENERIC_COOLING])
        assertEquals(AquaDeviceControllerRoute.COOLING, routesByType[AquaDeviceControllerType.CUSTOM_COOLING_ADVANCED])

        assertEquals(AquaDeviceControllerRoute.UNSUPPORTED, routesByType[AquaDeviceControllerType.FULL_CONTROLLER])
        assertEquals(AquaDeviceControllerRoute.UNSUPPORTED, routesByType[AquaDeviceControllerType.UNSUPPORTED])
    }
}
