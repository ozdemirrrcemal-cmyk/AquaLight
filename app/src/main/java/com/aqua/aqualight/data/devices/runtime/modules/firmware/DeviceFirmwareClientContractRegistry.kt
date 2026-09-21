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
        val contracts = artifact.contracts
        val family = artifact.product.family
        if (contracts.wsSchema != AqlWsContract.SCHEMA) {
            throw DeviceFirmwareClientIncompatibleException(
                "Target firmware WebSocket schema requires a newer AquaLight app."
            )
        }
        if (contracts.wsProtocolVersion != AqlWsContract.PROTOCOL_VERSION) {
            throw DeviceFirmwareClientIncompatibleException(
                "Target firmware WebSocket protocol requires a newer AquaLight app."
            )
        }
        if (contracts.deviceApiVersion != SUPPORTED_DEVICE_API_VERSION) {
            throw DeviceFirmwareClientIncompatibleException(
                "Target firmware Device API requires a newer AquaLight app."
            )
        }

        val familyContracts = FAMILY_CONTRACTS[family]
            ?: throw DeviceFirmwareClientIncompatibleException(
                "Target firmware family is not supported by this AquaLight app."
            )
        if (contracts.requiredDomains != setOf(familyContracts.base)) {
            throw DeviceFirmwareClientIncompatibleException(
                "Target firmware base domain contract requires a newer AquaLight app."
            )
        }
        if (familyContracts.baseFeature !in artifact.features) {
            throw DeviceFirmwareClientIncompatibleException(
                "Target firmware does not advertise the required base control feature."
            )
        }
        // optionalDomains are additive by definition. An older app may ignore an unknown optional
        // domain as long as transport, device API and the one required family domain stay compatible.
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
