package com.aqua.aqualight.ui.tabs.devices

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
import com.aqua.aqualight.databinding.FragmentDevicesBinding
import com.aqua.aqualight.ui.common.feedback.FeedbackBottomSheet
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.AquaHeaderFilledIconAction
import com.aqua.aqualight.ui.common.header.AquaHeaderPrimaryAction
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.devices.route.DeviceRoute
import com.aqua.aqualight.ui.tabs.devices.route.DeviceRouteTarget
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import kotlinx.coroutines.launch

class DevicesFragment : Fragment(R.layout.fragment_devices) {

    private var _binding: FragmentDevicesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DevicesViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }
    private val deviceAdapter = DeviceCardAdapter(
        onDeviceClick = { item ->
            viewModel.onDeviceClicked(item.deviceUid)
        },
        onDeviceLongClick = { item ->
            viewModel.onDeviceLongClicked(item.deviceUid)
        }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDevicesBinding.bind(view)

        setupHeader()
        setupFeedbackResultListener()
        setupUiShell()
        observeViewModel()
    }

    private fun setupFeedbackResultListener() {
        childFragmentManager.setFragmentResultListener(
            DELETE_DEVICES_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            if (result.getString(FeedbackBottomSheet.RESULT_KEY) ==
                FeedbackBottomSheet.RESULT_PRIMARY
            ) viewModel.deleteSelectedDevices()
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onScreenVisible()
    }

    private fun setupHeader(
        state: DevicesViewModel.DevicesUiState = viewModel.uiState.value
    ) {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = if (state.selectionMode) {
                AquaHeaderConfig(
                    titleOverride = getString(
                        R.string.devices_selected_count_title,
                        state.selectedCount
                    ),
                    showBackButton = false,
                    filledIconAction = AquaHeaderFilledIconAction(
                        iconRes = R.drawable.ic_delete_24,
                        contentDescription = getString(
                            R.string.devices_delete_selected_content_description
                        ),
                        enabled = state.selectedCount > 0 &&
                            !state.isDeletingDevices &&
                            !state.isOpeningDeviceMenu,
                        onClick = {
                            showDeleteConfirmation()
                        }
                    )
                )
            } else {
                AquaHeaderConfig(
                    titleOverride = getString(R.string.screen_title_devices),
                    showBackButton = false,
                    primaryAction = AquaHeaderPrimaryAction(
                        text = getString(R.string.devices_add_action),
                        contentDescription = getString(
                            R.string.devices_add_content_description
                        ),
                        onClick = {
                            if (
                                !state.isDeletingDevices &&
                                !state.isOpeningDeviceMenu
                            ) {
                                openAddDevice()
                            }
                        }
                    )
                )
            }
        )
    }

    private fun openAddDevice() {
        val state = viewModel.uiState.value
        if (state.isDeletingDevices || state.isOpeningDeviceMenu) {
            return
        }

        findNavController().navigate(
            DevicesFragmentDirections.actionDevicesFragmentToDeviceAddFragment()
        )
    }

    private fun setupUiShell() {
        binding.rvSelectedDevices.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSelectedDevices.adapter = deviceAdapter
        binding.rvSelectedDevices.setHasFixedSize(false)
        binding.rvSelectedDevices.isVisible = false
        binding.tvEmptyState.isVisible = true
        binding.btnEmptyAddDevice.setOnClickListener {
            openAddDevice()
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
                            is DevicesEvent.OpenRoute -> openDeviceRoute(event.route)
                            is DevicesEvent.ShowDeviceUnavailable -> {
                                showDeviceUnavailable(event)
                            }
                            is DevicesEvent.ShowDeletePartialSuccess -> {
                                showDeletePartialSuccess(event)
                            }
                            is DevicesEvent.ShowDeleteFailed -> {
                                showDeleteFailed(event)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun renderState(state: DevicesViewModel.DevicesUiState) {
        if (_binding == null) return

        setupHeader(state)
        deviceAdapter.submitList(state.devices)
        binding.rvSelectedDevices.isEnabled =
            !state.isDeletingDevices && !state.isOpeningDeviceMenu
        binding.btnEmptyAddDevice.isEnabled =
            !state.isDeletingDevices && !state.isOpeningDeviceMenu
        binding.rvSelectedDevices.isVisible = state.devices.isNotEmpty()
        binding.tvEmptyState.isVisible = state.isEmpty
        baseActivity()?.setGlobalLoading(
            ownerKey = DEVICE_DELETE_LOADING_OWNER,
            show = state.isDeletingDevices
        )
        baseActivity()?.setGlobalLoading(
            ownerKey = DEVICE_MENU_LOADING_OWNER,
            show = state.isOpeningDeviceMenu
        )
    }

    private fun showDeviceUnavailable(
        event: DevicesEvent.ShowDeviceUnavailable
    ) {
        baseActivity()?.clearGlobalLoading(DEVICE_MENU_LOADING_OWNER)
        baseActivity()?.showDeviceOfflineDialog(
            deviceTitle = event.title,
            messageRes = event.messageRes
        )
    }

    private fun showDeletePartialSuccess(
        event: DevicesEvent.ShowDeletePartialSuccess
    ) {
        val removedSentence = resources.getQuantityString(
            R.plurals.devices_delete_removed_sentence,
            event.succeededCount,
            event.succeededCount
        )
        val failedSentence = resources.getQuantityString(
            R.plurals.devices_delete_remain_selected_sentence,
            event.failedCount,
            event.failedCount
        )

        DialogManager.showInfoDialog(
            context = requireContext(),
            type = DialogType.WARNING,
            title = getString(R.string.devices_delete_partial_title),
            message = "$removedSentence $failedSentence"
        )
    }

    private fun showDeleteFailed(
        event: DevicesEvent.ShowDeleteFailed
    ) {
        DialogManager.showInfoDialog(
            context = requireContext(),
            type = DialogType.ERROR,
            title = getString(R.string.devices_delete_failed_title),
            message = resources.getQuantityString(
                R.plurals.devices_delete_failed_message,
                event.failedCount,
                event.failedCount
            )
        )
    }

    private fun showDeleteConfirmation() {
        val state = viewModel.uiState.value
        val selectedCount = state.selectedCount
        if (
            selectedCount <= 0 ||
            state.isDeletingDevices ||
            state.isOpeningDeviceMenu
        ) {
            return
        }

        FeedbackBottomSheet.show(
            fragmentManager = childFragmentManager,
            title = resources.getQuantityString(
                R.plurals.devices_delete_confirm_title,
                selectedCount,
                selectedCount
            ),
            message = resources.getQuantityString(
                R.plurals.devices_delete_confirm_message,
                selectedCount,
                selectedCount
            ),
            primaryText = getString(R.string.common_delete),
            cancelText = getString(R.string.common_cancel),
            tone = FeedbackBottomSheet.FeedbackTone.WARNING,
            requestKey = DELETE_DEVICES_REQUEST_KEY,
            actionId = "delete_selected"
        )
    }

    private fun openDeviceRoute(route: DeviceRoute) {
        if (!isAdded) {
            viewModel.onDeviceNavigationStarted(route.deviceUid)
            return
        }

        // The generated graph still exposes the legacy deviceTitle argument, but supported roots
        // deliberately receive no dynamic title through navigation. They resolve it from the
        // central device snapshot by deviceUid.
        val directions = when (route.target) {
            DeviceRouteTarget.LIGHT_ROOT ->
                DevicesFragmentDirections.actionDevicesFragmentToDeviceLightRootFragment(
                    deviceUid = route.deviceUid,
                    deviceTitle = ""
                )
            DeviceRouteTarget.DOSING_ROOT ->
                DevicesFragmentDirections.actionDevicesFragmentToDeviceDosingRootFragment(
                    deviceUid = route.deviceUid,
                    deviceTitle = ""
                )
            DeviceRouteTarget.TIMER_ROOT ->
                DevicesFragmentDirections.actionDevicesFragmentToDeviceTimerRootFragment(
                    deviceUid = route.deviceUid,
                    deviceTitle = ""
                )
            DeviceRouteTarget.COOLING_ROOT ->
                DevicesFragmentDirections.actionDevicesFragmentToDeviceCoolingRootFragment(
                    deviceUid = route.deviceUid,
                    deviceTitle = ""
                )
            DeviceRouteTarget.UNSUPPORTED ->
                DevicesFragmentDirections.actionDevicesFragmentToUnsupportedDeviceFragment(
                    deviceTitle = route.unsupportedTitle.ifBlank {
                        getString(R.string.device_menu_default_title)
                    },
                    message = route.messageRes.takeIf { it != 0 }
                        ?.let { getString(it) }
                        .orEmpty(),
                    deviceUid = route.deviceUid
                )
        }

        baseActivity()?.clearGlobalLoading(DEVICE_MENU_LOADING_OWNER)
        binding.root.postOnAnimation {
            if (
                _binding == null ||
                !isAdded ||
                findNavController().currentDestination?.id != R.id.devicesFragment
            ) {
                viewModel.onDeviceNavigationStarted(route.deviceUid)
                return@postOnAnimation
            }
            try {
                findNavController().navigate(directions)
            } finally {
                viewModel.onDeviceNavigationStarted(route.deviceUid)
            }
        }
    }

    private fun baseActivity(): BaseActivity? {
        return activity as? BaseActivity
    }

    override fun onDestroyView() {
        baseActivity()?.clearGlobalLoading(DEVICE_DELETE_LOADING_OWNER)
        baseActivity()?.clearGlobalLoading(DEVICE_MENU_LOADING_OWNER)
        binding.rvSelectedDevices.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val DEVICE_DELETE_LOADING_OWNER = "DevicesFragment.Delete"
        const val DEVICE_MENU_LOADING_OWNER = "DevicesFragment.MenuOpen"
        const val DELETE_DEVICES_REQUEST_KEY = "devices_delete_result"
    }
}
