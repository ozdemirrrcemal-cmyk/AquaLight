package com.aqua.aqualight.ui.tabs.devices.detail.light.settings

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceLightSettingsBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.devices.detail.light.settings.model.DeviceLightSettingsEvent
import com.aqua.aqualight.ui.tabs.devices.detail.light.settings.model.DeviceLightSettingsUiState
import com.aqua.aqualight.ui.tabs.devices.detail.light.settings.sheet.LightSettingValueSheet
import kotlinx.coroutines.launch

class DeviceLightSettingsFragment :
    Fragment(R.layout.fragment_device_light_settings) {

    private var _binding: FragmentDeviceLightSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DeviceLightSettingsViewModel by viewModels()

    private var isRendering = false

    private val deviceId: Long
        get() = arguments?.getLong(ARG_DEVICE_ID, 0L) ?: 0L

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDeviceLightSettingsBinding.bind(view)

        setupHeader()
        setupClicks()
        observeUiState()
        observeEvents()

        viewModel.initialize(deviceId)
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            AquaHeaderConfig(
                title = "Light Settings",
                showBackButton = true,
                onBackClick = {
                    findNavController().popBackStack()
                }
            )
        )
    }

    private fun setupClicks() {
        binding.btnUpdateFirmware.setOnClickListener {
            viewModel.updateFirmware()
        }

        binding.btnSyncTime.setOnClickListener {
            viewModel.syncTimeWithPhone()
        }

        binding.switchTemperatureProtection.setOnCheckedChangeListener { _, isChecked ->
            if (isRendering) return@setOnCheckedChangeListener

            viewModel.setTemperatureProtectionEnabled(isChecked)
        }

        binding.rowLimitTemperature.setOnClickListener {
            val state = viewModel.uiState.value

            LightSettingValueSheet
                .create(requireContext())
                .show(
                    title = "Limit Temperature",
                    subtitle = "Reduce light output when temperature goes above this value.",
                    values = (40..75).toList(),
                    suffix = "°C",
                    initialValue = state.limitTemperatureCelsius
                ) { value ->
                    viewModel.updateLimitTemperature(value)
                }
        }

        binding.rowLightReduction.setOnClickListener {
            val state = viewModel.uiState.value

            LightSettingValueSheet
                .create(requireContext())
                .show(
                    title = "Light Reduction",
                    subtitle = "Output multiplier applied when protection is triggered.",
                    values = listOf(40, 50, 60, 70, 80, 90),
                    suffix = "%",
                    initialValue = state.lightReductionPercent
                ) { value ->
                    viewModel.updateLightReduction(value)
                }
        }

        binding.rowRecoveryInterval.setOnClickListener {
            val state = viewModel.uiState.value

            LightSettingValueSheet
                .create(requireContext())
                .show(
                    title = "Recovery Interval",
                    subtitle = "How often the controller adjusts light output during protection.",
                    values = listOf(15, 30, 45, 60, 90, 120, 180, 240, 300),
                    suffix = "s",
                    initialValue = state.recoveryIntervalSeconds
                ) { value ->
                    viewModel.updateRecoveryInterval(value)
                }
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderUiState(state)
                }
            }
        }
    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is DeviceLightSettingsEvent.ShowMessage -> {
                            Toast.makeText(
                                requireContext(),
                                event.message,
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        is DeviceLightSettingsEvent.ShowError -> {
                            Toast.makeText(
                                requireContext(),
                                event.message,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
    }

    private fun renderUiState(
        state: DeviceLightSettingsUiState
    ) {
        isRendering = true

        binding.tvDeviceName.text = state.deviceName
        binding.tvDeviceType.text = state.deviceType
        binding.tvDeviceModel.text = state.deviceModel
        binding.tvFirmwareVersion.text = state.firmwareVersion
        binding.tvConnectionState.text = state.connectionState
		
		binding.tvDeviceIp.text = state.deviceIp
binding.tvSerialNumber.text = state.serialNumber
binding.tvHardwareRevision.text = state.hardwareRevision
binding.tvApiVersion.text = state.apiVersion
binding.tvChannelCount.text = state.channelCount

        binding.tvDeviceTime.text = state.deviceTime
        binding.tvPhoneTime.text = state.phoneTime
        binding.tvLastSyncTime.text = state.lastSyncTime

        binding.switchTemperatureProtection.isChecked =
            state.temperatureProtectionEnabled

        binding.tvLimitTemperatureValue.text =
            "${state.limitTemperatureCelsius}°C"

        binding.tvLightReductionValue.text =
            "${state.lightReductionPercent}%"

        binding.tvRecoveryIntervalValue.text =
            "${state.recoveryIntervalSeconds}s"

        binding.tvCoolingMode.text = state.coolingMode
        binding.tvFanStartValue.text = "${state.fanStartTemperatureCelsius}°C"
        binding.tvFanFullSpeedValue.text = "${state.fanFullSpeedTemperatureCelsius}°C"

        val protectionAlpha = if (state.temperatureProtectionEnabled) {
            1f
        } else {
            0.45f
        }

        binding.rowLimitTemperature.alpha = protectionAlpha
        binding.rowLightReduction.alpha = protectionAlpha
        binding.rowRecoveryInterval.alpha = protectionAlpha

        binding.rowLimitTemperature.isEnabled = state.temperatureProtectionEnabled
        binding.rowLightReduction.isEnabled = state.temperatureProtectionEnabled
        binding.rowRecoveryInterval.isEnabled = state.temperatureProtectionEnabled

        isRendering = false
    }

    override fun onResume() {
    super.onResume()
    viewModel.refreshTimes()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_DEVICE_ID = "deviceId"
    }
}