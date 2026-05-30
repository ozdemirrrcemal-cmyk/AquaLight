package com.aqua.aqualight.ui.tabs.devices.detail

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.setPadding
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
import com.aqua.aqualight.ui.tabs.devices.detail.chrome.DeviceChromeHost
import com.aqua.aqualight.ui.tabs.devices.detail.chrome.DeviceHeaderAction
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.DeviceCoolingFragment
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.DeviceDosingFragment
import com.aqua.aqualight.ui.tabs.devices.detail.light.DeviceLightControllerFragment
import com.aqua.aqualight.ui.tabs.devices.detail.timer.DeviceTimerFragment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.widget.ImageView

class DeviceRouterFragment :
    Fragment(R.layout.fragment_device_router),
    DeviceChromeHost {

    private var _binding: FragmentDeviceRouterBinding? = null
    private val binding get() = _binding!!

    private lateinit var devicesStore: DevicesDataStoreManager
    private lateinit var tankStore: AquariumTankDataStoreManager

    private var routedDeviceId: Long? = null

    private var currentBackClick: (() -> Unit)? = null

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

        setDeviceHeader(
            title = DEFAULT_DEVICE_TITLE,
            actions = emptyList(),
            onBackClick = {
                findNavController().popBackStack()
            }
        )

        binding.tvSubtitle.visibility = View.GONE

        binding.btnBack.setOnClickListener {
            currentBackClick?.invoke()
                ?: findNavController().popBackStack()
        }

        val deviceId = requireArguments().getLong(
            ARG_DEVICE_ID,
            INVALID_DEVICE_ID
        )

        if (deviceId <= 0L) {
            showUnavailableState(
                title = DEFAULT_DEVICE_TITLE,
                message = "Device information could not be found."
            )
            return
        }

        routeDevice(
            deviceId = deviceId
        )
    }

    override fun setDeviceHeader(
        title: String,
        actions: List<DeviceHeaderAction>,
        onBackClick: (() -> Unit)?
    ) {
        if (_binding == null) {
            return
        }

        binding.tvTitle.text = title.ifBlank {
            DEFAULT_DEVICE_TITLE
        }

        binding.tvSubtitle.visibility = View.GONE

        currentBackClick = onBackClick ?: {
            findNavController().popBackStack()
        }

        renderHeaderActions(
            actions = actions
        )
    }

    private fun renderHeaderActions(
        actions: List<DeviceHeaderAction>
    ) = with(binding) {
        headerActionsContainer.removeAllViews()

        if (actions.isEmpty()) {
            headerActionsContainer.visibility = View.GONE
            return@with
        }

        headerActionsContainer.visibility = View.VISIBLE

        actions.forEach { action ->
            val button = ImageButton(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    HEADER_ACTION_SIZE_DP.dp(),
                    HEADER_ACTION_SIZE_DP.dp()
                )

                background = resolveSelectableBorderlessBackground()
                contentDescription = action.contentDescription
                scaleType = ImageView.ScaleType.CENTER
                setImageResource(action.iconRes)
                setPadding(HEADER_ACTION_PADDING_DP.dp())
                setColorFilter(
                    requireContext().getColorCompat(
                        android.R.attr.textColorSecondary
                    )
                )

                setOnClickListener {
                    action.onClick.invoke()
                }
            }

            headerActionsContainer.addView(button)
        }
    }

    private fun routeDevice(
        deviceId: Long
    ) {
        if (routedDeviceId == deviceId && currentControllerFragment() != null) {
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
                    title = device.productModel.ifBlank {
                        "Unsupported Device"
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

            val routerTitle = resolveRouterTitle(
                productModel = device.productModel,
                definition = definition
            )

            val controllerTitle = resolveControllerTitle(
                assignedTankName = assignedTankName,
                userDeviceName = device.name,
                fallbackDeviceTitle = routerTitle
            )

            setDeviceHeader(
                title = routerTitle,
                actions = emptyList(),
                onBackClick = {
                    findNavController().popBackStack()
                }
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
        }
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
        val controllerFragment = when (definition.uiController) {
            AquaDeviceUiController.GENERIC_LIGHT -> {
                DeviceLightControllerFragment.newInstance(
                    deviceId = deviceId,
                    deviceIp = deviceIp,
                    deviceTitle = controllerTitle,
                    defaultDeviceTitle = defaultDeviceTitle
                )
            }

            AquaDeviceUiController.GENERIC_TIMER,
            AquaDeviceUiController.CUSTOM_TIMER_MULTI_CONTROL,
            AquaDeviceUiController.CUSTOM_TIMER_SCENE_PRO -> {
                DeviceTimerFragment.newInstance(
                    deviceId = deviceId,
                    deviceIp = deviceIp,
                    deviceTitle = controllerTitle,
                    canEditDeviceName = canEditDeviceName,
                    userDeviceName = userDeviceName,
                    defaultDeviceTitle = defaultDeviceTitle
                )
            }

            AquaDeviceUiController.GENERIC_COOLING,
            AquaDeviceUiController.CUSTOM_COOLING_ADVANCED -> {
                DeviceCoolingFragment.newInstance(
                    deviceId = deviceId,
                    deviceIp = deviceIp
                )
            }

            AquaDeviceUiController.GENERIC_DOSING,
            AquaDeviceUiController.CUSTOM_DOSING_4CH -> {
                DeviceDosingFragment.newInstance(
                    deviceId = deviceId,
                    deviceIp = deviceIp,
                    deviceTitle = controllerTitle,
                    canEditDeviceName = canEditDeviceName,
                    userDeviceName = userDeviceName,
                    defaultDeviceTitle = defaultDeviceTitle
                )
            }

            AquaDeviceUiController.FULL_CONTROLLER,
            AquaDeviceUiController.CUSTOM_LIGHT_ADVANCED,
            AquaDeviceUiController.CUSTOM_LIGHT_MATRIX,
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
            setReorderingAllowed(true)
            replace(
                R.id.deviceControllerContainer,
                controllerFragment
            )
        }
    }

    private fun currentControllerFragment(): Fragment? {
        return childFragmentManager.findFragmentById(
            R.id.deviceControllerContainer
        )
    }

    private fun showUnavailableState(
        title: String,
        message: String
    ) {
        if (_binding == null) {
            return
        }

        setDeviceHeader(
            title = title,
            actions = emptyList(),
            onBackClick = {
                findNavController().popBackStack()
            }
        )

        currentControllerFragment()?.let { existingFragment ->
            childFragmentManager.commit {
                setReorderingAllowed(true)
                remove(existingFragment)
            }
        }

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

    private fun resolveSelectableBorderlessBackground(): android.graphics.drawable.Drawable? {
        val typedValue = android.util.TypedValue()

        requireContext().theme.resolveAttribute(
            android.R.attr.selectableItemBackgroundBorderless,
            typedValue,
            true
        )

        return androidx.appcompat.content.res.AppCompatResources.getDrawable(
            requireContext(),
            typedValue.resourceId
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
        currentBackClick = null
        _binding = null

        super.onDestroyView()
    }

    companion object {
        const val ARG_DEVICE_ID = "deviceId"
        const val ARG_DEVICE_IP = "deviceIp"

        private const val INVALID_DEVICE_ID = -1L
        private const val DEFAULT_DEVICE_TITLE = "Device"

        private const val HEADER_ACTION_SIZE_DP = 40
        private const val HEADER_ACTION_PADDING_DP = 9
    }
}