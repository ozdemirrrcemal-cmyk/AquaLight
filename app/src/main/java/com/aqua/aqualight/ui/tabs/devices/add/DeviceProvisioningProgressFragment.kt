package com.aqua.aqualight.ui.tabs.devices.add

import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
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
import com.aqua.aqualight.databinding.FragmentDeviceProvisioningProgressBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.devices.DevicesFragmentDirections
import com.aqua.aqualight.ui.tabs.devices.route.DeviceRoute
import com.aqua.aqualight.ui.tabs.devices.route.DeviceRouteTarget
import kotlinx.coroutines.launch

class DeviceProvisioningProgressFragment : Fragment(R.layout.fragment_device_provisioning_progress) {

    private val args: DeviceProvisioningProgressFragmentArgs by navArgs()
    private val viewModel: DeviceProvisioningProgressViewModel by viewModels()
    private val permissionController = DeviceAddPermissionController()

    private var _binding: FragmentDeviceProvisioningProgressBinding? = null
    private val binding get() = _binding!!

    private var autoStartRequested = false
    private var wifiFailureReturned = false

    private val blePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (permissionController.hasBlePermissionsFromResult(requireContext(), result)) {
            viewModel.startProvisioning()
        } else {
            viewModel.onBlePermissionDenied()
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDeviceProvisioningProgressBinding.bind(view)

        setupHeader()
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
                onBackClick = {
                    findNavController().navigateUp()
                }
            )
        )
    }

    private fun setupActions() {
        binding.btnStartProvisioning.setOnClickListener {
            startProvisioningWithPermissionCheck()
        }
    }

    private fun requestAutoStart(view: View) {
        if (autoStartRequested) return
        autoStartRequested = true
        view.post {
            if (_binding != null) {
                startProvisioningWithPermissionCheck()
            }
        }
    }

    private fun startProvisioningWithPermissionCheck() {
        if (permissionController.hasBlePermissions(requireContext())) {
            viewModel.startProvisioning()
            return
        }

        blePermissionLauncher.launch(
            permissionController.blePermissions()
        )
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderState(state)
                }
            }
        }
    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is DeviceProvisioningProgressEvent.OpenAddedDevice -> {
                            openAddedDevice(event.route)
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
        binding.tvStepOne.text = "✓ ${state.stepOne}"
        binding.tvStepTwo.text = "✓ ${state.stepTwo}"
        binding.tvStepThree.text = "${state.currentStepIcon()} ${state.stepThree}"
        binding.btnStartProvisioning.isVisible = state.canStart
        binding.btnStartProvisioning.isEnabled = state.canStart
        binding.btnStartProvisioning.text = state.buttonText
        binding.btnStartProvisioning.alpha = if (state.canStart) 1f else 0.45f
        binding.progressBar.isVisible = state.showProgress

        if (!wifiFailureReturned && state.shouldReturnToWifiCredentials()) {
            wifiFailureReturned = true
            returnToWifiCredentials(state)
        }
    }

    private fun DeviceProvisioningProgressUiState.currentStepIcon(): String {
        return when {
            canStart -> "!"
            showProgress -> "●"
            else -> "✓"
        }
    }

    private fun DeviceProvisioningProgressUiState.shouldReturnToWifiCredentials(): Boolean {
        return stepThree == getString(R.string.device_provisioning_step_wifi_failed)
    }

    private fun returnToWifiCredentials(state: DeviceProvisioningProgressUiState) {
        val navController = findNavController()
        val previousEntry = navController.previousBackStackEntry ?: run {
            navController.navigateUp()
            return
        }

        previousEntry.savedStateHandle[DeviceWifiProvisioningResult.KEY_FAILURE_MESSAGE] =
            state.toWifiCredentialFailureMessage()
        previousEntry.savedStateHandle[DeviceWifiProvisioningResult.KEY_FAILURE_FIELD] =
            state.toWifiCredentialFailureField()

        navController.popBackStack()
    }

    private fun DeviceProvisioningProgressUiState.toWifiCredentialFailureMessage(): String {
        return when (message) {
            getString(R.string.device_provisioning_status_wifi_network_not_found_message) ->
                getString(R.string.device_wifi_network_not_found_error)
            getString(R.string.device_provisioning_status_wifi_timeout_message) ->
                getString(R.string.device_wifi_connection_timeout_error)
            getString(R.string.device_provisioning_status_wifi_router_rejected_message) ->
                getString(R.string.device_wifi_router_rejected_error)
            getString(R.string.device_provisioning_status_wifi_auth_failed_message) ->
                getString(R.string.device_wifi_password_incorrect_error)
            else -> getString(R.string.device_wifi_provisioning_failed_error)
        }
    }

    private fun DeviceProvisioningProgressUiState.toWifiCredentialFailureField(): String {
        return when (message) {
            getString(R.string.device_provisioning_status_wifi_network_not_found_message) ->
                DeviceWifiProvisioningResult.FIELD_SSID
            else -> DeviceWifiProvisioningResult.FIELD_PASSWORD
        }
    }

    private fun openAddedDevice(route: DeviceRoute) {
        val navController = findNavController()

        navController.popBackStack(
            R.id.devicesFragment,
            false
        )

        navController.navigate(
            route.toDevicesDestination()
        )
    }

    private fun DeviceRoute.toDevicesDestination(): NavDirections {
        return when (target) {
            DeviceRouteTarget.LIGHT_ROOT -> {
                DevicesFragmentDirections.actionDevicesFragmentToDeviceLightRootFragment(
                    deviceUid = deviceUid,
                    deviceTitle = title
                )
            }

            DeviceRouteTarget.DOSING_ROOT -> {
                DevicesFragmentDirections.actionDevicesFragmentToDeviceDosingRootFragment(
                    deviceUid = deviceUid,
                    deviceTitle = title
                )
            }

            DeviceRouteTarget.TIMER_ROOT -> {
                DevicesFragmentDirections.actionDevicesFragmentToDeviceTimerRootFragment(
                    deviceUid = deviceUid,
                    deviceTitle = title
                )
            }

            DeviceRouteTarget.COOLING_ROOT -> {
                DevicesFragmentDirections.actionDevicesFragmentToDeviceCoolingRootFragment(
                    deviceUid = deviceUid,
                    deviceTitle = title
                )
            }

            DeviceRouteTarget.UNSUPPORTED -> {
                DevicesFragmentDirections.actionDevicesFragmentToUnsupportedDeviceFragment(
                    deviceTitle = title.ifBlank { getString(R.string.device_wifi_default_device_name) },
                    message = message,
                    deviceUid = deviceUid
                )
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
