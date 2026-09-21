package com.aqua.aqualight.data.devices.runtime.modules.firmware

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
    private const val LIGHT_THERMAL_V1 = "aql.light-thermal.v1"
    private const val TIMER_V1 = "aqualight.timer.v1"
    private const val DOSING_V1 = "aqualight.dosing.v1"
    private const val COOLING_V1 = "aql.cooling.v1"

    fun validate(
        contracts: DeviceFirmwareManifestContracts,
        family: String
    ) {
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
        if (familyContracts.base !in contracts.requiredDomains) {
            throw DeviceFirmwareClientIncompatibleException(
                "Target firmware is missing the required base domain contract."
            )
        }

        val publishedDomains = contracts.requiredDomains + contracts.optionalDomains
        if (!familyContracts.supported.containsAll(publishedDomains)) {
            throw DeviceFirmwareClientIncompatibleException(
                "Target firmware requires a domain contract that this AquaLight app does not support."
            )
        }
    }

    private data class FamilyContracts(
        val base: String,
        val supported: Set<String>
    )

    private val FAMILY_CONTRACTS = mapOf(
        "light" to FamilyContracts(
            base = LIGHT_V1,
            supported = setOf(LIGHT_V1, LIGHT_THERMAL_V1)
        ),
        "timer" to FamilyContracts(
            base = TIMER_V1,
            supported = setOf(TIMER_V1)
        ),
        "dosing" to FamilyContracts(
            base = DOSING_V1,
            supported = setOf(DOSING_V1)
        ),
        "cooling" to FamilyContracts(
            base = COOLING_V1,
            supported = setOf(COOLING_V1)
        )
    )
}

internal class DeviceFirmwareClientIncompatibleException(
    message: String
) : IllegalArgumentException(message)
