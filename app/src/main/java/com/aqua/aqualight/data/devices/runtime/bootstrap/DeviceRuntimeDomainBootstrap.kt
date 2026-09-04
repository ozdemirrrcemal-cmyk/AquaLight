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

/** Pure catalog-derived planner. Screen/navigation state is intentionally not consulted. */
internal object DeviceRuntimeBootstrapPlanFactory {
    fun create(metadata: DeviceRuntimeMetadata): DeviceRuntimeBootstrapPlan {
        val modules = metadata.modules
        val capabilities = metadata.capabilities.capabilities
        val features = metadata.capabilities.supportedFeatures
        return DeviceRuntimeBootstrapPlan(
            buildList {
                if (
                    modules.light &&
                    capabilities.light &&
                    AqlDeviceFeatureKey.LIGHT_CONTROL in features
                ) {
                    add(DeviceRuntimeDomain.LIGHT)
                }
                if (
                    modules.light &&
                    AqlDeviceFeatureKey.LIGHT_TEMPERATURE_PROTECTION in features
                ) {
                    add(DeviceRuntimeDomain.LIGHT_PROTECTION)
                }
                if (
                    modules.light &&
                    capabilities.temperature &&
                    capabilities.fan &&
                    metadata.identity.productKey.value == LIGHT_THERMAL_V1_PRODUCT_KEY
                ) {
                    add(DeviceRuntimeDomain.LIGHT_THERMAL)
                }
                if (
                    modules.cooling &&
                    capabilities.cooling &&
                    AqlDeviceFeatureKey.COOLING_CONTROL in features
                ) {
                    add(DeviceRuntimeDomain.COOLING)
                }
                if (
                    modules.timerApi &&
                    capabilities.standaloneTimer &&
                    AqlDeviceFeatureKey.TIMER_CONTROL in features
                ) {
                    add(DeviceRuntimeDomain.TIMER)
                }
            }
        )
    }
}

private const val LIGHT_THERMAL_V1_PRODUCT_KEY = "LIGHT_WRGB_PRO_ELITE"
