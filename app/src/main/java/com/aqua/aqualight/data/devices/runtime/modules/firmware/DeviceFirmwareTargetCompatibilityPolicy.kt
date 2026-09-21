package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.data.devices.contract.AqlDeviceFeatureKey
import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.dosing.v1.DeviceDosingV1Contract
import com.aqua.aqualight.data.devices.model.SUPPORTED_DEVICE_API_VERSION
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1Contract
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeContract
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerRuntimeContract

/**
 * Central Android-side gate for a target OTA firmware contract.
 *
 * This policy evaluates the signed target artifact before an OTA plan is exposed. Optional domain
 * additions do not block an older app, but transport, shared device API and the required family
 * domain must remain consumable by the current Android build.
 */
internal object DeviceFirmwareTargetCompatibilityPolicy {

    fun requireCompatible(artifact: DeviceFirmwareManifestArtifact) {
        val contracts = artifact.contracts
        require(contracts.wsSchema == AqlWsContract.SCHEMA) {
            "Target firmware WebSocket schema requires a newer AquaLight app."
        }
        require(contracts.wsProtocolVersion == AqlWsContract.PROTOCOL_VERSION) {
            "Target firmware WebSocket protocol requires a newer AquaLight app."
        }
        require(contracts.deviceApiVersion == SUPPORTED_DEVICE_API_VERSION) {
            "Target firmware device API requires a newer AquaLight app."
        }

        val family = artifact.product.family
        val expectedDomain = REQUIRED_DOMAIN_BY_FAMILY[family]
            ?: error("Target firmware family is not a commercial AquaLight family.")
        require(artifact.contracts.requiredDomains == setOf(expectedDomain)) {
            "Target firmware base domain contract requires a newer AquaLight app."
        }

        val baseFeature = REQUIRED_BASE_FEATURE_BY_FAMILY[family]
            ?: error("Target firmware family has no base feature policy.")
        require(baseFeature in artifact.features) {
            "Target firmware does not advertise the required base control feature."
        }
    }

    private val REQUIRED_DOMAIN_BY_FAMILY = mapOf(
        "light" to DeviceLightRuntimeContract.SCHEMA,
        "timer" to DeviceTimerRuntimeContract.SCHEMA,
        "dosing" to DeviceDosingV1Contract.SCHEMA,
        "cooling" to DeviceCoolingV1Contract.SCHEMA
    )

    private val REQUIRED_BASE_FEATURE_BY_FAMILY = mapOf(
        "light" to AqlDeviceFeatureKey.LIGHT_CONTROL.wireValue,
        "timer" to AqlDeviceFeatureKey.TIMER_CONTROL.wireValue,
        "dosing" to AqlDeviceFeatureKey.DOSING_CONTROL.wireValue,
        "cooling" to AqlDeviceFeatureKey.COOLING_CONTROL.wireValue
    )
}
