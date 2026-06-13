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
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.databinding.FragmentTankDeviceSelectBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.loading.clearFragmentGlobalLoading
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import kotlinx.coroutines.launch

class TankDeviceSelectFragment :
    Fragment(R.layout.fragment_tank_device_select) {

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
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding =
            FragmentTankDeviceSelectBinding.bind(
                view
            )

        setupHeader()
        setupRecycler()
        observeViewModel()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(
                    R.string.tank_device_select_title
                )
            )
        )
    }

    private fun setupRecycler() {
        adapter =
            TankDeviceSelectAdapter { item ->
                viewModel.assignDeviceToTank(
                    deviceId = item.deviceId,
                    tankId = tankId
                )
            }

        binding.rvDevices.layoutManager =
            LinearLayoutManager(
                requireContext()
            )

        binding.rvDevices.adapter =
            adapter
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                launch {
                    viewModel.uiState.collect { state ->
                        renderState(
                            state = state
                        )
                    }
                }

                launch {
                    viewModel.events.collect { event ->
                        handleEvent(
                            event = event
                        )
                    }
                }
            }
        }
    }

    private fun renderState(
        state: TankDeviceSelectUiState
    ) {
        adapter.submitList(
            state.devices
        )

        binding.rvDevices.isVisible =
            state.devices.isNotEmpty()

        binding.tvEmptyState.isVisible =
            state.isEmpty

        setFragmentGlobalLoading(
            show = state.isAssigning
        )
    }

    private fun handleEvent(
        event: TankDeviceSelectEvent
    ) {
        when (event) {
            is TankDeviceSelectEvent.DeviceAssigned -> {
                clearFragmentGlobalLoading()

                findNavController()
                    .previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(
                        RESULT_SELECTED_DEVICE_ID,
                        event.deviceId
                    )

                findNavController()
                    .previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(
                        RESULT_SELECTED_TANK_ID,
                        event.tankId
                    )

                findNavController()
                    .popBackStack()
            }

            TankDeviceSelectEvent.ShowAssignError -> {
                clearFragmentGlobalLoading()

                (activity as? BaseActivity)?.showSnackBar(
                    message = getString(
                        R.string.tank_device_select_assign_error
                    ),
                    type = BaseActivity.SnackType.ERROR
                )
            }
        }
    }

    override fun onDestroyView() {
        clearFragmentGlobalLoading()

        binding.rvDevices.adapter =
            null

        _binding =
            null

        super.onDestroyView()
    }

    companion object {

        const val ARG_TANK_ID =
            "tankId"

        const val RESULT_SELECTED_DEVICE_ID =
            "tankDeviceSelectResultDeviceId"

        const val RESULT_SELECTED_TANK_ID =
            "tankDeviceSelectResultTankId"
    }
}
