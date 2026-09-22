package com.aqua.aqualight.ui.common.devicepresence

import android.content.Context
import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType

object DeviceAccessFeedbackPresenter {

    fun show(
        context: Context,
        deviceTitle: String,
        @StringRes titleRes: Int,
        @StringRes messageRes: Int
    ) {
        val safeTitle = deviceTitle.trim().ifBlank {
            context.getString(R.string.device_menu_default_title)
        }
        DialogManager.showInfoDialog(
            context = context,
            type = DialogType.WARNING,
            title = context.getString(titleRes),
            message = context.getString(
                R.string.device_access_dialog_message,
                safeTitle,
                context.getString(messageRes)
            ),
            buttonTextResId = android.R.string.ok
        )
    }
}
