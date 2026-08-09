package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentDeviceDosingChannelDestinationBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.DeviceDosingRootViewModel
import kotlinx.coroutines.launch

/** Shared Dosing-only destination shell for one catalog channel slot. */
abstract class DeviceDosingChannelDestinationFragment :
    Fragment(R.layout.fragment_device_dosing_channel_destination) {

    protected abstract val deviceUid: String
    protected abstract val slotId: String

    private val viewModel: DeviceDosingRootViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }

    private var _binding: FragmentDeviceDosingChannelDestinationBinding? = null
    private val binding get() = _binding!!
    private var renderedTitle: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDeviceDosingChannelDestinationBinding.bind(view)

        renderHeader(getString(R.string.device_family_dosing))
        observeChannelTitle()
        viewModel.bind(
            deviceUidText = deviceUid,
            fallbackTitle = ""
        )
    }

    private fun observeChannelTitle() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (_binding == null) return@collect
                    val channelTitle = state.channels
                        .firstOrNull { channel -> channel.slotId == slotId }
                        ?.displayName
                        .orEmpty()
                    if (channelTitle.isNotBlank()) {
                        renderHeader(channelTitle)
                    }
                }
            }
        }
    }

    private fun renderHeader(title: String) {
        if (renderedTitle == title) return
        renderedTitle = title
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = title,
                onBackClick = {
                    findNavController().navigateUp()
                }
            )
        )
    }

    override fun onDestroyView() {
        renderedTitle = null
        _binding = null
        super.onDestroyView()
    }
}
