package com.aqua.aqualight.ui.tabs.devices

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceDashboardBinding
import com.aqua.aqualight.ui.common.bottomsheet.DeviceMenuBottomSheet
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType

class DeviceDashboardFragment : Fragment(R.layout.fragment_device_dashboard) {

    private var _binding: FragmentDeviceDashboardBinding? = null
    private val binding get() = _binding!!

    // 🔹 Artık SafeArgs yok, verileri direkt Bundle’dan alıyoruz
    private val deviceId: Long by lazy { requireArguments().getLong("deviceId") }
    private val deviceName: String by lazy { requireArguments().getString("deviceName").orEmpty() }
    private val deviceIp: String by lazy { requireArguments().getString("deviceIp").orEmpty() }
    private val aquaName: String by lazy { requireArguments().getString("aquaName").orEmpty() }
    private val serial: String by lazy { requireArguments().getString("serial").orEmpty() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDeviceDashboardBinding.bind(view)

        // Başlık: cihaz adı (boşsa aquaName, o da boşsa "Device")
        val title = deviceName.ifBlank { aquaName.ifBlank { "Device" } }
        binding.tvDeviceName.text = title

        // Alt başlık: AquaName + IP
        binding.tvDeviceSubtitle.text =
            getString(R.string.device_dashboard_subtitle_format, aquaName, deviceIp)

        // Geri tuşu
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        // Menü butonu – bottom sheet aç
        binding.btnMenu.setOnClickListener {
            showDeviceMenu()
        }

        // İstersen kartın tamamına tıklayınca da menü aç
        binding.cardMain.setOnClickListener {
            showDeviceMenu()
        }
    }

    private fun showDeviceMenu() {
        val sheet = DeviceMenuBottomSheet.newInstance(
            deviceName = deviceName,
            aquaName = aquaName
        )
        sheet.menuActionListener = { action ->
            handleMenuAction(action)
        }
        sheet.show(childFragmentManager, "DeviceMenuBottomSheet")
    }

    private fun handleMenuAction(action: String) {
        when (action) {
            DeviceMenuBottomSheet.ACTION_LIGHT -> {
                DialogManager.showInfoDialog(
                    context = requireContext(),
                    type = DialogType.INFO,
                    title = getString(R.string.device_menu_light_title),
                    message = getString(R.string.device_menu_light_message)
                )
                // TODO: findNavController().navigate(R.id.deviceLightFragment, ...)
            }

            DeviceMenuBottomSheet.ACTION_TIMER -> {
                DialogManager.showInfoDialog(
                    context = requireContext(),
                    type = DialogType.INFO,
                    title = getString(R.string.device_menu_timer_title),
                    message = getString(R.string.device_menu_timer_message)
                )
            }

            DeviceMenuBottomSheet.ACTION_TEMPERATURE -> {
                DialogManager.showInfoDialog(
                    context = requireContext(),
                    type = DialogType.INFO,
                    title = getString(R.string.device_menu_temperature_title),
                    message = getString(R.string.device_menu_temperature_message)
                )
            }

            DeviceMenuBottomSheet.ACTION_WIFI -> {
                DialogManager.showInfoDialog(
                    context = requireContext(),
                    type = DialogType.INFO,
                    title = getString(R.string.device_menu_wifi_title),
                    message = getString(R.string.device_menu_wifi_message)
                )
            }

            DeviceMenuBottomSheet.ACTION_TIME -> {
                DialogManager.showInfoDialog(
                    context = requireContext(),
                    type = DialogType.INFO,
                    title = getString(R.string.device_menu_time_title),
                    message = getString(R.string.device_menu_time_message)
                )
            }

            DeviceMenuBottomSheet.ACTION_GENERAL -> {
                DialogManager.showInfoDialog(
                    context = requireContext(),
                    type = DialogType.INFO,
                    title = getString(R.string.device_menu_general_title),
                    message = getString(R.string.device_menu_general_message)
                )
            }

            DeviceMenuBottomSheet.ACTION_PWM -> {
                DialogManager.showInfoDialog(
                    context = requireContext(),
                    type = DialogType.INFO,
                    title = getString(R.string.device_menu_pwm_title),
                    message = getString(R.string.device_menu_pwm_message)
                )
            }

            DeviceMenuBottomSheet.ACTION_NET -> {
                DialogManager.showInfoDialog(
                    context = requireContext(),
                    type = DialogType.INFO,
                    title = getString(R.string.device_menu_net_title),
                    message = getString(R.string.device_menu_net_message)
                )
            }

            DeviceMenuBottomSheet.ACTION_FILESYSTEM -> {
                DialogManager.showInfoDialog(
                    context = requireContext(),
                    type = DialogType.INFO,
                    title = getString(R.string.device_menu_filesystem_title),
                    message = getString(R.string.device_menu_filesystem_message)
                )
            }

            DeviceMenuBottomSheet.ACTION_REBOOT -> {
                DialogManager.showConfirmDialog(
                    context = requireContext(),
                    type = DialogType.WARNING,
                    title = getString(R.string.device_menu_reboot_title),
                    message = getString(R.string.device_menu_reboot_message),
                    onConfirm = {
                        // TODO: Buraya ESP32 reboot endpoint çağrısını koy
                        DialogManager.showInfoDialog(
                            context = requireContext(),
                            type = DialogType.SUCCESS,
                            title = getString(R.string.device_menu_reboot_done_title),
                            message = getString(R.string.device_menu_reboot_done_message),
                            autoDismissMillis = 1500L
                        )
                    },
                    onCancel = { /* boş bırakabilirsin */ }
                )
            }

            DeviceMenuBottomSheet.ACTION_INFO -> {
                DialogManager.showInfoDialog(
                    context = requireContext(),
                    type = DialogType.INFO,
                    title = getString(R.string.device_menu_info_title),
                    message = getString(R.string.device_menu_info_message)
                )
            }

            DeviceMenuBottomSheet.ACTION_ABOUT -> {
                DialogManager.showInfoDialog(
                    context = requireContext(),
                    type = DialogType.INFO,
                    title = getString(R.string.device_menu_about_title),
                    message = getString(R.string.device_menu_about_message)
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}