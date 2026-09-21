package com.aqua.aqualight.ui.common.devicepresence

import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.ui.common.feedback.FeedbackBottomSheet
import com.aqua.aqualight.ui.navigation.AppRouteNavigator
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType

/**
 * Single commercial presentation path for device-access failures.
 *
 * Firmware compatibility failures keep the OTA rescue plane reachable even when a family control
 * surface cannot be opened. Other failures remain one-way informational feedback.
 */
object DeviceAccessFeedbackPresenter {

    fun show(
        fragment: Fragment,
        deviceUid: String,
        deviceTitle: String,
        reason: DeviceMenuUnavailableReason
    ) {
        if (!fragment.isAdded) return
        val feedback = DeviceMenuUnavailableMessageMapper.feedback(reason)
        val normalizedUid = deviceUid.trim()

        if (
            feedback.action != DeviceAccessFeedbackAction.OPEN_FIRMWARE_UPDATE ||
            normalizedUid.isBlank()
        ) {
            val safeDeviceTitle = deviceTitle.trim().ifBlank {
                fragment.getString(R.string.device_menu_default_title)
            }
            DialogManager.showInfoDialog(
                context = fragment.requireContext(),
                type = DialogType.WARNING,
                title = fragment.getString(feedback.titleRes),
                message = fragment.getString(
                    R.string.device_access_dialog_message,
                    safeDeviceTitle,
                    fragment.getString(feedback.messageRes)
                ),
                buttonTextResId = android.R.string.ok
            )
            return
        }

        val manager = fragment.parentFragmentManager
        val requestKey = REQUEST_PREFIX + normalizedUid.hashCode() + ":" + reason.name
        manager.setFragmentResultListener(
            requestKey,
            fragment.viewLifecycleOwner
        ) { _, result ->
            manager.clearFragmentResultListener(requestKey)
            val isPrimary =
                result.getString(FeedbackBottomSheet.RESULT_KEY) ==
                    FeedbackBottomSheet.RESULT_PRIMARY
            val isFirmwareAction =
                result.getString(FeedbackBottomSheet.RESULT_ACTION_ID) == ACTION_FIRMWARE_UPDATE
            if (isPrimary && isFirmwareAction) {
                AppRouteNavigator.openDeviceFirmwareUpdate(
                    navController = fragment.findNavController(),
                    deviceUid = normalizedUid
                )
            }
        }

        val safeDeviceTitle = deviceTitle.trim().ifBlank {
            fragment.getString(R.string.device_menu_default_title)
        }
        FeedbackBottomSheet.show(
            fragmentManager = manager,
            title = fragment.getString(feedback.titleRes),
            message = fragment.getString(
                R.string.device_access_dialog_message,
                safeDeviceTitle,
                fragment.getString(feedback.messageRes)
            ),
            primaryText = fragment.getString(R.string.device_settings_check_updates_action),
            cancelText = fragment.getString(R.string.cancel),
            tone = FeedbackBottomSheet.FeedbackTone.WARNING,
            requestKey = requestKey,
            actionId = ACTION_FIRMWARE_UPDATE
        )
    }

    private const val ACTION_FIRMWARE_UPDATE = "device_firmware_update"
    private const val REQUEST_PREFIX = "device_access_feedback:"
}
