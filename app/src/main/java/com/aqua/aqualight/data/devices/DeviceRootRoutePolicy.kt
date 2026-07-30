package com.aqua.aqualight.data.devices

import com.aqua.aqualight.application.devices.DeviceRootRoute
import com.aqua.aqualight.application.devices.DeviceRootRouteResolver
import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogProduct

internal object DeviceRootRoutePolicy {

    fun allowedRoutes(product: AqlCommercialCatalogProduct): Set<DeviceRootRoute> {
        val family = product.family.toOwnerDeviceFamily()
        return DeviceRootMenuFeatureResolver.resolve(product)
            .mapNotNullTo(linkedSetOf()) { feature ->
                DeviceRootRouteResolver.resolve(family, feature)
            }
    }

    fun authorize(
        product: AqlCommercialCatalogProduct,
        route: DeviceRootRoute
    ): Boolean = route in allowedRoutes(product)
}
