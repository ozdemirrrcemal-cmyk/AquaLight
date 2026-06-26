package com.aqua.aqualight.ui.tabs.devices.detail

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import kotlinx.coroutines.launch

class DeviceRouterFragment : Fragment(R.layout.fragment_device_router) {

    private val args: DeviceRouterFragmentArgs by navArgs()
    private val viewModel: DeviceRouterViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeViewModel()
        viewModel.resolveRoute(deviceId = args.deviceId, deviceTitle = args.deviceTitle)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is DeviceRouterEvent.OpenDestination -> openUnsupportedDevice(
                            title = event.destination.title,
                            message = event.destination.message
                        )
                    }
                }
            }
        }
    }

    private fun openUnsupportedDevice(title: String, message: String) {
        if (!isAdded) return
        findNavController().navigate(
            DeviceRouterFragmentDirections.actionDeviceRouterFragmentToUnsupportedDeviceFragment(
                deviceTitle = title,
                message = message,
                deviceId = args.deviceId
            )
        )
    }

    companion object {
        const val ARG_DEVICE_ID = "deviceId"
        const val ARG_DEVICE_TITLE = "deviceTitle"
        const val ARG_MESSAGE = "message"
    }
}
