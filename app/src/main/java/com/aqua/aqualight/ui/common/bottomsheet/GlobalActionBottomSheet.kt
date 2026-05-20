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
    val dialog = BottomSheetDialog(context)

    val view = LayoutInflater.from(context).inflate(
      R.layout.bottom_sheet_global_action,
      null,
      false
    )

    val titleText = view.findViewById<TextView>(
      R.id.tvGlobalActionTitle
    )

    val messageText = view.findViewById<TextView>(
      R.id.tvGlobalActionMessage
    )

    val detailsContainer = view.findViewById<LinearLayout>(
      R.id.globalActionDetailsContainer
    )

    val buttonsContainer = view.findViewById<LinearLayout>(
      R.id.globalActionButtonsContainer
    )

    titleText.text = title

    if (message.isNullOrBlank()) {
      messageText.visibility = View.GONE
    } else {
      messageText.text = message
      messageText.visibility = View.VISIBLE
    }

    renderDetails(
      context = context,
      container = detailsContainer,
      details = details
    )

    renderActions(
      context = context,
      container = buttonsContainer,
      dialog = dialog,
      actions = actions
    )

    dialog.setContentView(view)

    dialog.setOnShowListener {
      val bottomSheet = dialog.findViewById<View>(
        com.google.android.material.R.id.design_bottom_sheet
      )

      bottomSheet?.background = ColorDrawable(
        Color.TRANSPARENT
      )
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

    details.forEachIndexed {
      index,
      detail ->

      val row = LayoutInflater.from(context).inflate(
        R.layout.item_global_bottom_sheet_detail_row,
        container,
        false
      )

      val labelText = row.findViewById<TextView>(
        R.id.tvDetailLabel
      )

      val valueText = row.findViewById<TextView>(
        R.id.tvDetailValue
      )

      labelText.text = detail.label
      valueText.text = detail.value

      val params = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )

      if (index > 0) {
        params.topMargin = 11.dp(context)
      }

      row.layoutParams = params

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

    actions.forEachIndexed {
      index,
      action ->

      val button = createActionButton(
        context = context,
        dialog = dialog,
        action = action
      )

      val params = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        50.dp(context)
      )

      if (index > 0) {
        params.topMargin = 10.dp(context)
      }

      button.layoutParams = params

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
          setTextColor(Color.WHITE)
          backgroundTintList = ColorStateList.valueOf(
            Color.parseColor("#2196F3")
          )
        }

        BottomSheetActionStyle.DANGER -> {
          setTextColor(Color.parseColor("#FF8A8A"))
          backgroundTintList = ColorStateList.valueOf(
            Color.parseColor("#321E2A")
          )
          strokeWidth = 1.dp(context)
          strokeColor = ColorStateList.valueOf(
            Color.parseColor("#7A3344")
          )
        }

        BottomSheetActionStyle.NEUTRAL -> {
          setTextColor(Color.parseColor("#D8E6F5"))
          backgroundTintList = ColorStateList.valueOf(
            Color.parseColor("#20384F")
          )
          strokeWidth = 1.dp(context)
          strokeColor = ColorStateList.valueOf(
            Color.parseColor("#35536E")
          )
        }
      }

      setOnClickListener {
        dialog.dismiss()
        action.onClick()
      }
    }
  }

  private fun Int.dp(
    context: Context
  ): Int {
    return (this * context.resources.displayMetrics.density).toInt()
  }
}