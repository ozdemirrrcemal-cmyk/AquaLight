package com.aqua.aqualight.utils

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.DialogAppBinding
import com.aqua.aqualight.databinding.DialogAppConfirmBinding
import com.google.android.material.button.MaterialButton

enum class DialogType { INFO, ERROR, SUCCESS, WARNING }

object DialogManager {

    fun showInfoDialog(
        context: Context,
        type: DialogType,
        title: String,
        message: String,
        @StringRes buttonTextResId: Int = R.string.ok,
        onDismiss: (() -> Unit)? = null,
        autoDismissMillis: Long = 0L   // 0 = butonlu, >0 = auto-dismiss
    ) {
        val binding = DialogAppBinding.inflate(
            android.view.LayoutInflater.from(context)
        )

        // 🔹 İkon
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

        // 🔹 Metinler
        binding.dialogTitle.text = title
        binding.dialogMessage.text = message

        // 🔹 Dialog oluştur
        val dialog = AlertDialog.Builder(context, R.style.AppDialogTheme)
            .setView(binding.root)
            .setCancelable(false)
            .create()

        dialog.setOnDismissListener {
            onDismiss?.invoke()
        }

        if (autoDismissMillis <= 0L) {
            // 👉 Butonlu klasik davranış
            binding.dialogButton.visibility = android.view.View.VISIBLE
            binding.dialogButton.text = context.getString(buttonTextResId)
            binding.dialogButton.setOnClickListener {
                dialog.dismiss()
            }
        } else {
            // 👉 Otomatik kapanan info (toast vari)
            binding.dialogButton.visibility = android.view.View.GONE

            Handler(Looper.getMainLooper()).postDelayed({
                if (dialog.isShowing) {
                    dialog.dismiss()
                }
            }, autoDismissMillis)
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

        // 🔹 İkon
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

        // 🔹 Metinler
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