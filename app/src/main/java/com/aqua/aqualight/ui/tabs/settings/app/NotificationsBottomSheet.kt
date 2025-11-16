package com.aqua.aqualight.ui.tabs.settings.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.DialogNotificationPermissionBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class NotificationsBottomSheet(
    private val type: PermissionType
) : BottomSheetDialogFragment(R.layout.dialog_notification_permission) {

    enum class PermissionType {
        NOTIFICATION,
        WIFI,
        LOCATION
    }

    private var _binding: DialogNotificationPermissionBinding? = null
    private val binding get() = _binding!!

    var onSettingsOpened: (() -> Unit)? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = DialogNotificationPermissionBinding.bind(view)

        with(binding) {
            when (type) {
                PermissionType.NOTIFICATION -> {
                    imgNotificationIcon.setImageResource(R.drawable.ic_notification)
                    tvDialogTitle.setText(R.string.notifications_permission_title)
                    tvDialogMessage.setText(R.string.notifications_permission_message_info)

                    layoutWifiBlock.visibility = View.VISIBLE
                    layoutAquariumBlock.visibility = View.VISIBLE

                    imgWifi.setImageResource(R.drawable.ic_wifi)
                    tvWifiMessage.setText(R.string.notifications_permission_message_wifi)

                    imgAquarium.setImageResource(R.drawable.ic_aquarium_notifications)
                    tvAquariumMessage.setText(R.string.notifications_permission_message_aquarium)

                    btnSettings.setText(R.string.notifications_permission_button_open_settings)
                }

                PermissionType.WIFI -> {
                    imgNotificationIcon.setImageResource(R.drawable.ic_wifi)
                    tvDialogTitle.setText(R.string.wifi_permission_title)
                    tvDialogMessage.setText(R.string.wifi_permission_message_info)

                    layoutWifiBlock.visibility = View.GONE
                    layoutAquariumBlock.visibility = View.GONE

                    btnSettings.setText(R.string.wifi_permission_button_open_settings)
                }

                PermissionType.LOCATION -> {
                    imgNotificationIcon.setImageResource(R.drawable.ic_location)
                    tvDialogTitle.setText(R.string.location_permission_title)
                    tvDialogMessage.setText(R.string.location_permission_message_info)

                    layoutWifiBlock.visibility = View.GONE
                    layoutAquariumBlock.visibility = View.GONE

                    btnSettings.setText(R.string.location_permission_button_open_settings)
                }
            }

            btnSettings.setOnClickListener {
                when (type) {
                    PermissionType.NOTIFICATION -> openNotificationSettings()
                    PermissionType.WIFI,
                    PermissionType.LOCATION -> openAppSettings()
                }
                onSettingsOpened?.invoke()
                dismiss()
            }
        }
    }

    private fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
        }
        startActivity(intent)
    }

    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", requireContext().packageName, null)
        )
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}