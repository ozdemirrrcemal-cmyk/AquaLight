package com.aqua.aqualight.ui.tabs.devices.detail.settings

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.NavAquariumDirections
import com.aqua.aqualight.NavDevicesDirections
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DEVICE_CUSTOM_NAME_MAX_LENGTH
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentDeviceFamilySettingsBinding
import com.aqua.aqualight.databinding.LayoutDeviceLightSettingsSectionBinding
import com.aqua.aqualight.ui.common.bottomsheet.TextInputBottomSheet
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootPresentationMapper
import kotlinx.coroutines.launch

/**
 * Shared commercial Settings shell for every AquaLight device family.
 *
 * Family entry fragments provide only the active device UID. Shared Settings presentation,
 * centralized components and the capability-gated Light inventory remain owned here.
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
        // ViewStub transfers its own layout params to the inflated card, so section spacing belongs
        // on the stub and uses the same centralized token as the preceding Settings card.
        binding.lightSettingsSectionStub.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = resources.getDimensionPixelSize(R.dimen.aqua_size_14)
        }
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
            viewModel.previewDeviceName(
                result.getString(TextInputBottomSheet.RESULT_VALUE).orEmpty()
            )
        }
    }

    private fun setupActions() {
        binding.deviceNameRow.setOnClickListener {
            openDeviceNameEditor()
        }
        binding.btnCheckForUpdates.setOnClickListener {
            when (val updateState = latestState.updateActionState) {
                is DeviceSettingsUpdateActionState.UpdateAvailable,
                is DeviceSettingsUpdateActionState.UpdateInProgress ->
                    openFirmwareUpdateScreen()
                DeviceSettingsUpdateActionState.Checking -> Unit
                DeviceSettingsUpdateActionState.Idle,
                DeviceSettingsUpdateActionState.UpToDate,
                DeviceSettingsUpdateActionState.Unsupported ->
                    viewModel.checkForUpdates()
                is DeviceSettingsUpdateActionState.Failed -> {
                    if (updateState.failure.recoverable) viewModel.checkForUpdates()
                    else openFirmwareUpdateScreen()
                }
            }
        }
    }

    private fun openDeviceNameEditor() {
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
        val direction = navController.currentDestination
            ?.takeIf { destination -> destination.id in SETTINGS_DESTINATIONS }
            ?.let(::firmwareUpdateDirection)
            ?: return
        navController.navigate(direction)
    }

    private fun firmwareUpdateDirection(destination: NavDestination): NavDirections? {
        val ownerGraphId = destination.hierarchy
            .map { node -> node.id }
            .firstOrNull { graphId ->
                graphId == R.id.nav_devices || graphId == R.id.nav_aquarium
            }
        return when (ownerGraphId) {
            R.id.nav_devices -> NavDevicesDirections
                .actionGlobalDeviceFirmwareUpdateFragment(deviceUid)
            R.id.nav_aquarium -> NavAquariumDirections
                .actionGlobalDeviceFirmwareUpdateFragment(deviceUid)
            else -> null
        }
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
        latestState = state
        val unavailable = getString(R.string.common_not_available_em_dash)

        binding.tvDeviceNameValue.text = state.deviceName.ifBlank { unavailable }
        binding.tvSerialNumberValue.text = state.serialNumber.ifBlank { unavailable }
        binding.tvHardwareRevisionValue.apply {
            text = state.hardwareRevision.ifBlank { unavailable }
            isInvisible = state.informationLoadState ==
                DeviceSettingsInformationLoadState.LOADING
        }
        binding.tvFirmwareVersionValue.text = state.firmwareVersion.ifBlank { unavailable }

        renderUpdateAction(state.updateActionState)
        renderLightInventory(
            show = state.showLightProtectionInventory,
            unavailable = unavailable
        )
    }

    private fun renderUpdateAction(state: DeviceSettingsUpdateActionState) {
        val presentation = state.toFirmwareActionPresentation()

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

    private fun DeviceSettingsUpdateActionState.toFirmwareActionPresentation(): FirmwareActionPresentation =
        when (this) {
            DeviceSettingsUpdateActionState.Idle -> FirmwareActionPresentation(
                buttonText = getString(R.string.device_settings_check_updates_action),
                enabled = true
            )
            DeviceSettingsUpdateActionState.Checking -> FirmwareActionPresentation(
                buttonText = getString(R.string.device_settings_checking_updates),
                enabled = false,
                showProgress = true
            )
            DeviceSettingsUpdateActionState.UpToDate -> FirmwareActionPresentation(
                buttonText = getString(R.string.device_settings_firmware_up_to_date),
                enabled = false
            )
            is DeviceSettingsUpdateActionState.UpdateAvailable -> FirmwareActionPresentation(
                buttonText = getString(R.string.device_settings_view_update_action),
                statusText = getString(
                    R.string.device_settings_update_available_status,
                    version
                ),
                enabled = true
            )
            is DeviceSettingsUpdateActionState.UpdateInProgress -> FirmwareActionPresentation(
                buttonText = getString(R.string.device_settings_view_update_action),
                statusText = getString(
                    R.string.device_settings_update_in_progress_status,
                    version,
                    progressPermille.coerceIn(
                        0,
                        COMPLETE_PROGRESS_PERMILLE
                    ) / PERMILLE_PER_PERCENT
                ),
                enabled = true
            )
            is DeviceSettingsUpdateActionState.Failed -> FirmwareActionPresentation(
                buttonText = getString(
                    if (failure.recoverable) {
                        R.string.device_settings_retry_update_check_action
                    } else {
                        R.string.device_settings_view_update_action
                    }
                ),
                statusText = getString(
                    DeviceRootPresentationMapper.otaFailureMessageRes(failure.reason)
                ),
                enabled = true
            )
            DeviceSettingsUpdateActionState.Unsupported -> FirmwareActionPresentation(
                buttonText = getString(R.string.device_settings_check_updates_action),
                statusText = getString(R.string.device_settings_update_unsupported_status),
                enabled = false
            )
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
        const val COMPLETE_PROGRESS_PERMILLE = 1_000
        const val PERMILLE_PER_PERCENT = 10
        const val DEVICE_NAME_REQUEST_KEY = "device_settings_name_request"
        val SETTINGS_DESTINATIONS = setOf(
            R.id.deviceLightSettingsFragment,
            R.id.deviceDosingSettingsFragment,
            R.id.deviceTimerSettingsFragment,
            R.id.deviceCoolingSettingsFragment
        )
    }
}
