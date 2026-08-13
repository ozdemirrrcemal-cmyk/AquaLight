package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.reservoir

import android.os.Bundle
import android.text.InputType
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.ui.common.bottomsheet.TextInputBottomSheet
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.common.DeviceDosingChannelDestinationFragment
import java.text.NumberFormat
import kotlinx.coroutines.launch

/** Render/input host for the firmware-backed Dosing reservoir state. */
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
        viewModel.bind(
            deviceUid = args.deviceUid,
            slotId = args.slotId,
            restoredDraft = savedInstanceState?.toReservoirDraft()
        )
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
                        capacityValue = draft.reservoirCapacityMl.toVolumeText(),
                        remainingValue = draft.remainingMl.toVolumeText(),
                        accountingCertain = draft.accountingCertain,
                        saveEnabled = draft.saveEnabled,
                        refillAvailable = draft.refillAvailable,
                        busy = draft.busy
                    ),
                    actions = DeviceDosingReservoirActions(
                        onTrackingEnabledChange = viewModel::setTrackingEnabled,
                        onCapacityClick = ::showReservoirCapacityEditor,
                        onRefillClick = ::refillReservoir,
                        onSaveClick = ::saveReservoir
                    )
                )
            }
        }
    }

    private fun saveReservoir() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (viewModel.save()) findNavController().navigateUp()
        }
    }

    private fun refillReservoir() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.refill()
        }
    }

    private fun setupReservoirCapacityResult() {
        childFragmentManager.setFragmentResultListener(
            RESERVOIR_CAPACITY_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            val expected = result.getString(TextInputBottomSheet.RESULT_PAYLOAD_ID) == args.slotId &&
                result.getString(TextInputBottomSheet.RESULT_KEY) == TextInputBottomSheet.RESULT_SAVED
            if (!expected) return@setFragmentResultListener
            parseReservoirCapacity(result.getString(TextInputBottomSheet.RESULT_VALUE).orEmpty())
                ?.let(viewModel::setCapacityMl)
        }
    }

    private fun showReservoirCapacityEditor() {
        val draft = viewModel.currentDraft()
        if (!draft.trackingEnabled || draft.busy) return
        TextInputBottomSheet.show(
            fragmentManager = childFragmentManager,
            title = getString(R.string.device_dosing_detail_container_volume),
            label = getString(R.string.device_dosing_detail_container_volume_input_label),
            hint = getString(R.string.device_dosing_detail_container_volume_hint),
            initialValue = draft.reservoirCapacityMl?.let(::formatReservoirCapacityInput).orEmpty(),
            saveText = getString(R.string.common_save),
            cancelText = getString(R.string.common_cancel),
            required = true,
            requiredMessage = getString(R.string.device_dosing_detail_container_volume_required),
            requestKey = RESERVOIR_CAPACITY_REQUEST_KEY,
            payloadId = args.slotId,
            maxLength = RESERVOIR_CAPACITY_MAX_LENGTH,
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
            minimumNumericValueExclusive = 0.0,
            disableSaveWhenUnchanged = true,
            requestFocus = true
        )
    }

    private fun Double?.toVolumeText(): String = this?.let { value ->
        getString(R.string.device_dosing_detail_value_container_ml, value)
    } ?: getString(R.string.device_dosing_detail_value_unavailable)

    private fun formatReservoirCapacityInput(capacityMl: Double): String =
        NumberFormat.getNumberInstance(resources.configuration.locales[0]).apply {
            isGroupingUsed = false
            minimumFractionDigits = 0
            maximumFractionDigits = 3
        }.format(capacityMl)
}

private fun Bundle.toReservoirDraft() = DeviceDosingReservoirDraft(
    reservoirCapacityMl = getDouble(STATE_RESERVOIR_CAPACITY_ML)
        .takeIf { containsKey(STATE_RESERVOIR_CAPACITY_ML) },
    trackingEnabled = getBoolean(STATE_TRACKING_ENABLED, false)
)

private fun DeviceDosingReservoirDraft.writeTo(outState: Bundle) {
    reservoirCapacityMl?.let { outState.putDouble(STATE_RESERVOIR_CAPACITY_ML, it) }
    outState.putBoolean(STATE_TRACKING_ENABLED, trackingEnabled)
}

private fun parseReservoirCapacity(rawValue: String): Double? = rawValue
    .trim()
    .replace(',', '.')
    .toDoubleOrNull()

private const val STATE_RESERVOIR_CAPACITY_ML = "reservoir_capacity_ml"
private const val STATE_TRACKING_ENABLED = "reservoir_tracking_enabled"
private const val RESERVOIR_CAPACITY_REQUEST_KEY = "dosing_reservoir_capacity_input"
private const val RESERVOIR_CAPACITY_MAX_LENGTH = 12
