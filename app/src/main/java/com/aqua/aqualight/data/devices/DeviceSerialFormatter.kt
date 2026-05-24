package com.aqua.aqualight.data.devices

object DeviceSerialFormatter {

    fun buildSerial(
        aquaName: String,
        name: String,
        id: Long
    ): String {
        return buildSerial(
            aquaName = aquaName,
            name = name,
            rawId = id.toString()
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

        val shortId = shortId(
            rawId = rawId
        )

        return "$prefix-$shortId"
    }

    fun displaySerial(
        serial: String
    ): String {
        if (serial.isBlank()) {
            return ""
        }

        val prefix = serial.substringBefore(
            delimiter = "-",
            missingDelimiterValue = ""
        ).trim()

        val rawId = serial.substringAfter(
            delimiter = "-",
            missingDelimiterValue = ""
        ).trim()

        if (prefix.isBlank() || rawId.isBlank()) {
            return serial
        }

        return "$prefix-${shortId(rawId)}"
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

    private fun shortId(
        rawId: String
    ): String {
        return rawId
            .filter { char ->
                char.isDigit()
            }
            .takeLast(4)
            .ifBlank {
                "0000"
            }
    }
}