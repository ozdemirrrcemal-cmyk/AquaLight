package com.aqua.aqualight.ui.tabs.devices.add

import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentDeviceProvisioningProgressBinding
import com.aqua.aqualight.platform.permissions.AppCapability
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.permission.CapabilityPermissionCoordinator
import com.aqua.aqualight.ui.tabs.devices.DevicesFragmentDirections
import com.aqua.aqualight.ui.tabs.devices.route.DeviceRoute
import com.aqua.aqualight.ui.tabs.devices.route.DeviceRouteTarget
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import kotlinx.coroutines.launch

class DeviceProvisioningProgressFragment : Fragment(R.layout.fragment_device_provisioning_progress) {

    private val args: DeviceProvisioningProgressFragmentArgs by navArgs()
    private val viewModel: DeviceProvisioningProgressViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }

    private val permissionCoordinator = CapabilityPermissionCoordinator(this) { action ->
        when (action) {
            ACTION_START_PROVISIONING -> viewModel.startProvisioning()
        }
    }

    private var _binding: FragmentDeviceProvisioningProgressBinding? = null
    private val binding get() = _binding!!

    private var autoStartRequested = false
    private var wifiFailureReturned = false
    private var committingPreparedNavigation = false

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDeviceProvisioningProgressBinding.bind(view)

        setupHeader()
        setupBackHandling()
        setupActions()
        observeViewModel()
        observeEvents()

        viewModel.bind(args.sessionId)
        requestAutoStart(view)
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.device_provisioning_title),
                onBackClick = viewModel::requestExit
            )
        )
    }

    private fun setupBackHandling() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    viewModel.requestExit()
                }
            }
        )
    }

    private fun setupActions() {
        binding.btnStartProvisioning.setOnClickListener {
            if (viewModel.uiState.value.requiresFreshDeviceSelection) {
                viewModel.requestExit()
            } else {
                startProvisioningWithPermissionCheck()
            }
        }
    }

    private fun requestAutoStart(view: View) {
        if (autoStartRequested) return
        autoStartRequested = true
        view.post {
            if (_binding != null) startProvisioningWithPermissionCheck()
        }
    }

    private fun startProvisioningWithPermissionCheck() {
        permissionCoordinator.runWhenGranted(
            capability = AppCapability.BLE_PROVISIONING,
            actionToken = ACTION_START_PROVISIONING
        )
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::renderState)
            }
        }
    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is DeviceProvisioningProgressEvent.OpenAddedDeviceRoute -> {
                            openAddedDeviceRoute(event.route)
                        }
                        is DeviceProvisioningProgressEvent.ShowAddedDeviceUnavailable -> {
                            showAddedDeviceUnavailable(event)
                        }
                        DeviceProvisioningProgressEvent.ExitProvisioning -> {
                            val navController = findNavController()
                            val returnedToAdd = navController.popBackStack(
                                R.id.deviceAddFragment,
                                false
                            )
                            if (!returnedToAdd) {
                                navController.navigate(R.id.deviceAddFragment)
                            }
                        }
                        DeviceProvisioningProgressEvent.ShowCancellationFailed -> {
                            showCancellationFailed()
                        }
                    }
                }
            }
        }
    }

    private fun renderState(state: DeviceProvisioningProgressUiState) {
        if (_binding == null) return

        binding.tvTitle.text = state.title
        binding.tvMessage.text = state.message
        binding.tvDeviceName.text = state.deviceName
        binding.tvDeviceSerial.text = getString(
            R.string.device_provisioning_serial_format,
            state.deviceSerial
        )
        binding.tvBleAddress.text = getString(
            R.string.device_provisioning_ble_format,
            state.bleAddress
        )
        binding.tvWifiSsid.text = getString(
            R.string.device_provisioning_wifi_format,
            state.wifiSsid
        )
        binding.tvStepOne.text = stepText(
            R.string.device_provisioning_step_icon_complete,
            state.stepOne
        )
        binding.tvStepTwo.text = stepText(
            R.string.device_provisioning_step_icon_complete,
            state.stepTwo
        )
        binding.tvStepThree.text = stepText(state.currentStepIconRes(), state.stepThree)
        binding.btnStartProvisioning.isVisible = state.canStart && !state.isCancelling
        binding.btnStartProvisioning.isEnabled = state.canStart && !state.isCancelling
        binding.btnStartProvisioning.text = state.buttonText
        binding.btnStartProvisioning.alpha = if (state.canStart) 1f else 0.45f
        binding.progressBar.isVisible = state.showProgress

        val wifiFailure = state.wifiCredentialFailure
        if (!wifiFailureReturned && wifiFailure != null) {
            wifiFailureReturned = true
            returnToWifiCredentials(wifiFailure)
        }
    }

    private fun showCancellationFailed() {
        DialogManager.showInfoDialog(
            context = requireContext(),
            type = DialogType.ERROR,
            title = getString(R.string.device_provisioning_cancel_failed_title),
            message = getString(R.string.device_provisioning_cancel_failed_message)
        )
    }

    private fun stepText(@StringRes iconRes: Int, text: String): String = getString(
        R.string.device_provisioning_step_with_icon,
        getString(iconRes),
        text
    )

    @StringRes
    private fun DeviceProvisioningProgressUiState.currentStepIconRes(): Int = when {
        canStart -> R.string.device_provisioning_step_icon_attention
        showProgress -> R.string.device_provisioning_step_icon_progress
        else -> R.string.device_provisioning_step_icon_complete
    }

    private fun returnToWifiCredentials(failure: DeviceProvisioningWifiCredentialFailure) {
        val navController = findNavController()
        val previousEntry = navController.previousBackStackEntry ?: run {
            navController.navigateUp()
            return
        }

        previousEntry.savedStateHandle[DeviceWifiProvisioningResult.KEY_FAILURE_MESSAGE] =
            failure.message
        previousEntry.savedStateHandle[DeviceWifiProvisioningResult.KEY_FAILURE_FIELD] =
            failure.field.toResultField()
        navController.popBackStack()
    }

    private fun DeviceProvisioningWifiCredentialField.toResultField(): String = when (this) {
        DeviceProvisioningWifiCredentialField.SSID -> DeviceWifiProvisioningResult.FIELD_SSID
        DeviceProvisioningWifiCredentialField.PASSWORD ->
            DeviceWifiProvisioningResult.FIELD_PASSWORD
    }

    private fun openAddedDeviceRoute(route: DeviceRoute) {
        if (!isAdded || _binding == null) {
            viewModel.onDeviceNavigationFinished(route.deviceUid, committed = false)
            return
        }

        val directions = route.toDevicesDestination()
        val navController = findNavController()
        var committed = false
        committingPreparedNavigation = true
        try {
            val returnedToDevices = navController.popBackStack(R.id.devicesFragment, false)
            val devicesHostReady =
                returnedToDevices && navController.currentDestination?.id == R.id.devicesFragment
            if (!devicesHostReady) return

            navController.navigate(directions)
            committed = true
        } finally {
            viewModel.onDeviceNavigationFinished(route.deviceUid, committed)
            committingPreparedNavigation = false
        }
    }

    private fun DeviceRoute.toDevicesDestination(): NavDirections = when (target) {
        DeviceRouteTarget.LIGHT_ROOT ->
            DevicesFragmentDirections.actionDevicesFragmentToDeviceLightRootFragment(
                deviceUid = deviceUid
            )
        DeviceRouteTarget.DOSING_ROOT ->
            DevicesFragmentDirections.actionDevicesFragmentToDeviceDosingRootFragment(
                deviceUid = deviceUid
            )
        DeviceRouteTarget.TIMER_ROOT ->
            DevicesFragmentDirections.actionDevicesFragmentToDeviceTimerRootFragment(
                deviceUid = deviceUid
            )
        DeviceRouteTarget.COOLING_ROOT ->
            DevicesFragmentDirections.actionDevicesFragmentToDeviceCoolingRootFragment(
                deviceUid = deviceUid
            )
        DeviceRouteTarget.UNSUPPORTED ->
            DevicesFragmentDirections.actionDevicesFragmentToUnsupportedDeviceFragment(
                deviceTitle = unsupportedTitle.ifBlank {
                    getString(R.string.device_menu_default_title)
                },
                message = messageRes.takeIf { it != 0 }
                    ?.let { getString(it) }
                    .orEmpty(),
                deviceUid = deviceUid
            )
    }

    private fun showAddedDeviceUnavailable(
        event: DeviceProvisioningProgressEvent.ShowAddedDeviceUnavailable
    ) {
        val baseActivity = activity as? BaseActivity
        val navController = findNavController()
        navController.popBackStack(R.id.devicesFragment, false)
        baseActivity?.showDeviceOfflineDialog(
            deviceTitle = event.title,
            messageRes = event.messageRes
        )
    }

    override fun onDestroyView() {
        if (!committingPreparedNavigation) {
            viewModel.onNavigationHostDestroyed()
        }
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val ACTION_START_PROVISIONING = "start_device_provisioning"
    }
}
