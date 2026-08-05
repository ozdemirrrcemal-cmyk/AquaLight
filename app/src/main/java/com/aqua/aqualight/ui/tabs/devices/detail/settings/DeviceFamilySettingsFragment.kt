package com.aqua.aqualight.ui.tabs.devices.detail.settings

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
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
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.ui.common.bottomsheet.IntegerStepperBottomSheet
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
        setupTemperatureThresholdResult()
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

    private fun setupTemperatureThresholdResult() {
        childFragmentManager.setFragmentResultListener(
            TEMPERATURE_THRESHOLD_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            if (
                !isSavedSettingsEditorResult(
                    result = result.getString(IntegerStepperBottomSheet.RESULT_KEY),
                    payloadId = result.getString(IntegerStepperBottomSheet.RESULT_PAYLOAD_ID),
                    expectedPayloadId = deviceUid,
                    savedResult = IntegerStepperBottomSheet.RESULT_SAVED
                )
            ) {
                return@setFragmentResultListener
            }
            viewModel.updateTemperatureProtectionThreshold(
                result.getInt(IntegerStepperBottomSheet.RESULT_VALUE)
            )
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

    private fun openTemperatureThresholdEditor() {
        val editor = latestState.lightProtection.editor ?: return
        if (latestState.lightProtection.updateInProgress) return

        IntegerStepperBottomSheet.show(
            fragmentManager = childFragmentManager,
            title = getString(
                R.string.device_settings_light_temperature_threshold_label
            ),
            helperText = getString(
                R.string.device_settings_light_temperature_threshold_editor_helper,
                editor.minimumCelsius,
                editor.maximumCelsius
            ),
            valueFormat = getString(
                R.string.device_settings_light_temperature_value_format
            ),
            initialValue = editor.currentCelsius,
            minValue = editor.minimumCelsius,
            maxValue = editor.maximumCelsius,
            step = editor.stepCelsius,
            saveText = getString(R.string.device_settings_save_action),
            cancelText = getString(R.string.device_settings_cancel_action),
            decreaseContentDescription = getString(
                R.string.device_settings_light_temperature_decrease_description
            ),
            increaseContentDescription = getString(
                R.string.device_settings_light_temperature_increase_description
            ),
            requestKey = TEMPERATURE_THRESHOLD_REQUEST_KEY,
            payloadId = deviceUid,
            disableSaveWhenUnchanged = true
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
        binding.tvFirmwareVersionValue.text = state.firmwareVersion.ifBlank { unavailable }

        renderUpdateAction(
            state = state.updateActionState,
            installedVersion = state.firmwareVersion
        )
        renderLightInventory(
            show = state.showLightProtectionInventory,
            state = state.lightProtection
        )
    }

    private fun renderUpdateAction(
        state: DeviceSettingsUpdateActionState,
        installedVersion: String
    ) {
        val presentation = state.toFirmwareActionPresentation(installedVersion)

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
        is DeviceSettingsUpdateActionState.Failed -> FirmwareActionPresentation(
            titleText = getString(
                if (failure.recoverable) {
                    R.string.device_settings_retry_update_check_action
                } else {
                    R.string.device_settings_update_needs_attention_title
                }
            ),
            subtitleText = getString(
                DeviceRootPresentationMapper.otaFailureMessageRes(failure.reason)
            ),
            enabled = true,
            opensDetails = !failure.recoverable,
            strokeColorRes = R.color.aqua_status_danger
        )
        DeviceSettingsUpdateActionState.Unsupported -> FirmwareActionPresentation(
            titleText = getString(R.string.device_settings_update_status_unsupported),
            subtitleText = getString(R.string.device_settings_update_unsupported_status),
            enabled = false
        )
    }

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

    private fun renderLightInventory(
        show: Boolean,
        state: DeviceLightProtectionUiState
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
            inflated.tvCurrentTemperatureLabel.setText(
                R.string.device_settings_light_current_temperature_label
            )
            inflated.tvTemperatureProtectionThresholdLabel.setText(
                R.string.device_settings_light_temperature_threshold_label
            )
            inflated.temperatureProtectionThresholdRow.setOnClickListener {
                if (
                    latestState.lightProtection.loadState ==
                    DeviceLightProtectionLoadState.FAILED
                ) {
                    viewModel.retryLightProtection()
                } else {
                    openTemperatureThresholdEditor()
                }
            }
        }

        section.root.isVisible = true
        renderLightTemperatureValues(section, state)
    }

    private fun renderLightTemperatureValues(
        section: LayoutDeviceLightSettingsSectionBinding,
        state: DeviceLightProtectionUiState
    ) {
        val presentation = state.toLightTemperatureActionPresentation()
        section.tvCurrentTemperatureValue.text = lightTemperatureValueText(
            value = state.currentTemperatureCelsius,
            loadState = state.loadState
        )
        section.tvTemperatureProtectionThresholdValue.text = lightTemperatureValueText(
            value = state.thresholdCelsius,
            loadState = state.loadState
        )
        section.temperatureProtectionThresholdRow.apply {
            isEnabled = presentation.enabled
            contentDescription = getString(presentation.contentDescriptionRes)
        }
        section.ivTemperatureProtectionThresholdArrow.isInvisible = !presentation.enabled
    }

    private fun lightTemperatureValueText(
        value: Double?,
        loadState: DeviceLightProtectionLoadState
    ): CharSequence = when {
        value != null -> temperatureText(value)
        loadState == DeviceLightProtectionLoadState.LOADING ->
            getString(R.string.device_settings_light_temperature_loading_value)
        else -> getString(R.string.common_not_available_em_dash)
    }

    private fun DeviceLightProtectionUiState.toLightTemperatureActionPresentation():
        LightTemperatureActionPresentation {
        val retryEnabled = loadState == DeviceLightProtectionLoadState.FAILED &&
            !updateInProgress
        val editorEnabled = editor != null && !updateInProgress
        val contentDescriptionRes = if (retryEnabled) {
            R.string.device_settings_light_retry_temperature_protection_description
        } else {
            R.string.device_settings_light_edit_temperature_threshold_description
        }
        return LightTemperatureActionPresentation(
            enabled = retryEnabled || editorEnabled,
            contentDescriptionRes = contentDescriptionRes
        )
    }

    private fun temperatureText(value: Double): String {
        val localizedValue = LocaleFormatter.formatDecimal(
            context = requireContext(),
            value = value,
            maximumFractionDigits = 1
        )
        return getString(
            R.string.device_settings_light_temperature_reading_format,
            localizedValue
        )
    }

    private fun handleEvent(event: DeviceFamilySettingsEvent) {
        when (event) {
            DeviceFamilySettingsEvent.DeviceNameUpdateFailed -> showSaveFailure(
                R.string.device_settings_device_name_save_failed_message
            )
            DeviceFamilySettingsEvent.TemperatureProtectionUpdateFailed -> showSaveFailure(
                R.string.device_settings_temperature_threshold_save_failed_message
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
        lightSectionBinding = null
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

    private data class LightTemperatureActionPresentation(
        val enabled: Boolean,
        @StringRes val contentDescriptionRes: Int
    )

    private companion object {
        const val COMPLETE_PROGRESS_PERMILLE = 1_000
        const val PERMILLE_PER_PERCENT = 10
        const val DEVICE_NAME_REQUEST_KEY = "device_settings_name_request"
        const val TEMPERATURE_THRESHOLD_REQUEST_KEY =
            "device_settings_temperature_threshold_request"
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
