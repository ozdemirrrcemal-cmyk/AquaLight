package com.aqua.aqualight.data.devices

import com.aqua.aqualight.application.devices.DeviceRootMenuFeature
import com.aqua.aqualight.application.devices.DeviceRootRoute
import com.aqua.aqualight.application.devices.DeviceRootRouteResolver
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogProduct
import com.aqua.aqualight.data.devices.contract.AqlDeviceFeatureKey
import com.aqua.aqualight.data.devices.contract.AqlDeviceScreenKey

internal object DeviceRootRoutePolicy {

    fun allowedRoutes(product: AqlCommercialCatalogProduct): Set<DeviceRootRoute> =
        allowedRoutes(
            family = product.family.toOwnerDeviceFamily(),
            menuFeatures = DeviceRootMenuFeatureResolver.resolve(product)
        )

    fun allowedRoutes(
        product: AqlCommercialCatalogProduct,
        features: Set<AqlDeviceFeatureKey>,
        screens: Set<AqlDeviceScreenKey>
    ): Set<DeviceRootRoute> = allowedRoutes(
        family = product.family.toOwnerDeviceFamily(),
        menuFeatures = DeviceRootMenuFeatureResolver.resolve(product, features, screens)
    )

    fun allowedRoutes(
        family: OwnerDeviceFamily,
        menuFeatures: Set<DeviceRootMenuFeature>
    ): Set<DeviceRootRoute> = menuFeatures.mapNotNullTo(linkedSetOf()) { feature ->
        DeviceRootRouteResolver.resolve(family, feature)
    }

    fun authorize(
        product: AqlCommercialCatalogProduct,
        route: DeviceRootRoute
    ): Boolean = route in allowedRoutes(product)
}
