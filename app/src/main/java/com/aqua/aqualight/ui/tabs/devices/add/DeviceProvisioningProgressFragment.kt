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
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceProvisioningProgressBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import kotlinx.coroutines.launch

class DeviceProvisioningProgressFragment : Fragment(R.layout.fragment_device_provisioning_progress) {

    private val args: DeviceProvisioningProgressFragmentArgs by navArgs()
    private val viewModel: DeviceProvisioningProgressViewModel by viewModels()
    private val permissionController = DeviceAddPermissionController()

    private var _binding: FragmentDeviceProvisioningProgressBinding? = null
    private val binding get() = _binding!!

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

        viewModel.bind(args.sessionId)
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = "Provisioning",
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

    private fun renderState(state: DeviceProvisioningProgressUiState) {
        if (_binding == null) return

        binding.tvTitle.text = state.title
        binding.tvMessage.text = state.message
        binding.tvDeviceName.text = state.deviceName
        binding.tvDeviceSerial.text = "Serial: ${state.deviceSerial}"
        binding.tvBleAddress.text = "BLE: ${state.bleAddress}"
        binding.tvWifiSsid.text = "Wi-Fi: ${state.wifiSsid}"
        binding.tvStepOne.text = state.stepOne
        binding.tvStepTwo.text = state.stepTwo
        binding.tvStepThree.text = state.stepThree
        binding.btnStartProvisioning.isEnabled = state.canStart
        binding.btnStartProvisioning.text = state.buttonText
        binding.btnStartProvisioning.alpha = if (state.canStart) 1f else 0.45f
        binding.progressBar.isVisible = state.showProgress
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
