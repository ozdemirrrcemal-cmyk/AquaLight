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
import com.aqua.aqualight.application.aquarium.health.LIVESTOCK_HEALTH_MAX_PHOTOS
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
            activeSlotIndex = { activePhotoSlotIndex },
            currentPhotoUri = { slotIndex -> checkPhotoUris[slotIndex] },
            onPhotoChanged = { slotIndex, photoUri ->
                checkPhotoUris[slotIndex] = photoUri
                _contentBinding?.renderCheckPhotoSlots(checkPhotoUris)
            }
        )
    }

    private var selectedStatus: String = STATUS_SAME
    private var affectedCount: Int = 1
    private var totalCount: Int = 1
    private var selectedTimeMillis: Long = 0L
    private val checkPhotoUris = MutableList<String?>(LIVESTOCK_HEALTH_MAX_PHOTOS) { null }
    private var activePhotoSlotIndex: Int = 0
    private var resultPublished: Boolean = false
    private var isSaving = false
    private var saveMayHaveCommitted = false
    private var requestId: String = UUID.randomUUID().toString()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val request = requireArguments().toLivestockHealthCheckRequest()
        selectedStatus = savedInstanceState?.getString(STATE_STATUS) ?: STATUS_SAME
        requestId = savedInstanceState?.getString(STATE_REQUEST_ID) ?: requestId
        affectedCount = savedInstanceState
            ?.getInt(STATE_AFFECTED_COUNT)
            ?.takeIf { it > 0 }
            ?: request.affectedCount
        selectedTimeMillis = savedInstanceState?.getLong(
            STATE_TIME_MILLIS,
            System.currentTimeMillis()
        ) ?: System.currentTimeMillis()
        savedInstanceState
            ?.getStringArrayList(STATE_PHOTO_URIS)
            ?.take(LIVESTOCK_HEALTH_MAX_PHOTOS)
            ?.forEachIndexed { index, uri ->
                checkPhotoUris[index] = uri.takeIf(String::isNotBlank)
            }
        activePhotoSlotIndex = savedInstanceState
            ?.getInt(STATE_ACTIVE_PHOTO_SLOT, 0)
            ?.coerceIn(0, LIVESTOCK_HEALTH_MAX_PHOTOS - 1)
            ?: 0
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
        photoController.bind(viewLifecycleOwner)
        contentBinding.renderCheckPhotoSlots(checkPhotoUris)
        contentBinding.bindCheckPhotoSlots(
            currentPhotoUris = { checkPhotoUris },
            onSlotSelected = { slotIndex ->
                activePhotoSlotIndex = slotIndex
                photoController.showSource(
                    title = getString(R.string.livestock_health_check_photo_source_title),
                    currentUri = checkPhotoUris[slotIndex]
                )
            }
        )
        contentBinding.etCheckNote.setText(savedInstanceState?.getString(STATE_NOTE))
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
                            photoUris = checkPhotoUris.filterNotNull()
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
        outState.putStringArrayList(
            STATE_PHOTO_URIS,
            ArrayList(checkPhotoUris.map { uri -> uri.orEmpty() })
        )
        outState.putInt(STATE_ACTIVE_PHOTO_SLOT, activePhotoSlotIndex)
        super.onSaveInstanceState(outState)
    }

    override fun onDismiss(dialog: DialogInterface) {
        if (!resultPublished && !saveMayHaveCommitted &&
            activity?.isChangingConfigurations != true
        ) {
            val pendingPhotos = checkPhotoUris.filterNotNull().distinct()
            lifecycleScope.launch {
                withContext(NonCancellable) {
                    pendingPhotos.forEach { uri -> mediaFlow.rollbackPendingMedia(uri) }
                }
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

        const val STATUS_INCREASED = "increased"
        const val STATUS_SAME = "same"
        const val STATUS_DECREASED = "decreased"
        const val STATUS_RECOVERED = "recovered"


        private const val STATE_STATUS = "status"
        private const val STATE_REQUEST_ID = "request_id"
        private const val STATE_AFFECTED_COUNT = "affected_count"
        private const val STATE_TIME_MILLIS = "time_millis"
        private const val STATE_NOTE = "note"
        private const val STATE_PHOTO_URIS = "photo_uris"
        private const val STATE_ACTIVE_PHOTO_SLOT = "active_photo_slot"

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
                    ARG_CHECK_AFFECTED_COUNT to request.affectedCount,
                    ARG_CHECK_TOTAL_COUNT to request.totalCount,
                    ARG_CHECK_LIVESTOCK_PHOTO_URI to request.livestockPhotoUri
                )
            }.show(fragmentManager, TAG)
        }

    }
}
