package com.aqua.aqualight.ui.tabs.devices.detail.settings

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.widget.ImageViewCompat
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
        binding.btnFirmwareStatusAction.setOnClickListener {
            handleFirmwareUpdateAction()
        }
        binding.btnFirmwarePrimaryAction.setOnClickListener {
            handleFirmwareUpdateAction()
        }
    }

    private fun handleFirmwareUpdateAction() {
        when (val updateState = latestState.updateActionState) {
            is DeviceSettingsUpdateActionState.UpdateAvailable,
            is DeviceSettingsUpdateActionState.UpdateInProgress -> openFirmwareUpdateScreen()
            DeviceSettingsUpdateActionState.Checking,
            DeviceSettingsUpdateActionState.Unsupported -> Unit
            DeviceSettingsUpdateActionState.Idle,
            DeviceSettingsUpdateActionState.UpToDate -> viewModel.checkForUpdates()
            is DeviceSettingsUpdateActionState.Failed -> {
                if (updateState.failure.recoverable) {
                    viewModel.checkForUpdates()
                } else {
                    openFirmwareUpdateScreen()
                }
            }
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
        binding.tvEditDeviceNameAction.setText(
            if (state.deviceNameSaving) {
                R.string.device_settings_saving_action
            } else {
                R.string.device_settings_edit_action
            }
        )
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
            state = state.lightProtection
        )
    }

    private fun renderUpdateAction(state: DeviceSettingsUpdateActionState) {
        val presentation = state.toFirmwareActionPresentation()

        binding.progressFirmwareUpdateStatus.apply {
            isVisible = presentation.showProgress
            contentDescription = presentation.title
        }
        binding.ivFirmwareUpdateStatus.apply {
            isVisible = !presentation.showProgress
            setImageResource(presentation.iconRes)
            ImageViewCompat.setImageTintList(
                this,
                ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), presentation.iconColorRes)
                )
            )
        }
        binding.tvFirmwareUpdateStatusTitle.text = presentation.title
        binding.tvFirmwareUpdateStatusDetail.text = presentation.detail
        binding.btnFirmwareStatusAction.apply {
            isVisible = presentation.inlineActionText != null
            isEnabled = presentation.inlineActionText != null
            text = presentation.inlineActionText.orEmpty()
            contentDescription = presentation.inlineActionText.orEmpty()
        }
        binding.btnFirmwarePrimaryAction.apply {
            isVisible = presentation.primaryActionText != null
            isEnabled = presentation.primaryActionText != null
            text = presentation.primaryActionText.orEmpty()
            contentDescription = presentation.primaryActionText.orEmpty()
        }
    }

    private fun DeviceSettingsUpdateActionState.toFirmwareActionPresentation():
        FirmwareActionPresentation = when (this) {
        DeviceSettingsUpdateActionState.Idle -> FirmwareActionPresentation(
            title = getString(R.string.device_settings_firmware_status_idle_title),
            detail = getString(R.string.device_settings_firmware_status_idle_detail),
            iconRes = R.drawable.ic_info,
            iconColorRes = R.color.aqua_accent_primary,
            inlineActionText = getString(
                R.string.device_settings_firmware_check_now_action
            )
        )
        DeviceSettingsUpdateActionState.Checking -> FirmwareActionPresentation(
            title = getString(R.string.device_settings_firmware_status_checking_title),
            detail = getString(R.string.device_settings_update_phase_checking),
            iconRes = R.drawable.ic_info,
            iconColorRes = R.color.aqua_accent_primary,
            showProgress = true
        )
        DeviceSettingsUpdateActionState.UpToDate -> FirmwareActionPresentation(
            title = getString(R.string.device_settings_update_status_up_to_date),
            detail = getString(R.string.device_settings_update_phase_up_to_date),
            iconRes = R.drawable.ic_check_24,
            iconColorRes = R.color.aqua_status_success,
            inlineActionText = getString(
                R.string.device_settings_firmware_check_again_action
            )
        )
        is DeviceSettingsUpdateActionState.UpdateAvailable -> FirmwareActionPresentation(
            title = getString(R.string.device_settings_firmware_status_available_title),
            detail = getString(
                R.string.device_settings_firmware_status_available_detail,
                version
            ),
            iconRes = R.drawable.ic_info,
            iconColorRes = R.color.aqua_accent_primary,
            primaryActionText = getString(
                R.string.device_settings_firmware_view_update_action
            )
        )
        is DeviceSettingsUpdateActionState.UpdateInProgress -> FirmwareActionPresentation(
            title = getString(R.string.device_settings_update_status_installing),
            detail = getString(
                R.string.device_settings_firmware_status_in_progress_detail,
                version,
                progressPermille.coerceIn(
                    0,
                    COMPLETE_PROGRESS_PERMILLE
                ) / PERMILLE_PER_PERCENT
            ),
            iconRes = R.drawable.ic_info,
            iconColorRes = R.color.aqua_accent_primary,
            showProgress = true,
            primaryActionText = getString(
                R.string.device_settings_firmware_view_progress_action
            )
        )
        is DeviceSettingsUpdateActionState.Failed -> failurePresentation()
        DeviceSettingsUpdateActionState.Unsupported -> FirmwareActionPresentation(
            title = getString(R.string.device_settings_firmware_status_unsupported_title),
            detail = getString(R.string.device_settings_firmware_status_unsupported_detail),
            iconRes = R.drawable.ic_warning,
            iconColorRes = R.color.aqua_content_warning
        )
    }

    private fun DeviceSettingsUpdateActionState.Failed.failurePresentation():
        FirmwareActionPresentation = FirmwareActionPresentation(
        title = getString(
            if (failure.recoverable) {
                R.string.device_settings_firmware_status_check_failed_title
            } else {
                R.string.device_settings_firmware_status_attention_title
            }
        ),
        detail = getString(
            DeviceRootPresentationMapper.otaFailureMessageRes(failure.reason)
        ),
        iconRes = R.drawable.ic_error,
        iconColorRes = R.color.aqua_status_danger,
        inlineActionText = getString(
            if (failure.recoverable) {
                R.string.device_settings_retry_update_action
            } else {
                R.string.device_settings_firmware_view_details_action
            }
        )
    )

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
        section.tvEditTemperatureProtectionThresholdAction.apply {
            setText(presentation.actionTextRes)
            isEnabled = presentation.enabled
        }
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
        val actionTextRes = when {
            updateInProgress -> R.string.device_settings_saving_action
            retryEnabled -> R.string.device_settings_light_retry_temperature_protection_action
            else -> R.string.device_settings_edit_action
        }
        val contentDescriptionRes = if (retryEnabled) {
            R.string.device_settings_light_retry_temperature_protection_description
        } else {
            R.string.device_settings_light_edit_temperature_threshold_description
        }
        return LightTemperatureActionPresentation(
            enabled = retryEnabled || editorEnabled,
            actionTextRes = actionTextRes,
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
        val title: CharSequence,
        val detail: CharSequence,
        @DrawableRes val iconRes: Int,
        @ColorRes val iconColorRes: Int,
        val showProgress: Boolean = false,
        val inlineActionText: CharSequence? = null,
        val primaryActionText: CharSequence? = null
    ) {
        init {
            require(inlineActionText == null || primaryActionText == null) {
                "Firmware status must expose at most one action."
            }
        }
    }

    private data class LightTemperatureActionPresentation(
        val enabled: Boolean,
        @StringRes val actionTextRes: Int,
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
