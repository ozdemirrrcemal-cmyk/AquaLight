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
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.databinding.FragmentTankDeviceSelectBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import kotlinx.coroutines.launch

class TankDeviceSelectFragment : Fragment(R.layout.fragment_tank_device_select) {

    private val args: TankDeviceSelectFragmentArgs by navArgs()
    private val viewModel: TankDeviceSelectViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }

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
                title = getString(R.string.tank_device_select_title)
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

                        is TankDeviceSelectEvent.ShowAssignmentConflict -> {
                            showError(
                                titleRes = R.string.tank_device_assignment_conflict_title,
                                messageRes = R.string.tank_device_assignment_conflict_message
                            )
                        }

                        TankDeviceSelectEvent.ShowTankNotFound -> {
                            showError(
                                titleRes = R.string.aquarium_tank_not_found_title,
                                messageRes = R.string.aquarium_tank_no_longer_exists_message
                            )
                        }

                        TankDeviceSelectEvent.ShowDeviceNotFound -> {
                            showError(
                                titleRes = R.string.aquarium_device_not_found_title,
                                messageRes = R.string.aquarium_device_not_found_message
                            )
                        }

                        TankDeviceSelectEvent.ShowAssignFailed -> {
                            showError(
                                titleRes = R.string.tank_device_assignment_failed_title,
                                messageRes = R.string.tank_device_assignment_failed_message
                            )
                        }

                        TankDeviceSelectEvent.ShowLoadFailed -> {
                            showError(
                                titleRes = R.string.tank_device_load_failed_title,
                                messageRes = R.string.tank_device_load_failed_message
                            )
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
        binding.rvDevices.isEnabled = !state.isAssigning
        binding.rvDevices.isVisible = !state.isLoading && state.isEmpty.not()
        binding.tvEmptyState.isVisible = !state.isLoading && state.isEmpty
        binding.tvEmptyState.text = if (!state.isLoading && state.isEmpty) {
            when (state.emptyReason) {
                TankDeviceSelectEmptyReason.NO_REGISTERED_DEVICES ->
                    getString(R.string.tank_device_select_empty_subtitle)
                TankDeviceSelectEmptyReason.ALL_REGISTERED_DEVICES_ASSIGNED ->
                    getString(R.string.tank_device_select_all_assigned_subtitle)
                TankDeviceSelectEmptyReason.NONE -> ""
            }
        } else {
            ""
        }

        baseActivity()?.setGlobalLoading(
            ownerKey = ASSIGNMENT_LOADING_OWNER,
            show = state.isLoading || state.isAssigning
        )
    }

    private fun showError(
        titleRes: Int,
        messageRes: Int
    ) {
        DialogManager.showInfoDialog(
            context = requireContext(),
            type = DialogType.ERROR,
            title = getString(titleRes),
            message = getString(messageRes)
        )
    }

    private fun baseActivity(): BaseActivity? {
        return activity as? BaseActivity
    }

    override fun onDestroyView() {
        baseActivity()?.clearGlobalLoading(ASSIGNMENT_LOADING_OWNER)
        binding.rvDevices.adapter = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_TANK_ID = "tankId"
        const val RESULT_SELECTED_DEVICE_ID = "tankDeviceSelectResultDeviceId"
        const val RESULT_SELECTED_TANK_ID = "tankDeviceSelectResultTankId"

        private const val ASSIGNMENT_LOADING_OWNER =
            "TankDeviceSelectFragment.Assignment"
    }
}
