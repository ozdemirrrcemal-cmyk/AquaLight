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
        val aquaInitial = aquaName.firstOrNull()
            ?.uppercaseChar()
            ?: 'X'

        val nameInitial = name.firstOrNull()
            ?.uppercaseChar()
            ?: 'X'

        val shortId = rawId
            .filter { char ->
                char.isDigit()
            }
            .takeLast(4)
            .ifBlank {
                "0000"
            }

        return "$aquaInitial$nameInitial-$shortId"
    }
}