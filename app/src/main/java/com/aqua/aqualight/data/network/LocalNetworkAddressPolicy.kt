package com.aqua.aqualight.data.network

/**
 * Commercial cleartext boundary for AquaLight hardware traffic.
 *
 * ESP32 firmware currently exposes local HTTP endpoints, so Android manifest
 * cleartext cannot be fully disabled without breaking device setup/control.
 * This policy keeps that exception scoped to private LAN, link-local and setup
 * AP addresses only. Public internet hosts must never be contacted over HTTP.
 */
object LocalNetworkAddressPolicy {

    fun requireLocalCleartextHost(
        host: String
    ) {
        require(isAllowedLocalCleartextHost(host)) {
            "Cleartext HTTP is allowed only for local AquaLight device addresses."
        }
    }

    fun isAllowedLocalCleartextHost(
        host: String
    ): Boolean {
        val normalizedHost = normalizeHost(host)

        if (normalizedHost.isBlank()) {
            return false
        }

        if (
            normalizedHost.equals("localhost", ignoreCase = true) ||
            normalizedHost.endsWith(".local", ignoreCase = true)
        ) {
            return true
        }

        if (
            normalizedHost == "::1" ||
            normalizedHost.startsWith("fe80:", ignoreCase = true)
        ) {
            return true
        }

        return isPrivateOrLinkLocalIpv4(normalizedHost)
    }

    private fun normalizeHost(
        rawHost: String
    ): String {
        val trimmed = rawHost
            .trim()
            .removePrefix("http://")
            .removePrefix("https://")

        return if (trimmed.startsWith("[") && trimmed.contains("]")) {
            trimmed.substringAfter("[").substringBefore("]")
        } else {
            trimmed.substringBefore("/").substringBefore(":")
        }
    }

    private fun isPrivateOrLinkLocalIpv4(
        host: String
    ): Boolean {
        val parts = host.split(".")

        if (parts.size != IPV4_PART_COUNT) {
            return false
        }

        val bytes = parts.map { part ->
            part.toIntOrNull() ?: return false
        }

        if (bytes.any { byte -> byte !in IPV4_BYTE_RANGE }) {
            return false
        }

        val first = bytes[0]
        val second = bytes[1]

        return first == 10 ||
            first == 127 ||
            first == 169 && second == 254 ||
            first == 172 && second in 16..31 ||
            first == 192 && second == 168
    }

    private const val IPV4_PART_COUNT = 4
    private val IPV4_BYTE_RANGE = 0..255
}
