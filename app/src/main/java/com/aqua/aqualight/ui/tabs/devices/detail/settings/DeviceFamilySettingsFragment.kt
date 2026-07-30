package com.aqua.aqualight.ui.tabs.devices.detail.settings

import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentDeviceFamilySettingsBinding
import com.aqua.aqualight.databinding.LayoutDeviceLightSettingsSectionBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import kotlinx.coroutines.launch

abstract class DeviceFamilySettingsFragment(
    private val copy: DeviceFamilySettingsCopy
) : Fragment(R.layout.fragment_device_family_settings) {

    protected abstract val deviceUid: String

    private val viewModel: DeviceFamilySettingsViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }

    private var _binding: FragmentDeviceFamilySettingsBinding? = null
    private val binding get() = _binding!!
    private var lightSectionBinding: LayoutDeviceLightSettingsSectionBinding? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        require(deviceUid.isNotBlank()) {
            "Family Settings requires a non-blank device UID."
        }

        _binding = FragmentDeviceFamilySettingsBinding.bind(view)
        setupHeader()
        applyStaticCopy()
        observeSettings()
        viewModel.bind(deviceUid)
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(copy.screenTitleRes),
                onBackClick = {
                    findNavController().navigateUp()
                }
            )
        )
    }

    private fun applyStaticCopy() {
        binding.tvDeviceInformationSectionTitle.setText(copy.deviceInformationTitleRes)
        binding.tvDeviceNameLabel.setText(copy.deviceNameLabelRes)
        binding.btnEditDeviceName.contentDescription = getString(copy.editDeviceNameDescriptionRes)
        binding.tvSerialNumberLabel.setText(copy.serialNumberLabelRes)
        binding.tvHardwareRevisionLabel.setText(copy.hardwareRevisionLabelRes)
        binding.tvSoftwareSectionTitle.setText(copy.softwareTitleRes)
        binding.tvFirmwareVersionLabel.setText(copy.firmwareVersionLabelRes)
        binding.btnCheckForUpdates.setText(copy.checkForUpdatesActionRes)
    }

    private fun observeSettings() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::renderState)
            }
        }
    }

    private fun renderState(state: DeviceFamilySettingsUiState) {
        if (_binding == null) return
        val unavailable = getString(copy.unavailableValueRes)

        binding.tvDeviceNameValue.text = state.deviceName.ifBlank { unavailable }
        binding.tvSerialNumberValue.text = state.serialNumber.ifBlank { unavailable }
        binding.tvHardwareRevisionValue.text = state.hardwareRevision.ifBlank { unavailable }
        binding.tvFirmwareVersionValue.text = state.firmwareVersion.ifBlank { unavailable }

        renderLightInventory(
            show = state.showLightProtectionInventory,
            copy = copy.lightCopy,
            unavailable = unavailable
        )
    }

    private fun renderLightInventory(
        show: Boolean,
        copy: DeviceLightSettingsCopy?,
        unavailable: String
    ) {
        if (copy == null) return
        if (!show) {
            lightSectionBinding?.root?.isVisible = false
            return
        }

        val section = lightSectionBinding ?: LayoutDeviceLightSettingsSectionBinding.bind(
            binding.lightSettingsSectionStub.inflate()
        ).also { inflated ->
            lightSectionBinding = inflated
            inflated.tvLightProtectionSectionTitle.setText(copy.sectionTitleRes)
            inflated.tvCoolingAutoOffLabel.setText(copy.coolingAutoOffLabelRes)
            inflated.tvOverTemperatureProtectionLabel.setText(
                copy.overTemperatureProtectionLabelRes
            )
            inflated.tvTemperatureProtectionThresholdLabel.setText(copy.thresholdLabelRes)
            inflated.btnEditTemperatureProtectionThreshold.contentDescription = getString(
                copy.editThresholdDescriptionRes
            )
        }

        section.root.isVisible = true
        section.tvCoolingAutoOffValue.text = unavailable
        section.tvOverTemperatureProtectionValue.text = unavailable
        section.tvTemperatureProtectionThresholdValue.setText(copy.thresholdPendingValueRes)
    }

    override fun onDestroyView() {
        lightSectionBinding = null
        _binding = null
        super.onDestroyView()
    }
}

data class DeviceFamilySettingsCopy(
    @StringRes val screenTitleRes: Int,
    @StringRes val deviceInformationTitleRes: Int,
    @StringRes val deviceNameLabelRes: Int,
    @StringRes val editDeviceNameDescriptionRes: Int,
    @StringRes val serialNumberLabelRes: Int,
    @StringRes val hardwareRevisionLabelRes: Int,
    @StringRes val softwareTitleRes: Int,
    @StringRes val firmwareVersionLabelRes: Int,
    @StringRes val checkForUpdatesActionRes: Int,
    @StringRes val unavailableValueRes: Int,
    val lightCopy: DeviceLightSettingsCopy? = null
)

data class DeviceLightSettingsCopy(
    @StringRes val sectionTitleRes: Int,
    @StringRes val coolingAutoOffLabelRes: Int,
    @StringRes val overTemperatureProtectionLabelRes: Int,
    @StringRes val thresholdLabelRes: Int,
    @StringRes val editThresholdDescriptionRes: Int,
    @StringRes val thresholdPendingValueRes: Int
)
