package com.aqua.aqualight.platform.permissions

/**
 * Product capabilities whose Android access requirements are centrally evaluated.
 *
 * Capabilities describe user intent rather than raw platform permission names so
 * screens remain independent from API-level access rules.
 */
enum class AppCapability {
    CAMERA_PHOTO,
    CAMERA_QR,
    BLE_SCAN,
    BLE_CONNECT,
    BLE_PROVISIONING,
    WIFI_SSID,
    NOTIFICATIONS,
    PRECISE_REMINDERS
}
