package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentLivestockHealthObservationBinding
import com.aqua.aqualight.ui.common.bottomsheet.SingleChoiceBottomSheet

internal fun bindLivestockObservationMeta(
    fragment: Fragment,
    binding: FragmentLivestockHealthObservationBinding,
    selectedOnset: () -> String,
    onOnsetSelected: (String) -> Unit
) {
    fun render() {
        binding.tvObservationStartValue.setText(
            LivestockHealthObservationCatalog.onsetLabelRes(selectedOnset())
        )
    }

    fragment.childFragmentManager.setFragmentResultListener(
        REQUEST_KEY,
        fragment.viewLifecycleOwner
    ) { _, result ->
        if (
            result.getString(SingleChoiceBottomSheet.RESULT_KEY) !=
            SingleChoiceBottomSheet.RESULT_SELECTED
        ) {
            return@setFragmentResultListener
        }
        if (
            result.getString(SingleChoiceBottomSheet.RESULT_PAYLOAD_ID) !=
            PAYLOAD_ONSET
        ) {
            return@setFragmentResultListener
        }
        onOnsetSelected(
            result.getString(SingleChoiceBottomSheet.RESULT_SELECTED_ID).orEmpty()
        )
        render()
    }

    binding.cardObservationStart.setOnClickListener {
        SingleChoiceBottomSheet.show(
            fragmentManager = fragment.childFragmentManager,
            title = fragment.getString(R.string.livestock_health_when_start_label),
            options = LivestockHealthObservationCatalog.onsetOptions().map { option ->
                option.id to fragment.getString(option.labelRes)
            },
            selectedId = selectedOnset(),
            columns = 1,
            requestKey = REQUEST_KEY,
            payloadId = PAYLOAD_ONSET
        )
    }

    render()
}

private const val REQUEST_KEY = "livestock_health_observation_meta_request"
private const val PAYLOAD_ONSET = "onset"
