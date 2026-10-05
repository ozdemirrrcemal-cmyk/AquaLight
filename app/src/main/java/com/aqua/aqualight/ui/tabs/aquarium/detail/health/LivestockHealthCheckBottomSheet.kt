package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.health.LivestockCheckInput
import com.aqua.aqualight.databinding.ContentSheetLivestockHealthCheckBinding
import com.aqua.aqualight.databinding.DialogSettingsBottomSheetBinding
import com.aqua.aqualight.application.media.MediaScope
import com.aqua.aqualight.ui.common.dialog.AppTimePickerDialogFragment
import com.aqua.aqualight.ui.common.media.MediaCropSpec
import com.aqua.aqualight.ui.common.media.MediaFlowCoordinatorViewModel
import com.aqua.aqualight.ui.common.media.mediaFlowFactory
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.UUID
import java.io.IOException

internal class LivestockHealthCheckBottomSheet : BottomSheetDialogFragment() {
    private val healthViewModel: LivestockHealthViewModel by activityViewModels()

    private var _sheetBinding: DialogSettingsBottomSheetBinding? = null
    private val sheetBinding get() = _sheetBinding!!

    private var _contentBinding: ContentSheetLivestockHealthCheckBinding? = null
    private val contentBinding get() = _contentBinding!!

    private val mediaFlow: MediaFlowCoordinatorViewModel by viewModels {
        mediaFlowFactory(
            scope = MediaScope.LIVESTOCK,
            ownerToken = {
                "health_check_${requireArguments().getLong(ARG_CHECK_LIVESTOCK_ID)}"
            },
            cropSpec = MediaCropSpec.RECORD
        )
    }

    private val photoController by lazy {
        LivestockHealthCheckPhotoController(
            fragment = this,
            mediaFlow = mediaFlow,
            hasView = { _contentBinding != null },
            onPhotoChanged = { photoUri ->
                _contentBinding?.renderCheckPhoto(photoUri)
            }
        )
    }

    private var selectedStatus: String = STATUS_SAME
    private var affectedCount: Int = 1
    private var totalCount: Int = 1
    private var selectedTimeMillis: Long = 0L
    private var resultPublished: Boolean = false
    private var isSaving = false
    private var saveMayHaveCommitted = false
    private var requestId: String = UUID.randomUUID().toString()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedStatus = savedInstanceState?.getString(STATE_STATUS) ?: STATUS_SAME
        requestId = savedInstanceState?.getString(STATE_REQUEST_ID) ?: requestId
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

        val request = requireArguments().toLivestockHealthCheckRequest()
        totalCount = request.totalCount
        affectedCount = affectedCount.coerceIn(1, totalCount)

