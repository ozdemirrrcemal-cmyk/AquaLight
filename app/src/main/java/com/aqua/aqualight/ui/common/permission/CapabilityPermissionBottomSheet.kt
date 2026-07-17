package com.aqua.aqualight.ui.common.permission

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.BottomSheetCapabilityPermissionBinding
import com.aqua.aqualight.platform.permissions.AppCapability
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Process-safe rationale/settings sheet shared by every runtime-permission flow.
 *
 * The sheet accepts arguments only and returns the selected action through Fragment
 * Result. It intentionally has no constructor parameters or callback fields.
 */
class CapabilityPermissionBottomSheet : BottomSheetDialogFragment(
    R.layout.bottom_sheet_capability_permission
) {

    private var _binding: BottomSheetCapabilityPermissionBinding? = null
    private val binding get() = _binding!!
    private var resultSent = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = BottomSheetCapabilityPermissionBinding.bind(view)

        val capability = requireCapability()
        val mode = requireMode()
        val copy = copyFor(capability, mode)

        binding.tvPermissionTitle.setText(copy.titleRes)
        binding.tvPermissionMessage.setText(copy.messageRes)
        binding.btnPermissionPrimary.setText(
            if (mode == Mode.RATIONALE) {
                R.string.permission_sheet_allow
            } else {
                R.string.permission_sheet_open_settings
            }
        )

        binding.btnPermissionPrimary.setOnClickListener {
            sendResult(
                if (mode == Mode.RATIONALE) RESULT_ALLOW else RESULT_OPEN_SETTINGS
            )
            dismiss()
        }
        binding.btnPermissionCancel.setOnClickListener {
            sendResult(RESULT_CANCEL)
            dismiss()
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        sendResult(RESULT_CANCEL)
        super.onCancel(dialog)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun requireCapability(): AppCapability {
        return AppCapability.valueOf(requireArguments().getString(ARG_CAPABILITY).orEmpty())
    }

    private fun requireMode(): Mode {
        return Mode.valueOf(requireArguments().getString(ARG_MODE).orEmpty())
    }

    private fun sendResult(result: String) {
        if (resultSent) return
        resultSent = true
        parentFragmentManager.setFragmentResult(
            requireArguments().getString(ARG_REQUEST_KEY).orEmpty(),
            bundleOf(RESULT_KEY to result)
        )
    }

    private fun copyFor(capability: AppCapability, mode: Mode): Copy {
        return when (capability) {
            AppCapability.CAMERA_PHOTO -> Copy(
                rationaleTitle = R.string.permission_camera_photo_rationale_title,
                rationaleMessage = R.string.permission_camera_photo_rationale_message,
                settingsTitle = R.string.permission_camera_photo_settings_title,
                settingsMessage = R.string.permission_camera_photo_settings_message,
                mode = mode
            )
            AppCapability.CAMERA_QR -> Copy(
                rationaleTitle = R.string.permission_camera_qr_rationale_title,
                rationaleMessage = R.string.permission_camera_qr_rationale_message,
                settingsTitle = R.string.permission_camera_qr_settings_title,
                settingsMessage = R.string.permission_camera_qr_settings_message,
                mode = mode
            )
            AppCapability.BLE_SCAN -> Copy(
                rationaleTitle = R.string.permission_ble_scan_rationale_title,
                rationaleMessage = R.string.permission_ble_scan_rationale_message,
                settingsTitle = R.string.permission_ble_scan_settings_title,
                settingsMessage = R.string.permission_ble_scan_settings_message,
                mode = mode
            )
            AppCapability.BLE_CONNECT -> Copy(
                rationaleTitle = R.string.permission_ble_connect_rationale_title,
                rationaleMessage = R.string.permission_ble_connect_rationale_message,
                settingsTitle = R.string.permission_ble_connect_settings_title,
                settingsMessage = R.string.permission_ble_connect_settings_message,
                mode = mode
            )
            AppCapability.BLE_PROVISIONING -> Copy(
                rationaleTitle = R.string.permission_ble_provisioning_rationale_title,
                rationaleMessage = R.string.permission_ble_provisioning_rationale_message,
                settingsTitle = R.string.permission_ble_provisioning_settings_title,
                settingsMessage = R.string.permission_ble_provisioning_settings_message,
                mode = mode
            )
            AppCapability.WIFI_SSID -> Copy(
                rationaleTitle = R.string.permission_wifi_ssid_rationale_title,
                rationaleMessage = R.string.permission_wifi_ssid_rationale_message,
                settingsTitle = R.string.permission_wifi_ssid_settings_title,
                settingsMessage = R.string.permission_wifi_ssid_settings_message,
                mode = mode
            )
            AppCapability.NOTIFICATIONS -> Copy(
                rationaleTitle = R.string.permission_notifications_rationale_title,
                rationaleMessage = R.string.permission_notifications_rationale_message,
                settingsTitle = R.string.permission_notifications_settings_title,
                settingsMessage = R.string.permission_notifications_settings_message,
                mode = mode
            )
        }
    }

    private data class Copy(
        @StringRes val rationaleTitle: Int,
        @StringRes val rationaleMessage: Int,
        @StringRes val settingsTitle: Int,
        @StringRes val settingsMessage: Int,
        val mode: Mode
    ) {
        @get:StringRes
        val titleRes: Int
            get() = if (mode == Mode.RATIONALE) rationaleTitle else settingsTitle

        @get:StringRes
        val messageRes: Int
            get() = if (mode == Mode.RATIONALE) rationaleMessage else settingsMessage
    }

    enum class Mode {
        RATIONALE,
        OPEN_SETTINGS
    }

    companion object {
        const val RESULT_KEY = "capability_permission_result"
        const val RESULT_ALLOW = "allow"
        const val RESULT_OPEN_SETTINGS = "open_settings"
        const val RESULT_CANCEL = "cancel"

        private const val ARG_CAPABILITY = "arg_capability"
        private const val ARG_MODE = "arg_mode"
        private const val ARG_REQUEST_KEY = "arg_request_key"

        fun newInstance(
            capability: AppCapability,
            mode: Mode,
            requestKey: String
        ): CapabilityPermissionBottomSheet {
            return CapabilityPermissionBottomSheet().apply {
                arguments = bundleOf(
                    ARG_CAPABILITY to capability.name,
                    ARG_MODE to mode.name,
                    ARG_REQUEST_KEY to requestKey
                )
            }
        }
    }
}
