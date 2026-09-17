package com.aqua.aqualight.ui.tabs.devices.detail.settings

import android.os.Bundle
import android.view.View
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
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
import com.aqua.aqualight.ui.common.bottomsheet.TextInputBottomSheet
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootPresentationMapper
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import kotlinx.coroutines.launch

/**
 * Shared commercial Settings shell for every AquaLight device family.
 *
 * Family entry fragments provide only the active device UID. Shared Settings presentation and
 * centralized components remain owned here.
 */
@Suppress("TooManyFunctions")
abstract class DeviceFamilySettingsFragment : Fragment(R.layout.fragment_device_family_settings) {

    protected abstract val deviceUid: String

    private val viewModel: DeviceFamilySettingsViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }

    private var _binding: FragmentDeviceFamilySettingsBinding? = null
    private val binding get() = _binding!!
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
    }

    private fun setupDeviceNameResult() {
        childFragmentManager.setFragmentResultListener(
            DEVICE_NAME_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            if (
                !isSavedSettingsEditorResult(
                    result = result.getString(TextInputBottomSheet.RESULT_KEY),
                    payloadId = result.getString(TextInputBottomSheet.RESULT_PAYLOAD_ID),
                    expectedPayloadId = deviceUid,
                    savedResult = TextInputBottomSheet.RESULT_SAVED
                )
            ) {
                return@setFragmentResultListener
            }
            val customName = result.getString(TextInputBottomSheet.RESULT_VALUE).orEmpty()
            if (customName.isBlank()) {
                viewModel.resetDeviceNameToDefault()
            } else {
                viewModel.updateDeviceName(customName)
            }
        }
    }

    private fun setupActions() {
        binding.deviceNameRow.setOnClickListener {
            openDeviceNameEditor()
        }
        binding.cardFirmwareUpdateAction.setOnClickListener {
            viewModel.onFirmwareUpdateAction()
        }
    }

    private fun openDeviceNameEditor() {
        if (latestState.deviceNameSaving) return
        val canUseDefaultName = latestState.hasCustomDeviceName &&
            latestState.productDisplayName.isNotBlank()
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
            requestFocus = true,
            presetActionText = if (canUseDefaultName) {
                getString(R.string.device_settings_use_default_name_action)
            } else {
                ""
            },
            presetDisplayValue = latestState.productDisplayName,
            presetResultValue = ""
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
                launch {
                    viewModel.uiState.collect(::renderState)
                }
                launch {
                    viewModel.events.collect(::handleEvent)
                }
            }
        }
    }

    private fun renderState(state: DeviceFamilySettingsUiState) {
        if (_binding == null) return
        latestState = state
        val unavailable = getString(R.string.common_not_available_em_dash)

        binding.tvDeviceNameValue.text = state.deviceName.ifBlank { unavailable }
        binding.deviceNameRow.isEnabled = !state.deviceNameSaving
        binding.ivDeviceNameArrow.isInvisible = state.deviceNameSaving
        binding.tvSerialNumberValue.text = state.serialNumber.ifBlank { unavailable }
        binding.tvHardwareRevisionValue.apply {
            text = state.hardwareRevision.ifBlank { unavailable }
            isInvisible = state.informationLoadState ==
                DeviceSettingsInformationLoadState.LOADING
        }
        binding.tvFirmwareVersionValue.text = if (
            state.firmwareLoadState == DeviceSettingsFirmwareLoadState.READY
        ) {
            state.firmwareVersion.ifBlank { unavailable }
        } else {
            unavailable
        }

        renderUpdateAction(
            state = state.updateActionState,
            installedVersion = state.firmwareVersion,
            firmwareLoadState = state.firmwareLoadState
        )
    }

    private fun renderUpdateAction(
        state: DeviceSettingsUpdateActionState,
        installedVersion: String,
        firmwareLoadState: DeviceSettingsFirmwareLoadState
    ) {
        val presentation = if (
            state == DeviceSettingsUpdateActionState.Idle ||
            state == DeviceSettingsUpdateActionState.ReleaseNotPublished ||
            state == DeviceSettingsUpdateActionState.UpToDate
        ) {
            firmwareLoadState.toFirmwareLoadPresentation()
                ?: state.toFirmwareActionPresentation(installedVersion)
        } else {
            state.toFirmwareActionPresentation(installedVersion)
        }

        binding.tvFirmwareUpdateActionTitle.text = presentation.titleText
        binding.tvFirmwareUpdateActionSubtitle.text = presentation.subtitleText
        binding.progressCheckForUpdates.isVisible = presentation.showProgress
        binding.ivFirmwareUpdateArrow.isVisible = presentation.opensDetails
        binding.cardFirmwareUpdateAction.apply {
            isEnabled = presentation.enabled
            isClickable = presentation.enabled
            isFocusable = presentation.enabled
            setStrokeColor(color(presentation.strokeColorRes))
            contentDescription = getString(
                if (presentation.opensDetails) {
                    R.string.device_settings_update_card_open_details_description
                } else {
                    R.string.device_settings_update_card_content_description
                },
                presentation.titleText,
                presentation.subtitleText
            )
        }
    }

    private fun DeviceSettingsFirmwareLoadState.toFirmwareLoadPresentation():
        FirmwareActionPresentation? = when (this) {
        DeviceSettingsFirmwareLoadState.LOADING -> FirmwareActionPresentation(
            titleText = getString(R.string.device_settings_update_action_loading),
            subtitleText = getString(R.string.device_settings_firmware_loading_description),
            enabled = false,
            showProgress = true,
            strokeColorRes = R.color.aqua_accent_primary
        )
        DeviceSettingsFirmwareLoadState.READY -> null
        DeviceSettingsFirmwareLoadState.CONNECTION_FAILED -> FirmwareActionPresentation(
            titleText = getString(R.string.device_settings_update_reconnect_action),
            subtitleText = getString(R.string.device_settings_update_error_connection),
            enabled = true,
            strokeColorRes = R.color.aqua_status_danger
        )
    }

    private fun DeviceSettingsUpdateActionState.toFirmwareActionPresentation(
        installedVersion: String
    ): FirmwareActionPresentation = when (this) {
        DeviceSettingsUpdateActionState.Idle -> FirmwareActionPresentation(
            titleText = getString(R.string.device_settings_check_updates_action),
            subtitleText = getString(R.string.device_settings_update_check_description),
            enabled = true
        )
        DeviceSettingsUpdateActionState.Checking -> FirmwareActionPresentation(
            titleText = getString(R.string.device_settings_update_status_checking),
            subtitleText = getString(R.string.device_settings_update_checking_description),
            enabled = false,
            showProgress = true,
            strokeColorRes = R.color.aqua_accent_primary
        )
        DeviceSettingsUpdateActionState.UpToDate -> FirmwareActionPresentation(
            titleText = getString(R.string.device_settings_check_updates_action),
            subtitleText = installedFirmwareDescription(installedVersion),
            enabled = true
        )
        DeviceSettingsUpdateActionState.ReleaseNotPublished -> FirmwareActionPresentation(
            titleText = getString(R.string.device_settings_update_status_not_published),
            subtitleText = getString(R.string.device_settings_update_not_published_description),
            enabled = true
        )
        is DeviceSettingsUpdateActionState.UpdateAvailable -> FirmwareActionPresentation(
            titleText = getString(R.string.device_settings_update_status_available),
            subtitleText = getString(R.string.device_settings_update_available_status, version),
            enabled = true,
            opensDetails = true,
            strokeColorRes = R.color.aqua_accent_primary
        )
        is DeviceSettingsUpdateActionState.UpdateInProgress -> FirmwareActionPresentation(
            titleText = getString(R.string.device_settings_update_status_installing),
            subtitleText = getString(
                R.string.device_settings_update_in_progress_status,
                version,
                progressPermille.coerceIn(0, COMPLETE_PROGRESS_PERMILLE) /
                    PERMILLE_PER_PERCENT
            ),
            enabled = true,
            opensDetails = true,
            strokeColorRes = R.color.aqua_accent_primary
        )
        is DeviceSettingsUpdateActionState.Failed -> toFailedFirmwareActionPresentation()
        is DeviceSettingsUpdateActionState.PostUpdateAttention ->
            toPostUpdatePresentation()
        DeviceSettingsUpdateActionState.Unsupported -> FirmwareActionPresentation(
            titleText = getString(R.string.device_settings_update_status_unsupported),
            subtitleText = getString(R.string.device_settings_update_unsupported_status),
            enabled = false
        )
    }

    private fun DeviceSettingsUpdateActionState.Failed.toFailedFirmwareActionPresentation() =
        FirmwareActionPresentation(
            titleText = getString(
                if (failure.canRetryAvailabilityCheck) {
                    R.string.device_settings_retry_update_check_action
                } else {
                    R.string.device_settings_update_needs_attention_title
                }
            ),
            subtitleText = getString(
                DeviceRootPresentationMapper.otaFailureMessageRes(failure.reason)
            ),
            enabled = true,
            opensDetails = !failure.canRetryAvailabilityCheck,
            strokeColorRes = R.color.aqua_status_danger
        )

    private fun DeviceSettingsUpdateActionState.PostUpdateAttention.toPostUpdatePresentation() =
        FirmwareActionPresentation(
            titleText = getString(R.string.device_settings_update_needs_attention_title),
            subtitleText = getString(
                when (kind) {
                    DeviceSettingsUpdateAttention.ROLLED_BACK ->
                        R.string.device_settings_update_card_rolled_back_description
                    DeviceSettingsUpdateAttention.CONNECTION_TIMEOUT ->
                        R.string.device_settings_update_card_timeout_description
                    DeviceSettingsUpdateAttention.UNEXPECTED_FIRMWARE ->
                        R.string.device_settings_update_card_unexpected_description
                }
            ),
            enabled = true,
            opensDetails = true,
            strokeColorRes = R.color.aqua_content_warning
        )

    private fun installedFirmwareDescription(installedVersion: String): CharSequence {
        return if (installedVersion.isBlank()) {
            getString(R.string.device_settings_update_installed_unknown_description)
        } else {
            getString(
                R.string.device_settings_update_up_to_date_description,
                installedVersion
            )
        }
    }

    private fun color(@ColorRes colorRes: Int): Int {
        return ContextCompat.getColor(requireContext(), colorRes)
    }

    private fun handleEvent(event: DeviceFamilySettingsEvent) {
        when (event) {
            DeviceFamilySettingsEvent.DeviceNameUpdateFailed -> showSaveFailure(
                R.string.device_settings_device_name_save_failed_message
            )
            DeviceFamilySettingsEvent.OpenFirmwareUpdate -> openFirmwareUpdateScreen()
        }
    }

    private fun showSaveFailure(@StringRes messageRes: Int) {
        DialogManager.showInfoDialog(
            context = requireContext(),
            type = DialogType.ERROR,
            title = getString(R.string.device_settings_save_failed_title),
            message = getString(messageRes)
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private data class FirmwareActionPresentation(
        val titleText: CharSequence,
        val subtitleText: CharSequence,
        val enabled: Boolean,
        val opensDetails: Boolean = false,
        val showProgress: Boolean = false,
        @ColorRes val strokeColorRes: Int = R.color.aqua_card_device_section_outline
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

internal fun isSavedSettingsEditorResult(
    result: String?,
    payloadId: String?,
    expectedPayloadId: String,
    savedResult: String
): Boolean = result == savedResult && payloadId == expectedPayloadId
