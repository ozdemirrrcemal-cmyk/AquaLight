package com.aqua.aqualight.ui.tabs.devices.detail

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
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
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.DeviceDosingFragment
import com.aqua.aqualight.ui.tabs.devices.detail.light.DeviceLightFragment
import com.aqua.aqualight.ui.tabs.devices.detail.light.DeviceLightManualFragment
import com.aqua.aqualight.ui.tabs.devices.detail.timer.DeviceTimerFragment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DeviceRouterFragment : Fragment(R.layout.fragment_device_router) {

    private var _binding: FragmentDeviceRouterBinding? = null
    private val binding get() = _binding!!

    private lateinit var devicesStore: DevicesDataStoreManager
    private lateinit var tankStore: AquariumTankDataStoreManager

    private var routedDeviceId: Long? = null

    private var activeRouterScreen: RouterScreen = RouterScreen.Controller
    private var activeLightDeviceId: Long? = null
    private var activeLightTitle: String = DEFAULT_DEVICE_TITLE

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

        showHeaderActions(
            sync = false,
            settings = false,
            more = false
        )

        setupHeaderClicks()
        setupSystemBackHandling()

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

    private fun setupHeaderClicks() = with(binding) {
        btnBack.setOnClickListener {
            handleBackAction()
        }

        btnHeaderSync.setOnClickListener {
            when (activeRouterScreen) {
                RouterScreen.LightOverview -> {
                    currentLightOverviewFragment()?.onHeaderSyncClick()
                }

                RouterScreen.LightManual -> {
                    currentLightManualFragment()?.onHeaderSyncClick()
                }

                RouterScreen.Controller -> Unit
            }
        }

        btnHeaderSettings.setOnClickListener {
            when (activeRouterScreen) {
                RouterScreen.LightOverview -> {
                    currentLightOverviewFragment()?.onHeaderSettingsClick()
                }

                RouterScreen.LightManual,
                RouterScreen.Controller -> Unit
            }
        }

        btnHeaderMore.setOnClickListener {
            when (activeRouterScreen) {
                RouterScreen.LightOverview -> {
                    currentLightOverviewFragment()?.onHeaderMoreClick()
                }

                RouterScreen.LightManual,
                RouterScreen.Controller -> Unit
            }
        }
    }

    private fun setupSystemBackHandling() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    handleBackAction()
                }
            }
        )
    }

    private fun handleBackAction() {
        when (activeRouterScreen) {
            RouterScreen.LightManual -> {
                openLightOverview()
            }

            RouterScreen.LightOverview,
            RouterScreen.Controller -> {
                findNavController().popBackStack()
            }
        }
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

            binding.tvTitle.text = routerTitle
            binding.tvSubtitle.visibility = View.GONE

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
                activeRouterScreen = RouterScreen.LightOverview
                activeLightDeviceId = deviceId
                activeLightTitle = routerTitle

                binding.tvTitle.text = routerTitle

                showHeaderActions(
                    sync = true,
                    settings = true,
                    more = true
                )

                DeviceLightFragment.newInstance(
                    deviceId = deviceId
                )
            }

            AquaDeviceUiController.GENERIC_TIMER,
            AquaDeviceUiController.CUSTOM_TIMER_MULTI_CONTROL,
            AquaDeviceUiController.CUSTOM_TIMER_SCENE_PRO -> {
                activeRouterScreen = RouterScreen.Controller
                activeLightDeviceId = null

                showHeaderActions(
                    sync = false,
                    settings = false,
                    more = false
                )

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
                activeRouterScreen = RouterScreen.Controller
                activeLightDeviceId = null

                showHeaderActions(
                    sync = false,
                    settings = false,
                    more = false
                )

                DeviceCoolingFragment.newInstance(
                    deviceId = deviceId,
                    deviceIp = deviceIp
                )
            }

            AquaDeviceUiController.GENERIC_DOSING,
            AquaDeviceUiController.CUSTOM_DOSING_4CH -> {
                activeRouterScreen = RouterScreen.Controller
                activeLightDeviceId = null

                showHeaderActions(
                    sync = false,
                    settings = false,
                    more = false
                )

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
                activeRouterScreen = RouterScreen.Controller
                activeLightDeviceId = null

                showHeaderActions(
                    sync = false,
                    settings = false,
                    more = false
                )

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

    fun openLightOverview() {
        val deviceId = activeLightDeviceId ?: routedDeviceId ?: return

        activeRouterScreen = RouterScreen.LightOverview

        binding.tvTitle.text = activeLightTitle.ifBlank {
            DEFAULT_DEVICE_TITLE
        }

        showHeaderActions(
            sync = true,
            settings = true,
            more = true
        )

        childFragmentManager.commit {
            replace(
                R.id.deviceControllerContainer,
                DeviceLightFragment.newInstance(
                    deviceId = deviceId
                )
            )
        }
    }

    fun openManualControl() {
        val deviceId = activeLightDeviceId ?: routedDeviceId ?: return

        activeRouterScreen = RouterScreen.LightManual

        binding.tvTitle.text = getString(
            R.string.light_manual_title
        )

        showHeaderActions(
            sync = true,
            settings = false,
            more = false
        )

        childFragmentManager.commit {
            replace(
                R.id.deviceControllerContainer,
                DeviceLightManualFragment.newInstance(
                    deviceId = deviceId
                )
            )
        }
    }

    private fun currentLightOverviewFragment(): DeviceLightFragment? {
        return childFragmentManager.findFragmentById(
            R.id.deviceControllerContainer
        ) as? DeviceLightFragment
    }

    private fun currentLightManualFragment(): DeviceLightManualFragment? {
        return childFragmentManager.findFragmentById(
            R.id.deviceControllerContainer
        ) as? DeviceLightManualFragment
    }

    private fun showHeaderActions(
        sync: Boolean,
        settings: Boolean,
        more: Boolean
    ) = with(binding) {
        val showAnyAction = sync || settings || more

        headerActions.visibility =
            if (showAnyAction) {
                View.VISIBLE
            } else {
                View.GONE
            }

        btnHeaderSync.visibility =
            if (sync) {
                View.VISIBLE
            } else {
                View.GONE
            }

        btnHeaderSettings.visibility =
            if (settings) {
                View.VISIBLE
            } else {
                View.GONE
            }

        btnHeaderMore.visibility =
            if (more) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    private fun showUnavailableState(
        title: String,
        message: String
    ) {
        if (_binding == null) {
            return
        }

        activeRouterScreen = RouterScreen.Controller
        activeLightDeviceId = null

        binding.tvTitle.text = title
        binding.tvSubtitle.visibility = View.GONE

        showHeaderActions(
            sync = false,
            settings = false,
            more = false
        )

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

    private enum class RouterScreen {
        Controller,
        LightOverview,
        LightManual
    }

    companion object {
        const val ARG_DEVICE_ID = "deviceId"
        const val ARG_DEVICE_IP = "deviceIp"

        private const val INVALID_DEVICE_ID = -1L
        private const val DEFAULT_DEVICE_TITLE = "Device"
    }
}