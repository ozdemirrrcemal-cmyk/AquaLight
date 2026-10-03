package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.aqua.aqualight.R
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.ContentSheetLivestockHealthCheckBinding
import com.aqua.aqualight.databinding.DialogSettingsBottomSheetBinding
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.platform.media.AppMediaScope
import com.aqua.aqualight.ui.common.dialog.AppTimePickerDialogFragment
import com.aqua.aqualight.ui.common.media.MediaCropSpec
import com.aqua.aqualight.ui.common.media.MediaFlowCoordinatorViewModel
import com.aqua.aqualight.ui.common.media.bindRecordPhoto
import com.aqua.aqualight.ui.tabs.aquarium.catalog.livestock.LivestockCategories
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch

internal class LivestockHealthCheckBottomSheet : BottomSheetDialogFragment() {

    private var _sheetBinding: DialogSettingsBottomSheetBinding? = null
    private val sheetBinding get() = _sheetBinding!!

    private var _contentBinding: ContentSheetLivestockHealthCheckBinding? = null
    private val contentBinding get() = _contentBinding!!

    private val mediaFlow: MediaFlowCoordinatorViewModel by viewModels {
        val container = requireContext().requireAppContainer()
        MediaFlowCoordinatorViewModel.factory(
            context = requireContext().applicationContext,
            scope = AppMediaScope.LIVESTOCK,
            ownerToken = "health_check_${requireArguments().getLong(ARG_LIVESTOCK_ID)}",
            ownerUid = container.authenticatedOwnerIdentity.requireOwnerUid(),
            cropSpec = MediaCropSpec.RECORD,
            mediaProcessor = container.imageMediaProcessor
        )
    }

    private val photoController by lazy {
        LivestockHealthCheckPhotoController(
            fragment = this,
            mediaFlow = mediaFlow,
            hasView = { _contentBinding != null },
            onPhotoChanged = ::renderSelectedPhoto
        )
    }

