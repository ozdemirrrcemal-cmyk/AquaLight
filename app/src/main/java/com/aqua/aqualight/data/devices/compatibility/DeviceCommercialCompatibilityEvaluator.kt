package com.aqua.aqualight.data.devices.compatibility

import com.aqua.aqualight.application.devices.DeviceRootMenuFeature
import com.aqua.aqualight.application.devices.DeviceRootRoute
import com.aqua.aqualight.data.devices.DeviceRootMenuFeatureResolver
import com.aqua.aqualight.data.devices.DeviceRootRoutePolicy
import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogProduct
import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogValidation
import com.aqua.aqualight.data.devices.catalog.AqlCommercialDeviceCatalog
import com.aqua.aqualight.data.devices.contract.AqlDeviceFeatureKey
import com.aqua.aqualight.data.devices.contract.AqlDeviceScreenKey
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceSnapshot

/**
 * Single data-layer evaluator for authenticated commercial compatibility.
 *
 * It deliberately excludes network presence. Optional feature/screen tokens control only the
 * corresponding menu/route; immutable product validation stays in AqlCommercialDeviceCatalog.
 */
internal object DeviceCommercialCompatibilityEvaluator {

    fun evaluate(snapshot: DeviceSnapshot): DeviceCommercialCompatibilityEvaluation {
        if (!snapshot.hasValidatedRuntimeMetadata) {
            return DeviceCommercialCompatibilityEvaluation.Incompatible(
                DeviceCommercialCompatibilityIssue.RUNTIME_METADATA_UNAVAILABLE
            )
        }

        val product = when (val validation = AqlCommercialDeviceCatalog.validateSnapshot(snapshot)) {
            is AqlCommercialCatalogValidation.Valid -> validation.product
            is AqlCommercialCatalogValidation.Invalid ->
                return DeviceCommercialCompatibilityEvaluation.Incompatible(
                    DeviceCommercialCompatibilityIssue.COMMERCIAL_PRODUCT_MISMATCH
                )
        }

        val features = snapshot.supportedFeatures
            .mapNotNull(AqlDeviceFeatureKey::fromWireExact)
            .toSet()
        val screens = snapshot.supportedScreens
            .mapNotNull(AqlDeviceScreenKey::fromWireExact)
            .toSet()

        if (!DeviceFamilyBaseContractPolicy.isCompatible(product.family, features, screens)) {
            return DeviceCommercialCompatibilityEvaluation.Incompatible(
                DeviceCommercialCompatibilityIssue.BASE_CONTRACT_INCOMPATIBLE
            )
        }

        val menuFeatures = DeviceRootMenuFeatureResolver.resolve(product, features, screens)
        val routes = DeviceRootRoutePolicy.allowedRoutes(
            product = product,
            features = features,
            screens = screens
        )
        return DeviceCommercialCompatibilityEvaluation.Compatible(
            product = product,
            features = features,
            screens = screens,
            menuFeatures = menuFeatures,
            allowedRoutes = routes
        )
    }
}

internal sealed interface DeviceCommercialCompatibilityEvaluation {
    data class Compatible(
        val product: AqlCommercialCatalogProduct,
        val features: Set<AqlDeviceFeatureKey>,
        val screens: Set<AqlDeviceScreenKey>,
        val menuFeatures: Set<DeviceRootMenuFeature>,
        val allowedRoutes: Set<DeviceRootRoute>
    ) : DeviceCommercialCompatibilityEvaluation

    data class Incompatible(
        val issue: DeviceCommercialCompatibilityIssue
    ) : DeviceCommercialCompatibilityEvaluation
}

internal enum class DeviceCommercialCompatibilityIssue {
    RUNTIME_METADATA_UNAVAILABLE,
    COMMERCIAL_PRODUCT_MISMATCH,
    BASE_CONTRACT_INCOMPATIBLE
}

/**
 * Base family requirements only. Optional features such as Quick Setup, calibration, history,
 * presets, thermal, reservoir or manual-run surfaces are intentionally absent.
 */
private object DeviceFamilyBaseContractPolicy {

    fun isCompatible(
        family: DeviceFamily,
        features: Set<AqlDeviceFeatureKey>,
        screens: Set<AqlDeviceScreenKey>
    ): Boolean = when (family) {
        DeviceFamily.LIGHT ->
            AqlDeviceFeatureKey.LIGHT_CONTROL in features &&
                AqlDeviceScreenKey.OVERVIEW in screens &&
                AqlDeviceScreenKey.LIGHT_CONTROL in screens &&
                AqlDeviceScreenKey.LIGHT_CHANNELS in screens
        DeviceFamily.TIMER ->
            AqlDeviceFeatureKey.TIMER_CONTROL in features &&
                AqlDeviceScreenKey.OVERVIEW in screens &&
                AqlDeviceScreenKey.TIMER_CONTROL in screens &&
                AqlDeviceScreenKey.TIMER_CHANNELS in screens
        DeviceFamily.DOSING ->
            AqlDeviceFeatureKey.DOSING_CONTROL in features &&
                AqlDeviceScreenKey.OVERVIEW in screens &&
                AqlDeviceScreenKey.DOSING_CONTROL in screens &&
                AqlDeviceScreenKey.DOSING_CHANNELS in screens
        DeviceFamily.COOLING ->
            AqlDeviceFeatureKey.COOLING_CONTROL in features &&
                AqlDeviceScreenKey.OVERVIEW in screens &&
                AqlDeviceScreenKey.COOLING_CONTROL in screens
        DeviceFamily.UNKNOWN -> false
    }
}
