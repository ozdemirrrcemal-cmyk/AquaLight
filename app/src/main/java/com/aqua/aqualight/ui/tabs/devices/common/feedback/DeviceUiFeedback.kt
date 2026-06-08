package com.aqua.aqualight.ui.tabs.devices.common.feedback

import androidx.fragment.app.Fragment
import com.aqua.aqualight.base.BaseActivity

fun Fragment.showDeviceLoading(
    show: Boolean
) {
    val baseActivity = activity as? BaseActivity
        ?: return

    baseActivity.showLoading(show)
}

fun Fragment.showDeviceSnack(
    message: String,
    type: DeviceFeedbackType = DeviceFeedbackType.NORMAL
) {
    if (message.isBlank()) {
        return
    }

    val baseActivity = activity as? BaseActivity
        ?: return

    baseActivity.showSnackBar(
        message = message,
        type = type.toBaseSnackType()
    )
}

private fun DeviceFeedbackType.toBaseSnackType(): BaseActivity.SnackType {
    return when (this) {
        DeviceFeedbackType.NORMAL -> BaseActivity.SnackType.NORMAL
        DeviceFeedbackType.SUCCESS -> BaseActivity.SnackType.SUCCESS
        DeviceFeedbackType.ERROR -> BaseActivity.SnackType.ERROR
        DeviceFeedbackType.WARNING -> BaseActivity.SnackType.WARNING
    }
}