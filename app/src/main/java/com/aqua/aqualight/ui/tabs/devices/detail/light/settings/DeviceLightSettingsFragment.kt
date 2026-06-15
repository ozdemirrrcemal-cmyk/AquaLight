package com.aqua.aqualight.ui.tabs.devices.detail.light.settings

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceLightSettingsBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.devices.common.feedback.DeviceConfirmBottomSheet
import com.aqua.aqualight.ui.tabs.devices.common.feedback.DeviceConfirmTone
import com.aqua.aqualight.ui.tabs.devices.common.feedback.DeviceFeedbackType
import com.aqua.aqualight.ui.tabs.devices.common.feedback.showDeviceLoading
import com.aqua.aqualight.ui.tabs.devices.common.feedback.showDeviceSnack
import com.aqua.aqualight.ui.tabs.devices.detail.light.settings.model.DeviceLightSettingsEvent
import com.aqua.aqualight.ui.tabs.devices.detail.light.settings.model.DeviceLightSettingsUiState
import com.aqua.aqualight.ui.tabs.devices.detail.light.settings.sheet.LightSettingValueSheet
import kotlinx.coroutines.launch
import androidx.navigation.fragment.navArgs

class DeviceLightSettingsFragment :
    Fragment(R.layout.fragment_device_light_settings) {

    private val args: DeviceLightSettingsFragmentArgs by navArgs()


    private var _binding: FragmentDeviceLightSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DeviceLightSettingsViewModel by viewModels()


    private val deviceId: Long
        get() = args.deviceId

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

    override fun onStart() {
        super.onStart()
        viewModel.onSettingsVisible()
    }

    override fun onStop() {
        viewModel.onSettingsHidden()
        super.onStop()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = "Light Settings"
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

        binding.rowLimitTemperature.setOnClickListener {
            val state = viewModel.uiState.value

            LightSettingValueSheet
                .create(requireContext())
                .show(
                    title = "Limit Temperature",
                    subtitle = "Light output is reduced above this controller temperature.",
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
                    subtitle = "Output percentage used while thermal protection is active.",
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
                    subtitle = "How often the controller recalculates recovery after cooling.",
                    values = listOf(15, 30, 45, 60, 90, 120, 180, 240, 300),
                    suffix = "s",
                    initialValue = state.recoveryIntervalSeconds
                ) { value ->
                    viewModel.updateRecoveryInterval(value)
                }
        }

        binding.rowCoolingMode.setOnClickListener {
            showCoolingModeConfirm()
        }

        binding.rowFanStart.setOnClickListener {
            val state = viewModel.uiState.value

            LightSettingValueSheet
                .create(requireContext())
                .show(
                    title = "Fan Start",
                    subtitle = "Fan cooling starts above this temperature.",
                    values = (25..45).toList(),
                    suffix = "°C",
                    initialValue = state.fanStartTemperatureCelsius
                ) { value ->
                    viewModel.updateFanStartTemperature(value)
                }
        }

        binding.rowFanFullSpeed.setOnClickListener {
            val state = viewModel.uiState.value

            val minFullSpeed =
                (state.fanStartTemperatureCelsius + 5).coerceAtMost(70)

            LightSettingValueSheet
                .create(requireContext())
                .show(
                    title = "Full Speed",
                    subtitle = "Fan reaches full speed at this temperature.",
                    values = (minFullSpeed..70).toList(),
                    suffix = "°C",
                    initialValue = state.fanFullSpeedTemperatureCelsius
                        .coerceAtLeast(minFullSpeed)
                ) { value ->
                    viewModel.updateFanFullSpeedTemperature(value)
                }
        }
    }

    private fun showCoolingModeConfirm() {
        val state = viewModel.uiState.value
        val enableCooling = !state.coolingModeEnabled

        val title = if (enableCooling) {
            "Enable cooling?"
        } else {
            "Disable cooling?"
        }

        val message = if (enableCooling) {
            "Fans will run automatically when the controller temperature rises above the configured start value."
        } else {
            "Fans will stay off. Thermal light protection will still reduce output if the controller gets too hot."
        }

        val confirmText = if (enableCooling) {
            "Enable"
        } else {
            "Disable"
        }

        val tone = if (enableCooling) {
            DeviceConfirmTone.INFO
        } else {
            DeviceConfirmTone.WARNING
        }

        DeviceConfirmBottomSheet
            .create(requireContext())
            .show(
                title = title,
                message = message,
                confirmText = confirmText,
                cancelText = "Cancel",
                tone = tone,
                onConfirm = {
                    viewModel.updateCoolingMode(
                        enabled = enableCooling
                    )
                }
            )
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
                            showDeviceSnack(
                                message = event.message,
                                type = DeviceFeedbackType.SUCCESS
                            )
                        }

                        is DeviceLightSettingsEvent.ShowWarning -> {
                            showDeviceSnack(
                                message = event.message,
                                type = DeviceFeedbackType.WARNING
                            )
                        }

                        is DeviceLightSettingsEvent.ShowError -> {
                            showDeviceSnack(
                                message = event.message,
                                type = DeviceFeedbackType.ERROR
                            )
                        }

                        is DeviceLightSettingsEvent.SetLoading -> {
                            showDeviceLoading(event.isLoading)
                        }
                    }
                }
            }
        }
    }

    private fun renderUiState(
        state: DeviceLightSettingsUiState
    ) {
        binding.tvDeviceName.text = state.deviceName.ifBlank {
            "AquaLight"
        }

        binding.tvDeviceType.text = state.deviceType.ifBlank {
            "Light Controller"
        }

        binding.tvFirmwareVersion.text = state.firmwareVersion.ifBlank {
            "—"
        }

        binding.tvDeviceIp.text = state.lastKnownIpText.ifBlank {
            "—"
        }

        binding.tvSerialNumber.text = state.serialNumber.ifBlank {
            "—"
        }

        renderIdentityRow(
            row = binding.rowProductId,
            textView = binding.tvProductId,
            value = state.productId
        )

        renderIdentityRow(
            row = binding.rowProductKey,
            textView = binding.tvProductKey,
            value = state.productKey
        )

        renderIdentityRow(
            row = binding.rowSkuCode,
            textView = binding.tvSkuCode,
            value = state.skuCode
        )

        renderIdentityRow(
            row = binding.rowSetupCode,
            textView = binding.tvSetupCode,
            value = state.setupCode
        )

        renderIdentityRow(
            row = binding.rowDeviceUid,
            textView = binding.tvDeviceUid,
            value = state.deviceUid
        )

        renderIdentityRow(
            row = binding.rowMacAddress,
            textView = binding.tvMacAddress,
            value = state.macAddress
        )

        renderIdentityRow(
            row = binding.rowHardwareRevision,
            textView = binding.tvHardwareRevision,
            value = state.hardwareRevision
        )

        renderIdentityRow(
            row = binding.rowProtocolVersion,
            textView = binding.tvProtocolVersion,
            value = state.protocolVersion
        )

        binding.tvDeviceTime.text = state.deviceTime.ifBlank {
            "--:--"
        }

        binding.tvPhoneTime.text = state.phoneTime.ifBlank {
            "--:--"
        }

        binding.tvLastSyncTime.text = state.lastSyncTime.ifBlank {
            "Never"
        }

        binding.tvThermalProtectionStatus.text =
            state.thermalProtectionStatusText.ifBlank {
                "Protected"
            }

        binding.tvCurrentTemperatureValue.text =
            normalizeTemperatureText(
                state.currentTemperatureText.ifBlank {
                    "-- °C"
                }
            )

        binding.tvLimitTemperatureValue.text =
            formatCelsius(state.limitTemperatureCelsius)

        binding.tvLightReductionValue.text =
            "${state.lightReductionPercent}%"

        binding.tvRecoveryIntervalValue.text =
            "${state.recoveryIntervalSeconds} s"

        binding.tvCoolingStatus.text =
            state.coolingStatusText.ifBlank {
                "Standby"
            }

        binding.tvCoolingControllerTemp.text =
            normalizeTemperatureText(
                state.currentTemperatureText.ifBlank {
                    "-- °C"
                }
            )

        binding.tvCoolingFans.text =
            state.coolingFansText.ifBlank {
                "—"
            }

        binding.tvCoolingMode.text =
            state.coolingMode.ifBlank {
                "Auto"
            }

        binding.tvFanStartValue.text =
            formatCelsius(state.fanStartTemperatureCelsius)

        binding.tvFanFullSpeedValue.text =
            formatCelsius(state.fanFullSpeedTemperatureCelsius)

        renderControlAvailability(
            state
        )
    }

    private fun renderIdentityRow(
        row: View,
        textView: TextView,
        value: String
    ) {
        val displayValue = value.trim()
        row.isVisible = displayValue.isNotBlank()
        textView.text = displayValue.ifBlank {
            "—"
        }
    }

    private fun renderControlAvailability(
        state: DeviceLightSettingsUiState
    ) {
        val enabled = state.controlsEnabled
        val alpha = if (enabled) {
            1f
        } else {
            0.52f
        }

        binding.btnSyncTime.isEnabled = enabled
        binding.btnUpdateFirmware.isEnabled = enabled
        binding.rowLimitTemperature.isEnabled = enabled
        binding.rowLightReduction.isEnabled = enabled
        binding.rowRecoveryInterval.isEnabled = enabled
        binding.rowCoolingMode.isEnabled = enabled
        binding.rowFanStart.isEnabled = enabled
        binding.rowFanFullSpeed.isEnabled = enabled

        binding.rowLimitTemperature.alpha = alpha
        binding.rowLightReduction.alpha = alpha
        binding.rowRecoveryInterval.alpha = alpha
        binding.rowCoolingMode.alpha = alpha
        binding.rowFanStart.alpha = alpha
        binding.rowFanFullSpeed.alpha = alpha

        if (!state.isDeviceOnline) {
            binding.tvThermalProtectionStatus.text = "Runtime data unavailable"
            binding.tvCoolingStatus.text = "Unavailable"
            binding.tvCoolingMode.text = "Unavailable"
        }
    }

    private fun formatCelsius(
        value: Int
    ): String = "$value °C"

    private fun normalizeTemperatureText(
        value: String
    ): String {
        val normalized = value.trim().ifBlank {
            "-- °C"
        }

        return normalized
            .replace("°C", " °C")
            .replace(Regex("\\s+°C"), " °C")
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_DEVICE_ID = "deviceId"
    }
}
