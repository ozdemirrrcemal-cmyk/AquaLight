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
     *
     * The old AW-1221 / AC-7872 synthetic format is intentionally no longer
     * generated for new commercial devices.
     */
    fun buildCommercialIdentifier(
        setupCode: String,
        serialNumber: String? = null,
        shortId: String? = null,
        deviceUid: String? = null,
        macAddress: String? = null,
        firmwareSerial: String? = null,
        legacyId: Long? = null
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
                legacyId = legacyId
            )

        return "$SERIAL_PREFIX-$normalizedSetupCode-$code"
    }

    fun displaySerial(
        serial: String
    ): String {
        return serial.trim()
    }

    /**
     * Legacy helper kept for old call sites during migration. New code should
     * call buildCommercialIdentifier().
     */
    fun buildSerial(
        aquaName: String,
        name: String,
        id: Long,
        firmwareSerial: String = "",
        deviceUid: String = "",
        macAddress: String = ""
    ): String {
        return buildSerial(
            aquaName = aquaName,
            name = name,
            rawId = firmwareSerial
                .ifBlank { deviceUid }
                .ifBlank { macAddress }
                .ifBlank { id.toString() }
        )
    }

    /**
     * Legacy helper kept for old previews/migration only.
     */
    fun buildSerial(
        aquaName: String,
        name: String,
        rawId: String
    ): String {
        val prefix = buildPrefix(
            aquaName = aquaName,
            name = name
        )

        val cleanId = cleanId(
            rawId = rawId
        )

        return "$prefix-$cleanId"
    }

    private fun deriveShortId(
        deviceUid: String?,
        macAddress: String?,
        firmwareSerial: String?,
        legacyId: Long?
    ): String {
        return cleanId(
            rawId = deviceUid
                ?.ifBlank { null }
                ?: macAddress
                    ?.ifBlank { null }
                ?: firmwareSerial
                    ?.ifBlank { null }
                ?: legacyId
                    ?.takeIf { value -> value > 0L }
                    ?.toString()
                ?: DEFAULT_SHORT_ID
        ).takeLast(SHORT_ID_LENGTH)
    }

    private fun buildPrefix(
        aquaName: String,
        name: String
    ): String {
        val aquaInitial = aquaName.firstOrNull()
            ?.uppercaseChar()
            ?: 'X'

        val nameInitial = name.firstOrNull()
            ?.uppercaseChar()
            ?: 'X'

        return "$aquaInitial$nameInitial"
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
