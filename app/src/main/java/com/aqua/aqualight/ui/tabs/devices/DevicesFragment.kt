package com.aqua.aqualight.ui.tabs.devices

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDevicesBinding

class DevicesFragment : Fragment(R.layout.fragment_devices) {

    private var _binding: FragmentDevicesBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDevicesBinding.bind(view)

        binding.btnScanDevices.setOnClickListener {
            // nav_graph’te action tanımladığını varsayıyorum:
            // action_devicesFragment_to_scanDevicesFragment
            findNavController().navigate(
                R.id.action_devicesFragment_to_scanDevicesFragment
            )
        }

        // TODO: Burada DataStore’dan seçili cihazı okuyup tvSelectedDevice’e yazacaksın
        // loadSelectedDevice()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}