package com.aqua.aqualight.ui.tabs.devices.detail.light.dashboard

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
import com.aqua.aqualight.databinding.FragmentDeviceLightBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderAction
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.devices.common.feedback.DeviceFeedbackType
import com.aqua.aqualight.ui.tabs.devices.common.feedback.showDeviceSnack
import com.aqua.aqualight.ui.tabs.devices.detail.light.common.LIGHT_DEVICE_INFORMATION_MISSING
import com.aqua.aqualight.ui.tabs.devices.detail.light.common.renderLightModeChip
import com.aqua.aqualight.ui.tabs.devices.detail.light.dashboard.model.DeviceLightDashboardUiState
import kotlinx.coroutines.launch

class DeviceLightFragment : Fragment(R.layout.fragment_device_light) {

    private val args: DeviceLightFragmentArgs by navArgs()

    private var _binding: FragmentDeviceLightBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DeviceLightViewModel by viewModels()

    private var latestState: DeviceLightDashboardUiState = DeviceLightDashboardUiState()

    private val deviceId: Long
        get() = args.deviceId

    private val deviceTitle: String
        get() = args.deviceTitle.ifBlank {
            "WRGB Pro Elite"
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding =
            FragmentDeviceLightBinding.bind(view)

        setupHeader()
        setupClicks()
        observeUiState()

        viewModel.initialize(
            deviceId
        )
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = deviceTitle,
                actions = listOf(
                    AquaHeaderAction(
                        iconRes = R.drawable.ic_settings,
                        contentDescription = "Light settings",
                        onClick = {
                            openSettings()
                        }
                    ),
                    AquaHeaderAction(
                        iconRes = R.drawable.ic_refresh,
                        contentDescription = "Refresh device status",
                        onClick = {
                            refreshDeviceStatus()
                        }
                    )
                )
            )
        )
    }

    private fun setupClicks() {
        binding.cardManual.setOnClickListener {
            if (!ensureControlsEnabled()) {
                return@setOnClickListener
            }

            findNavController().navigate(
                DeviceLightFragmentDirections.actionDeviceLightFragmentToDeviceLightManualFragment(
                    deviceId = deviceId
                )
            )
        }

        binding.cardPrograms.setOnClickListener {
            if (!ensureControlsEnabled()) {
                return@setOnClickListener
            }

            findNavController().navigate(
                DeviceLightFragmentDirections.actionDeviceLightFragmentToDeviceLightProgramsFragment(
                    deviceId = deviceId
                )
            )
        }

        binding.cardQuickSetup.setOnClickListener {
            if (!ensureControlsEnabled()) {
                return@setOnClickListener
            }

            findNavController().navigate(
                DeviceLightFragmentDirections.actionDeviceLightFragmentToDeviceLightQuickSetupFragment(
                    deviceId = deviceId
                )
            )
        }

        binding.cardPresets.setOnClickListener {
            if (!ensureControlsEnabled()) {
                return@setOnClickListener
            }

            findNavController().navigate(
                DeviceLightFragmentDirections.actionDeviceLightFragmentToDeviceLightPresetsFragment(
                    deviceId = deviceId
                )
            )
        }
    }

    private fun openSettings() {
        if (!ensureControlsEnabled()) {
            return
        }

        findNavController().navigate(
            DeviceLightFragmentDirections.actionDeviceLightFragmentToDeviceLightSettingsFragment(
                deviceId = deviceId
            )
        )
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                viewModel.uiState.collect { state ->
                    renderUiState(
                        state
                    )
                }
            }
        }
    }

    private fun renderUiState(
        state: DeviceLightDashboardUiState
    ) {
        latestState = state

        binding.tvActiveProgramName.text =
            state.activeProgramName

        binding.tvLightRunStatus.text =
            state.runStatus

        binding.tvLiveModeChip.renderLightModeChip(
            mode = state.liveMode
        )

        binding.tvLiveRedChannel.text =
            state.redChannelText

        binding.tvLiveGreenChannel.text =
            state.greenChannelText

        binding.tvLiveBlueChannel.text =
            state.blueChannelText

        binding.tvLiveWhiteChannel.text =
            state.whiteChannelText

        binding.tvCurrentWatt.text =
            state.currentWattText

        binding.tvCurrentOutputPercent.text =
            state.outputPercentText

        binding.tvDeviceTime.text =
            state.deviceTimeText

        binding.tvNextEvent.text =
            state.nextEventText

        binding.tvTimelineStatus.text =
            state.timelineStatusText

        binding.tvHealthTempValue.text =
            state.healthTemperatureText

        binding.tvHealthTempStatus.text =
            state.healthTemperatureStatusText

        binding.tvHealthFanValue.text =
            state.healthFanText

        binding.tvHealthFanStatus.text =
            state.healthFanStatusText

        binding.todayLightPlanGraphView.setState(
            state.todayPlanGraphState
        )

        renderControlAvailability(
            state
        )
    }

    private fun renderControlAvailability(
        state: DeviceLightDashboardUiState
    ) {
        val enabled = state.controlsEnabled
        val alpha = if (enabled) {
            1f
        } else {
            0.55f
        }

        binding.cardManual.isEnabled = enabled
        binding.cardPrograms.isEnabled = enabled
        binding.cardQuickSetup.isEnabled = enabled
        binding.cardPresets.isEnabled = enabled

        binding.cardManual.alpha = alpha
        binding.cardPrograms.alpha = alpha
        binding.cardQuickSetup.alpha = alpha
        binding.cardPresets.alpha = alpha
    }

    private fun ensureControlsEnabled(): Boolean {
        if (latestState.controlsEnabled) {
            return true
        }

        showDeviceSnack(
            message = latestState.connectionStatusText,
            type = DeviceFeedbackType.WARNING
        )

        return false
    }

    private fun refreshDeviceStatus() {
        if (deviceId <= 0L) {
            showDeviceSnack(
                message = LIGHT_DEVICE_INFORMATION_MISSING,
                type = DeviceFeedbackType.ERROR
            )

            return
        }

        viewModel.refreshNow()
    }

    override fun onDestroyView() {
        _binding =
            null

        super.onDestroyView()
    }

    companion object {
        const val ARG_DEVICE_ID = "deviceId"
        const val ARG_DEVICE_TITLE = "deviceTitle"
    }
}
