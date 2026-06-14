package com.aqua.aqualight.ui.tabs.devices

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import com.aqua.aqualight.databinding.FragmentDevicesBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.AquaHeaderPrimaryAction
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.navigation.AppRouteNavigator
import com.aqua.aqualight.ui.tabs.devices.model.DeviceCardUi
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import kotlinx.coroutines.launch

class DevicesFragment : Fragment(R.layout.fragment_devices) {

    private var _binding: FragmentDevicesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DevicesViewModel by viewModels()

    private lateinit var adapter: DevicesListAdapter

    private var currentUiState =
        DevicesViewModel.DevicesUiState()

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding =
            FragmentDevicesBinding.bind(view)

        setupHeader()
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                showBackButton = false,
                primaryAction = AquaHeaderPrimaryAction(
                    text = if (currentUiState.selectionMode) {
                        "Delete"
                    } else {
                        "+ Add"
                    },
                    contentDescription = if (currentUiState.selectionMode) {
                        "Delete selected devices"
                    } else {
                        getString(
                            R.string.devices_scan_button_desc
                        )
                    },
                    onClick = {
                        if (currentUiState.selectionMode) {
                            showDeleteConfirmDialog()
                        } else {
                            openAddDeviceScreen()
                        }
                    }
                )
            )
        )

        applyPrimaryActionStyle()
    }

    private fun setupRecyclerView() {
        adapter =
            DevicesListAdapter(
                onSelectionModeStart = {
                    viewModel.enterSelectionMode()
                },
                onSelectionChanged = { count ->
                    viewModel.onSelectionChanged(
                        selectedCount = count
                    )
                },
                onDeviceClick = { device ->
                    openDeviceMenu(
                        device = device
                    )
                }
            )

        binding.rvSelectedDevices.layoutManager =
            LinearLayoutManager(
                requireContext()
            )

        binding.rvSelectedDevices.adapter =
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
        state: DevicesViewModel.DevicesUiState
    ) {
        if (_binding == null) {
            return
        }

        val wasSelectionMode =
            currentUiState.selectionMode

        currentUiState =
            state

        if (
            wasSelectionMode &&
            !state.selectionMode
        ) {
            adapter.exitSelectionMode()
        }

        binding.tvEmptyState.visibility =
            if (state.isEmpty) {
                View.VISIBLE
            } else {
                View.GONE
            }

        binding.rvSelectedDevices.visibility =
            if (state.isEmpty) {
                View.GONE
            } else {
                View.VISIBLE
            }

        adapter.submitList(
            state.devices
        )

        setupHeader()

        showGlobalLoading(
            show = state.isOpeningDevice || state.isDeletingDevices
        )
    }

    private fun handleEvent(
        event: DevicesViewModel.DevicesEvent
    ) {
        if (!isAdded || _binding == null) {
            return
        }

        when (event) {
            is DevicesViewModel.DevicesEvent.NavigateToDeviceRouter -> {
                openDeviceController(
                    deviceId = event.deviceId,
                    deviceTitle = event.deviceTitle
                )
            }

            DevicesViewModel.DevicesEvent.ShowOffline -> {
                showDeviceOfflineDialog()
            }

            DevicesViewModel.DevicesEvent.ShowNotFound -> {
                showDeviceInfoDialog(
                    title = "Device Not Found",
                    message = "This device is no longer available."
                )
            }

            DevicesViewModel.DevicesEvent.ShowUnsupported -> {
                showDeviceInfoDialog(
                    title = "Unsupported Device",
                    message = "This device is not supported by this app version."
                )
            }

            DevicesViewModel.DevicesEvent.ShowOpenFailed -> {
                showDeviceInfoDialog(
                    title = "Open Failed",
                    message = "The device could not be opened. Please try again."
                )
            }

            DevicesViewModel.DevicesEvent.ShowDeleteFailed -> {
                DialogManager.showInfoDialog(
                    context = requireContext(),
                    type = DialogType.ERROR,
                    title = "Delete Failed",
                    message = "Selected devices could not be deleted."
                )
            }
        }
    }

    private fun openAddDeviceScreen() {
        navigateFromDevices(
            DevicesFragmentDirections.actionDevicesFragmentToDeviceAddFragment()
        )
    }

    private fun openDeviceMenu(
        device: DeviceCardUi
    ) {
        viewModel.openDevice(
            device = device
        )
    }

    private fun showDeviceOfflineDialog() {
        showDeviceInfoDialog(
            title = "Device Offline",
            message = getString(
                R.string.device_offline_message
            )
        )
    }

    private fun showDeviceInfoDialog(
        title: String,
        message: String
    ) {
        if (!isAdded || _binding == null) {
            return
        }

        DialogManager.showInfoDialog(
            context = requireContext(),
            type = DialogType.WARNING,
            title = title,
            message = message
        )
    }

    private fun navigateFromDevices(
        directions: NavDirections
    ) {
        val navController =
            findNavController()

        if (navController.currentDestination?.id != R.id.devicesFragment) {
            return
        }

        navController.navigate(
            directions
        )
    }

    private fun openDeviceController(
        deviceId: Long,
        deviceTitle: String
    ) {
        val navController =
            findNavController()

        if (navController.currentDestination?.id != R.id.devicesFragment) {
            return
        }

        AppRouteNavigator.openDevice(
            navController = navController,
            deviceId = deviceId,
            deviceTitle = deviceTitle
        )
    }

    private fun showDeleteConfirmDialog() {
        if (currentUiState.isDeletingDevices) {
            return
        }

        val ids =
            adapter.getSelectedIds()

        if (ids.isEmpty()) {
            viewModel.exitSelectionMode()
            return
        }

        val count =
            ids.size

        val title =
            if (count == 1) {
                getString(
                    R.string.devices_delete_title_single
                )
            } else {
                getString(
                    R.string.devices_delete_title_multi,
                    count
                )
            }

        val message =
            if (count == 1) {
                getString(
                    R.string.devices_delete_message_single
                )
            } else {
                getString(
                    R.string.devices_delete_message_multi,
                    count
                )
            }

        DialogManager.showConfirmDialog(
            context = requireContext(),
            type = DialogType.WARNING,
            title = title,
            message = message,
            onConfirm = {
                viewModel.deleteSelectedDevices(
                    ids = ids
                )
            },
            onCancel = {
                viewModel.exitSelectionMode()
            }
        )
    }

    private fun applyPrimaryActionStyle() {
        val button =
            binding.appHeader.btnPrimaryAction

        if (currentUiState.selectionMode) {
            button.setTextColor(
                Color.parseColor("#FF8A8A")
            )

            button.backgroundTintList =
                ColorStateList.valueOf(
                    Color.parseColor("#321E2A")
                )

            button.strokeWidth =
                1.dp()

            button.strokeColor =
                ColorStateList.valueOf(
                    Color.parseColor("#7A3344")
                )
        } else {
            button.setTextColor(
                Color.WHITE
            )

            button.backgroundTintList =
                ColorStateList.valueOf(
                    Color.parseColor("#1C3252")
                )

            button.strokeWidth =
                0

            button.strokeColor =
                ColorStateList.valueOf(
                    Color.TRANSPARENT
                )
        }
    }

    private fun showGlobalLoading(
        show: Boolean
    ) {
        setFragmentGlobalLoading(
            show
        )
    }

    private fun Int.dp(): Int {
        return (
            this * resources.displayMetrics.density
        ).toInt()
    }

    override fun onDestroyView() {
        showGlobalLoading(
            show = false
        )

        binding.rvSelectedDevices.adapter =
            null

        _binding =
            null

        super.onDestroyView()
    }
}
