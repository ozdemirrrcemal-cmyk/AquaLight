package com.aqua.aqualight.data.devices

import java.util.Locale

object DeviceSerialFormatter {

    /**
     * Commercial display identifier.
     *
     * Priority:
     * 1. Factory/customer SerialNumber from firmware.
     * 2. AQL-<setupCode>-<shortId> device code.
     * 3. AQL-<setupCode>-<derivedId> fallback.
     */
    fun buildCommercialIdentifier(
        setupCode: String,
        serialNumber: String? = null,
        shortId: String? = null,
        deviceUid: String? = null,
        macAddress: String? = null,
        firmwareSerial: String? = null,
        fallbackNumericId: Long? = null
    ): String {
        val factorySerial = serialNumber
            ?.trim()
            .orEmpty()

        if (factorySerial.isNotBlank()) {
            return factorySerial
        }

        val normalizedSetupCode = setupCode
            .trim()
            .uppercase(Locale.US)
            .ifBlank {
                DEFAULT_SETUP_CODE
            }

        val code = shortId
            ?.trim()
            ?.uppercase(Locale.US)
            ?.filter { char ->
                char.isLetterOrDigit()
            }
            ?.takeIf { value ->
                value.isNotBlank()
            }
            ?: deriveShortId(
                deviceUid = deviceUid,
                macAddress = macAddress,
                firmwareSerial = firmwareSerial,
                fallbackNumericId = fallbackNumericId
            )

        return "$SERIAL_PREFIX-$normalizedSetupCode-$code"
    }

    fun displaySerial(
        serial: String
    ): String {
        return serial.trim()
    }

    private fun deriveShortId(
        deviceUid: String?,
        macAddress: String?,
        firmwareSerial: String?,
        fallbackNumericId: Long?
    ): String {
        return cleanId(
            rawId = deviceUid
                ?.ifBlank { null }
                ?: macAddress
                    ?.ifBlank { null }
                ?: firmwareSerial
                    ?.ifBlank { null }
                ?: fallbackNumericId
                    ?.takeIf { value -> value > 0L }
                    ?.toString()
                ?: DEFAULT_SHORT_ID
        ).takeLast(SHORT_ID_LENGTH)
    }

    private fun cleanId(
        rawId: String
    ): String {
        return rawId
            .filter { char ->
                char.isLetterOrDigit()
            }
            .uppercase(Locale.US)
            .takeLast(12)
            .ifBlank {
                DEFAULT_SHORT_ID
            }
    }

    private const val SERIAL_PREFIX = "AQL"
    private const val DEFAULT_SETUP_CODE = "DEV"
    private const val DEFAULT_SHORT_ID = "000000"
    private const val SHORT_ID_LENGTH = 6
}
