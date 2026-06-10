package com.aqua.aqualight.ui.tabs.devices.detail

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.devices.access.DeviceAccessGuard
import com.aqua.aqualight.data.devices.access.DeviceOpenResult
import com.aqua.aqualight.data.devices.catalog.AquaDeviceDefinition
import com.aqua.aqualight.data.devices.catalog.AquaDeviceUiController
import com.aqua.aqualight.data.tanks.AquariumTankDataStoreManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DeviceRouterFragment : Fragment(R.layout.fragment_device_router) {

    private lateinit var accessGuard: DeviceAccessGuard
    private lateinit var tankStore: AquariumTankDataStoreManager

    private var hasRouted = false

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        accessGuard = DeviceAccessGuard(requireContext())
        tankStore = AquariumTankDataStoreManager(
            requireContext().applicationContext
        )

        if (hasRouted) {
            return
        }

        val deviceId = requireArguments().getLong(
            ARG_DEVICE_ID,
            INVALID_DEVICE_ID
        )

        if (deviceId <= 0L) {
            openUnsupportedDevice(
                title = DEFAULT_DEVICE_TITLE,
                message = "Device information could not be found."
            )
            return
        }

        routeDevice(deviceId)
    }

    private fun routeDevice(
        deviceId: Long
    ) {
        hasRouted = true

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                showGlobalLoading(true)

                val preferredIp = requireArguments()
                    .getString(ARG_DEVICE_IP)
                    .orEmpty()

                when (
                    val openResult = accessGuard.resolveForOpen(
                        deviceId = deviceId,
                        preferredIp = preferredIp
                    )
                ) {
                    is DeviceOpenResult.Allowed -> {
                        if (!isAdded) {
                            return@launch
                        }

                        val device = openResult.device
                        val definition = openResult.definition

                        val tanks = tankStore.tanksFlow.first()

                        val assignedTankName = device.tankId?.let { tankId ->
                            tanks.firstOrNull { tank ->
                                tank.id == tankId
                            }?.name
                        }.orEmpty()

                        val routerTitle = resolveRouterTitle(
                            productModel = device.productModel,
                            definition = definition
                        )

                        val controllerTitle = resolveControllerTitle(
                            assignedTankName = assignedTankName,
                            userDeviceName = device.name,
                            fallbackDeviceTitle = routerTitle
                        )

                        routeToController(
                            deviceId = device.id,
                            deviceIp = openResult.ip,
                            routerTitle = routerTitle,
                            controllerTitle = controllerTitle,
                            definition = definition
                        )
                    }

                    is DeviceOpenResult.Offline -> {
                        openOfflineDevice(
                            title = openResult.device.name.ifBlank {
                                openResult.device.productModel.ifBlank {
                                    DEFAULT_DEVICE_TITLE
                                }
                            }
                        )
                    }

                    is DeviceOpenResult.Unsupported -> {
                        openUnsupportedDevice(
                            title = openResult.device.productModel.ifBlank {
                                "Unsupported Device"
                            },
                            message = "This device is not supported by this app version."
                        )
                    }

                    DeviceOpenResult.NotFound -> {
                        openUnsupportedDevice(
                            title = "Device Not Found",
                            message = "This device is no longer available."
                        )
                    }
                }
            } finally {
                showGlobalLoading(false)
            }
        }
    }

    private fun routeToController(
        deviceId: Long,
        deviceIp: String,
        routerTitle: String,
        controllerTitle: String,
        definition: AquaDeviceDefinition
    ) {
        when (definition.uiController) {
            AquaDeviceUiController.GENERIC_LIGHT -> {
                findNavController().navigate(
                    R.id.action_deviceRouterFragment_to_deviceLightFragment,
                    bundleOf(
                        ARG_DEVICE_ID to deviceId,
                        ARG_DEVICE_TITLE to controllerTitle
                    )
                )
            }

            AquaDeviceUiController.GENERIC_DOSING,
            AquaDeviceUiController.CUSTOM_DOSING_4CH -> {
                findNavController().navigate(
                    R.id.action_deviceRouterFragment_to_deviceDosingFragment,
                    bundleOf(
                        ARG_DEVICE_ID to deviceId,
                        ARG_DEVICE_IP to deviceIp,
                        ARG_DEVICE_TITLE to controllerTitle
                    )
                )
            }

            AquaDeviceUiController.GENERIC_TIMER,
            AquaDeviceUiController.CUSTOM_TIMER_MULTI_CONTROL,
            AquaDeviceUiController.CUSTOM_TIMER_SCENE_PRO -> {
                findNavController().navigate(
                    R.id.action_deviceRouterFragment_to_deviceTimerFragment,
                    bundleOf(
                        ARG_DEVICE_ID to deviceId,
                        ARG_DEVICE_IP to deviceIp,
                        ARG_DEVICE_TITLE to controllerTitle
                    )
                )
            }

            AquaDeviceUiController.GENERIC_COOLING,
            AquaDeviceUiController.CUSTOM_COOLING_ADVANCED -> {
                findNavController().navigate(
                    R.id.action_deviceRouterFragment_to_deviceCoolingFragment,
                    bundleOf(
                        ARG_DEVICE_ID to deviceId,
                        ARG_DEVICE_IP to deviceIp,
                        ARG_DEVICE_TITLE to controllerTitle
                    )
                )
            }

            else -> {
                openUnsupportedDevice(
                    title = routerTitle,
                    message = "This device controller will be added in the new professional navigation structure."
                )
            }
        }
    }


    private fun openOfflineDevice(
        title: String
    ) {
        findNavController().navigate(
            R.id.action_deviceRouterFragment_to_unsupportedDeviceFragment,
            bundleOf(
                ARG_DEVICE_TITLE to title,
                ARG_MESSAGE to getString(
                    R.string.device_offline_message
                )
            )
        )
    }

    private fun openUnsupportedDevice(
        title: String,
        message: String
    ) {
        findNavController().navigate(
            R.id.action_deviceRouterFragment_to_unsupportedDeviceFragment,
            bundleOf(
                ARG_DEVICE_TITLE to title,
                ARG_MESSAGE to message
            )
        )
    }

    private fun resolveRouterTitle(
        productModel: String,
        definition: AquaDeviceDefinition
    ): String {
        return productModel.ifBlank {
            definition.displayName.ifBlank {
                DEFAULT_DEVICE_TITLE
            }
        }
    }

    private fun resolveControllerTitle(
        assignedTankName: String,
        userDeviceName: String,
        fallbackDeviceTitle: String
    ): String {
        return assignedTankName.ifBlank {
            userDeviceName.ifBlank {
                fallbackDeviceTitle
            }
        }
    }

    private fun showGlobalLoading(
        show: Boolean
    ) {
        (activity as? BaseActivity)?.showLoading(show)
    }

    companion object {
        const val ARG_DEVICE_ID = "deviceId"
        const val ARG_DEVICE_IP = "deviceIp"
        const val ARG_DEVICE_TITLE = "deviceTitle"
        const val ARG_CAN_EDIT_DEVICE_NAME = "canEditDeviceName"
        const val ARG_USER_DEVICE_NAME = "userDeviceName"
        const val ARG_DEFAULT_DEVICE_TITLE = "defaultDeviceTitle"
        const val ARG_MESSAGE = "message"

        private const val INVALID_DEVICE_ID = -1L
        private const val DEFAULT_DEVICE_TITLE = "Device"
    }
}