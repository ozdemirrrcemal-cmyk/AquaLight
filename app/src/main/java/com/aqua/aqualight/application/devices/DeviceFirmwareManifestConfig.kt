package com.aqua.aqualight.application.devices

import java.util.Locale

const val DEVICE_FIRMWARE_MANIFEST_ENV_PLACEHOLDER = "{env}"

val DEVICE_FIRMWARE_PRODUCT_ENVIRONMENTS: Set<String> = setOf(
    "light_wrgb_pro_elite",
    "light_rgb_pro_slim",
    "timer_relay_pro_2",
    "timer_relay_pro_4",
    "dosing_dose_pro_2",
    "dosing_dose_pro_4",
    "cooling_cool_pro_1f",
    "cooling_cool_pro_2f",
    "cooling_cool_pro_3f"
)

/**
 * Stable product-scoped manifest template shared by foreground OTA and background discovery.
 *
 * The placeholder is resolved only from authenticated/durable firmware productKey metadata. A
 * Dosing release therefore cannot become the manifest source for a Timer device.
 */
const val DEVICE_FIRMWARE_MANIFEST_URL =
    "https://raw.githubusercontent.com/ozdemirrrcemal-cmyk/AquaLight-OTA-Releases/" +
        "main/channels/stable/{env}/manifest-stable.json"

object DeviceFirmwareManifestUrlResolver {
    fun resolve(template: String, productKey: String): String {
        val source = template.trim()
        require(source.isNotBlank()) { "OTA manifest URL template is blank." }
        require(source == template) {
            "OTA manifest URL template must not contain surrounding whitespace."
        }
        val normalizedProductKey = productKey.trim()
        val environment = normalizedProductKey.lowercase(Locale.ROOT)
        require(
            productKey == normalizedProductKey &&
            normalizedProductKey == normalizedProductKey.uppercase(Locale.ROOT) &&
                environment in DEVICE_FIRMWARE_PRODUCT_ENVIRONMENTS
        ) {
            "Authenticated productKey is not an exact commercial OTA product."
        }

        val placeholderCount = source.windowed(
            size = DEVICE_FIRMWARE_MANIFEST_ENV_PLACEHOLDER.length,
            step = 1
        ).count { it == DEVICE_FIRMWARE_MANIFEST_ENV_PLACEHOLDER }
        if (placeholderCount == 0) {
            require(isExactProductManifestUrl(source, environment)) {
                "Explicit OTA manifest URL does not match the authenticated product."
            }
            return source
        }
        require(placeholderCount == 1) {
            "OTA manifest URL template must contain exactly one env placeholder."
        }
        val resolved = source.replace(DEVICE_FIRMWARE_MANIFEST_ENV_PLACEHOLDER, environment)
        require(isExactProductManifestUrl(resolved, environment)) {
            "Resolved OTA manifest URL does not identify the authenticated product channel."
        }
        return resolved
    }

    private fun isExactProductManifestUrl(url: String, environment: String): Boolean {
        val escapedEnvironment = Regex.escape(environment)
        val channel = Regex(
            "^https://raw\\.githubusercontent\\.com/ozdemirrrcemal-cmyk/" +
                "AquaLight-OTA-Releases/main/channels/(stable|beta|dev)/" +
                "$escapedEnvironment/" +
                "manifest-\\1\\.json$"
        )
        val immutable = Regex(
            "^https://github\\.com/ozdemirrrcemal-cmyk/AquaLight-OTA-Releases/" +
                "releases/download/($escapedEnvironment-v" +
                "(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*))/" +
                "manifest-\\1\\.json$"
        )
        return channel.matches(url) || immutable.matches(url)
    }
}
