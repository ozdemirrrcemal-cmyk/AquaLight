package com.aqua.aqualight.platform.permissions

/**
 * Product capabilities that may require one or more Android runtime permissions.
 *
 * Capabilities deliberately describe user intent rather than raw permission names so
 * the UI can present context-specific rationale while [PermissionPolicy] owns the API
 * level permission matrix.
 */
enum class AppCapability {
    CAMERA_PHOTO,
    CAMERA_QR,
    BLE_SCAN,
    BLE_CONNECT,
    BLE_PROVISIONING,
    WIFI_SSID,
    NOTIFICATIONS
}
