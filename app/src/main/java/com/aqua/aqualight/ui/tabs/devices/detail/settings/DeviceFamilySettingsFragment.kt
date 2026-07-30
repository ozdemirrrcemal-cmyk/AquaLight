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
import com.aqua.aqualight.NavDevicesDirections
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

@Suppress("TooManyFunctions")
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
                titleOverride = getString(copy.screenTitleRes),
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
            saveText = getString(R.string.common_save),
            cancelText = getString(R.string.common_cancel),
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
            NavDevicesDirections.actionGlobalDeviceFirmwareUpdateFragment(deviceUid)
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

        renderFirmwareState(state)
        renderLightInventory(
            show = state.showLightProtectionInventory,
            copy = copy.lightCopy,
            unavailable = unavailable
        )
    }

    @Suppress("CyclomaticComplexMethod")
    private fun renderFirmwareState(state: DeviceFamilySettingsUiState) {
        val statusText: CharSequence?
        val buttonTextRes: Int
        val buttonEnabled: Boolean

        when (val otaState = state.otaState) {
            is DeviceOtaState.Idle -> {
                buttonTextRes = R.string.device_settings_check_updates_action
                statusText = null
                buttonEnabled = true
            }
            is DeviceOtaState.Checking -> {
                buttonTextRes = R.string.device_settings_checking_updates
                statusText = null
                buttonEnabled = false
            }
            is DeviceOtaState.UpToDate -> {
                buttonTextRes = if (state.showUpToDateAction) {
                    R.string.device_settings_up_to_date_action
                } else {
                    R.string.device_settings_check_updates_action
                }
                statusText = getString(R.string.device_settings_up_to_date_status)
                buttonEnabled = true
            }
            is DeviceOtaState.UpdateAvailable -> {
                buttonTextRes = R.string.device_settings_review_update
                statusText = getString(
                    R.string.device_settings_update_available_status,
                    otaState.plan.targetVersion
                )
                buttonEnabled = true
            }
            is DeviceOtaState.Unsupported -> {
                buttonTextRes = R.string.device_settings_check_updates_action
                statusText = getString(R.string.device_settings_update_unsupported)
                buttonEnabled = false
            }
            is DeviceOtaState.Failed -> {
                buttonTextRes = R.string.device_settings_try_again
                statusText = getString(R.string.device_settings_update_check_failed)
                buttonEnabled = true
            }
            is DeviceOtaState.Starting -> {
                buttonTextRes = R.string.device_settings_update_starting
                statusText = getString(R.string.device_settings_update_starting)
                buttonEnabled = false
            }
            is DeviceOtaState.InProgress -> {
                val progress = otaState.progressPermille / PERMILLE_PER_PERCENT
                buttonTextRes = R.string.device_settings_update_in_progress
                statusText = getString(R.string.device_settings_update_in_progress, progress)
                buttonEnabled = false
            }
            is DeviceOtaState.Recovering -> {
                val progress = otaState.progressPermille / PERMILLE_PER_PERCENT
                buttonTextRes = R.string.device_settings_update_recovering
                statusText = getString(R.string.device_settings_update_recovering, progress)
                buttonEnabled = false
            }
            is DeviceOtaState.RestartRequired -> {
                buttonTextRes = R.string.device_settings_restart_required
                statusText = getString(R.string.device_settings_restart_required)
                buttonEnabled = false
            }
            is DeviceOtaState.Succeeded -> {
                buttonTextRes = R.string.device_settings_check_updates_action
                statusText = getString(R.string.device_settings_update_complete)
                buttonEnabled = true
            }
        }

        binding.btnCheckForUpdates.apply {
            isEnabled = buttonEnabled
            if (
                state.otaState is DeviceOtaState.InProgress ||
                state.otaState is DeviceOtaState.Recovering
            ) {
                text = statusText
            } else {
                setText(buttonTextRes)
            }
        }
        binding.tvFirmwareUpdateStatus.apply {
            isVisible = !statusText.isNullOrBlank()
            text = statusText
        }
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
            inflated.tvTemperatureProtectionThresholdLabel.setText(copy.threshold.labelRes)
            inflated.btnEditTemperatureProtectionThreshold.contentDescription = getString(
                copy.threshold.editDescriptionRes
            )
        }

        section.root.isVisible = true
        section.tvCoolingAutoOffValue.text = unavailable
        section.tvOverTemperatureProtectionValue.text = unavailable
        section.tvTemperatureProtectionThresholdValue.setText(copy.threshold.pendingValueRes)
    }

    override fun onDestroyView() {
        lightSectionBinding = null
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val DEVICE_NAME_REQUEST_KEY = "device_settings_name_request"
        const val PERMILLE_PER_PERCENT = 10
        val SETTINGS_DESTINATIONS = setOf(
            R.id.deviceLightSettingsFragment,
            R.id.deviceDosingSettingsFragment,
            R.id.deviceTimerSettingsFragment,
            R.id.deviceCoolingSettingsFragment
        )
    }
}

data class DeviceFamilySettingsCopy(
    @StringRes val screenTitleRes: Int,
    val lightCopy: DeviceLightSettingsCopy? = null
)

data class DeviceLightSettingsCopy(
    @StringRes val sectionTitleRes: Int,
    @StringRes val coolingAutoOffLabelRes: Int,
    @StringRes val overTemperatureProtectionLabelRes: Int,
    val threshold: DeviceThresholdSettingsCopy
)

data class DeviceThresholdSettingsCopy(
    @StringRes val labelRes: Int,
    @StringRes val editDescriptionRes: Int,
    @StringRes val pendingValueRes: Int
)
