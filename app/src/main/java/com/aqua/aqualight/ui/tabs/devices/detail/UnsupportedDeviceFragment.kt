package com.aqua.aqualight.ui.tabs.devices.detail

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentUnsupportedDeviceBinding
import androidx.navigation.fragment.navArgs

class UnsupportedDeviceFragment : Fragment(R.layout.fragment_unsupported_device) {

    private val args: UnsupportedDeviceFragmentArgs by navArgs()

    private var _binding: FragmentUnsupportedDeviceBinding? = null
    private val binding get() = _binding!!

    private val deviceTitle: String
        get() = args.deviceTitle.ifBlank { DEFAULT_DEVICE_TITLE }

    private val message: String
        get() = args.message.ifBlank { DEFAULT_MESSAGE }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentUnsupportedDeviceBinding.bind(view)

        binding.tvUnsupportedTitle.text = deviceTitle
        binding.tvUnsupportedMessage.text = message
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_DEVICE_TITLE = "deviceTitle"
        const val ARG_MESSAGE = "message"

        private const val DEFAULT_DEVICE_TITLE = "Device"
        private const val DEFAULT_MESSAGE = "This device is not available."
    }
}
