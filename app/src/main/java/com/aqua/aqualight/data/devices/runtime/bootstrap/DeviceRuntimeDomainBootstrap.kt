package com.aqua.aqualight.data.devices.runtime.bootstrap

import com.aqua.aqualight.data.devices.contract.AqlDeviceFeatureKey
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadata
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataGeneration
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration

enum class DeviceRuntimeDomain {
    LIGHT,
    LIGHT_PROTECTION,
    LIGHT_THERMAL,
    COOLING,
    TIMER
}

data class DeviceRuntimeBootstrapContext(
    val deviceUid: DeviceUid,
    val connectionGeneration: DeviceRuntimeConnectionGeneration,
    val metadataGeneration: DeviceRuntimeMetadataGeneration,
    val metadata: DeviceRuntimeMetadata
)

data class DeviceRuntimeBootstrapPlan(
    val domains: List<DeviceRuntimeDomain>
) {
    init { require(domains.distinct().size == domains.size) }
}

sealed interface DeviceRuntimeDomainHydrationResult {
    val domain: DeviceRuntimeDomain

    data class Hydrated(
        override val domain: DeviceRuntimeDomain,
        val generation: DeviceRuntimeConnectionGeneration
    ) : DeviceRuntimeDomainHydrationResult

    data class Failed(
        override val domain: DeviceRuntimeDomain,
        val outcome: DeviceRuntimeCommandOutcome<*>
    ) : DeviceRuntimeDomainHydrationResult

    data class RejectedStale(
        override val domain: DeviceRuntimeDomain
    ) : DeviceRuntimeDomainHydrationResult
}

internal interface DeviceRuntimeDomainBootstrapPort {
    val domain: DeviceRuntimeDomain

    suspend fun hydrate(
        context: DeviceRuntimeBootstrapContext
    ): DeviceRuntimeDomainHydrationResult
}

/**
 * Pure catalog-derived planner. Screen/navigation state is intentionally not consulted.
 * Version-pinned domain adapters are selected only for the exact commercial product they mirror.
 */
internal object DeviceRuntimeBootstrapPlanFactory {
    fun create(metadata: DeviceRuntimeMetadata): DeviceRuntimeBootstrapPlan =
        DeviceRuntimeBootstrapPlan(
            domains = listOfNotNull(
                DeviceRuntimeDomain.LIGHT.takeIf { metadata.supportsLightRuntime() },
                DeviceRuntimeDomain.LIGHT_PROTECTION.takeIf {
                    metadata.supportsLightProtectionRuntime()
                },
                DeviceRuntimeDomain.LIGHT_THERMAL.takeIf {
                    metadata.supportsLightThermalRuntime()
                },
                DeviceRuntimeDomain.COOLING.takeIf { metadata.supportsCoolingRuntime() },
                DeviceRuntimeDomain.TIMER.takeIf { metadata.supportsTimerRuntime() }
            )
        )
}

private fun DeviceRuntimeMetadata.supportsLightRuntime(): Boolean {
    val moduleAvailable = modules.light
    val capabilityAvailable = capabilities.capabilities.light
    val featureAvailable = AqlDeviceFeatureKey.LIGHT_CONTROL in capabilities.supportedFeatures
    return moduleAvailable && capabilityAvailable && featureAvailable
}

private fun DeviceRuntimeMetadata.supportsLightProtectionRuntime(): Boolean =
    modules.light &&
        AqlDeviceFeatureKey.LIGHT_TEMPERATURE_PROTECTION in capabilities.supportedFeatures

private fun DeviceRuntimeMetadata.supportsLightThermalRuntime(): Boolean {
    val thermalCapabilities =
        capabilities.capabilities.temperature && capabilities.capabilities.fan
    val productSupported = identity.productKey.value == LIGHT_THERMAL_V1_PRODUCT_KEY
    return modules.light && thermalCapabilities && productSupported
}

private fun DeviceRuntimeMetadata.supportsCoolingRuntime(): Boolean {
    val coolingAvailable = modules.cooling && capabilities.capabilities.cooling
    val featureAvailable = AqlDeviceFeatureKey.COOLING_CONTROL in capabilities.supportedFeatures
    val productSupported = identity.productKey.value == COOLING_V1_PRODUCT_KEY
    return coolingAvailable && featureAvailable && productSupported
}

private fun DeviceRuntimeMetadata.supportsTimerRuntime(): Boolean {
    val timerAvailable = modules.timerApi && capabilities.capabilities.standaloneTimer
    val featureAvailable = AqlDeviceFeatureKey.TIMER_CONTROL in capabilities.supportedFeatures
    return timerAvailable && featureAvailable
}

private const val LIGHT_THERMAL_V1_PRODUCT_KEY = "LIGHT_WRGB_PRO_ELITE"
private const val COOLING_V1_PRODUCT_KEY = "COOLING_COOL_PRO_1F"
