package com.aqua.aqualight.utils

import android.content.Context
import android.content.ContextWrapper
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentActivity
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.feedback.FeedbackBottomSheet

enum class DialogType {
    INFO,
    ERROR,
    SUCCESS,
    WARNING
}

/** Compatibility facade for one-way informational feedback. Confirmations use ConfirmDialogFragment. */
object DialogManager {

    fun showInfoDialog(
        context: Context,
        type: DialogType,
        title: String,
        message: String,
        @StringRes buttonTextResId: Int = R.string.ok,
        autoDismissMillis: Long = 0L
    ) {
        val activity = context.findFragmentActivity() ?: return
        val requestKey = buildString {
            append(INFO_REQUEST_PREFIX)
            append(type.name)
            append(':')
            append(title.hashCode())
            append(':')
            append(message.hashCode())
        }
        FeedbackBottomSheet.show(
            fragmentManager = activity.supportFragmentManager,
            title = title,
            message = message,
            primaryText = if (autoDismissMillis > 0L) "" else context.getString(buttonTextResId),
            cancelText = null,
            tone = type.toFeedbackTone(),
            requestKey = requestKey,
            actionId = "",
            autoDismissMillis = autoDismissMillis
        )
    }

    private fun DialogType.toFeedbackTone(): FeedbackBottomSheet.FeedbackTone {
        return when (this) {
            DialogType.INFO -> FeedbackBottomSheet.FeedbackTone.INFO
            DialogType.SUCCESS -> FeedbackBottomSheet.FeedbackTone.SUCCESS
            DialogType.WARNING -> FeedbackBottomSheet.FeedbackTone.WARNING
            DialogType.ERROR -> FeedbackBottomSheet.FeedbackTone.ERROR
        }
    }

    private tailrec fun Context.findFragmentActivity(): FragmentActivity? {
        return when (this) {
            is FragmentActivity -> this
            is ContextWrapper -> baseContext.findFragmentActivity()
            else -> null
        }
    }

    private const val INFO_REQUEST_PREFIX = "dialog_manager_info:"
}
