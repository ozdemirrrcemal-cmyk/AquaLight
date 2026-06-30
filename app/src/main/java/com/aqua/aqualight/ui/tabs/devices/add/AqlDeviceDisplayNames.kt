package com.aqua.aqualight.ui.tabs.devices.add

import java.util.Locale

internal object AqlDeviceDisplayNames {

    fun productTitle(
        vararg candidates: String,
        fallback: String = "AquaLight Device"
    ): String {
        candidates
            .asSequence()
            .mapNotNull { value -> knownProductTitle(value) }
            .firstOrNull()
            ?.let { return it }

        candidates
            .asSequence()
            .map { value -> value.trim() }
            .filter { value -> value.isNotBlank() }
            .filterNot { value -> value.isLikelyBleSetupName() }
            .filterNot { value -> value.isLikelyBleAddress() }
            .firstOrNull()
            ?.let { value -> return value.humanizedModelName() }

        return fallback
    }

    fun setupMethodLabel(isQrSetup: Boolean): String {
        return if (isQrSetup) {
            "Secure QR setup"
        } else {
            "Manual BLE setup"
        }
    }

    private fun knownProductTitle(value: String): String? {
        val normalized = value
            .trim()
            .lowercase(Locale.US)
            .replace('-', '_')
            .replace('.', '_')

        return when {
            "dose_pro_4" in normalized -> "Dose Pro 4"
            "wrgb" in normalized -> "WRGB"
            "timer" in normalized -> "Timer"
            "cooling" in normalized -> "Cooling"
            "dosing" in normalized -> "Dosing"
            else -> null
        }
    }

    private fun String.humanizedModelName(): String {
        val raw = trim()
        if (!raw.contains('_') && !raw.contains('-')) return raw

        return raw
            .replace('-', '_')
            .split('_')
            .filter { part -> part.isNotBlank() }
            .joinToString(separator = " ") { part ->
                when {
                    part.all { char -> char.isDigit() } -> part
                    part.length <= 4 && part.any { char -> char.isDigit() } -> part.uppercase(Locale.US)
                    else -> part.replaceFirstChar { char ->
                        if (char.isLowerCase()) char.titlecase(Locale.US) else char.toString()
                    }
                }
            }
    }

    private fun String.isLikelyBleSetupName(): Boolean {
        return startsWith("AQL-SETUP-", ignoreCase = true)
    }

    private fun String.isLikelyBleAddress(): Boolean {
        return matches(Regex("(?i)^([0-9a-f]{2}:){5}[0-9a-f]{2}$"))
    }
}
