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

/**
 * Single data-layer evaluator for authenticated commercial compatibility.
 *
 * It deliberately excludes network presence. Optional feature/screen tokens control only the
 * corresponding menu/route; immutable product validation stays in AqlCommercialDeviceCatalog.
 */
internal object DeviceCommercialCompatibilityEvaluator {

    fun evaluate(snapshot: DeviceSnapshot): DeviceCommercialCompatibilityEvaluation =
        snapshot.initialCompatibilityIssue()
            ?.let(DeviceCommercialCompatibilityEvaluation::Incompatible)
            ?: evaluateCatalog(snapshot)

    private fun evaluateCatalog(
        snapshot: DeviceSnapshot
    ): DeviceCommercialCompatibilityEvaluation =
        when (val validation = AqlCommercialDeviceCatalog.validateSnapshot(snapshot)) {
            is AqlCommercialCatalogValidation.Valid ->
                evaluateProduct(snapshot, validation.product)
            is AqlCommercialCatalogValidation.Invalid ->
                DeviceCommercialCompatibilityEvaluation.Incompatible(
                    DeviceCommercialCompatibilityIssue.COMMERCIAL_PRODUCT_MISMATCH
                )
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
        return if (DeviceFamilyBaseContractPolicy.isCompatible(product.family, features, screens)) {
            DeviceCommercialCompatibilityEvaluation.Compatible(
                product = product,
                features = features,
                screens = screens,
                menuFeatures = DeviceRootMenuFeatureResolver.resolve(product, features, screens),
                allowedRoutes = DeviceRootRoutePolicy.allowedRoutes(
                    product = product,
                    features = features,
                    screens = screens
                )
            )
        } else {
            DeviceCommercialCompatibilityEvaluation.Incompatible(
                DeviceCommercialCompatibilityIssue.BASE_CONTRACT_INCOMPATIBLE
            )
        }
    }

    private fun DeviceSnapshot.initialCompatibilityIssue(): DeviceCommercialCompatibilityIssue? {
        val apiVersion = apiVersion.toIntOrNull()
        return when {
            !hasValidatedRuntimeMetadata ->
                DeviceCommercialCompatibilityIssue.RUNTIME_METADATA_UNAVAILABLE
            apiVersion == SUPPORTED_DEVICE_API_VERSION -> null
            apiVersion != null && apiVersion > SUPPORTED_DEVICE_API_VERSION ->
                DeviceCommercialCompatibilityIssue.APPLICATION_UPDATE_REQUIRED
            else -> DeviceCommercialCompatibilityIssue.BASE_CONTRACT_INCOMPATIBLE
        }
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
    BASE_CONTRACT_INCOMPATIBLE,
    APPLICATION_UPDATE_REQUIRED
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
