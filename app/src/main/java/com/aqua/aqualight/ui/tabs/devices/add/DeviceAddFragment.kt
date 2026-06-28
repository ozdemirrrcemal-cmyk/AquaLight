package com.aqua.aqualight.ui.tabs.devices.add

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceAddBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderAction
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import kotlinx.coroutines.launch

class DeviceAddFragment : Fragment(R.layout.fragment_device_add) {

    private val viewModel: DeviceAddViewModel by viewModels()
    private val permissionController = DeviceAddPermissionController()

    private var _binding: FragmentDeviceAddBinding? = null
    private val binding get() = _binding!!

    private val candidateAdapter = DeviceAddCandidateAdapter { candidate ->
        viewModel.onCandidateClicked(candidate)
    }

    private val blePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (permissionController.hasBlePermissionsFromResult(requireContext(), result)) {
            viewModel.startBleScan()
        } else {
            viewModel.onBlePermissionDenied()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDeviceAddBinding.bind(view)

        setupHeader()
        setupRecycler()
        setupActions()
        observeViewModel()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = "Add Device",
                onBackClick = {
                    findNavController().navigateUp()
                },
                actions = listOf(
                    AquaHeaderAction(
                        iconRes = R.drawable.ic_qr_code_24,
                        contentDescription = "Add with QR code",
                        onClick = {
                            viewModel.onQrClicked()
                        }
                    )
                )
            )
        )
    }

    private fun setupRecycler() {
        binding.rvCandidates.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCandidates.adapter = candidateAdapter
        binding.rvCandidates.setHasFixedSize(false)
    }

    private fun setupActions() {
        binding.btnScan.setOnClickListener {
            startBleScanWithPermissionCheck()
        }
    }

    private fun startBleScanWithPermissionCheck() {
        if (permissionController.hasBlePermissions(requireContext())) {
            viewModel.startBleScan()
            return
        }

        blePermissionLauncher.launch(
            permissionController.blePermissions()
        )
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

        binding.cardHero.isVisible = showSearchCard
        binding.tipContainer.isVisible = showSearchCard && state.mode == DeviceAddScanMode.READY
        binding.tvFoundDevicesLabel.isVisible = hasCandidates
        binding.tvFoundDevicesHint.isVisible = hasCandidates
        binding.rvCandidates.isVisible = hasCandidates

        binding.tvScanBadge.text = state.scanBadge
        binding.tvHeroTitle.text = state.heroTitle
        binding.tvHeroSubtitle.text = state.heroSubtitle

        candidateAdapter.submitList(state.candidates)

        if (state.mode == DeviceAddScanMode.SCANNING) {
            binding.scanPulseView.startScan()
            binding.btnScan.text = "Scanning..."
            binding.btnScan.isEnabled = false
            binding.btnScan.alpha = 0.72f
        } else {
            binding.scanPulseView.stopScan()
            binding.btnScan.text = "Scan"
            binding.btnScan.isEnabled = true
            binding.btnScan.alpha = 1f
        }
    }

    private fun handleEvent(event: DeviceAddEvent) {
        when (event) {
            is DeviceAddEvent.ShowMessage -> {
                Toast.makeText(
                    requireContext(),
                    event.message,
                    Toast.LENGTH_SHORT
                ).show()
            }

            DeviceAddEvent.OpenQrScanner -> {
                openQrScanner()
            }

            is DeviceAddEvent.OpenWifiProvisioning -> {
                openWifiProvisioning(event.candidate)
            }
        }
    }

    private fun openQrScanner() {
        findNavController().navigate(
            DeviceAddFragmentDirections.actionDeviceAddFragmentToDeviceQrScanFragment()
        )
    }

    private fun openWifiProvisioning(candidate: DeviceAddCandidateUi) {
        findNavController().navigate(
            DeviceAddFragmentDirections.actionDeviceAddFragmentToDeviceWifiProvisioningFragment(
                candidateId = candidate.id,
                deviceTitle = candidate.title,
                deviceSerial = candidate.serial,
                deviceModel = candidate.model,
                bleAddress = candidate.bleAddress,
                bleName = candidate.bleName,
                claimCode = "",
                rawQrPayload = ""
            )
        )
    }

    override fun onDestroyView() {
        binding.scanPulseView.stopScan()
        binding.rvCandidates.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
