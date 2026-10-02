package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.ContentSheetLivestockHealthCheckBinding
import com.aqua.aqualight.databinding.DialogSettingsBottomSheetBinding
import com.aqua.aqualight.ui.common.media.bindRecordPhoto
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.card.MaterialCardView

internal class LivestockHealthCheckBottomSheet : BottomSheetDialogFragment() {

    private var _sheetBinding: DialogSettingsBottomSheetBinding? = null
    private val sheetBinding get() = _sheetBinding!!

    private var _contentBinding: ContentSheetLivestockHealthCheckBinding? = null
    private val contentBinding get() = _contentBinding!!

    private var selectedStatus: String = STATUS_SAME
    private var affectedCount: Int = 1
    private var totalCount: Int = 1

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _sheetBinding = DialogSettingsBottomSheetBinding.inflate(inflater, container, false)
        return sheetBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sheetBinding.tvSheetTitle.setText(R.string.livestock_health_new_check_title)

        _contentBinding = ContentSheetLivestockHealthCheckBinding.inflate(layoutInflater)
        sheetBinding.sheetContentContainer.removeAllViews()
        sheetBinding.sheetContentContainer.addView(contentBinding.root)

        totalCount = requireArguments().getInt(ARG_TOTAL_COUNT, 1).coerceAtLeast(1)
        contentBinding.tvCheckLivestockName.text = requireArguments()
            .getString(ARG_LIVESTOCK_NAME)
            .orEmpty()
        contentBinding.ivCheckLivestock.bindRecordPhoto(
            requireArguments().getString(ARG_PHOTO_URI)
        )

        bindStatusCards()
        bindCounter()
        contentBinding.btnSaveCheck.setOnClickListener {
            parentFragmentManager.setFragmentResult(
                REQUEST_KEY,
                bundleOf(
                    RESULT_STATUS to selectedStatus,
                    RESULT_AFFECTED_COUNT to affectedCount
                )
            )
            dismiss()
        }
        updateCounter()
    }

    private fun bindStatusCards() {
        val cards = linkedMapOf(
            STATUS_INCREASED to contentBinding.cardCheckIncreased,
            STATUS_SAME to contentBinding.cardCheckSame,
            STATUS_DECREASED to contentBinding.cardCheckDecreased,
            STATUS_RECOVERED to contentBinding.cardCheckRecovered
        )

        cards.forEach {
            (status, card) ->
            card.setOnClickListener {
                selectedStatus = status
                updateStatusCards(cards)
            }
        }
        updateStatusCards(cards)
    }

    private fun updateStatusCards(cards: Map<String, MaterialCardView>) {
        val selectedStroke = ContextCompat.getColor(requireContext(), R.color.aqua_button_blue)
        val normalStroke = ContextCompat.getColor(requireContext(), R.color.aqua_card_outline)
        val selectedSurface = ContextCompat.getColor(requireContext(), R.color.aqua_surface_action)
        val normalSurface = ContextCompat.getColor(requireContext(), R.color.aqua_card_surface)

        cards.forEach {
            (status, card) ->
            val selected = status == selectedStatus
            card.strokeWidth = resources.getDimensionPixelSize(
                if (selected) R.dimen.aqua_size_2 else R.dimen.aqua_size_1
            )
            card.setStrokeColor(if (selected) selectedStroke else normalStroke)
            card.setCardBackgroundColor(if (selected) selectedSurface else normalSurface)
        }
    }

    private fun bindCounter() {
        contentBinding.btnCheckMinus.setOnClickListener {
            affectedCount = (affectedCount - 1).coerceAtLeast(1)
            updateCounter()
        }
        contentBinding.btnCheckPlus.setOnClickListener {
            affectedCount = (affectedCount + 1).coerceAtMost(totalCount)
            updateCounter()
        }
    }

    private fun updateCounter() {
        affectedCount = affectedCount.coerceIn(1, totalCount)
        contentBinding.tvCheckAffectedCount.text = affectedCount.toString()
        contentBinding.tvCheckAffectedTotal.text = getString(
            R.string.livestock_health_affected_total_format,
            totalCount
        )
        contentBinding.btnCheckMinus.isEnabled = affectedCount > 1
        contentBinding.btnCheckPlus.isEnabled = affectedCount < totalCount
    }

    override fun onDestroyView() {
        _contentBinding = null
        _sheetBinding = null
        super.onDestroyView()
    }

    companion object {
        const val REQUEST_KEY = "livestock_health_check_request"
        const val RESULT_STATUS = "livestock_health_check_status"
        const val RESULT_AFFECTED_COUNT = "livestock_health_check_affected_count"

        const val STATUS_INCREASED = "increased"
        const val STATUS_SAME = "same"
        const val STATUS_DECREASED = "decreased"
        const val STATUS_RECOVERED = "recovered"

        private const val ARG_LIVESTOCK_NAME = "livestock_name"
        private const val ARG_TOTAL_COUNT = "total_count"
        private const val ARG_PHOTO_URI = "photo_uri"
        private const val TAG = "LivestockHealthCheckBottomSheet"

        fun show(
            fragmentManager: FragmentManager,
            livestockName: String,
            totalCount: Int,
            photoUri: String?
        ) {
            if (fragmentManager.findFragmentByTag(TAG) != null || fragmentManager.isStateSaved) {
                return
            }

            LivestockHealthCheckBottomSheet().apply {
                arguments = bundleOf(
                    ARG_LIVESTOCK_NAME to livestockName,
                    ARG_TOTAL_COUNT to totalCount,
                    ARG_PHOTO_URI to photoUri
                )
            }.show(fragmentManager, TAG)
        }
    }
}
