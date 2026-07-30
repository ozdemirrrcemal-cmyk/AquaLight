package com.aqua.aqualight.ui.tabs.devices.detail.settings

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DEVICE_CUSTOM_NAME_MAX_LENGTH
import com.aqua.aqualight.application.devices.DeviceOtaState
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentDeviceFamilySettingsBinding
import com.aqua.aqualight.databinding.LayoutDeviceLightSettingsSectionBinding
import com.aqua.aqualight.ui.common.bottomsheet.TextInputBottomSheet
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import kotlinx.coroutines.launch

/**
 * Shared commercial Settings shell for every AquaLight device family.
 *
 * Family entry fragments only provide a device UID. Device information, software actions,
 * centralized text-input presentation and optional Light protection inventory remain owned here.
 */
@Suppress("TooManyFunctions")
abstract class DeviceFamilySettingsFragment : Fragment(R.layout.fragment_device_family_settings) {

    protected abstract val deviceUid: String

    private val viewModel: DeviceFamilySettingsViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }

    private var _binding: FragmentDeviceFamilySettingsBinding? = null
    private val binding get() = _binding!!
    private var lightSectionBinding: LayoutDeviceLightSettingsSectionBinding? = null
    private var latestState = DeviceFamilySettingsUiState()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        require(deviceUid.isNotBlank()) {
            "Family Settings requires a non-blank device UID."
        }

        _binding = FragmentDeviceFamilySettingsBinding.bind(view)
        setupHeader()
        applyStaticCopy()
        setupDeviceNameResult()
        setupActions()
        observeSettings()
        viewModel.bind(deviceUid)
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.device_settings_title),
                onBackClick = {
                    findNavController().navigateUp()
                }
            )
        )
    }

    private fun applyStaticCopy() {
        binding.tvDeviceInformationSectionTitle.setText(
            R.string.device_settings_device_information_section
        )
        binding.tvDeviceNameLabel.setText(R.string.device_settings_device_name_label)
        binding.tvEditDeviceNameAction.setText(R.string.device_settings_edit_action)
        binding.deviceNameRow.contentDescription = getString(
            R.string.device_settings_edit_device_name_description
        )
        binding.tvSerialNumberLabel.setText(R.string.device_settings_serial_number_label)
        binding.tvHardwareRevisionLabel.setText(
            R.string.device_settings_hardware_revision_label
        )
        binding.tvSoftwareSectionTitle.setText(R.string.device_settings_software_section)
        binding.tvFirmwareVersionLabel.setText(
            R.string.device_settings_firmware_version_label
        )
        binding.btnCheckForUpdates.setText(R.string.device_settings_check_updates_action)
    }

    private fun setupDeviceNameResult() {
        childFragmentManager.setFragmentResultListener(
            DEVICE_NAME_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            if (
                result.getString(TextInputBottomSheet.RESULT_KEY) !=
                TextInputBottomSheet.RESULT_SAVED
            ) {
                return@setFragmentResultListener
            }
            if (result.getString(TextInputBottomSheet.RESULT_PAYLOAD_ID) != deviceUid) {
                return@setFragmentResultListener
            }
            viewModel.updateDeviceName(
                result.getString(TextInputBottomSheet.RESULT_VALUE).orEmpty()
            )
        }
    }

    private fun setupActions() {
        binding.deviceNameRow.setOnClickListener {
            openDeviceNameEditor()
        }
        binding.btnCheckForUpdates.setOnClickListener {
            when (latestState.otaState) {
                is DeviceOtaState.UpdateAvailable -> openFirmwareUpdateScreen()
                is DeviceOtaState.Checking,
                is DeviceOtaState.Starting,
                is DeviceOtaState.InProgress,
                is DeviceOtaState.Recovering -> Unit
                else -> viewModel.checkForUpdates()
            }
        }
    }

    private fun openDeviceNameEditor() {
        if (latestState.isSavingDeviceName) return
        TextInputBottomSheet.show(
            fragmentManager = childFragmentManager,
            title = getString(R.string.device_settings_change_name_title),
            label = getString(R.string.device_settings_device_name_label),
            hint = getString(R.string.device_settings_device_name_label),
            initialValue = latestState.deviceName,
            saveText = getString(R.string.device_settings_save_action),
            cancelText = getString(R.string.device_settings_cancel_action),
            required = true,
            requiredMessage = getString(R.string.device_settings_device_name_required),
            requestKey = DEVICE_NAME_REQUEST_KEY,
            payloadId = deviceUid,
            maxLength = DEVICE_CUSTOM_NAME_MAX_LENGTH,
            disableSaveWhenUnchanged = true,
            requestFocus = true
        )
    }

    private fun openFirmwareUpdateScreen() {
        val navController = findNavController()
        if (navController.currentDestination?.id !in SETTINGS_DESTINATIONS) return
        navController.navigate(
            R.id.action_global_deviceFirmwareUpdateFragment,
            bundleOf(DEVICE_UID_ARGUMENT to deviceUid)
        )
    }

    private fun observeSettings() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect(::renderState)
                }
                launch {
                    viewModel.events.collect(::renderEvent)
                }
            }
        }
    }

    private fun renderEvent(event: DeviceFamilySettingsEvent) {
        val activity = activity as? BaseActivity ?: return
        when (event) {
            DeviceFamilySettingsEvent.DeviceNameUpdated -> activity.showSnackBar(
                getString(R.string.device_settings_device_name_updated),
                BaseActivity.SnackType.SUCCESS
            )
            DeviceFamilySettingsEvent.DeviceNameUpdateFailed -> activity.showSnackBar(
                getString(R.string.device_settings_device_name_update_failed),
                BaseActivity.SnackType.ERROR
            )
        }
    }

    private fun renderState(state: DeviceFamilySettingsUiState) {
        if (_binding == null) return
        latestState = state
        val unavailable = getString(R.string.common_not_available_em_dash)

        binding.tvDeviceNameValue.text = state.deviceName.ifBlank { unavailable }
        binding.tvSerialNumberValue.text = state.serialNumber.ifBlank { unavailable }
        binding.tvHardwareRevisionValue.text = state.hardwareRevision.ifBlank { unavailable }
        binding.tvFirmwareVersionValue.text = state.firmwareVersion.ifBlank { unavailable }
        binding.deviceNameRow.isEnabled = !state.isSavingDeviceName
        binding.tvEditDeviceNameAction.alpha = if (state.isSavingDeviceName) {
            DISABLED_ACTION_ALPHA
        } else {
            ENABLED_ACTION_ALPHA
        }

        renderFirmwareState(state)
        renderLightInventory(
            show = state.showLightProtectionInventory,
            unavailable = unavailable
        )
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun renderFirmwareState(state: DeviceFamilySettingsUiState) {
        val presentation = when (val otaState = state.otaState) {
            is DeviceOtaState.Idle -> FirmwareActionPresentation(
                buttonText = getString(R.string.device_settings_check_updates_action),
                enabled = true
            )
            is DeviceOtaState.Checking -> FirmwareActionPresentation(
                buttonText = getString(R.string.device_settings_checking_updates),
                enabled = false,
                showProgress = true
            )
            is DeviceOtaState.UpToDate -> if (state.showUpToDateAction) {
                FirmwareActionPresentation(
                    buttonText = getString(R.string.device_settings_firmware_up_to_date),
                    enabled = false
                )
            } else {
                FirmwareActionPresentation(
                    buttonText = getString(R.string.device_settings_check_updates_action),
                    enabled = true
                )
            }
            is DeviceOtaState.UpdateAvailable -> FirmwareActionPresentation(
                buttonText = getString(R.string.device_settings_view_update_action),
                statusText = getString(
                    R.string.device_settings_update_available_status,
                    otaState.plan.targetVersion
                ),
                enabled = true
            )
            is DeviceOtaState.Unsupported -> FirmwareActionPresentation(
                buttonText = getString(R.string.device_settings_check_updates_action),
                statusText = getString(R.string.device_settings_update_unsupported),
                enabled = false
            )
            is DeviceOtaState.Failed -> FirmwareActionPresentation(
                buttonText = getString(R.string.device_settings_try_again),
                statusText = getString(R.string.device_settings_update_check_failed),
                enabled = true
            )
            is DeviceOtaState.Starting -> FirmwareActionPresentation(
                buttonText = getString(R.string.device_settings_update_starting),
                statusText = getString(R.string.device_settings_update_starting),
                enabled = false
            )
            is DeviceOtaState.InProgress -> {
                val progress = otaState.progressPermille / PERMILLE_PER_PERCENT
                FirmwareActionPresentation(
                    buttonText = getString(
                        R.string.device_settings_update_in_progress,
                        progress
                    ),
                    statusText = getString(
                        R.string.device_settings_update_in_progress,
                        progress
                    ),
                    enabled = false
                )
            }
            is DeviceOtaState.Recovering -> {
                val progress = otaState.progressPermille / PERMILLE_PER_PERCENT
                FirmwareActionPresentation(
                    buttonText = getString(
                        R.string.device_settings_update_recovering,
                        progress
                    ),
                    statusText = getString(
                        R.string.device_settings_update_recovering,
                        progress
                    ),
                    enabled = false
                )
            }
            is DeviceOtaState.RestartRequired -> FirmwareActionPresentation(
                buttonText = getString(R.string.device_settings_restart_required),
                statusText = getString(R.string.device_settings_restart_required),
                enabled = false
            )
            is DeviceOtaState.Succeeded -> FirmwareActionPresentation(
                buttonText = getString(R.string.device_settings_check_updates_action),
                statusText = getString(R.string.device_settings_update_complete),
                enabled = true
            )
        }

        binding.progressCheckForUpdates.isVisible = presentation.showProgress
        binding.btnCheckForUpdates.apply {
            isEnabled = presentation.enabled
            text = if (presentation.showProgress) "" else presentation.buttonText
            contentDescription = presentation.buttonText
        }
        binding.tvFirmwareUpdateStatus.apply {
            isVisible = !presentation.statusText.isNullOrBlank()
            text = presentation.statusText
        }
    }

    private fun renderLightInventory(
        show: Boolean,
        unavailable: String
    ) {
        if (!show) {
            lightSectionBinding?.root?.isVisible = false
            return
        }

        val section = lightSectionBinding ?: LayoutDeviceLightSettingsSectionBinding.bind(
            binding.lightSettingsSectionStub.inflate()
        ).also { inflated ->
            lightSectionBinding = inflated
            inflated.tvLightProtectionSectionTitle.setText(
                R.string.device_settings_light_protection_section
            )
            inflated.tvCoolingAutoOffLabel.setText(
                R.string.device_settings_light_cooling_auto_off_label
            )
            inflated.tvOverTemperatureProtectionLabel.setText(
                R.string.device_settings_light_over_temperature_protection_label
            )
            inflated.tvTemperatureProtectionThresholdLabel.setText(
                R.string.device_settings_light_temperature_threshold_label
            )
            inflated.btnEditTemperatureProtectionThreshold.contentDescription = getString(
                R.string.device_settings_light_edit_temperature_threshold_description
            )
        }

        section.root.isVisible = true
        section.tvCoolingAutoOffValue.text = unavailable
        section.tvOverTemperatureProtectionValue.text = unavailable
        section.tvTemperatureProtectionThresholdValue.setText(
            R.string.device_settings_light_temperature_threshold_pending_value
        )
    }

    override fun onDestroyView() {
        lightSectionBinding = null
        _binding = null
        super.onDestroyView()
    }

    private data class FirmwareActionPresentation(
        val buttonText: CharSequence,
        val statusText: CharSequence? = null,
        val enabled: Boolean,
        val showProgress: Boolean = false
    )

    private companion object {
        const val DEVICE_NAME_REQUEST_KEY = "device_settings_name_request"
        const val DEVICE_UID_ARGUMENT = "deviceUid"
        const val PERMILLE_PER_PERCENT = 10
        const val ENABLED_ACTION_ALPHA = 1.0f
        const val DISABLED_ACTION_ALPHA = 0.5f
        val SETTINGS_DESTINATIONS = setOf(
            R.id.deviceLightSettingsFragment,
            R.id.deviceDosingSettingsFragment,
            R.id.deviceTimerSettingsFragment,
            R.id.deviceCoolingSettingsFragment
        )
    }
}
