package com.aqua.aqualight.ui.tabs.settings.device

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
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentDeviceStatusBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DeviceStatusFragment : Fragment(R.layout.fragment_device_status) {

    private var _binding: FragmentDeviceStatusBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DeviceStatusViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }

    private lateinit var adapter: DeviceStatusAdapter

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding =
            FragmentDeviceStatusBinding.bind(view)

        setupHeader()
        setupRecycler()
        observeUiState()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                title = getString(R.string.screen_title_device_status)
            )
        )
    }

    private fun setupRecycler() {
        adapter =
            DeviceStatusAdapter()

        binding.rvDevices.layoutManager =
            LinearLayoutManager(
                requireContext()
            )

        binding.rvDevices.adapter =
            adapter
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                viewModel.uiState.collectLatest { state ->
                    renderState(
                        state = state
                    )
                }
            }
        }
    }

    private fun renderState(
        state: DeviceStatusUiState
    ) {
        binding.rvDevices.isVisible =
            state.isEmpty.not()

        binding.emptyStateContainer.isVisible =
            state.isEmpty

        adapter.submitList(
            state.devices
        )
    }

    override fun onDestroyView() {
        binding.rvDevices.adapter =
            null

        _binding =
            null

        super.onDestroyView()
    }
}
