package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.menu

import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.ui.common.bottomsheet.BottomSheetAction
import com.aqua.aqualight.ui.common.bottomsheet.BottomSheetActionStyle
import com.aqua.aqualight.ui.common.bottomsheet.GlobalActionBottomSheet
import com.aqua.aqualight.ui.common.feedback.FeedbackBottomSheet
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.DeviceLightAutomaticProgramsEffect
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.DeviceLightAutomaticProgramsUiState
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.DeviceLightAutomaticProgramsViewModel
import kotlinx.coroutines.launch

internal class DeviceLightAutomaticProgramSheetCoordinator(
    private val fragment: Fragment,
    private val onDuplicate: () -> Unit,
    private val onDelete: (String) -> Unit
) {
    fun register(owner: LifecycleOwner) {
        fragment.childFragmentManager.setFragmentResultListener(
            ACTIONS_REQUEST_KEY,
            owner
        ) { _, result ->
            if (result.getString(GlobalActionBottomSheet.RESULT_KEY) !=
                GlobalActionBottomSheet.RESULT_ACTION
            ) {
                return@setFragmentResultListener
            }
            val programId = result
                .getString(GlobalActionBottomSheet.RESULT_PAYLOAD_ID)
                .orEmpty()
            when (result.getString(GlobalActionBottomSheet.RESULT_ACTION_ID)) {
                ACTION_DUPLICATE -> onDuplicate()
                ACTION_DELETE -> showDeleteConfirmation(programId)
            }
        }
        fragment.childFragmentManager.setFragmentResultListener(
            DELETE_REQUEST_KEY,
            owner
        ) { _, result ->
            if (result.getString(FeedbackBottomSheet.RESULT_KEY) ==
                FeedbackBottomSheet.RESULT_PRIMARY
            ) {
                onDelete(
                    result.getString(FeedbackBottomSheet.RESULT_ACTION_ID).orEmpty()
                )
            }
        }
    }

    fun showActions(programId: String) {
        GlobalActionBottomSheet.show(
            fragmentManager = fragment.childFragmentManager,
            title = fragment.getString(R.string.device_light_auto_actions_title),
            actions = listOf(
                BottomSheetAction(
                    id = ACTION_DUPLICATE,
                    text = fragment.getString(R.string.device_light_auto_duplicate),
                    style = BottomSheetActionStyle.NEUTRAL
                ),
                BottomSheetAction(
                    id = ACTION_DELETE,
                    text = fragment.getString(R.string.device_light_auto_delete),
                    style = BottomSheetActionStyle.DANGER
                )
            ),
            requestKey = ACTIONS_REQUEST_KEY,
            payloadId = programId
        )
    }

    private fun showDeleteConfirmation(programId: String) {
        FeedbackBottomSheet.show(
            fragmentManager = fragment.childFragmentManager,
            title = fragment.getString(R.string.device_light_auto_delete_title),
            message = fragment.getString(R.string.device_light_auto_delete_message),
            primaryText = fragment.getString(R.string.device_light_auto_delete_confirm),
            cancelText = fragment.getString(R.string.cancel),
            tone = FeedbackBottomSheet.FeedbackTone.DANGER,
            requestKey = DELETE_REQUEST_KEY,
            actionId = programId
        )
    }

    private companion object {
        const val ACTIONS_REQUEST_KEY = "device_light_auto_actions"
        const val DELETE_REQUEST_KEY = "device_light_auto_delete"
        const val ACTION_DUPLICATE = "duplicate"
        const val ACTION_DELETE = "delete"
    }
}

internal fun LifecycleOwner.observeAutomaticPrograms(
    viewModel: DeviceLightAutomaticProgramsViewModel,
    onState: (DeviceLightAutomaticProgramsUiState) -> Unit,
    onEffect: (DeviceLightAutomaticProgramsEffect) -> Unit
) {
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch { viewModel.uiState.collect(onState) }
            launch { viewModel.effects.collect(onEffect) }
        }
    }
}

internal fun Fragment.renderAutomaticProgramsEffect(
    effect: DeviceLightAutomaticProgramsEffect
) {
    when (effect) {
        is DeviceLightAutomaticProgramsEffect.ShowMessage -> {
            (activity as? BaseActivity)?.showSnackBar(
                message = getString(effect.messageRes),
                type = if (effect.success) {
                    BaseActivity.SnackType.SUCCESS
                } else {
                    BaseActivity.SnackType.ERROR
                }
            )
        }
    }
}
