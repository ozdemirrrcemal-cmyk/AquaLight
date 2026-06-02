package com.aqua.aqualight.ui.tabs.devices.detail

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCatalog
import com.aqua.aqualight.data.devices.catalog.AquaDeviceDefinition
import com.aqua.aqualight.data.devices.catalog.AquaDeviceUiController
import com.aqua.aqualight.data.tanks.AquariumTankDataStoreManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DeviceRouterFragment : Fragment(R.layout.fragment_device_router) {

    private lateinit var devicesStore: DevicesDataStoreManager
    private lateinit var tankStore: AquariumTankDataStoreManager

    private var hasRouted = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        devicesStore = DevicesDataStoreManager.create(requireContext())
        tankStore = AquariumTankDataStoreManager(requireContext().applicationContext)

        if (hasRouted) return

        val deviceId = requireArguments().getLong(ARG_DEVICE_ID, INVALID_DEVICE_ID)

        if (deviceId <= 0L) {
            openUnsupportedDevice(
                title = DEFAULT_DEVICE_TITLE,
                message = "Device information could not be found."
            )
            return
        }

        routeDevice(deviceId)
    }

    private fun routeDevice(deviceId: Long) {
        hasRouted = true

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                showGlobalLoading(true)

                val device = devicesStore.devicesFlow
                    .first()
                    .firstOrNull { it.id == deviceId }

                if (!isAdded) return@launch

                if (device == null) {
                    openUnsupportedDevice(
                        title = "Device Not Found",
                        message = "This device is no longer available."
                    )
                    return@launch
                }

                val definition = AquaDeviceCatalog.findByType(device.deviceType)

                if (definition == null) {
                    openUnsupportedDevice(
                        title = device.productModel.ifBlank { "Unsupported Device" },
                        message = "This device is not supported by this app version."
                    )
                    return@launch
                }

                val tanks = tankStore.tanksFlow.first()

                val assignedTankName = device.tankId?.let { tankId ->
                    tanks.firstOrNull { it.id == tankId }?.name
                }.orEmpty()

                val deviceIp = requireArguments()
                    .getString(ARG_DEVICE_IP)
                    .orEmpty()
                    .ifBlank { device.ip }

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
                    deviceIp = deviceIp,
                    routerTitle = routerTitle,
                    controllerTitle = controllerTitle,
                    canEditDeviceName = assignedTankName.isBlank(),
                    userDeviceName = device.name,
                    defaultDeviceTitle = routerTitle,
                    definition = definition
                )
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
        canEditDeviceName: Boolean,
        userDeviceName: String,
        defaultDeviceTitle: String,
        definition: AquaDeviceDefinition
    ) {
        val popRouterOptions = navOptions {
            popUpTo(R.id.deviceRouterFragment) {
                inclusive = true
            }
        }

        when (definition.uiController) {
            AquaDeviceUiController.GENERIC_LIGHT -> {
                findNavController().navigate(
                    R.id.deviceLightFragment,
                    bundleOf(
                        ARG_DEVICE_ID to deviceId,
                        ARG_DEVICE_TITLE to controllerTitle
                    ),
                    popRouterOptions
                )
            }

            AquaDeviceUiController.GENERIC_DOSING,
            AquaDeviceUiController.CUSTOM_DOSING_4CH -> {
                findNavController().navigate(
                    R.id.deviceDosingFragment,
                    bundleOf(
                        ARG_DEVICE_ID to deviceId,
                        ARG_DEVICE_IP to deviceIp,
                        ARG_DEVICE_TITLE to controllerTitle,
                        ARG_CAN_EDIT_DEVICE_NAME to canEditDeviceName,
                        ARG_USER_DEVICE_NAME to userDeviceName,
                        ARG_DEFAULT_DEVICE_TITLE to defaultDeviceTitle
                    ),
                    popRouterOptions
                )
            }

            AquaDeviceUiController.GENERIC_TIMER,
            AquaDeviceUiController.CUSTOM_TIMER_MULTI_CONTROL,
            AquaDeviceUiController.CUSTOM_TIMER_SCENE_PRO -> {
                findNavController().navigate(
                    R.id.deviceTimerFragment,
                    bundleOf(
                        ARG_DEVICE_ID to deviceId,
                        ARG_DEVICE_IP to deviceIp,
                        ARG_DEVICE_TITLE to controllerTitle,
                        ARG_CAN_EDIT_DEVICE_NAME to canEditDeviceName,
                        ARG_USER_DEVICE_NAME to userDeviceName,
                        ARG_DEFAULT_DEVICE_TITLE to defaultDeviceTitle
                    ),
                    popRouterOptions
                )
            }

            AquaDeviceUiController.GENERIC_COOLING,
            AquaDeviceUiController.CUSTOM_COOLING_ADVANCED -> {
                findNavController().navigate(
                    R.id.deviceCoolingFragment,
                    bundleOf(
                        ARG_DEVICE_ID to deviceId,
                        ARG_DEVICE_IP to deviceIp
                    ),
                    popRouterOptions
                )
            }

            else -> {
                openUnsupportedDevice(
                    title = routerTitle,
                    message = "This device controller is not available in this app version."
                )
            }
        }
    }

    private fun openUnsupportedDevice(title: String, message: String) {
        findNavController().navigate(
            R.id.unsupportedDeviceFragment,
            bundleOf(
                ARG_DEVICE_TITLE to title,
                ARG_MESSAGE to message
            ),
            navOptions {
                popUpTo(R.id.deviceRouterFragment) {
                    inclusive = true
                }
            }
        )
    }

    private fun resolveRouterTitle(
        productModel: String,
        definition: AquaDeviceDefinition
    ): String {
        return productModel.ifBlank {
            definition.displayName.ifBlank { DEFAULT_DEVICE_TITLE }
        }
    }

    private fun resolveControllerTitle(
        assignedTankName: String,
        userDeviceName: String,
        fallbackDeviceTitle: String
    ): String {
        return assignedTankName.ifBlank {
            userDeviceName.ifBlank { fallbackDeviceTitle }
        }
    }

    private fun showGlobalLoading(show: Boolean) {
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