package com.aqua.aqualight.ui.tabs.devices.common.feedback

import android.content.Context
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import com.aqua.aqualight.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton

class DeviceConfirmBottomSheet private constructor(
    private val context: Context
) {

    fun show(
        title: String,
        message: String,
        confirmText: String,
        cancelText: String = "Cancel",
        tone: DeviceConfirmTone = DeviceConfirmTone.WARNING,
        onConfirm: () -> Unit
    ) {
        val dialog = BottomSheetDialog(context)

        val view = LayoutInflater.from(context).inflate(
            R.layout.bottom_sheet_device_confirm,
            null,
            false
        )

        val iconView =
            view.findViewById<ImageView>(R.id.ivConfirmIcon)

        val titleView =
            view.findViewById<TextView>(R.id.tvConfirmTitle)

        val messageView =
            view.findViewById<TextView>(R.id.tvConfirmMessage)

        val cancelButton =
            view.findViewById<MaterialButton>(R.id.btnConfirmCancel)

        val confirmButton =
            view.findViewById<MaterialButton>(R.id.btnConfirmPrimary)

        titleView.text = title
        messageView.text = message
        cancelButton.text = cancelText
        confirmButton.text = confirmText

        iconView.setImageResource(
            iconForTone(tone)
        )

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        confirmButton.setOnClickListener {
            dialog.dismiss()
            onConfirm()
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun iconForTone(
        tone: DeviceConfirmTone
    ): Int {
        return when (tone) {
            DeviceConfirmTone.INFO -> R.drawable.ic_info
            DeviceConfirmTone.WARNING -> R.drawable.ic_warning
            DeviceConfirmTone.DANGER -> R.drawable.ic_warning
        }
    }

    companion object {
        fun create(
            context: Context
        ): DeviceConfirmBottomSheet {
            return DeviceConfirmBottomSheet(context)
        }
    }
}