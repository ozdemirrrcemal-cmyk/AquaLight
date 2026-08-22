package com.aqua.aqualight.ui.tabs.devices.add

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentDeviceAddBinding
import com.aqua.aqualight.platform.permissions.AppCapability
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.permission.CapabilityPermissionCoordinator
import kotlinx.coroutines.launch

class DeviceAddFragment : Fragment(R.layout.fragment_device_add) {

    private val viewModel: DeviceAddViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }

    private val permissionCoordinator = CapabilityPermissionCoordinator(this) { action ->
        when (action) {
            ACTION_START_BLE_SCAN -> viewModel.startBleScan()
        }
    }

    private var _binding: FragmentDeviceAddBinding? = null
    private val binding get() = _binding!!

    private val candidateAdapter = DeviceAddCandidateAdapter { candidate ->
        viewModel.onCandidateClicked(candidate)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDeviceAddBinding.bind(view)

        setupHeader()
        setupRecycler()
        setupActions()
        observeViewModel()
    }

    override fun onStart() {
        super.onStart()
        viewModel.onScreenVisible()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                title = getString(R.string.device_add_title),
                onBackClick = {
                    findNavController().navigateUp()
                }
            )
        )
    }

    private fun setupRecycler() {
        binding.rvCandidates.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCandidates.adapter = candidateAdapter
        binding.rvCandidates.setHasFixedSize(false)
    }

    private fun setupActions() {
        binding.btnQrSetup.setOnClickListener {
            viewModel.onQrClicked()
        }

        binding.btnScan.setOnClickListener {
            permissionCoordinator.runWhenGranted(
                capability = AppCapability.BLE_PROVISIONING,
                actionToken = ACTION_START_BLE_SCAN
            )
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        renderState(state)
                    }
                }

                launch {
                    viewModel.events.collect { event ->
                        handleEvent(event)
                    }
                }
            }
        }
    }

    private fun renderState(state: DeviceAddUiState) {
        if (_binding == null) return

        val hasCandidates = state.candidates.isNotEmpty()
        val showSearchCard = !hasCandidates
        val isScanning = state.mode == DeviceAddScanMode.SCANNING

        binding.cardHero.isVisible = showSearchCard
        binding.tipContainer.isVisible = showSearchCard && state.mode == DeviceAddScanMode.READY
        binding.btnQrSetup.isVisible = showSearchCard && !isScanning
        binding.tvFoundDevicesLabel.isVisible = hasCandidates
        binding.tvFoundDevicesHint.isVisible = hasCandidates
        binding.rvCandidates.isVisible = hasCandidates

        binding.tvScanBadge.text = state.scanBadge
        binding.tvHeroTitle.text = state.heroTitle
        binding.tvHeroSubtitle.text = state.heroSubtitle

        candidateAdapter.submitList(state.candidates)

        if (isScanning) {
            binding.scanPulseView.startScan()
            binding.btnScan.text = getString(R.string.device_add_scan_button_scanning)
            binding.btnScan.isEnabled = false
            binding.btnScan.alpha = 0.72f
        } else {
            binding.scanPulseView.stopScan()
            binding.btnScan.text = getString(R.string.device_add_scan_button)
            binding.btnScan.isEnabled = true
            binding.btnScan.alpha = 1f
        }
    }

    private fun handleEvent(event: DeviceAddEvent) {
        when (event) {
            is DeviceAddEvent.ShowMessage -> {
                (activity as? BaseActivity)?.showSnackBar(
                    message = event.message,
                    type = BaseActivity.SnackType.WARNING
                )
            }

            DeviceAddEvent.OpenQrScanner -> {
                openQrScanner()
            }

            is DeviceAddEvent.OpenWifiProvisioning -> {
                openManualWifiProvisioning(event.candidate)
            }
        }
    }

    private fun openQrScanner() {
        findNavController().navigate(
            DeviceAddFragmentDirections.actionDeviceAddFragmentToDeviceQrScanFragment()
        )
    }

    private fun openManualWifiProvisioning(candidate: DeviceAddCandidateUi) {
        findNavController().navigate(
            DeviceAddFragmentDirections.actionDeviceAddFragmentToDeviceWifiProvisioningFragment(
                candidateId = candidate.id,
                deviceTitle = candidate.title,
                deviceSerial = candidate.serial,
                deviceModel = candidate.model,
                bleAddress = candidate.bleAddress,
                bleName = candidate.bleName,
                qrSecretReference = ""
            )
        )
    }

    override fun onDestroyView() {
        binding.scanPulseView.stopScan()
        binding.rvCandidates.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val ACTION_START_BLE_SCAN = "start_ble_scan"
    }
}
