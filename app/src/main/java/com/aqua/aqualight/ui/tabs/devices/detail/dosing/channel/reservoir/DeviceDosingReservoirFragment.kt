package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.reservoir

import android.os.Bundle
import android.text.InputType
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.bottomsheet.TextInputBottomSheet
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.common.DeviceDosingChannelDestinationFragment
import java.text.NumberFormat

/** Process-safe UI draft owner for the Reservoir Monitoring child feature. */
class DeviceDosingReservoirFragment :
    DeviceDosingChannelDestinationFragment(R.layout.fragment_device_dosing_channel_detail) {

    private val args: DeviceDosingReservoirFragmentArgs by navArgs()
    private var reservoirCapacityMl by mutableDoubleStateOf(DEFAULT_RESERVOIR_CAPACITY_ML)
    private var trackingEnabled by mutableStateOf(false)
    private var lowLevelAlertEnabled by mutableStateOf(true)

    override val destinationTitle: String
        get() = getString(R.string.device_dosing_detail_reservoir_title)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        restoreState(savedInstanceState)
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
        outState.putDouble(STATE_RESERVOIR_CAPACITY_ML, reservoirCapacityMl)
        outState.putBoolean(STATE_TRACKING_ENABLED, trackingEnabled)
        outState.putBoolean(STATE_LOW_LEVEL_ALERT_ENABLED, lowLevelAlertEnabled)
        super.onSaveInstanceState(outState)
    }

    private fun restoreState(savedInstanceState: Bundle?) {
        reservoirCapacityMl = savedInstanceState?.getDouble(
            STATE_RESERVOIR_CAPACITY_ML,
            DEFAULT_RESERVOIR_CAPACITY_ML
        ) ?: DEFAULT_RESERVOIR_CAPACITY_ML
        trackingEnabled = savedInstanceState?.getBoolean(STATE_TRACKING_ENABLED, false) ?: false
        lowLevelAlertEnabled = savedInstanceState?.getBoolean(
            STATE_LOW_LEVEL_ALERT_ENABLED,
            true
        ) ?: true
    }

    private fun setupContent(view: View) {
        view.findViewById<ComposeView>(R.id.channelDetailContent).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                DeviceDosingReservoirScreen(
                    state = DeviceDosingReservoirUiState(
                        trackingEnabled = trackingEnabled,
                        capacityValue = getString(
                            R.string.device_dosing_detail_value_container_ml,
                            reservoirCapacityMl
                        ),
                        lowLevelAlertEnabled = lowLevelAlertEnabled
                    ),
                    actions = DeviceDosingReservoirActions(
                        onTrackingEnabledChange = { enabled -> trackingEnabled = enabled },
                        onCapacityClick = ::showReservoirCapacityEditor,
                        onLowLevelAlertEnabledChange = { enabled -> lowLevelAlertEnabled = enabled },
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
                ?.let { capacityMl -> reservoirCapacityMl = capacityMl }
        }
    }

    private fun showReservoirCapacityEditor() {
        if (!trackingEnabled) return
        TextInputBottomSheet.show(
            fragmentManager = childFragmentManager,
            title = getString(R.string.device_dosing_detail_container_volume),
            label = getString(R.string.device_dosing_detail_container_volume_input_label),
            hint = getString(R.string.device_dosing_detail_container_volume_hint),
            initialValue = formatReservoirCapacityInput(),
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

    private fun formatReservoirCapacityInput(): String =
        NumberFormat.getNumberInstance(resources.configuration.locales[0]).apply {
            isGroupingUsed = false
            minimumFractionDigits = 0
            maximumFractionDigits = 1
        }.format(reservoirCapacityMl)

    private companion object {
        const val STATE_RESERVOIR_CAPACITY_ML = "reservoir_capacity_ml"
        const val STATE_TRACKING_ENABLED = "reservoir_tracking_enabled"
        const val STATE_LOW_LEVEL_ALERT_ENABLED = "reservoir_low_level_alert_enabled"
        const val RESERVOIR_CAPACITY_REQUEST_KEY = "dosing_reservoir_capacity_input"
        const val RESERVOIR_CAPACITY_PAYLOAD_ID = "reservoir_capacity"
        const val RESERVOIR_CAPACITY_MAX_LENGTH = 7
        const val DEFAULT_RESERVOIR_CAPACITY_ML = 450.0
    }
}

private fun parseReservoirCapacity(rawValue: String): Double? = rawValue
    .trim()
    .replace(',', '.')
    .toDoubleOrNull()
    ?.takeIf { capacityMl -> capacityMl > 0.0 }
