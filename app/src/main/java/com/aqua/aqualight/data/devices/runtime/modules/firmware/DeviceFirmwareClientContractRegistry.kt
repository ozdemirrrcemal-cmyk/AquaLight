package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.data.devices.contract.AqlDeviceFeatureKey
import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.SUPPORTED_DEVICE_API_VERSION

/**
 * Android-owned compatibility registry for firmware release planning.
 *
 * This is not a runtime source of truth for domain topology. It only states which published
 * contract identifiers this Android build can safely operate after an OTA update.
 */
internal object DeviceFirmwareClientContractRegistry {

    private const val LIGHT_V1 = "aqualight.light.v1"
    private const val TIMER_V1 = "aqualight.timer.v1"
    private const val DOSING_V1 = "aqualight.dosing.v1"
    private const val COOLING_V1 = "aql.cooling.v1"

    fun validate(
        artifact: DeviceFirmwareManifestArtifact
    ) {
        val reason = incompatibilityReason(artifact)
        if (reason != null) throw DeviceFirmwareClientIncompatibleException(reason)
    }

    private fun incompatibilityReason(
        artifact: DeviceFirmwareManifestArtifact
    ): String? {
        val contracts = artifact.contracts
        val familyContracts = FAMILY_CONTRACTS[artifact.product.family]
        return when {
            contracts.wsSchema != AqlWsContract.SCHEMA ->
                "Target firmware WebSocket schema requires a newer AquaLight app."
            contracts.wsProtocolVersion != AqlWsContract.PROTOCOL_VERSION ->
                "Target firmware WebSocket protocol requires a newer AquaLight app."
            contracts.deviceApiVersion != SUPPORTED_DEVICE_API_VERSION ->
                "Target firmware Device API requires a newer AquaLight app."
            familyContracts == null ->
                "Target firmware family is not supported by this AquaLight app."
            contracts.requiredDomains != setOf(familyContracts.base) ->
                "Target firmware base domain contract requires a newer AquaLight app."
            familyContracts.baseFeature !in artifact.features ->
                "Target firmware does not advertise the required base control feature."
            else -> null
        }
    }

    private data class FamilyContracts(
        val base: String,
        val baseFeature: String
    )

    private val FAMILY_CONTRACTS = mapOf(
        "light" to FamilyContracts(
            base = LIGHT_V1,
            baseFeature = AqlDeviceFeatureKey.LIGHT_CONTROL.wireValue
        ),
        "timer" to FamilyContracts(
            base = TIMER_V1,
            baseFeature = AqlDeviceFeatureKey.TIMER_CONTROL.wireValue
        ),
        "dosing" to FamilyContracts(
            base = DOSING_V1,
            baseFeature = AqlDeviceFeatureKey.DOSING_CONTROL.wireValue
        ),
        "cooling" to FamilyContracts(
            base = COOLING_V1,
            baseFeature = AqlDeviceFeatureKey.COOLING_CONTROL.wireValue
        )
    )
}

internal class DeviceFirmwareClientIncompatibleException(
    message: String
) : IllegalArgumentException(message)