    private var selectedStatus: String = STATUS_SAME
    private var affectedCount: Int = 1
    private var totalCount: Int = 1
    private var selectedTimeMillis: Long = 0L
    private var resultPublished: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedStatus = savedInstanceState?.getString(STATE_STATUS) ?: STATUS_SAME
        affectedCount = savedInstanceState?.getInt(STATE_AFFECTED_COUNT, 1) ?: 1
        selectedTimeMillis = savedInstanceState?.getLong(
            STATE_TIME_MILLIS,
            System.currentTimeMillis()
        ) ?: System.currentTimeMillis()
    }

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
        affectedCount = affectedCount.coerceIn(1, totalCount)

        bindLivestock()
        bindStatusCards()
        bindCounter()
        bindTimePicker()
        bindPhotoAndNote()
        bindSave()
        renderTime()
        updateCounter()
    }

    private fun bindLivestock() {
        contentBinding.tvCheckLivestockName.text = requireArguments()
            .getString(ARG_LIVESTOCK_NAME)
            .orEmpty()
        contentBinding.tvCheckLivestockIssue.text = requireArguments()
            .getString(ARG_ISSUE_LABEL)
            .orEmpty()
        contentBinding.ivCheckLivestock.bindRecordPhoto(
            requireArguments().getString(ARG_PHOTO_URI),
            LivestockCategories.iconRes(
                requireArguments().getString(ARG_CATEGORY).orEmpty()
            )
        )
    }

    private fun bindStatusCards() {
        val cards = linkedMapOf(
            STATUS_INCREASED to contentBinding.cardCheckIncreased,
            STATUS_SAME to contentBinding.cardCheckSame,
            STATUS_DECREASED to contentBinding.cardCheckDecreased,
            STATUS_RECOVERED to contentBinding.cardCheckRecovered
        )

        cards.forEach { (status, card) ->
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

        cards.forEach { (status, card) ->
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
        contentBinding.tvCheckAffectedCount.text = getString(
            R.string.livestock_health_affected_counter_format,
            affectedCount,
            totalCount
        )
        contentBinding.btnCheckMinus.isEnabled = affectedCount > 1
        contentBinding.btnCheckPlus.isEnabled = affectedCount < totalCount
    }

    private fun bindTimePicker() {
        childFragmentManager.setFragmentResultListener(
            TIME_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            if (
                result.getString(AppTimePickerDialogFragment.RESULT_KEY) !=
                AppTimePickerDialogFragment.RESULT_SELECTED
            ) {
                return@setFragmentResultListener
            }
            selectedTimeMillis = result.getLong(AppTimePickerDialogFragment.RESULT_MILLIS)
            renderTime()
        }

        contentBinding.cardCheckTime.setOnClickListener {
            AppTimePickerDialogFragment.show(
                fragmentManager = childFragmentManager,
                requestKey = TIME_REQUEST_KEY,
                initialMillis = selectedTimeMillis
            )
        }
    }

    private fun renderTime() {
        contentBinding.tvCheckTimeValue.text = getString(
            R.string.livestock_health_check_time_today_format,
            LocaleFormatter.formatTime(requireContext(), selectedTimeMillis)
        )
    }

    private fun bindPhotoAndNote() {
        mediaFlow.initializeSelection(null)
        photoController.bind(viewLifecycleOwner)
        renderSelectedPhoto(photoController.selectedUri())

        contentBinding.checkPhotoMediaArea.setOnClickListener {
            photoController.showSource(
                getString(R.string.livestock_health_check_photo_source_title)
            )
        }
        contentBinding.btnRemoveCheckPhoto.setOnClickListener {
            photoController.removePhoto()
        }
    }

    private fun renderSelectedPhoto(photoUri: String?) {
        if (_contentBinding == null) return
        val hasPhoto = !photoUri.isNullOrBlank()
        contentBinding.ivCheckPhotoPreview.isVisible = hasPhoto
        contentBinding.ivCheckPhotoPlaceholder.isVisible = !hasPhoto
        contentBinding.btnRemoveCheckPhoto.isVisible = hasPhoto
        if (hasPhoto) {
            contentBinding.ivCheckPhotoPreview.bindRecordPhoto(photoUri)
        } else {
            contentBinding.ivCheckPhotoPreview.setImageDrawable(null)
        }
    }

    private fun bindSave() {
        contentBinding.btnSaveCheck.setOnClickListener {
            resultPublished = true
            parentFragmentManager.setFragmentResult(
                REQUEST_KEY,
                Bundle().apply {
                    putString(RESULT_STATUS, selectedStatus)
                    putInt(RESULT_AFFECTED_COUNT, affectedCount)
                    putLong(RESULT_TIME_MILLIS, selectedTimeMillis)
                    putString(
                        RESULT_NOTE,
                        contentBinding.etCheckNote.text?.toString()?.trim().orEmpty()
                    )
                    putString(RESULT_PHOTO_URI, photoController.selectedUri())
                }
            )
            dismiss()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_STATUS, selectedStatus)
        outState.putInt(STATE_AFFECTED_COUNT, affectedCount)
        outState.putLong(STATE_TIME_MILLIS, selectedTimeMillis)
        super.onSaveInstanceState(outState)
    }

    override fun onDismiss(dialog: DialogInterface) {
        if (!resultPublished) {
            lifecycleScope.launch { mediaFlow.rollbackSelection() }
        }
        super.onDismiss(dialog)
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
        const val RESULT_TIME_MILLIS = "livestock_health_check_time_millis"
        const val RESULT_NOTE = "livestock_health_check_note"
        const val RESULT_PHOTO_URI = "livestock_health_check_photo_uri"

        const val STATUS_INCREASED = "increased"
        const val STATUS_SAME = "same"
        const val STATUS_DECREASED = "decreased"
        const val STATUS_RECOVERED = "recovered"

        private const val ARG_LIVESTOCK_NAME = "livestock_name"
        private const val ARG_ISSUE_LABEL = "issue_label"
        private const val ARG_CATEGORY = "livestock_category"
        private const val ARG_LIVESTOCK_ID = "livestock_id"
        private const val ARG_TOTAL_COUNT = "total_count"
        private const val ARG_PHOTO_URI = "photo_uri"

        private const val STATE_STATUS = "status"
        private const val STATE_AFFECTED_COUNT = "affected_count"
        private const val STATE_TIME_MILLIS = "time_millis"

        private const val TIME_REQUEST_KEY = "livestock_health_check_time_request"
        private const val TAG = "LivestockHealthCheckBottomSheet"

        fun show(
            fragmentManager: FragmentManager,
            livestockId: Long,
            livestockName: String,
            category: String,
            issueLabel: String,
            totalCount: Int,
            photoUri: String?
        ) {
            if (fragmentManager.findFragmentByTag(TAG) != null || fragmentManager.isStateSaved) {
                return
            }

            LivestockHealthCheckBottomSheet().apply {
                arguments = bundleOf(
                    ARG_LIVESTOCK_ID to livestockId,
                    ARG_LIVESTOCK_NAME to livestockName,
                    ARG_CATEGORY to category,
                    ARG_ISSUE_LABEL to issueLabel,
                    ARG_TOTAL_COUNT to totalCount,
                    ARG_PHOTO_URI to photoUri
                )
            }.show(fragmentManager, TAG)
        }
    }
}
