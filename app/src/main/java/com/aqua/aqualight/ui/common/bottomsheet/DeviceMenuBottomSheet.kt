package com.aqua.aqualight.ui.common.bottomsheet

import android.os.Bundle
import android.view.View
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.BottomSheetDeviceMenuBinding

class DeviceMenuBottomSheet : BottomSheetDialogFragment(R.layout.bottom_sheet_device_menu) {

    private var _binding: BottomSheetDeviceMenuBinding? = null
    private val binding get() = _binding!!

    var menuActionListener: ((String) -> Unit)? = null

    private var deviceName: String? = null
    private var aquaName: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = BottomSheetDeviceMenuBinding.bind(view)

        deviceName = arguments?.getString(ARG_DEVICE_NAME)
        aquaName = arguments?.getString(ARG_AQUA_NAME)

        // Başlık
        val titleText = when {
            !deviceName.isNullOrBlank() -> deviceName
            !aquaName.isNullOrBlank() -> aquaName
            else -> getString(R.string.device_menu_title_default)
        }
        binding.tvTitle.text = titleText

        // -------- ANA MENÜ --------
        binding.rowMainLight.setOnClickListener {
            notifyAction(ACTION_LIGHT)
        }
        binding.rowMainTimer.setOnClickListener {
            notifyAction(ACTION_TIMER)
        }
        binding.rowMainTemperature.setOnClickListener {
            notifyAction(ACTION_TEMPERATURE)
        }

        // -------- AYARLAR --------
        binding.rowSettingsWifi.setOnClickListener {
            notifyAction(ACTION_WIFI)
        }
        binding.rowSettingsTime.setOnClickListener {
            notifyAction(ACTION_TIME)
        }
        binding.rowSettingsGeneral.setOnClickListener {
            notifyAction(ACTION_GENERAL)
        }
        binding.rowSettingsPwm.setOnClickListener {
            notifyAction(ACTION_PWM)
        }
        binding.rowSettingsNet.setOnClickListener {
            notifyAction(ACTION_NET)
        }

        // -------- ARAÇLAR --------
        binding.rowToolsFilesystem.setOnClickListener {
            notifyAction(ACTION_FILESYSTEM)
        }
        binding.rowToolsReboot.setOnClickListener {
            notifyAction(ACTION_REBOOT)
        }
        binding.rowToolsInfo.setOnClickListener {
            notifyAction(ACTION_INFO)
        }
        binding.rowToolsAbout.setOnClickListener {
            notifyAction(ACTION_ABOUT)
        }

        // Kapatma butonu (varsa)
        binding.btnClose.setOnClickListener {
            dismiss()
        }
    }

    private fun notifyAction(action: String) {
        menuActionListener?.invoke(action)
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_DEVICE_NAME = "arg_device_name"
        private const val ARG_AQUA_NAME = "arg_aqua_name"

        // 🔹 Aksiyon sabitleri
        const val ACTION_LIGHT = "light"
        const val ACTION_TIMER = "timer"
        const val ACTION_TEMPERATURE = "temperature"

        const val ACTION_WIFI = "wifi"
        const val ACTION_TIME = "time"
        const val ACTION_GENERAL = "general"
        const val ACTION_PWM = "pwm"
        const val ACTION_NET = "net"

        const val ACTION_FILESYSTEM = "filesystem"
        const val ACTION_REBOOT = "reboot"
        const val ACTION_INFO = "info"
        const val ACTION_ABOUT = "about"

        fun newInstance(
            deviceName: String?,
            aquaName: String?
        ): DeviceMenuBottomSheet {
            val args = Bundle().apply {
                putString(ARG_DEVICE_NAME, deviceName)
                putString(ARG_AQUA_NAME, aquaName)
            }
            return DeviceMenuBottomSheet().apply {
                arguments = args
            }
        }
    }
}