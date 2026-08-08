package com.aqua.aqualight.application.notifications

/** Stable extras used only by AquaLight's internal firmware notification pending intents. */
object DeviceFirmwareNotificationIntentContract {
    const val EXTRA_KIND = "EXTRA_DEVICE_FIRMWARE_NOTIFICATION_KIND"
    const val EXTRA_TARGET_VERSION = "EXTRA_DEVICE_FIRMWARE_TARGET_VERSION"

    fun parseKind(value: String?): DeviceFirmwareNotificationKind? {
        val normalized = value.orEmpty().trim()
        if (normalized.isBlank()) return null
        return runCatching {
            DeviceFirmwareNotificationKind.valueOf(normalized)
        }.getOrNull()
    }
}
