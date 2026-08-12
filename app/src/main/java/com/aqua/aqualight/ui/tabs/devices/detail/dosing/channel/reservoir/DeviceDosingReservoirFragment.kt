package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.reservoir

import android.os.Bundle
import android.text.InputType
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceDosingReservoirDraftPolicy
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.ui.common.bottomsheet.TextInputBottomSheet
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.common.DeviceDosingChannelDestinationFragment
import java.text.NumberFormat

/** Render/input host for the ViewModel-owned reservoir draft. */
class DeviceDosingReservoirFragment :
    DeviceDosingChannelDestinationFragment(R.layout.fragment_device_dosing_channel_detail) {

    private val args: DeviceDosingReservoirFragmentArgs by navArgs()
    private val viewModel: DeviceDosingReservoirViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }

    override val destinationTitle: String
        get() = getString(R.string.device_dosing_detail_reservoir_title)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.bindInitial(savedInstanceState?.toReservoirDraft())
        setupReservoirCapacityResult()
        setupSelectedPump(
            view = view,
            deviceUid = args.deviceUid,
            slotId = args.slotId,
            pumpCount = args.pumpCount,
            channelNumber = args.channelNumber
        )
        setupContent(view)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        viewModel.currentDraft().writeTo(outState)
        super.onSaveInstanceState(outState)
    }

    private fun setupContent(view: View) {
        view.findViewById<ComposeView>(R.id.channelDetailContent).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val draft by viewModel.draft.collectAsStateWithLifecycle()
                DeviceDosingReservoirScreen(
                    state = DeviceDosingReservoirUiState(
                        trackingEnabled = draft.trackingEnabled,
                        capacityValue = getString(
                            R.string.device_dosing_detail_value_container_ml,
                            draft.reservoirCapacityMl
                        ),
                        lowLevelAlertEnabled = draft.lowLevelAlertEnabled
                    ),
                    actions = DeviceDosingReservoirActions(
                        onTrackingEnabledChange = viewModel::setTrackingEnabled,
                        onCapacityClick = ::showReservoirCapacityEditor,
                        onLowLevelAlertEnabledChange = viewModel::setLowLevelAlertEnabled,
                        onSaveClick = null
                    )
                )
            }
        }
    }

    private fun setupReservoirCapacityResult() {
        childFragmentManager.setFragmentResultListener(
            RESERVOIR_CAPACITY_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            val expected = result.getString(TextInputBottomSheet.RESULT_PAYLOAD_ID) ==
                RESERVOIR_CAPACITY_PAYLOAD_ID &&
                result.getString(TextInputBottomSheet.RESULT_KEY) == TextInputBottomSheet.RESULT_SAVED
            if (!expected) return@setFragmentResultListener
            parseReservoirCapacity(result.getString(TextInputBottomSheet.RESULT_VALUE).orEmpty())
                ?.let(viewModel::setCapacityMl)
        }
    }

    private fun showReservoirCapacityEditor() {
        val draft = viewModel.currentDraft()
        if (!draft.trackingEnabled) return
        TextInputBottomSheet.show(
            fragmentManager = childFragmentManager,
            title = getString(R.string.device_dosing_detail_container_volume),
            label = getString(R.string.device_dosing_detail_container_volume_input_label),
            hint = getString(R.string.device_dosing_detail_container_volume_hint),
            initialValue = formatReservoirCapacityInput(draft.reservoirCapacityMl),
            saveText = getString(R.string.common_save),
            cancelText = getString(R.string.common_cancel),
            required = true,
            requiredMessage = getString(R.string.device_dosing_detail_container_volume_required),
            requestKey = RESERVOIR_CAPACITY_REQUEST_KEY,
            payloadId = RESERVOIR_CAPACITY_PAYLOAD_ID,
            maxLength = RESERVOIR_CAPACITY_MAX_LENGTH,
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
            disableSaveWhenUnchanged = true,
            requestFocus = true
        )
    }

    private fun formatReservoirCapacityInput(capacityMl: Double): String =
        NumberFormat.getNumberInstance(resources.configuration.locales[0]).apply {
            isGroupingUsed = false
            minimumFractionDigits = 0
            maximumFractionDigits = 1
        }.format(capacityMl)
}

private fun Bundle.toReservoirDraft() = DeviceDosingReservoirDraft(
    reservoirCapacityMl = getDouble(
        STATE_RESERVOIR_CAPACITY_ML,
        DeviceDosingReservoirDraftPolicy.DEFAULT_CAPACITY_ML
    ),
    trackingEnabled = getBoolean(STATE_TRACKING_ENABLED, false),
    lowLevelAlertEnabled = getBoolean(STATE_LOW_LEVEL_ALERT_ENABLED, true)
)

private fun DeviceDosingReservoirDraft.writeTo(outState: Bundle) {
    outState.putDouble(STATE_RESERVOIR_CAPACITY_ML, reservoirCapacityMl)
    outState.putBoolean(STATE_TRACKING_ENABLED, trackingEnabled)
    outState.putBoolean(STATE_LOW_LEVEL_ALERT_ENABLED, lowLevelAlertEnabled)
}

private fun parseReservoirCapacity(rawValue: String): Double? = rawValue
    .trim()
    .replace(',', '.')
    .toDoubleOrNull()

private const val STATE_RESERVOIR_CAPACITY_ML = "reservoir_capacity_ml"
private const val STATE_TRACKING_ENABLED = "reservoir_tracking_enabled"
private const val STATE_LOW_LEVEL_ALERT_ENABLED = "reservoir_low_level_alert_enabled"
private const val RESERVOIR_CAPACITY_REQUEST_KEY = "dosing_reservoir_capacity_input"
private const val RESERVOIR_CAPACITY_PAYLOAD_ID = "reservoir_capacity"
private const val RESERVOIR_CAPACITY_MAX_LENGTH = 7
