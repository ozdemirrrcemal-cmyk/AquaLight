package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.reservoir

import android.text.InputType
import androidx.lifecycle.LifecycleOwner
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirCapacityPolicy
import com.aqua.aqualight.ui.common.bottomsheet.TextInputBottomSheet
import java.util.Locale

/** Owns reservoir capacity formatting and modal input. */
internal class DeviceDosingReservoirCapacityEditor(
    private val fragment: DeviceDosingReservoirFragment,
    private val viewModel: DeviceDosingReservoirViewModel
) {
    fun registerResult(owner: LifecycleOwner) {
        fragment.childFragmentManager.setFragmentResultListener(REQUEST_KEY, owner) { _, result ->
            val expected = result.getString(TextInputBottomSheet.RESULT_PAYLOAD_ID) == PAYLOAD_ID &&
                result.getString(TextInputBottomSheet.RESULT_KEY) == TextInputBottomSheet.RESULT_SAVED
            if (!expected) return@setFragmentResultListener
            viewModel.setCapacityInput(
                rawValue = result.getString(TextInputBottomSheet.RESULT_VALUE).orEmpty(),
                locale = currentLocale()
            )
        }
    }

    fun formatCapacity(microliters: Long): String = fragment.getString(
        R.string.device_dosing_detail_value_container_ml,
        DeviceDosingReservoirCapacityPolicy.format(microliters, currentLocale())
    )

    fun formatRemainingVolume(microliters: Long): String = fragment.getString(
        R.string.device_dosing_detail_value_container_ml,
        DeviceDosingReservoirCapacityPolicy.formatRuntimeVolume(microliters, currentLocale())
    )

    fun show() {
        val state = viewModel.currentEditorState()
        val draft = state.draft
        if (!draft.trackingEnabled || !state.editable || state.operationInProgress) return
        TextInputBottomSheet.show(
            fragmentManager = fragment.childFragmentManager,
            title = fragment.getString(R.string.device_dosing_detail_container_volume),
            label = fragment.getString(R.string.device_dosing_detail_container_volume_input_label),
            hint = fragment.getString(R.string.device_dosing_detail_container_volume_hint),
            initialValue = DeviceDosingReservoirCapacityPolicy.format(
                draft.reservoirCapacityMicroliters,
                currentLocale()
            ),
            saveText = fragment.getString(R.string.common_save),
            cancelText = fragment.getString(R.string.common_cancel),
            required = true,
            requiredMessage = fragment.getString(
                R.string.device_dosing_detail_container_volume_required
            ),
            requestKey = REQUEST_KEY,
            payloadId = PAYLOAD_ID,
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
            disableSaveWhenUnchanged = true,
            requestFocus = true
        )
    }

    private fun currentLocale(): Locale = fragment.resources.configuration.locales[0]

    private companion object {
        const val REQUEST_KEY = "dosing_reservoir_capacity_input"
        const val PAYLOAD_ID = "reservoir_capacity"
    }
}
