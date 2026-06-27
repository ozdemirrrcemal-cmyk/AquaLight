package com.aqua.aqualight.ui.tabs.aquarium.detail.devices.select

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTankDeviceSelectBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import kotlinx.coroutines.launch

class TankDeviceSelectFragment : Fragment(R.layout.fragment_tank_device_select) {

    private val args: TankDeviceSelectFragmentArgs by navArgs()
    private val viewModel: TankDeviceSelectViewModel by viewModels()

    private var _binding: FragmentTankDeviceSelectBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: TankDeviceSelectAdapter

    private val tankId: Long
        get() = args.tankId

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentTankDeviceSelectBinding.bind(view)

        setupHeader()
        setupRecycler()
        observeViewModel()
        observeEvents()

        viewModel.bind(tankId)
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.tank_device_select_title)
            )
        )
    }

    private fun setupRecycler() {
        adapter = TankDeviceSelectAdapter { item ->
            viewModel.onDeviceClicked(item)
        }

        binding.rvDevices.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDevices.adapter = adapter
        binding.rvDevices.setHasFixedSize(false)
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
                        TankDeviceSelectEvent.DeviceAssigned -> {
                            findNavController().navigateUp()
                        }
                    }
                }
            }
        }
    }

    private fun renderState(
        state: TankDeviceSelectUiState
    ) {
        adapter.submitList(state.devices)
        binding.rvDevices.isVisible = state.isEmpty.not()
        binding.tvEmptyState.isVisible = state.isEmpty
        binding.tvEmptyState.text = if (state.isEmpty) {
            getString(R.string.tank_device_select_empty_subtitle)
        } else {
            ""
        }
    }

    override fun onDestroyView() {
        binding.rvDevices.adapter = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_TANK_ID = "tankId"
        const val RESULT_SELECTED_DEVICE_ID = "tankDeviceSelectResultDeviceId"
        const val RESULT_SELECTED_TANK_ID = "tankDeviceSelectResultTankId"
    }
}
