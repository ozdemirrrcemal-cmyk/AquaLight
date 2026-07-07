package com.aqua.aqualight.ui.tabs.aquarium.materials

import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

object CustomMaterialSheet {
    fun show(
        fragment: Fragment,
        categoryTitle: String,
        initialName: String,
        onSave: (String) -> Unit
    ) {
        val context = fragment.requireContext()
        val dialog = BottomSheetDialog(context, R.style.AquaBottomSheetDialogTheme)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22.dp(context), 14.dp(context), 22.dp(context), 24.dp(context))
            background = ContextCompat.getDrawable(context, R.drawable.bg_bottomsheet_rounded)
        }

        addHandle(root)
        addSheetHeader(
            root = root,
            title = context.getString(R.string.material_picker_new_material),
            dialog = dialog
        )

        root.addView(createLabel(context, context.getString(R.string.material_picker_material_name)))

        val nameInput = createNameInput(
            fragment = fragment,
            initialName = initialName
        )
        root.addView(createInputCard(nameInput))

        root.addView(createLabel(context, context.getString(R.string.material_picker_category), topMargin = 22))
        root.addView(createInputCard(createCategoryText(fragment, categoryTitle)))

        root.addView(
            createSaveButton(fragment) {
                val materialName = nameInput.text
                    ?.toString()
                    ?.trim()
                    .orEmpty()

                if (materialName.isBlank()) {
                    nameInput.error = context.getString(R.string.material_picker_required)
                    return@createSaveButton
                }

                onSave(materialName)
                dialog.dismiss()
            }
        )

        root.addView(createCancelButton(fragment, dialog))

        dialog.setContentView(root)
        dialog.setOnShowListener {
            dialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            )?.setBackgroundColor(Color.TRANSPARENT)
        }
        dialog.show()
    }

    private fun addHandle(root: LinearLayout) {
        val context = root.context
        val handle = View(context).apply {
            background = ContextCompat.getDrawable(context, R.drawable.bg_bottomsheet_handle)
            layoutParams = LinearLayout.LayoutParams(
                42.dp(context),
                4.dp(context)
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = 16.dp(context)
            }
        }
        root.addView(handle)
    }

    private fun createLabel(
        context: android.content.Context,
        text: String,
        topMargin: Int = 18
    ): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                this.topMargin = topMargin.dp(context)
            }
        }
    }

    private fun createNameInput(
        fragment: Fragment,
        initialName: String
    ): EditText {
        val context = fragment.requireContext()
        return EditText(ContextThemeWrapper(context, R.style.Widget_Aqua_Input_PlainEditText_Embedded)).apply {
            setText(initialName)
            if (initialName.isNotBlank()) {
                setSelection(initialName.length)
            }
            hint = context.getString(R.string.material_picker_enter_material_name)
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
    }

    private fun createCategoryText(
        fragment: Fragment,
        categoryTitle: String
    ): TextView {
        val context = fragment.requireContext()
        return TextView(context).apply {
            text = categoryTitle
            gravity = Gravity.CENTER_VERTICAL
            setTypeface(null, Typeface.NORMAL)
            includeFontPadding = false
        }
    }

    private fun createInputCard(
        content: View
    ): MaterialCardView {
        val context = content.context
        return MaterialCardView(context).apply {
            radius = 18.dp(context).toFloat()
            strokeWidth = 1.dp(context)
            strokeColor = context.color(R.color.aqua_bottom_sheet_outline_subtle)
            setCardBackgroundColor(context.color(R.color.aqua_bottom_sheet_surface_elevated))
            cardElevation = 0f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                56.dp(context)
            ).apply {
                topMargin = 10.dp(context)
            }
            addView(
                content,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    private fun createSaveButton(
        fragment: Fragment,
        onClick: () -> Unit
    ): MaterialButton {
        val context = fragment.requireContext()
        return MaterialButton(context).apply {
            text = context.getString(R.string.material_picker_save)
            textSize = 16f
            setTextColor(context.color(R.color.aqua_bottom_sheet_on_primary))
            setTypeface(null, Typeface.BOLD)
            setAllCaps(false)
            cornerRadius = 18.dp(context)
            backgroundTintList = android.content.res.ColorStateList.valueOf(
                context.color(R.color.aqua_bottom_sheet_primary)
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                56.dp(context)
            ).apply {
                topMargin = 28.dp(context)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun createCancelButton(
        fragment: Fragment,
        dialog: BottomSheetDialog
    ): TextView {
        val context = fragment.requireContext()
        return TextView(context).apply {
            text = context.getString(R.string.material_picker_cancel)
            gravity = Gravity.CENTER
            setTextColor(context.color(R.color.aqua_bottom_sheet_text_secondary))
            setPadding(0, 18.dp(context), 0, 0)
            setOnClickListener { dialog.dismiss() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun addSheetHeader(
        root: LinearLayout,
        title: String,
        dialog: BottomSheetDialog
    ) {
        val context = root.context
        val header = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                46.dp(context)
            )
        }

        val titleView = TextView(context).apply {
            text = title
            textSize = 18f
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val closeView = TextView(context).apply {
            text = context.getString(R.string.common_close)
            textSize = 34f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setOnClickListener { dialog.dismiss() }
            layoutParams = FrameLayout.LayoutParams(
                44.dp(context),
                44.dp(context),
                Gravity.END or Gravity.CENTER_VERTICAL
            )
        }

        header.addView(titleView)
        header.addView(closeView)
        root.addView(header)
    }

    private fun android.content.Context.color(colorRes: Int): Int =
        ContextCompat.getColor(this, colorRes)

    private fun Int.dp(context: android.content.Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }
}
