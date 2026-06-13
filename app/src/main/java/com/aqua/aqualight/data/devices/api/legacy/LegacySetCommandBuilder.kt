package com.aqua.aqualight.data.devices.api.legacy

class LegacySetCommandBuilder {

    fun build(
        command: String,
        parameters: Map<String, String> = emptyMap()
    ): String {
        val cleanCommand = command.trim()

        if (parameters.isEmpty()) {
            return cleanCommand
        }

        val encodedParameters = parameters.entries.joinToString(
            separator = "&"
        ) { entry ->
            "${entry.key}=${entry.value}"
        }

        return "$cleanCommand?$encodedParameters"
    }
}
