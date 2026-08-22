package com.aqua.aqualight.ui.tabs.aquarium.detail

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
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentTankDetailDevicesBinding
import com.aqua.aqualight.ui.common.feedback.FeedbackBottomSheet
import com.aqua.aqualight.ui.tabs.aquarium.detail.devices.TankAssignedDeviceItem
import com.aqua.aqualight.ui.tabs.aquarium.detail.devices.TankAssignedDevicesAdapter
import com.aqua.aqualight.ui.tabs.aquarium.detail.devices.TankDetailDevicesEvent
import com.aqua.aqualight.ui.tabs.aquarium.detail.devices.TankDetailDevicesUiState
import com.aqua.aqualight.ui.tabs.aquarium.detail.devices.TankDetailDevicesViewModel
import com.aqua.aqualight.ui.tabs.devices.route.DeviceRoute
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import kotlinx.coroutines.launch

class TankDetailDevicesFragment : Fragment(R.layout.fragment_tank_detail_devices) {

    interface Host {
        fun onTankDetailAddDeviceClicked(
            tankId: Long
        )

        fun onTankDetailDeviceClicked(
            route: DeviceRoute
        )
    }

    private val viewModel: TankDetailDevicesViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }

    private var _binding: FragmentTankDetailDevicesBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: TankAssignedDevicesAdapter

    private var tankId: Long = 0L

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        tankId = requireArguments().getLong(ARG_TANK_ID)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentTankDetailDevicesBinding.bind(view)

        setupFeedbackResultListener()
        setupRecycler()
        setupClickListeners()
        observeViewModel()

        viewModel.bind(tankId)
    }

    private fun setupFeedbackResultListener() {
        parentFragmentManager.setFragmentResultListener(
            REMOVE_DEVICE_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            if (
                result.getString(FeedbackBottomSheet.RESULT_KEY) !=
                FeedbackBottomSheet.RESULT_PRIMARY
            ) {
                return@setFragmentResultListener
            }

            val deviceUid = result.getString(FeedbackBottomSheet.RESULT_ACTION_ID)
                ?.takeIf(String::isNotBlank)
                ?: return@setFragmentResultListener

            viewModel.removeDeviceFromTank(deviceUid)
        }
    }

    private fun setupRecycler() {
        adapter = TankAssignedDevicesAdapter(
            onDeviceClick = { item ->
                viewModel.onDeviceClicked(item.deviceUid)
            },
            onDeviceLongClick = { item ->
                confirmRemoveDevice(item)
            }
        )

        binding.rvAssignedDevices.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAssignedDevices.adapter = adapter
        binding.rvAssignedDevices.setHasFixedSize(false)
    }

    private fun setupClickListeners() {
        binding.btnAddDevice.setOnClickListener {
            val state = viewModel.uiState.value
            if (!state.isRemovingDevice && !state.isOpeningDeviceMenu) {
                parentHost()?.onTankDetailAddDeviceClicked(
                    tankId = tankId
                )
            }
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
                        when (event) {
                            is TankDetailDevicesEvent.OpenDeviceRoute -> {
                                try {
                                    parentHost()?.onTankDetailDeviceClicked(event.route)
                                } finally {
                                    viewModel.onDeviceMenuOpenHandled(event.requestUid)
                                }
                            }

                            is TankDetailDevicesEvent.ShowDeviceUnavailable -> {
                                try {
                                    showDeviceUnavailable(event)
                                } finally {
                                    viewModel.onDeviceMenuOpenHandled(event.requestUid)
                                }
                            }

                            TankDetailDevicesEvent.ShowRemoveFailed -> {
                                showError(
                                    title = getString(R.string.tank_device_remove_failed_title),
                                    message = getString(
                                        R.string.aquarium_error_device_remove_failed
                                    )
                                )
                            }

                            TankDetailDevicesEvent.ShowLoadFailed -> {
                                showError(
                                    title = getString(R.string.tank_device_load_failed_title),
                                    message = getString(R.string.tank_device_load_failed_message)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun renderState(
        state: TankDetailDevicesUiState
    ) {
        val globalBusy = state.isLoading ||
            state.isRemovingDevice ||
            state.isOpeningDeviceMenu

        adapter.submitList(state.devices)
        binding.rvAssignedDevices.isEnabled = !globalBusy
        binding.btnAddDevice.isEnabled =
            !globalBusy && !state.isOpeningDeviceMenu
        binding.rvAssignedDevices.isVisible = !state.isLoading && state.isEmpty.not()
        binding.cardDevicesEmpty.isVisible = !state.isLoading && state.isEmpty
        baseActivity()?.setGlobalLoading(
            ownerKey = TANK_DEVICE_LOADING_OWNER,
            show = globalBusy
        )
    }

    private fun showDeviceUnavailable(
        event: TankDetailDevicesEvent.ShowDeviceUnavailable
    ) {
        baseActivity()?.showDeviceOfflineDialog(
            deviceTitle = event.title,
            messageRes = event.messageRes
        )
    }

    private fun showError(
        title: String,
        message: String
    ) {
        DialogManager.showInfoDialog(
            context = requireContext(),
            type = DialogType.ERROR,
            title = title,
            message = message
        )
    }

    private fun confirmRemoveDevice(
        item: TankAssignedDeviceItem
    ) {
        val state = viewModel.uiState.value
        if (state.isRemovingDevice || state.isOpeningDeviceMenu) {
            return
        }

        FeedbackBottomSheet.show(
            fragmentManager = parentFragmentManager,
            title = getString(R.string.aquarium_remove_device_title),
            message = getString(
                R.string.aquarium_remove_device_message,
                item.title.ifBlank { getString(R.string.device_menu_default_title) }
            ),
            primaryText = getString(R.string.aquarium_remove_action),
            cancelText = getString(R.string.common_cancel),
            tone = FeedbackBottomSheet.FeedbackTone.DANGER,
            requestKey = REMOVE_DEVICE_REQUEST_KEY,
            actionId = item.deviceUid
        )
    }

    private fun parentHost(): Host? {
        return parentFragment as? Host
    }

    private fun baseActivity(): BaseActivity? {
        return activity as? BaseActivity
    }

    override fun onDestroyView() {
        baseActivity()?.clearGlobalLoading(TANK_DEVICE_LOADING_OWNER)
        binding.rvAssignedDevices.adapter = null
        _binding = null
        super.onDestroyView()
    }

    companion object {

        private const val ARG_TANK_ID = "tankId"
        private const val REMOVE_DEVICE_REQUEST_KEY = "tank_detail_remove_device_result"
        private const val TANK_DEVICE_LOADING_OWNER =
            "TankDetailDevicesFragment.DeviceOperation"

        fun newInstance(
            tankId: Long
        ): TankDetailDevicesFragment {
            return TankDetailDevicesFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_TANK_ID, tankId)
                }
            }
        }
    }
}
