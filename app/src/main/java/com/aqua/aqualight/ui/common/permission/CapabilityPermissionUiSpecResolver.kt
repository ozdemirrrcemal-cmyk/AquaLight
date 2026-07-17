package com.aqua.aqualight.ui.common.permission

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.platform.permissions.AppCapability

/**
 * Single visual-language authority for the shared capability permission sheet.
 *
 * Feature screens provide only [AppCapability]. They never select icons, copy,
 * button labels or blocked-state visuals. Keeping this mapping beside the shared
 * sheet preserves one commercial UI contract across every permission flow.
 */
internal object CapabilityPermissionUiSpecResolver {

    fun resolve(
        capability: AppCapability,
        mode: CapabilityPermissionBottomSheet.Mode
    ): CapabilityPermissionUiSpec {
        val content = when (capability) {
            AppCapability.CAMERA_PHOTO -> CapabilityContent(
                iconRes = R.drawable.ic_permission_camera_photo,
                rationaleTitleRes = R.string.permission_camera_photo_rationale_title,
                rationaleMessageRes = R.string.permission_camera_photo_rationale_message,
                settingsTitleRes = R.string.permission_camera_photo_settings_title,
                settingsMessageRes = R.string.permission_camera_photo_settings_message
            )

            AppCapability.CAMERA_QR -> CapabilityContent(
                iconRes = R.drawable.ic_permission_qr_scan,
                rationaleTitleRes = R.string.permission_camera_qr_rationale_title,
                rationaleMessageRes = R.string.permission_camera_qr_rationale_message,
                settingsTitleRes = R.string.permission_camera_qr_settings_title,
                settingsMessageRes = R.string.permission_camera_qr_settings_message
            )

            AppCapability.BLE_SCAN -> CapabilityContent(
                iconRes = R.drawable.ic_permission_ble_scan,
                rationaleTitleRes = R.string.permission_ble_scan_rationale_title,
                rationaleMessageRes = R.string.permission_ble_scan_rationale_message,
                settingsTitleRes = R.string.permission_ble_scan_settings_title,
                settingsMessageRes = R.string.permission_ble_scan_settings_message
            )

            AppCapability.BLE_CONNECT -> CapabilityContent(
                iconRes = R.drawable.ic_permission_ble_connect,
                rationaleTitleRes = R.string.permission_ble_connect_rationale_title,
                rationaleMessageRes = R.string.permission_ble_connect_rationale_message,
                settingsTitleRes = R.string.permission_ble_connect_settings_title,
                settingsMessageRes = R.string.permission_ble_connect_settings_message
            )

            AppCapability.BLE_PROVISIONING -> CapabilityContent(
                iconRes = R.drawable.ic_permission_ble_provisioning,
                rationaleTitleRes = R.string.permission_ble_provisioning_rationale_title,
                rationaleMessageRes = R.string.permission_ble_provisioning_rationale_message,
                settingsTitleRes = R.string.permission_ble_provisioning_settings_title,
                settingsMessageRes = R.string.permission_ble_provisioning_settings_message
            )

            AppCapability.WIFI_SSID -> CapabilityContent(
                iconRes = R.drawable.ic_permission_wifi,
                rationaleTitleRes = R.string.permission_wifi_ssid_rationale_title,
                rationaleMessageRes = R.string.permission_wifi_ssid_rationale_message,
                settingsTitleRes = R.string.permission_wifi_ssid_settings_title,
                settingsMessageRes = R.string.permission_wifi_ssid_settings_message
            )

            AppCapability.NOTIFICATIONS -> CapabilityContent(
                iconRes = R.drawable.ic_permission_notifications,
                rationaleTitleRes = R.string.permission_notifications_rationale_title,
                rationaleMessageRes = R.string.permission_notifications_rationale_message,
                settingsTitleRes = R.string.permission_notifications_settings_title,
                settingsMessageRes = R.string.permission_notifications_settings_message
            )
        }

        return when (mode) {
            CapabilityPermissionBottomSheet.Mode.RATIONALE -> CapabilityPermissionUiSpec(
                iconRes = content.iconRes,
                titleRes = content.rationaleTitleRes,
                messageRes = content.rationaleMessageRes,
                primaryActionRes = R.string.permission_sheet_allow,
                statusBadgeRes = null
            )

            CapabilityPermissionBottomSheet.Mode.OPEN_SETTINGS -> CapabilityPermissionUiSpec(
                iconRes = content.iconRes,
                titleRes = content.settingsTitleRes,
                messageRes = content.settingsMessageRes,
                primaryActionRes = R.string.permission_sheet_open_settings,
                statusBadgeRes = R.drawable.ic_permission_blocked_badge
            )
        }
    }

    private data class CapabilityContent(
        @DrawableRes val iconRes: Int,
        @StringRes val rationaleTitleRes: Int,
        @StringRes val rationaleMessageRes: Int,
        @StringRes val settingsTitleRes: Int,
        @StringRes val settingsMessageRes: Int
    )
}

internal data class CapabilityPermissionUiSpec(
    @DrawableRes val iconRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val messageRes: Int,
    @StringRes val primaryActionRes: Int,
    @DrawableRes val statusBadgeRes: Int?
)
