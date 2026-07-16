package com.aqua.aqualight.ui.tabs.devices.add

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.OnBackPressedCallback
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
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.provisioning.ProvisionedDevice
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentDeviceProvisioningProgressBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.devices.DevicesFragmentDirections
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import kotlinx.coroutines.launch

class DeviceProvisioningProgressFragment : Fragment(R.layout.fragment_device_provisioning_progress) {

    private val args: DeviceProvisioningProgressFragmentArgs by navArgs()
    private val viewModel: DeviceProvisioningProgressViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }
    private val permissionController = DeviceAddPermissionController()

    private var _binding: FragmentDeviceProvisioningProgressBinding? = null
    private val binding get() = _binding!!

    private var autoStartRequested = false
    private var wifiFailureReturned = false
    private var retryBleSetupOnResume = false

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

    override fun onResume() {
        super.onResume()
        if (retryBleSetupOnResume) {
            retryBleSetupOnResume = false
            if (permissionController.hasBlePermissions(requireContext())) {
                viewModel.startProvisioning()
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
        when (permissionController.bleNextAction(this)) {
            DeviceAddPermissionController.NextAction.GRANTED -> viewModel.startProvisioning()
            DeviceAddPermissionController.NextAction.REQUEST_PERMISSION -> {
                permissionController.markBlePermissionRequested(requireContext())
                blePermissionLauncher.launch(permissionController.blePermissions())
            }
            DeviceAddPermissionController.NextAction.OPEN_APP_SETTINGS -> {
                retryBleSetupOnResume = true
                val packageUri = Uri.fromParts(
                    "package",
                    requireContext().packageName,
                    null
                )
                runCatching {
                    startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            packageUri
                        )
                    )
                }.onFailure {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
        }
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
                        is DeviceProvisioningProgressEvent.OpenAddedDevice -> {
                            openAddedDevice(event.device)
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
        binding.tvStepOne.text = "✓ ${state.stepOne}"
        binding.tvStepTwo.text = "✓ ${state.stepTwo}"
        binding.tvStepThree.text = "${state.currentStepIcon()} ${state.stepThree}"
        binding.btnStartProvisioning.isVisible = state.canStart && !state.isCancelling
        binding.btnStartProvisioning.isEnabled = state.canStart && !state.isCancelling
        binding.btnStartProvisioning.text = if (
            state.canStart &&
            !state.requiresFreshDeviceSelection &&
            permissionController.bleNextAction(this) ==
            DeviceAddPermissionController.NextAction.OPEN_APP_SETTINGS
        ) {
            getString(R.string.device_qr_preflight_open_app_settings)
        } else {
            state.buttonText
        }
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

    private fun DeviceProvisioningProgressUiState.currentStepIcon(): String = when {
        canStart -> "!"
        showProgress -> "●"
        else -> "✓"
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

    private fun openAddedDevice(device: ProvisionedDevice) {
        val navController = findNavController()
        navController.popBackStack(R.id.devicesFragment, false)
        navController.navigate(device.toDevicesDestination())
    }

    private fun ProvisionedDevice.toDevicesDestination(): NavDirections = when (family) {
        OwnerDeviceFamily.LIGHT ->
            DevicesFragmentDirections.actionDevicesFragmentToDeviceLightRootFragment(
                deviceUid = deviceUid,
                deviceTitle = title
            )
        OwnerDeviceFamily.DOSING ->
            DevicesFragmentDirections.actionDevicesFragmentToDeviceDosingRootFragment(
                deviceUid = deviceUid,
                deviceTitle = title
            )
        OwnerDeviceFamily.TIMER ->
            DevicesFragmentDirections.actionDevicesFragmentToDeviceTimerRootFragment(
                deviceUid = deviceUid,
                deviceTitle = title
            )
        OwnerDeviceFamily.COOLING ->
            DevicesFragmentDirections.actionDevicesFragmentToDeviceCoolingRootFragment(
                deviceUid = deviceUid,
                deviceTitle = title
            )
        OwnerDeviceFamily.UNKNOWN ->
            DevicesFragmentDirections.actionDevicesFragmentToUnsupportedDeviceFragment(
                deviceTitle = title.ifBlank {
                    getString(R.string.device_wifi_default_device_name)
                },
                message = UNSUPPORTED_FAMILY_MESSAGE,
                deviceUid = deviceUid
            )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val UNSUPPORTED_FAMILY_MESSAGE =
            "Unsupported AquaLight device family. Firmware did not provide a known product.family value."
    }
}
