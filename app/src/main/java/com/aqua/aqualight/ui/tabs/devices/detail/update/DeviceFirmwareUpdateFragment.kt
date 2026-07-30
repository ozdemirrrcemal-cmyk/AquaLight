package com.aqua.aqualight.ui.tabs.devices.detail.update

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceFirmwareUpdateBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader

/** Central full-screen firmware update route. Content will be designed separately. */
class DeviceFirmwareUpdateFragment : Fragment(R.layout.fragment_device_firmware_update) {

    private val args: DeviceFirmwareUpdateFragmentArgs by navArgs()

    private var _binding: FragmentDeviceFirmwareUpdateBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        require(args.deviceUid.isNotBlank()) {
            "Firmware update requires a non-blank device UID."
        }

        _binding = FragmentDeviceFirmwareUpdateBinding.bind(view)
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.device_firmware_update_title),
                onBackClick = {
                    findNavController().navigateUp()
                }
            )
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
