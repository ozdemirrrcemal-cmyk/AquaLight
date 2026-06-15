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
import com.aqua.aqualight.data.devices.routing.DeviceRouterController
import com.aqua.aqualight.data.devices.routing.DeviceRouterDestination
import kotlinx.coroutines.launch

class DeviceRouterFragment : Fragment(R.layout.fragment_device_router) {

    private val args: DeviceRouterFragmentArgs by navArgs()
    private val viewModel: DeviceRouterViewModel by viewModels()

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        observeViewModel()

        viewModel.resolveRoute(
            deviceId = args.deviceId,
            deviceTitle = args.deviceTitle
        )
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                launch {
                    viewModel.events.collect { event ->
                        handleEvent(
                            event = event
                        )
                    }
                }
            }
        }
    }

    private fun handleEvent(
        event: DeviceRouterEvent
    ) {
        when (event) {
            is DeviceRouterEvent.OpenDestination -> {
                openDestination(
                    destination = event.destination
                )
            }
        }
    }

    private fun openDestination(
        destination: DeviceRouterDestination
    ) {
        if (!isAdded) {
            return
        }

        when (destination) {
            is DeviceRouterDestination.Controller -> {
                routeToController(
                    destination = destination
                )
            }

            is DeviceRouterDestination.Unsupported -> {
                openUnsupportedDevice(
                    title = destination.title,
                    message = destination.message
                )
            }
        }
    }

    private fun routeToController(
        destination: DeviceRouterDestination.Controller
    ) {
        val navController = findNavController()

        when (destination.controller) {
            DeviceRouterController.LIGHT -> {
                navController.navigate(
                    DeviceRouterFragmentDirections.actionDeviceRouterFragmentToDeviceLightFragment(
                        deviceId = destination.deviceId,
                        deviceTitle = destination.deviceTitle
                    )
                )
            }

            DeviceRouterController.DOSING -> {
                navController.navigate(
                    DeviceRouterFragmentDirections.actionDeviceRouterFragmentToDeviceDosingFragment(
                        deviceId = destination.deviceId,
                        deviceTitle = destination.deviceTitle
                    )
                )
            }

            DeviceRouterController.TIMER -> {
                navController.navigate(
                    DeviceRouterFragmentDirections.actionDeviceRouterFragmentToDeviceTimerFragment(
                        deviceId = destination.deviceId,
                        deviceTitle = destination.deviceTitle
                    )
                )
            }

            DeviceRouterController.COOLING -> {
                navController.navigate(
                    DeviceRouterFragmentDirections.actionDeviceRouterFragmentToDeviceCoolingFragment(
                        deviceId = destination.deviceId,
                        deviceTitle = destination.deviceTitle
                    )
                )
            }
        }
    }

    private fun openUnsupportedDevice(
        title: String,
        message: String
    ) {
        findNavController().navigate(
            DeviceRouterFragmentDirections.actionDeviceRouterFragmentToUnsupportedDeviceFragment(
                deviceTitle = title,
                message = message
            )
        )
    }

    companion object {
        const val ARG_DEVICE_ID = "deviceId"
        const val ARG_DEVICE_TITLE = "deviceTitle"
        const val ARG_CAN_EDIT_DEVICE_NAME = "canEditDeviceName"
        const val ARG_USER_DEVICE_NAME = "userDeviceName"
        const val ARG_DEFAULT_DEVICE_TITLE = "defaultDeviceTitle"
        const val ARG_MESSAGE = "message"
    }
}
