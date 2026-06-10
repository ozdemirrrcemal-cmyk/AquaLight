package com.aqua.aqualight.data.devices

object DeviceSerialFormatter {

    fun buildSerial(
        aquaName: String,
        name: String,
        id: Long,
        firmwareSerial: String? = null,
        deviceUid: String? = null,
        macAddress: String? = null
    ): String {
        val preferredIdentity = firstCleanIdentity(
            firmwareSerial,
            deviceUid,
            macAddress
        )

        return buildSerial(
            aquaName = aquaName,
            name = name,
            rawId = preferredIdentity ?: id.toString()
        )
    }

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

    fun displaySerial(
        serial: String
    ): String {
        if (serial.isBlank()) {
            return ""
        }

        return serial.trim()
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

    private fun firstCleanIdentity(
        vararg values: String?
    ): String? {
        return values.firstNotNullOfOrNull { value ->
            value
                ?.trim()
                ?.takeIf { identity ->
                    identity.isNotBlank()
                }
        }
    }

    private fun cleanId(
        rawId: String
    ): String {
        return rawId
            .filter { char ->
                char.isLetterOrDigit()
            }
            .uppercase()
            .takeLast(12)
            .ifBlank {
                "0000"
            }
    }
}