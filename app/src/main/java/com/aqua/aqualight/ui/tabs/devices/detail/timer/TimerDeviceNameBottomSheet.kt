package com.aqua.aqualight.ui.tabs.devices.detail.timer

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class TimerDeviceNameBottomSheet(
    private val fragment: Fragment,
    private val currentName: String,
    private val fallbackName: String,
    private val onSave: (
        newName: String,
        sheet: TimerDeviceNameBottomSheet
    ) -> Unit
) {

    private lateinit var dialog: BottomSheetDialog
    private lateinit var root: LinearLayout
    private lateinit var inputLayout: TextInputLayout
    private lateinit var editText: TextInputEditText
    private lateinit var errorText: TextView
    private lateinit var btnCancel: MaterialButton
    private lateinit var btnSave: MaterialButton

    private var isSaving: Boolean = false

    fun show() {
        val context = fragment.requireContext()

        dialog = BottomSheetDialog(
            context
        )

        root = createContent(
            context = context
        )

        dialog.setContentView(
            root
        )

        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            )

            bottomSheet?.background = ColorDrawable(
                Color.TRANSPARENT
            )

            bottomSheet?.let { sheet ->
                val behavior = BottomSheetBehavior.from(
                    sheet
                )

                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                behavior.isDraggable = true
            }
        }

        dialog.show()
    }

    private fun createContent(
        context: Context
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                22.dp(context),
                12.dp(context),
                22.dp(context),
                22.dp(context)
            )

            background = roundedDrawable(
                color = Color.parseColor("#0B1727"),
                radiusDp = 28,
                context = context
            )

            addView(
                createHandle(
                    context = context
                )
            )

            addView(
                createTitle(
                    context = context
                )
            )

            addView(
                createSubtitle(
                    context = context
                )
            )

            addView(
                createInput(
                    context = context
                )
            )

            addView(
                createErrorText(
                    context = context
                )
            )

            addView(
                createButtons(
                    context = context
                )
            )
        }
    }

    private fun createHandle(
        context: Context
    ): View {
        return View(context).apply {
            background = roundedDrawable(
                color = Color.parseColor("#31445F"),
                radiusDp = 100,
                context = context
            )

            layoutParams = LinearLayout.LayoutParams(
                54.dp(context),
                5.dp(context)
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = 20.dp(context)
            }
        }
    }

    private fun createTitle(
        context: Context
    ): TextView {
        return TextView(context).apply {
            text = "Device Name"
            setTextColor(
                Color.parseColor("#E8EEF7")
            )
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
        }
    }

    private fun createSubtitle(
        context: Context
    ): TextView {
        return TextView(context).apply {
            text = "This name is used only in the app."
            setTextColor(
                Color.parseColor("#9FAABB")
            )
            textSize = 13f

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 6.dp(context)
            }
        }
    }

    private fun createInput(
        context: Context
    ): TextInputLayout {
        editText = TextInputEditText(context).apply {
            setText(
                currentName
            )

            setSelection(
                text?.length ?: 0
            )

            setSingleLine(
                true
            )

            setTextColor(
                Color.parseColor("#E8EEF7")
            )

            setHintTextColor(
                Color.parseColor("#92A1B4")
            )

            textSize = 16f
        }

        inputLayout = TextInputLayout(context).apply {
            hint = fallbackName.ifBlank {
                "Device name"
            }

            boxBackgroundMode =
                TextInputLayout.BOX_BACKGROUND_OUTLINE

            boxBackgroundColor =
                Color.parseColor("#101F33")

            boxStrokeColor =
                Color.parseColor("#5F55C8")

            setBoxCornerRadii(
                18f,
                18f,
                18f,
                18f
            )

            hintTextColor = ColorStateList.valueOf(
                Color.parseColor("#9FAABB")
            )

            addView(
                editText,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 20.dp(context)
            }
        }

        return inputLayout
    }

    private fun createErrorText(
        context: Context
    ): TextView {
        errorText = TextView(context).apply {
            visibility = View.GONE
            setTextColor(
                Color.parseColor("#F18B9B")
            )
            textSize = 13f

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 10.dp(context)
            }
        }

        return errorText
    }

    private fun createButtons(
        context: Context
    ): LinearLayout {
        btnCancel = MaterialButton(context).apply {
            text = "Cancel"
            isAllCaps = false

            backgroundTintList = ColorStateList.valueOf(
                Color.parseColor("#15263A")
            )

            setTextColor(
                Color.parseColor("#D5DEEA")
            )

            strokeColor = ColorStateList.valueOf(
                Color.parseColor("#2A3E59")
            )

            strokeWidth = 1.dp(context)

            setOnClickListener {
                dialog.dismiss()
            }
        }

        btnSave = MaterialButton(context).apply {
            text = "Save"
            isAllCaps = false

            backgroundTintList = ColorStateList.valueOf(
                Color.parseColor("#6E63E8")
            )

            setTextColor(
                Color.WHITE
            )

            setOnClickListener {
                save()
            }
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                52.dp(context)
            ).apply {
                topMargin = 22.dp(context)
            }

            addView(
                btnCancel,
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1f
                ).apply {
                    rightMargin = 8.dp(context)
                }
            )

            addView(
                btnSave,
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1f
                ).apply {
                    leftMargin = 8.dp(context)
                }
            )
        }
    }

    private fun save() {
        if (isSaving) {
            return
        }

        errorText.visibility = View.GONE

        val newName = editText.text
            ?.toString()
            ?.trim()
            .orEmpty()

        if (newName.isBlank()) {
            showError(
                message = "Device name cannot be empty."
            )
            return
        }

        setSaving(
            saving = true
        )

        onSave(
            newName,
            this
        )
    }

    fun showSaveError(
        message: String
    ) {
        setSaving(
            saving = false
        )

        showError(
            message = message
        )
    }

    fun closeAfterSave() {
        dialog.dismiss()
    }

    private fun showError(
        message: String
    ) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
    }

    private fun setSaving(
        saving: Boolean
    ) {
        isSaving = saving

        dialog.setCancelable(
            !saving
        )

        dialog.setCanceledOnTouchOutside(
            !saving
        )

        editText.isEnabled = !saving
        btnCancel.isEnabled = !saving
        btnSave.isEnabled = !saving

        btnSave.text = if (saving) {
            "Saving..."
        } else {
            "Save"
        }
    }

    private fun roundedDrawable(
        color: Int,
        radiusDp: Int,
        context: Context
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(
                color
            )

            cornerRadius = radiusDp.dp(
                context = context
            ).toFloat()
        }
    }

    private fun Int.dp(
        context: Context
    ): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }
}