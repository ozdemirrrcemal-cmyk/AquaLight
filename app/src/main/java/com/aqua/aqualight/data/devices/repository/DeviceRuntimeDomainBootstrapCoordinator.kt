package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogProduct
import com.aqua.aqualight.data.devices.contract.AqlDeviceFeatureKey
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataGeneration
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration

internal enum class DeviceRuntimeDomainBootstrapStep {
    LIGHT_STATUS,
    LIGHT_THERMAL_STATUS,
    COOLING_STATUS,
    TIMER_STATUS,
    DOSING_STATUS
}

internal data class DeviceRuntimeDomainBootstrapPlan(
    val deviceUid: DeviceUid,
    val connectionGeneration: DeviceRuntimeConnectionGeneration,
    val metadataGeneration: DeviceRuntimeMetadataGeneration,
    val steps: List<DeviceRuntimeDomainBootstrapStep>
)

internal object DeviceRuntimeDomainBootstrapPlanResolver {
    fun resolve(
        deviceUid: DeviceUid,
        connectionGeneration: DeviceRuntimeConnectionGeneration,
        metadataGeneration: DeviceRuntimeMetadataGeneration,
        product: AqlCommercialCatalogProduct
    ): DeviceRuntimeDomainBootstrapPlan {
        val steps = when (product.family) {
            DeviceFamily.LIGHT -> buildList {
                add(DeviceRuntimeDomainBootstrapStep.LIGHT_STATUS)
                if (AqlDeviceFeatureKey.LIGHT_FAN_CONTROL in product.profile.supportedFeatures) {
                    add(DeviceRuntimeDomainBootstrapStep.LIGHT_THERMAL_STATUS)
                }
            }
            DeviceFamily.COOLING -> listOf(DeviceRuntimeDomainBootstrapStep.COOLING_STATUS)
            DeviceFamily.TIMER -> listOf(DeviceRuntimeDomainBootstrapStep.TIMER_STATUS)
            DeviceFamily.DOSING -> listOf(DeviceRuntimeDomainBootstrapStep.DOSING_STATUS)
            DeviceFamily.UNKNOWN -> error("Validated commercial product cannot have UNKNOWN family.")
        }
        require(steps.isNotEmpty()) { "Validated commercial product requires an owning domain." }
        return DeviceRuntimeDomainBootstrapPlan(
            deviceUid = deviceUid,
            connectionGeneration = connectionGeneration,
            metadataGeneration = metadataGeneration,
            steps = steps
        )
    }
}

internal sealed interface DeviceRuntimeDomainBootstrapResult {
    data class Completed(val plan: DeviceRuntimeDomainBootstrapPlan) :
        DeviceRuntimeDomainBootstrapResult

    data class Failed(
        val plan: DeviceRuntimeDomainBootstrapPlan,
        val step: DeviceRuntimeDomainBootstrapStep,
        val outcome: DeviceRuntimeCommandOutcome<*>
    ) : DeviceRuntimeDomainBootstrapResult

    data class Stale(val plan: DeviceRuntimeDomainBootstrapPlan) :
        DeviceRuntimeDomainBootstrapResult

    data class AlreadyStarted(val plan: DeviceRuntimeDomainBootstrapPlan) :
        DeviceRuntimeDomainBootstrapResult
}

/**
 * Serializes the firmware-authoritative owning-domain bootstrap for one authenticated generation.
 *
 * The coordinator owns generation/idempotency only. Transport and typed response hydration remain
 * in the module repositories supplied by [execute].
 */
internal class DeviceRuntimeDomainBootstrapCoordinator {
    private val lock = Any()
    private val generations = mutableMapOf<DeviceUid, DeviceRuntimeConnectionGeneration>()

    suspend fun run(
        plan: DeviceRuntimeDomainBootstrapPlan,
        execute: suspend (DeviceRuntimeDomainBootstrapStep) -> DeviceRuntimeCommandOutcome<*>?
    ): DeviceRuntimeDomainBootstrapResult {
        if (!begin(plan)) return DeviceRuntimeDomainBootstrapResult.AlreadyStarted(plan)

        for (step in plan.steps) {
            if (!isCurrent(plan)) return DeviceRuntimeDomainBootstrapResult.Stale(plan)
            val outcome = execute(step) ?: return DeviceRuntimeDomainBootstrapResult.Stale(plan)
            if (!isCurrent(plan)) return DeviceRuntimeDomainBootstrapResult.Stale(plan)
            when (outcome) {
                is DeviceRuntimeCommandOutcome.Success<*> -> {
                    if (outcome.generation != plan.connectionGeneration) {
                        return DeviceRuntimeDomainBootstrapResult.Stale(plan)
                    }
                }
                else -> return DeviceRuntimeDomainBootstrapResult.Failed(plan, step, outcome)
            }
        }

        return if (isCurrent(plan)) {
            DeviceRuntimeDomainBootstrapResult.Completed(plan)
        } else {
            DeviceRuntimeDomainBootstrapResult.Stale(plan)
        }
    }

    fun clear(deviceUid: DeviceUid) {
        synchronized(lock) { generations.remove(deviceUid) }
    }

    fun clearAll() {
        synchronized(lock) { generations.clear() }
    }

    private fun begin(plan: DeviceRuntimeDomainBootstrapPlan): Boolean = synchronized(lock) {
        val current = generations[plan.deviceUid]
        if (current == plan.connectionGeneration) {
            false
        } else {
            generations[plan.deviceUid] = plan.connectionGeneration
            true
        }
    }

    private fun isCurrent(plan: DeviceRuntimeDomainBootstrapPlan): Boolean = synchronized(lock) {
        generations[plan.deviceUid] == plan.connectionGeneration
    }
}

internal sealed interface DeviceRuntimeSessionReadiness {
    val deviceUid: DeviceUid
    val connectionGeneration: DeviceRuntimeConnectionGeneration

    data class CollectingSharedMetadata(
        override val deviceUid: DeviceUid,
        override val connectionGeneration: DeviceRuntimeConnectionGeneration
    ) : DeviceRuntimeSessionReadiness

    data class MetadataReady(
        override val deviceUid: DeviceUid,
        override val connectionGeneration: DeviceRuntimeConnectionGeneration,
        val metadataGeneration: DeviceRuntimeMetadataGeneration
    ) : DeviceRuntimeSessionReadiness

    data class DomainBootstrapping(
        val plan: DeviceRuntimeDomainBootstrapPlan
    ) : DeviceRuntimeSessionReadiness {
        override val deviceUid: DeviceUid = plan.deviceUid
        override val connectionGeneration: DeviceRuntimeConnectionGeneration = plan.connectionGeneration
    }

    data class RuntimeReady(
        val plan: DeviceRuntimeDomainBootstrapPlan
    ) : DeviceRuntimeSessionReadiness {
        override val deviceUid: DeviceUid = plan.deviceUid
        override val connectionGeneration: DeviceRuntimeConnectionGeneration = plan.connectionGeneration
    }

    data class Rejected(
        override val deviceUid: DeviceUid,
        override val connectionGeneration: DeviceRuntimeConnectionGeneration,
        val reason: String
    ) : DeviceRuntimeSessionReadiness
}
