package com.aqua.aqualight.data.devices.catalog

import java.util.Locale

/**
 * Yeni ticari setup Wi-Fi standardı:
 * AQL-<setupCode>-<shortId>
 */
data class AquaSetupSsid(
    val rawSsid: String,
    val setupCode: String,
    val shortId: String
) {
    init {
        require(rawSsid.isNotBlank()) {
            "rawSsid cannot be blank."
        }

        require(setupCode.isNotBlank()) {
            "setupCode cannot be blank."
        }

        require(shortId.isNotBlank()) {
            "shortId cannot be blank."
        }
    }

    companion object {
        private val SETUP_SSID_REGEX = Regex(
            pattern = "^AQL-([A-Z0-9]{2,5})-([A-Z0-9]{4,12})$",
            option = RegexOption.IGNORE_CASE
        )

        fun parse(
            ssid: String?
        ): AquaSetupSsid? {
            val normalized = ssid
                ?.trim()
                ?.uppercase(Locale.US)
                .orEmpty()

            val match = SETUP_SSID_REGEX.matchEntire(
                input = normalized
            ) ?: return null

            return AquaSetupSsid(
                rawSsid = normalized,
                setupCode = match.groupValues[1],
                shortId = match.groupValues[2]
            )
        }

        fun isValid(
            ssid: String?
        ): Boolean {
            return parse(
                ssid = ssid
            ) != null
        }
    }
}
