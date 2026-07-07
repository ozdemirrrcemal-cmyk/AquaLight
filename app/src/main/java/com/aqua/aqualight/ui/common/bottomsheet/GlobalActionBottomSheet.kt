package com.aqua.aqualight.ui.common.bottomsheet

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.aqua.aqualight.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton

data class BottomSheetDetailRow(
  val label: String,
  val value: String
)

data class BottomSheetAction(
  val text: String,
  val style: BottomSheetActionStyle,
  val onClick: () -> Unit
)

enum class BottomSheetActionStyle {
  PRIMARY,
  DANGER,
  NEUTRAL
}

object GlobalActionBottomSheet {

  fun show(
    context: Context,
    title: String,
    message: String? = null,
    details: List<BottomSheetDetailRow> = emptyList(),
    actions: List<BottomSheetAction> = emptyList()
  ) {
    val dialog = BottomSheetDialog(context, R.style.AquaBottomSheetDialogTheme)

    val view = LayoutInflater.from(context).inflate(
      R.layout.bottom_sheet_global_action,
      null,
      false
    )

    val titleText = view.findViewById<TextView>(R.id.tvGlobalActionTitle)
    val messageText = view.findViewById<TextView>(R.id.tvGlobalActionMessage)
    val detailsContainer = view.findViewById<LinearLayout>(R.id.globalActionDetailsContainer)
    val buttonsContainer = view.findViewById<LinearLayout>(R.id.globalActionButtonsContainer)

    titleText.text = title

    if (message.isNullOrBlank()) {
      messageText.visibility = View.GONE
    } else {
      messageText.text = message
      messageText.visibility = View.VISIBLE
    }

    renderDetails(context, detailsContainer, details)
    renderActions(context, buttonsContainer, dialog, actions)

    dialog.setContentView(view)
    dialog.setOnShowListener {
      dialog.findViewById<View>(
        com.google.android.material.R.id.design_bottom_sheet
      )?.background = ColorDrawable(Color.TRANSPARENT)
    }
    dialog.show()
  }

  private fun renderDetails(
    context: Context,
    container: LinearLayout,
    details: List<BottomSheetDetailRow>
  ) {
    container.removeAllViews()

    if (details.isEmpty()) {
      container.visibility = View.GONE
      return
    }

    container.visibility = View.VISIBLE

    details.forEachIndexed { index, detail ->
      val row = LayoutInflater.from(context).inflate(
        R.layout.item_global_bottom_sheet_detail_row,
        container,
        false
      )

      row.findViewById<TextView>(R.id.tvDetailLabel).text = detail.label
      row.findViewById<TextView>(R.id.tvDetailValue).text = detail.value

      row.layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply {
        if (index > 0) topMargin = 11.dp(context)
      }

      container.addView(row)
    }
  }

  private fun renderActions(
    context: Context,
    container: LinearLayout,
    dialog: BottomSheetDialog,
    actions: List<BottomSheetAction>
  ) {
    container.removeAllViews()

    if (actions.isEmpty()) {
      container.visibility = View.GONE
      return
    }

    container.visibility = View.VISIBLE

    actions.forEachIndexed { index, action ->
      val button = createActionButton(context, dialog, action)

      button.layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        50.dp(context)
      ).apply {
        if (index > 0) topMargin = 10.dp(context)
      }

      container.addView(button)
    }
  }

  private fun createActionButton(
    context: Context,
    dialog: BottomSheetDialog,
    action: BottomSheetAction
  ): MaterialButton {
    return MaterialButton(context).apply {
      text = action.text
      textSize = 14f
      setTypeface(null, Typeface.BOLD)
      isAllCaps = false
      minHeight = 50.dp(context)
      cornerRadius = 16.dp(context)
      insetTop = 0
      insetBottom = 0
      strokeWidth = 0

      when (action.style) {
        BottomSheetActionStyle.PRIMARY -> {
          setTextColor(context.color(R.color.aqua_bottom_sheet_on_primary))
          backgroundTintList = context.tint(R.color.aqua_bottom_sheet_primary)
        }

        BottomSheetActionStyle.DANGER -> {
          setTextColor(context.color(R.color.aqua_bottom_sheet_danger))
          backgroundTintList = context.tint(R.color.aqua_bottom_sheet_danger_container)
          strokeWidth = 1.dp(context)
          strokeColor = context.tint(R.color.aqua_bottom_sheet_danger_outline)
        }

        BottomSheetActionStyle.NEUTRAL -> {
          setTextColor(context.color(R.color.aqua_bottom_sheet_on_neutral))
          backgroundTintList = context.tint(R.color.aqua_bottom_sheet_neutral)
          strokeWidth = 1.dp(context)
          strokeColor = context.tint(R.color.aqua_bottom_sheet_outline)
        }
      }

      setOnClickListener {
        dialog.dismiss()
        action.onClick()
      }
    }
  }

  private fun Context.color(colorRes: Int): Int =
    ContextCompat.getColor(this, colorRes)

  private fun Context.tint(colorRes: Int): ColorStateList =
    ColorStateList.valueOf(color(colorRes))

  private fun Int.dp(context: Context): Int =
    (this * context.resources.displayMetrics.density).toInt()
}
