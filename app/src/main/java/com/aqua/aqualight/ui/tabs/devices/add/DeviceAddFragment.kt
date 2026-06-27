package com.aqua.aqualight.ui.tabs.devices.add

import android.os.Bundle
import android.view.View
import android.widget.Toast
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
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import kotlinx.coroutines.launch

class DeviceAddFragment : Fragment(R.layout.fragment_device_add) {

    private val viewModel: DeviceAddViewModel by viewModels()

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

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = "Add Device",
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
        binding.cardQr.setOnClickListener {
            viewModel.onQrClicked()
        }

        binding.cardBle.setOnClickListener {
            viewModel.onBleScanClicked()
        }

        binding.btnScanAgain.setOnClickListener {
            viewModel.onScanAgainClicked()
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

        binding.tvScanBadge.text = state.scanBadge
        binding.tvHeroTitle.text = state.heroTitle
        binding.tvHeroSubtitle.text = state.heroSubtitle
        binding.tvEmptyTitle.text = state.emptyTitle
        binding.tvEmptyMessage.text = state.emptyMessage

        val hasCandidates = state.candidates.isNotEmpty()
        binding.rvCandidates.isVisible = hasCandidates
        binding.emptyContainer.isVisible = !hasCandidates
        binding.tvFoundDevicesLabel.text = if (hasCandidates) {
            "Found devices"
        } else {
            "Discovery"
        }

        candidateAdapter.submitList(state.candidates)

        if (state.mode == DeviceAddScanMode.SCANNING) {
            binding.scanPulseView.startScan()
        } else {
            binding.scanPulseView.stopScan()
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
        }
    }

    override fun onDestroyView() {
        binding.scanPulseView.stopScan()
        binding.rvCandidates.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
