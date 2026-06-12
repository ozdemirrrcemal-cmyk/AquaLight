package com.aqua.aqualight.ui.tabs.devices.detail

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCatalog
import com.aqua.aqualight.data.devices.catalog.AquaDeviceControllerRoute
import com.aqua.aqualight.data.devices.catalog.AquaDeviceDefinition
import com.aqua.aqualight.data.devices.catalog.toControllerRoute
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DeviceRouterFragment : Fragment(R.layout.fragment_device_router) {

    private val args: DeviceRouterFragmentArgs by navArgs()

    private lateinit var devicesStore: DevicesDataStoreManager
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

        devicesStore =
            DevicesDataStoreManager.create(
                requireContext()
            )

        tankStore =
            AquariumTankDataStoreManager(
                requireContext().applicationContext
            )

        if (hasRouted) {
            return
        }

        val deviceId =
            args.deviceId

        if (deviceId <= 0L) {
            openUnsupportedDevice(
                title = DEFAULT_DEVICE_TITLE,
                message = "Device information could not be found."
            )
            return
        }

        routeDevice(
            deviceId = deviceId
        )
    }

    private fun routeDevice(
        deviceId: Long
    ) {
        hasRouted = true

        viewLifecycleOwner.lifecycleScope.launch {
            val device =
                devicesStore.devicesFlow
                    .first()
                    .firstOrNull { savedDevice ->
                        savedDevice.id == deviceId
                    }

            if (!isAdded) {
                return@launch
            }

            if (device == null) {
                openUnsupportedDevice(
                    title = "Device Not Found",
                    message = "This device is no longer available."
                )
                return@launch
            }

            val definition =
                AquaDeviceCatalog.findByType(
                    type = device.deviceType
                )

            if (definition == null) {
                openUnsupportedDevice(
                    title = device.productModel.ifBlank {
                        "Unsupported Device"
                    },
                    message = "This device is not supported by this app version."
                )
                return@launch
            }

            val routerTitle =
                resolveRouterTitle(
                    productModel = device.productModel,
                    definition = definition
                )

            val controllerTitle =
                args.deviceTitle.ifBlank {
                    resolveControllerTitleFromDevice(
                        device = device,
                        fallbackDeviceTitle = routerTitle
                    )
                }

            val resolvedIp =
                args.deviceIp.ifBlank {
                    device.ip
                }

            routeToController(
                deviceId = device.id,
                deviceIp = resolvedIp,
                routerTitle = routerTitle,
                controllerTitle = controllerTitle,
                definition = definition
            )
        }
    }

    private suspend fun resolveControllerTitleFromDevice(
        device: DevicesDataStoreManager.DeviceInfo,
        fallbackDeviceTitle: String
    ): String {
        val tanks =
            tankStore.tanksFlow.first()

        val assignedTankName =
            device.tankId?.let { tankId ->
                tanks.firstOrNull { tank ->
                    tank.id == tankId
                }?.name
            }.orEmpty()

        return resolveControllerTitle(
            assignedTankName = assignedTankName,
            userDeviceName = device.name,
            fallbackDeviceTitle = fallbackDeviceTitle
        )
    }

    private fun routeToController(
        deviceId: Long,
        deviceIp: String,
        routerTitle: String,
        controllerTitle: String,
        definition: AquaDeviceDefinition
    ) {
        when (definition.controllerType.toControllerRoute()) {
            AquaDeviceControllerRoute.LIGHT -> {
                findNavController().navigate(
                    DeviceRouterFragmentDirections.actionDeviceRouterFragmentToDeviceLightFragment(
                        deviceId = deviceId,
                        deviceTitle = controllerTitle
                    )
                )
            }

            AquaDeviceControllerRoute.DOSING -> {
                findNavController().navigate(
                    DeviceRouterFragmentDirections.actionDeviceRouterFragmentToDeviceDosingFragment(
                        deviceId = deviceId,
                        deviceIp = deviceIp,
                        deviceTitle = controllerTitle
                    )
                )
            }

            AquaDeviceControllerRoute.TIMER -> {
                findNavController().navigate(
                    DeviceRouterFragmentDirections.actionDeviceRouterFragmentToDeviceTimerFragment(
                        deviceId = deviceId,
                        deviceIp = deviceIp,
                        deviceTitle = controllerTitle
                    )
                )
            }

            AquaDeviceControllerRoute.COOLING -> {
                findNavController().navigate(
                    DeviceRouterFragmentDirections.actionDeviceRouterFragmentToDeviceCoolingFragment(
                        deviceId = deviceId,
                        deviceIp = deviceIp,
                        deviceTitle = controllerTitle
                    )
                )
            }

            AquaDeviceControllerRoute.UNSUPPORTED -> {
                openUnsupportedDevice(
                    title = routerTitle,
                    message = "This device controller is not supported by this app version."
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

    companion object {
        const val ARG_DEVICE_ID = "deviceId"
        const val ARG_DEVICE_IP = "deviceIp"
        const val ARG_DEVICE_TITLE = "deviceTitle"
        const val ARG_CAN_EDIT_DEVICE_NAME = "canEditDeviceName"
        const val ARG_USER_DEVICE_NAME = "userDeviceName"
        const val ARG_DEFAULT_DEVICE_TITLE = "defaultDeviceTitle"
        const val ARG_MESSAGE = "message"

        private const val DEFAULT_DEVICE_TITLE = "Device"
    }
}