package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.menu

import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.ui.common.bottomsheet.AquaTimePickerBottomSheet
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.DeviceLightAutomaticProgramEditorEffect
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.DeviceLightAutomaticProgramEditorViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.DeviceLightAutomaticTimeField

internal class DeviceLightAutomaticProgramEditorEffectHandler(
    private val fragment: DeviceLightAutomaticProgramEditorFragment,
    private val viewModel: DeviceLightAutomaticProgramEditorViewModel
) {
    fun registerTimeResult() {
        fragment.childFragmentManager.setFragmentResultListener(
            TIME_REQUEST_KEY,
            fragment.viewLifecycleOwner
        ) { _, result ->
            if (result.getString(AquaTimePickerBottomSheet.RESULT_KEY) !=
                AquaTimePickerBottomSheet.RESULT_SELECTED
            ) {
                return@setFragmentResultListener
            }
            val field = result.getString(AquaTimePickerBottomSheet.RESULT_PAYLOAD_ID)
                ?.let(DeviceLightAutomaticTimeField::valueOf)
                ?: return@setFragmentResultListener
            val minutes = result.getInt(AquaTimePickerBottomSheet.RESULT_MINUTES_OF_DAY)
            viewModel.draftEditor.updateTime(field, minutes * MILLIS_PER_MINUTE)
        }
    }

    fun handle(effect: DeviceLightAutomaticProgramEditorEffect) {
        when (effect) {
            is DeviceLightAutomaticProgramEditorEffect.OpenTimePicker -> showTimePicker(effect)
            is DeviceLightAutomaticProgramEditorEffect.ShowMessage -> showMessage(
                effect.messageRes,
                BaseActivity.SnackType.ERROR
            )
            is DeviceLightAutomaticProgramEditorEffect.Saved -> {
                showMessage(effect.messageRes, BaseActivity.SnackType.SUCCESS)
                fragment.findNavControllerSafely()
            }
        }
    }

    private fun showTimePicker(effect: DeviceLightAutomaticProgramEditorEffect.OpenTimePicker) {
        val fallbackMinutes = when (effect.field) {
            DeviceLightAutomaticTimeField.START -> DEFAULT_START_MINUTES
            DeviceLightAutomaticTimeField.END -> DEFAULT_END_MINUTES
        }
        val minutes = effect.currentTimeMs?.div(MILLIS_PER_MINUTE)?.toInt() ?: fallbackMinutes
        AquaTimePickerBottomSheet.show(
            fragment.childFragmentManager,
            AquaTimePickerBottomSheet.Request(
                title = fragment.getString(effect.field.titleRes()),
                message = fragment.getString(effect.field.messageRes()),
                initialHour = minutes / MINUTES_PER_HOUR,
                initialMinute = minutes % MINUTES_PER_HOUR,
                confirmText = fragment.getString(R.string.device_light_auto_editor_time_select),
                cancelText = fragment.getString(R.string.cancel),
                resultTarget = AquaTimePickerBottomSheet.ResultTarget(
                    requestKey = TIME_REQUEST_KEY,
                    payloadId = effect.field.name
                )
            )
        )
    }

    private fun showMessage(messageRes: Int, type: BaseActivity.SnackType) {
        fragment.setFragmentGlobalLoading(false)
        (fragment.activity as? BaseActivity)?.showSnackBar(
            message = fragment.getString(messageRes),
            type = type
        )
    }
}

private fun DeviceLightAutomaticProgramEditorFragment.findNavControllerSafely() {
    val navController = findNavController()
    if (navController.currentDestination?.id == R.id.deviceLightAutomaticProgramEditorFragment) {
        navController.navigateUp()
    }
}

private fun DeviceLightAutomaticTimeField.titleRes(): Int = when (this) {
    DeviceLightAutomaticTimeField.START -> R.string.device_light_auto_editor_start_time
    DeviceLightAutomaticTimeField.END -> R.string.device_light_auto_editor_end_time
}

private fun DeviceLightAutomaticTimeField.messageRes(): Int = when (this) {
    DeviceLightAutomaticTimeField.START -> R.string.device_light_auto_editor_start_time_picker
    DeviceLightAutomaticTimeField.END -> R.string.device_light_auto_editor_end_time_picker
}

private const val TIME_REQUEST_KEY = "device_light_auto_editor_time"
private const val MINUTES_PER_HOUR = 60
private const val MILLIS_PER_MINUTE = 60_000L
private const val DEFAULT_START_HOUR = 8
private const val DEFAULT_END_HOUR = 22
private const val DEFAULT_START_MINUTES = DEFAULT_START_HOUR * MINUTES_PER_HOUR
private const val DEFAULT_END_MINUTES = DEFAULT_END_HOUR * MINUTES_PER_HOUR
