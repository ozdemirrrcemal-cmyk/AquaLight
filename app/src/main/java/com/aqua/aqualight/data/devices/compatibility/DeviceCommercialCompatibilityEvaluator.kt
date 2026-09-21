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
import com.aqua.aqualight.data.devices.model.SUPPORTED_DEVICE_API_VERSION

internal object DeviceCommercialCompatibilityEvaluator {

    fun evaluate(snapshot: DeviceSnapshot): DeviceCommercialCompatibilityEvaluation {
        val initialIssue = snapshot.initialCompatibilityIssue()
        if (initialIssue != null) {
            return DeviceCommercialCompatibilityEvaluation.Incompatible(initialIssue)
        }
        return when (val validation = AqlCommercialDeviceCatalog.validateSnapshot(snapshot)) {
            is AqlCommercialCatalogValidation.Invalid ->
                DeviceCommercialCompatibilityEvaluation.Incompatible(
                    DeviceCommercialCompatibilityIssue.COMMERCIAL_PRODUCT_MISMATCH
                )
            is AqlCommercialCatalogValidation.Valid ->
                evaluateProduct(snapshot, validation.product)
        }
    }

    private fun evaluateProduct(
        snapshot: DeviceSnapshot,
        product: AqlCommercialCatalogProduct
    ): DeviceCommercialCompatibilityEvaluation {
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
        return DeviceCommercialCompatibilityEvaluation.Compatible(
            product = product,
            menuFeatures = DeviceRootMenuFeatureResolver.resolve(product, features, screens),
            allowedRoutes = DeviceRootRoutePolicy.allowedRoutes(product, features, screens)
        )
    }

    private fun DeviceSnapshot.initialCompatibilityIssue(): DeviceCommercialCompatibilityIssue? {
        if (!hasValidatedRuntimeMetadata) {
            return DeviceCommercialCompatibilityIssue.RUNTIME_METADATA_UNAVAILABLE
        }
        val reportedApiVersion = apiVersion.toIntOrNull()
        return when {
            reportedApiVersion == SUPPORTED_DEVICE_API_VERSION -> null
            reportedApiVersion != null && reportedApiVersion > SUPPORTED_DEVICE_API_VERSION ->
                DeviceCommercialCompatibilityIssue.APPLICATION_UPDATE_REQUIRED
            else -> DeviceCommercialCompatibilityIssue.BASE_CONTRACT_INCOMPATIBLE
        }
    }
}

internal sealed interface DeviceCommercialCompatibilityEvaluation {
    data class Compatible(
        val product: AqlCommercialCatalogProduct,
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
    BASE_CONTRACT_INCOMPATIBLE,
    APPLICATION_UPDATE_REQUIRED
}

private object DeviceFamilyBaseContractPolicy {

    fun isCompatible(
        family: DeviceFamily,
        features: Set<AqlDeviceFeatureKey>,
        screens: Set<AqlDeviceScreenKey>
    ): Boolean = REQUIREMENTS[family]?.let { requirement ->
        requirement.features.all(features::contains) &&
            requirement.screens.all(screens::contains)
    } ?: false

    private data class Requirement(
        val features: Set<AqlDeviceFeatureKey>,
        val screens: Set<AqlDeviceScreenKey>
    )

    private val REQUIREMENTS = mapOf(
        DeviceFamily.LIGHT to Requirement(
            features = setOf(AqlDeviceFeatureKey.LIGHT_CONTROL),
            screens = setOf(
                AqlDeviceScreenKey.OVERVIEW,
                AqlDeviceScreenKey.LIGHT_CONTROL,
                AqlDeviceScreenKey.LIGHT_CHANNELS
            )
        ),
        DeviceFamily.TIMER to Requirement(
            features = setOf(AqlDeviceFeatureKey.TIMER_CONTROL),
            screens = setOf(
                AqlDeviceScreenKey.OVERVIEW,
                AqlDeviceScreenKey.TIMER_CONTROL,
                AqlDeviceScreenKey.TIMER_CHANNELS
            )
        ),
        DeviceFamily.DOSING to Requirement(
            features = setOf(AqlDeviceFeatureKey.DOSING_CONTROL),
            screens = setOf(
                AqlDeviceScreenKey.OVERVIEW,
                AqlDeviceScreenKey.DOSING_CONTROL,
                AqlDeviceScreenKey.DOSING_CHANNELS
            )
        ),
        DeviceFamily.COOLING to Requirement(
            features = setOf(AqlDeviceFeatureKey.COOLING_CONTROL),
            screens = setOf(
                AqlDeviceScreenKey.OVERVIEW,
                AqlDeviceScreenKey.COOLING_CONTROL
            )
        )
    )
}
