package com.aqua.aqualight.ui.tabs.devices

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDevicesBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import kotlinx.coroutines.launch

class DevicesFragment : Fragment(R.layout.fragment_devices) {

    private var _binding: FragmentDevicesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DevicesViewModel by viewModels()
    private val deviceAdapter = DeviceCardAdapter { item ->
        viewModel.onDeviceClicked(item.deviceUid)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDevicesBinding.bind(view)

        setupHeader()
        setupUiShell()
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
                showBackButton = false
            )
        )
    }

    private fun setupUiShell() {
        binding.rvSelectedDevices.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSelectedDevices.adapter = deviceAdapter
        binding.rvSelectedDevices.setHasFixedSize(false)
        binding.rvSelectedDevices.isVisible = false
        binding.tvEmptyState.isVisible = true
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

    private fun renderState(state: DevicesViewModel.DevicesUiState) {
        if (_binding == null) return

        deviceAdapter.submitList(state.devices)
        binding.rvSelectedDevices.isVisible = state.devices.isNotEmpty()
        binding.tvEmptyState.isVisible = state.isEmpty
    }

    override fun onDestroyView() {
        binding.rvSelectedDevices.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
