package com.aqua.aqualight.ui.tabs.devices.detail

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCatalog
import com.aqua.aqualight.data.devices.catalog.AquaDeviceDefinition
import com.aqua.aqualight.data.devices.catalog.AquaDeviceUiController
import com.aqua.aqualight.data.tanks.AquariumTankDataStoreManager
import com.aqua.aqualight.databinding.FragmentDeviceRouterBinding
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.DeviceCoolingFragment
import com.aqua.aqualight.ui.tabs.devices.detail.light.DeviceLightFragment
import com.aqua.aqualight.ui.tabs.devices.detail.timer.DeviceTimerFragment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DeviceRouterFragment : Fragment(R.layout.fragment_device_router) {

    private var _binding: FragmentDeviceRouterBinding? = null
    private val binding get() = _binding!!

    private lateinit var devicesStore: DevicesDataStoreManager
    private lateinit var tankStore: AquariumTankDataStoreManager

    private var routedDeviceId: Long? = null

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding = FragmentDeviceRouterBinding.bind(view)

        devicesStore = DevicesDataStoreManager.create(
            requireContext()
        )

        tankStore = AquariumTankDataStoreManager(
            requireContext().applicationContext
        )

        binding.tvSubtitle.visibility = View.GONE

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        val deviceId = requireArguments().getLong(
            ARG_DEVICE_ID,
            INVALID_DEVICE_ID
        )

        if (deviceId <= 0L) {
            showUnavailableState(
                title = "Device",
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
        if (routedDeviceId == deviceId) {
            return
        }

        routedDeviceId = deviceId

        viewLifecycleOwner.lifecycleScope.launch {
            val device = devicesStore.devicesFlow
                .first()
                .firstOrNull { savedDevice ->
                    savedDevice.id == deviceId
                }

            if (_binding == null) {
                return@launch
            }

            if (device == null) {
                showUnavailableState(
                    title = "Device Not Found",
                    message = "This device is no longer available."
                )
                return@launch
            }

            val definition = AquaDeviceCatalog.findByType(
                type = device.deviceType
            )

            if (definition == null) {
                showUnavailableState(
                    title = device.name.ifBlank {
                        device.productModel.ifBlank {
                            "Unsupported Device"
                        }
                    },
                    message = "This device is not supported by this app version."
                )
                return@launch
            }

            val tanks = tankStore.tanksFlow.first()

            val assignedTankName = device.tankId?.let { tankId ->
                tanks.firstOrNull { tank ->
                    tank.id == tankId
                }?.name
            }.orEmpty()

            val deviceIp = requireArguments()
                .getString(ARG_DEVICE_IP)
                .orEmpty()
                .ifBlank {
                    device.ip
                }

            val routerTitle = resolveDeviceOwnTitle(
                userDeviceName = device.name,
                productModel = device.productModel,
                definition = definition
            )

            val controllerTitle = resolveControllerTitle(
                assignedTankName = assignedTankName,
                deviceOwnTitle = routerTitle
            )

            binding.tvTitle.text = routerTitle
            binding.tvSubtitle.visibility = View.GONE

            routeToController(
                deviceId = device.id,
                deviceIp = deviceIp,
                routerTitle = routerTitle,
                controllerTitle = controllerTitle,
                definition = definition
            )
        }
    }

    private fun resolveDeviceOwnTitle(
        userDeviceName: String,
        productModel: String,
        definition: AquaDeviceDefinition
    ): String {
        return userDeviceName.ifBlank {
            productModel.ifBlank {
                definition.displayName.ifBlank {
                    "Device"
                }
            }
        }
    }

    private fun resolveControllerTitle(
        assignedTankName: String,
        deviceOwnTitle: String
    ): String {
        return assignedTankName.ifBlank {
            deviceOwnTitle
        }
    }

    private fun routeToController(
        deviceId: Long,
        deviceIp: String,
        routerTitle: String,
        controllerTitle: String,
        definition: AquaDeviceDefinition
    ) {
        val controllerFragment = when (definition.uiController) {
            AquaDeviceUiController.GENERIC_LIGHT -> {
                DeviceLightFragment.newInstance(
                    deviceId = deviceId
                )
            }

            AquaDeviceUiController.GENERIC_TIMER,
            AquaDeviceUiController.CUSTOM_TIMER_MULTI_CONTROL,
            AquaDeviceUiController.CUSTOM_TIMER_SCENE_PRO -> {
                DeviceTimerFragment.newInstance(
                    deviceId = deviceId,
                    deviceIp = deviceIp,
                    deviceTitle = controllerTitle
                )
            }

            AquaDeviceUiController.GENERIC_COOLING -> {
                DeviceCoolingFragment.newInstance(
                    deviceId = deviceId,
                    deviceIp = deviceIp
                )
            }

            AquaDeviceUiController.FULL_CONTROLLER,
            AquaDeviceUiController.CUSTOM_LIGHT_ADVANCED,
            AquaDeviceUiController.CUSTOM_LIGHT_MATRIX,
            AquaDeviceUiController.CUSTOM_COOLING_ADVANCED,
            AquaDeviceUiController.UNSUPPORTED -> {
                null
            }
        }

        if (controllerFragment == null) {
            showUnavailableState(
                title = routerTitle,
                message = "This device controller is not available in this app version."
            )
            return
        }

        childFragmentManager.commit {
            replace(
                R.id.deviceControllerContainer,
                controllerFragment
            )
        }
    }

    private fun showUnavailableState(
        title: String,
        message: String
    ) {
        if (_binding == null) {
            return
        }

        binding.tvTitle.text = title
        binding.tvSubtitle.visibility = View.GONE

        binding.deviceControllerContainer.removeAllViews()

        val context = requireContext()

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(
                32.dp(),
                32.dp(),
                32.dp(),
                32.dp()
            )
        }

        val titleView = TextView(context).apply {
            text = title
            gravity = Gravity.CENTER
            textSize = 22f
            setTextColor(
                requireContext().getColorCompat(
                    android.R.attr.textColorPrimary
                )
            )
        }

        val messageView = TextView(context).apply {
            text = message
            gravity = Gravity.CENTER
            textSize = 14f
            setPadding(
                0,
                12.dp(),
                0,
                0
            )
            setTextColor(
                requireContext().getColorCompat(
                    android.R.attr.textColorSecondary
                )
            )
        }

        container.addView(
            titleView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        container.addView(
            messageView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        binding.deviceControllerContainer.addView(
            container,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private fun android.content.Context.getColorCompat(
        attr: Int
    ): Int {
        val typedValue = android.util.TypedValue()

        theme.resolveAttribute(
            attr,
            typedValue,
            true
        )

        return typedValue.data
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_DEVICE_ID = "deviceId"
        const val ARG_DEVICE_IP = "deviceIp"

        private const val INVALID_DEVICE_ID = -1L
    }
}