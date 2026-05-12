package com.aqua.aqualight.utils

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.DialogAppBinding
import com.aqua.aqualight.databinding.DialogAppConfirmBinding

enum class DialogType {
    INFO,
    ERROR,
    SUCCESS,
    WARNING
}

object DialogManager {

    fun showInfoDialog(
        context: Context,
        type: DialogType,
        title: String,
        message: String,
        @StringRes buttonTextResId: Int = R.string.ok,
        onDismiss: (() -> Unit)? = null,
        autoDismissMillis: Long = 0L
    ) {
        val binding = DialogAppBinding.inflate(
            android.view.LayoutInflater.from(context)
        )

        val iconRes = when (type) {
            DialogType.ERROR -> R.drawable.ic_error
            DialogType.SUCCESS -> R.drawable.ic_success
            DialogType.WARNING -> R.drawable.ic_warning
            DialogType.INFO -> R.drawable.ic_info
        }

        binding.dialogIcon.apply {
            imageTintList = null
            setImageResource(iconRes)
            contentDescription = context.getString(R.string.dialog_icon_desc)
        }

        binding.dialogTitle.text = title
        binding.dialogMessage.text = message

        val dialog = AlertDialog.Builder(context, R.style.AppDialogTheme)
            .setView(binding.root)
            .setCancelable(false)
            .create()

        val handler = Handler(Looper.getMainLooper())
        var dismissRunnable: Runnable? = null

        dialog.setOnDismissListener {
            dismissRunnable?.let { runnable ->
                handler.removeCallbacks(runnable)
            }
            onDismiss?.invoke()
        }

        if (autoDismissMillis <= 0L) {
            binding.dialogButton.visibility = android.view.View.VISIBLE
            binding.dialogButton.text = context.getString(buttonTextResId)
            binding.dialogButton.setOnClickListener {
                dialog.dismiss()
            }
        } else {
            binding.dialogButton.visibility = android.view.View.GONE

            dismissRunnable = Runnable {
                if (dialog.isShowing) {
                    dialog.dismiss()
                }
            }

            handler.postDelayed(dismissRunnable!!, autoDismissMillis)
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    fun showConfirmDialog(
        context: Context,
        type: DialogType,
        title: String,
        message: String,
        @StringRes confirmTextResId: Int = R.string.confirm,
        @StringRes cancelTextResId: Int = R.string.cancel,
        onConfirm: (() -> Unit)? = null,
        onCancel: (() -> Unit)? = null
    ) {
        val binding = DialogAppConfirmBinding.inflate(
            android.view.LayoutInflater.from(context)
        )

        val iconRes = when (type) {
            DialogType.ERROR -> R.drawable.ic_error
            DialogType.WARNING -> R.drawable.ic_warning
            DialogType.SUCCESS -> R.drawable.ic_success
            DialogType.INFO -> R.drawable.ic_info
        }

        binding.dialogIcon.apply {
            imageTintList = null
            setImageResource(iconRes)
            contentDescription = context.getString(R.string.dialog_icon_desc)
        }

        binding.dialogTitle.text = title
        binding.dialogMessage.text = message
        binding.btnCancel.text = context.getString(cancelTextResId)
        binding.btnConfirm.text = context.getString(confirmTextResId)

        val dialog = AlertDialog.Builder(context, R.style.AppDialogTheme)
            .setView(binding.root)
            .setCancelable(false)
            .create()

        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
            onCancel?.invoke()
        }

        binding.btnConfirm.setOnClickListener {
            dialog.dismiss()
            onConfirm?.invoke()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }
}