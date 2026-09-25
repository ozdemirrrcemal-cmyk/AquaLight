package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.platform.permissions.AppCapability
import com.aqua.aqualight.ui.common.bottomsheet.PhotoSourceBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.SingleChoiceBottomSheet
import com.aqua.aqualight.ui.common.dialog.AppDatePickerDialogFragment
import com.aqua.aqualight.ui.common.feedback.FeedbackBottomSheet
import com.aqua.aqualight.application.aquarium.LivestockHealthTrend
import kotlinx.coroutines.launch

internal const val HEALTH_CAPTURE_ACTION = "health_photo_capture"

internal fun LivestockHealthFragment.registerHealthResultListeners() {
        childFragmentManager.setFragmentResultListener(DATE_REQUEST, viewLifecycleOwner) { _, result ->
            if (result.getString(AppDatePickerDialogFragment.RESULT_KEY) ==
                AppDatePickerDialogFragment.RESULT_SELECTED
            ) {
                startedAtMillis = result.getLong(AppDatePickerDialogFragment.RESULT_MILLIS)
                tank?.let(::render)
            }
        }
        childFragmentManager.setFragmentResultListener(TREND_REQUEST, viewLifecycleOwner) { _, result ->
            if (result.getString(SingleChoiceBottomSheet.RESULT_KEY) ==
                SingleChoiceBottomSheet.RESULT_SELECTED
            ) {
                trend = LivestockHealthTrend.fromCode(
                    result.getString(SingleChoiceBottomSheet.RESULT_SELECTED_ID).orEmpty())
                tank?.let(::render)
            }
        }
        childFragmentManager.setFragmentResultListener(CLOSE_REQUEST, viewLifecycleOwner) { _, result ->
            if (result.getString(FeedbackBottomSheet.RESULT_KEY) ==
                FeedbackBottomSheet.RESULT_PRIMARY
            ) closeRecord(result.getString(FeedbackBottomSheet.RESULT_ACTION_ID)?.toLongOrNull())
        }
        childFragmentManager.setFragmentResultListener(PhotoSourceBottomSheet.REQUEST_KEY,
            viewLifecycleOwner) { _, result ->
            when (result.getString(PhotoSourceBottomSheet.RESULT_KEY)) {
                PhotoSourceBottomSheet.RESULT_CAMERA -> permissionCoordinator.runWhenGranted(
                    AppCapability.CAMERA_PHOTO, HEALTH_CAPTURE_ACTION)
                PhotoSourceBottomSheet.RESULT_GALLERY -> galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                PhotoSourceBottomSheet.RESULT_REMOVE -> {
                    viewLifecycleOwner.lifecycleScope.launch {
                        mediaFlow.selectRemoval()
                        photoUri = null
                        tank?.let(::render)
                    }
                }
            }
        }
 }

internal fun LivestockHealthFragment.observeHealthState() {
        tanks.tanks.observe(viewLifecycleOwner) { snapshots ->
            val current = snapshots.firstOrNull { it.id == args.tankId }
            if (current == null) {
                findNavController().popBackStack()
            } else {
                tank = current
                if (args.page == LivestockHealthPages.FORM && selectedLivestockId == 0L) {
                    selectedLivestockId = current.livestock.firstOrNull()?.id ?: 0L
                }
                render(current)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                maintenance.tankActivityStateFlow(args.tankId).collect { state ->
                    tankActivity = state
                    tank?.let(::render)
                }
            }
        } }
