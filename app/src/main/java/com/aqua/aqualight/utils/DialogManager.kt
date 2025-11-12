package com.aqua.aqualight.utils

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import com.aqua.aqualight.R
import com.google.android.material.button.MaterialButton

enum class DialogType { INFO, ERROR, SUCCESS, WARNING }

object DialogManager {

    fun showInfoDialog(
        context: Context,
        type: DialogType,
        title: String,
        message: String,
        buttonText: String = "OK",
        onDismiss: (() -> Unit)? = null
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_app, null)
        val icon = view.findViewById<ImageView>(R.id.dialogIcon)
        val titleView = view.findViewById<TextView>(R.id.dialogTitle)
        val messageView = view.findViewById<TextView>(R.id.dialogMessage)
        val button = view.findViewById<MaterialButton>(R.id.dialogButton)

        // 🔹 4 farklı tip için ikon ataması
        val iconRes = when (type) {
            DialogType.ERROR -> R.drawable.ic_error
            DialogType.SUCCESS -> R.drawable.ic_success
            DialogType.WARNING -> R.drawable.ic_warning
            DialogType.INFO -> R.drawable.ic_info
        }

        // 🔸 Tint sorununu sıfırla, ikon kendi fillColor rengini kullansın
        icon.imageTintList = null
        icon.setImageResource(iconRes)

        titleView.text = title
        messageView.text = message
        button.text = buttonText

        val dialog = AlertDialog.Builder(context, R.style.AppDialogTheme)
            .setView(view)
            .setCancelable(false)
            .create()

        button.setOnClickListener {
            dialog.dismiss()
            onDismiss?.invoke()
        }

        // 🔸 Arka planı tamamen saydam yap
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    fun showConfirmDialog(
        context: Context,
        type: DialogType,
        title: String,
        message: String,
        confirmText: String = "Confirm",
        cancelText: String = "Cancel",
        onConfirm: (() -> Unit)? = null,
        onCancel: (() -> Unit)? = null
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_app_confirm, null)
        val icon = view.findViewById<ImageView>(R.id.dialogIcon)
        val titleView = view.findViewById<TextView>(R.id.dialogTitle)
        val messageView = view.findViewById<TextView>(R.id.dialogMessage)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btnCancel)
        val btnConfirm = view.findViewById<MaterialButton>(R.id.btnConfirm)

        val iconRes = when (type) {
            DialogType.ERROR -> R.drawable.ic_error
            DialogType.WARNING -> R.drawable.ic_warning
            DialogType.SUCCESS -> R.drawable.ic_success
            DialogType.INFO -> R.drawable.ic_info
        }

        // 🔸 Tint’i sıfırla, fillColor aktif kalsın
        icon.imageTintList = null
        icon.setImageResource(iconRes)

        titleView.text = title
        messageView.text = message
        btnCancel.text = cancelText
        btnConfirm.text = confirmText

        val dialog = AlertDialog.Builder(context, R.style.AppDialogTheme)
            .setView(view)
            .setCancelable(false)
            .create()

        btnCancel.setOnClickListener {
            dialog.dismiss()
            onCancel?.invoke()
        }

        btnConfirm.setOnClickListener {
            dialog.dismiss()
            onConfirm?.invoke()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }
}