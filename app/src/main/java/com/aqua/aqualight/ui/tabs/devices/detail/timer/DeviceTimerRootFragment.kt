package com.aqua.aqualight.ui.tabs.devices.detail.timer

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentUnsupportedDeviceBinding

class DeviceTimerRootFragment : Fragment(R.layout.fragment_unsupported_device) {

    private val args: DeviceTimerRootFragmentArgs by navArgs()

    private var _binding: FragmentUnsupportedDeviceBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentUnsupportedDeviceBinding.bind(view)

        binding.tvUnsupportedTitle.text = args.deviceTitle.ifBlank { "Timer" }
        binding.tvUnsupportedMessage.text =
            "Timer root opened with deviceUid ${args.deviceUid}. WebSocket runtime controls will be connected after the common runtime layer lands."
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
