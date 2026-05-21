package com.aqua.aqualight.ui.common.bottomsheet

import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import com.aqua.aqualight.databinding.DialogSettingsBottomSheetBinding
import com.google.android.material.bottomsheet.BottomSheetDialog

object SettingsContentBottomSheet {

  fun show(
    fragment: Fragment,
    title: String,
    contentView: View,
    onDialogReady: ((BottomSheetDialog) -> Unit)? = null
  ) {
    val dialog = BottomSheetDialog(
      fragment.requireContext()
    )

    val sheetBinding = DialogSettingsBottomSheetBinding.inflate(
      fragment.layoutInflater
    )

    sheetBinding.tvSheetTitle.text = title

    sheetBinding.sheetContentContainer.removeAllViews()
    sheetBinding.sheetContentContainer.addView(contentView)

    dialog.setContentView(sheetBinding.root)

    dialog.setOnShowListener {
      val bottomSheet = dialog.findViewById<FrameLayout>(
        com.google.android.material.R.id.design_bottom_sheet
      )

      bottomSheet?.setBackgroundColor(
        Color.TRANSPARENT
      )
    }

    onDialogReady?.invoke(dialog)

    dialog.show()
  }
}