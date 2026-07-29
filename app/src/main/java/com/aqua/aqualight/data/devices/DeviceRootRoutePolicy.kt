package com.aqua.aqualight.data.devices

import com.aqua.aqualight.application.devices.DeviceRootMenuFeature
import com.aqua.aqualight.application.devices.DeviceRootRoute
import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogProduct

internal object DeviceRootRoutePolicy {

    fun allowedRoutes(product: AqlCommercialCatalogProduct): Set<DeviceRootRoute> {
        return DeviceRootMenuFeatureResolver.resolve(product)
            .mapTo(linkedSetOf()) { feature -> feature.toRoute() }
    }

    fun authorize(
        product: AqlCommercialCatalogProduct,
        route: DeviceRootRoute
    ): Boolean {
        return route in allowedRoutes(product)
    }

    private fun DeviceRootMenuFeature.toRoute(): DeviceRootRoute = when (this) {
        DeviceRootMenuFeature.LIGHT_MANUAL -> DeviceRootRoute.LIGHT_MANUAL
        DeviceRootMenuFeature.LIGHT_QUICK_SETUP -> DeviceRootRoute.LIGHT_QUICK_SETUP
        DeviceRootMenuFeature.LIGHT_PROGRAMS -> DeviceRootRoute.LIGHT_PROGRAMS
        DeviceRootMenuFeature.LIGHT_PRESETS -> DeviceRootRoute.LIGHT_PRESETS
        DeviceRootMenuFeature.LIGHT_SIMULATION -> DeviceRootRoute.LIGHT_SIMULATION
        DeviceRootMenuFeature.DOSING_CHANNELS -> DeviceRootRoute.DOSING_CHANNELS
        DeviceRootMenuFeature.DOSING_CALIBRATION -> DeviceRootRoute.DOSING_CALIBRATION
        DeviceRootMenuFeature.DOSING_SCHEDULES -> DeviceRootRoute.DOSING_SCHEDULES
        DeviceRootMenuFeature.TIMER_CHANNELS -> DeviceRootRoute.TIMER_CHANNELS
        DeviceRootMenuFeature.TIMER_SCHEDULES -> DeviceRootRoute.TIMER_SCHEDULES
        DeviceRootMenuFeature.COOLING_FANS -> DeviceRootRoute.COOLING_FANS
        DeviceRootMenuFeature.COOLING_TEMPERATURE -> DeviceRootRoute.COOLING_TEMPERATURE
        DeviceRootMenuFeature.DEVICE_SETTINGS -> DeviceRootRoute.DEVICE_SETTINGS
    }
}
