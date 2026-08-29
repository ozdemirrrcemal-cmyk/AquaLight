package com.aqua.aqualight.ui.tabs.devices.detail.update

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentDeviceFirmwareUpdateBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import kotlinx.coroutines.launch

/** Central full-screen software update route backed by the owner-scoped OTA coordinator. */
class DeviceFirmwareUpdateFragment : Fragment(R.layout.fragment_device_firmware_update) {

    private val args: DeviceFirmwareUpdateFragmentArgs by navArgs()
    private val viewModel: DeviceFirmwareUpdateViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }

    private var _binding: FragmentDeviceFirmwareUpdateBinding? = null
    private val binding get() = _binding!!
    private var latestState = DeviceFirmwareUpdateUiState()
    private var renderer: DeviceFirmwareUpdateScreenRenderer? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        require(args.deviceUid.isNotBlank()) {
            "Software update requires a non-blank device UID."
        }

        _binding = FragmentDeviceFirmwareUpdateBinding.bind(view)
        renderer = DeviceFirmwareUpdateScreenRenderer(this, binding)
        setupHeader()
        setupActions()
        observeUpdate()
        viewModel.bind(args.deviceUid)
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) viewModel.refreshActiveStatus()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.device_settings_firmware_update_title),
                onBackClick = { findNavController().navigateUp() }
            )
        )
    }

    private fun setupActions() {
        binding.btnUpdateAction.setOnClickListener {
            when (latestState.mode) {
                DeviceFirmwareUpdateMode.AVAILABLE -> viewModel.installUpdate()
                DeviceFirmwareUpdateMode.FAILED -> handleFailedAction()
                DeviceFirmwareUpdateMode.POST_RESTART_TIMEOUT -> viewModel.retry()
                DeviceFirmwareUpdateMode.SUCCEEDED,
                DeviceFirmwareUpdateMode.ROLLED_BACK,
                DeviceFirmwareUpdateMode.UNEXPECTED_FIRMWARE,
                DeviceFirmwareUpdateMode.UP_TO_DATE,
                DeviceFirmwareUpdateMode.UNSUPPORTED -> findNavController().navigateUp()
                else -> Unit
            }
        }
    }

    private fun handleFailedAction() {
        if (latestState.failure?.recoverable == true) {
            viewModel.retry()
        } else {
            findNavController().navigateUp()
        }
    }

    private fun observeUpdate() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    latestState = state
                    renderer?.render(state)
                }
            }
        }
    }

    override fun onDestroyView() {
        renderer?.release()
        renderer = null
        _binding = null
        super.onDestroyView()
    }
}