        contentBinding.bindCheckLivestock(request)
        contentBinding.etCheckNote.setText(savedInstanceState?.getString(STATE_NOTE))
        contentBinding.bindCheckStatusCards(
            fragment = this,
            selectedStatus = selectedStatus,
            onSelected = { selectedStatus = it }
        )
        contentBinding.bindAffectedCounter(
            fragment = this,
            currentCount = { affectedCount },
            totalCount = { totalCount },
            onCountChanged = { affectedCount = it }
        )
        bindTimePicker()
        bindPhotoAndNote(savedInstanceState)
        bindSave()
        contentBinding.renderCheckTime(this, selectedTimeMillis)
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
            contentBinding.renderCheckTime(this, selectedTimeMillis)
        }

        contentBinding.cardCheckTime.setOnClickListener {
            AppTimePickerDialogFragment.show(
                fragmentManager = childFragmentManager,
                requestKey = TIME_REQUEST_KEY,
                initialMillis = selectedTimeMillis
            )
        }
    }


    private fun bindPhotoAndNote(savedInstanceState: Bundle?) {
        mediaFlow.initializeSelection(savedInstanceState?.getString(STATE_PHOTO_URI))
        photoController.bind(viewLifecycleOwner)
        contentBinding.renderCheckPhoto(photoController.selectedUri())

        contentBinding.checkPhotoMediaArea.setOnClickListener {
            photoController.showSource(
                getString(R.string.livestock_health_check_photo_source_title)
            )
        }
        contentBinding.btnRemoveCheckPhoto.setOnClickListener {
            photoController.removePhoto()
        }
    }


    private fun bindSave() {
        contentBinding.btnSaveCheck.setOnClickListener {
            if (isSaving) return@setOnClickListener
            val request = requireArguments().toLivestockHealthCheckRequest()
            isSaving = true
            saveMayHaveCommitted = true
            isCancelable = false
            contentBinding.btnSaveCheck.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    healthViewModel.addCheck(
                        request.tankId, request.observationId,
                        LivestockCheckInput(
                            requestId = requestId,
                            status = selectedStatus,
                            affectedCount = affectedCount,
                            checkedAtMillis = selectedTimeMillis,
                            note = contentBinding.etCheckNote.text?.toString()?.trim().orEmpty(),
                            photoUri = photoController.selectedUri()
                        )
                    )
                    resultPublished = true
                    parentFragmentManager.setFragmentResult(
                        REQUEST_KEY,
                        Bundle().apply { putString(RESULT_STATUS, selectedStatus) }
                    )
                    dismiss()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: IOException) {
                    saveMayHaveCommitted = false
                    showLivestockHealthSaveFailure()
                } catch (_: IllegalArgumentException) {
                    saveMayHaveCommitted = false
                    showLivestockHealthSaveFailure()
                } catch (_: IllegalStateException) {
                    saveMayHaveCommitted = false
                    showLivestockHealthSaveFailure()
                } finally {
                    isSaving = false
                    isCancelable = true
                    _contentBinding?.btnSaveCheck?.isEnabled = true
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_STATUS, selectedStatus)
        outState.putString(STATE_REQUEST_ID, requestId)
        outState.putInt(STATE_AFFECTED_COUNT, affectedCount)
        outState.putLong(STATE_TIME_MILLIS, selectedTimeMillis)
        outState.putString(STATE_NOTE, _contentBinding?.etCheckNote?.text?.toString())
        outState.putString(STATE_PHOTO_URI, photoController.selectedUri())
        super.onSaveInstanceState(outState)
    }

    override fun onDismiss(dialog: DialogInterface) {
        if (!resultPublished && !saveMayHaveCommitted &&
            activity?.isChangingConfigurations != true
        ) {
            lifecycleScope.launch {
                withContext(NonCancellable) { mediaFlow.rollbackSelection() }
            }
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


        private const val STATE_STATUS = "status"
        private const val STATE_REQUEST_ID = "request_id"
        private const val STATE_AFFECTED_COUNT = "affected_count"
        private const val STATE_TIME_MILLIS = "time_millis"
        private const val STATE_NOTE = "note"
        private const val STATE_PHOTO_URI = "photo_uri"

        private const val TIME_REQUEST_KEY = "livestock_health_check_time_request"
        private const val TAG = "LivestockHealthCheckBottomSheet"

        fun show(
            fragmentManager: FragmentManager,
            request: LivestockHealthCheckSheetRequest
        ) {
            if (fragmentManager.findFragmentByTag(TAG) != null || fragmentManager.isStateSaved) {
                return
            }

            LivestockHealthCheckBottomSheet().apply {
                arguments = bundleOf(
                    ARG_CHECK_TANK_ID to request.tankId,
                    ARG_CHECK_OBSERVATION_ID to request.observationId,
                    ARG_CHECK_LIVESTOCK_ID to request.livestockId,
                    ARG_CHECK_LIVESTOCK_NAME to request.livestockName,
                    ARG_CHECK_CATEGORY to request.category,
                    ARG_CHECK_ISSUE_LABEL to request.issueLabel,
                    ARG_CHECK_TOTAL_COUNT to request.totalCount,
                    ARG_CHECK_PHOTO_URI to request.photoUri
                )
            }.show(fragmentManager, TAG)
        }

    }
}
