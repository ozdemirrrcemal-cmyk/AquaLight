package com.aqua.aqualight.ui.tabs.aquarium.materials

import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
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
        val dialog = BottomSheetDialog(context)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp(context), 22.dp(context), 24.dp(context), 24.dp(context))
            background = ContextCompat.getDrawable(
                context,
                R.drawable.bg_aqua_bottom_sheet
            )
        }

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

    private fun createLabel(
        context: android.content.Context,
        text: String,
        topMargin: Int = 18
    ): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(Color.WHITE)
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
        return EditText(context).apply {
            setText(initialName)
            if (initialName.isNotBlank()) {
                setSelection(initialName.length)
            }
            hint = context.getString(R.string.material_picker_enter_material_name)
            setHintTextColor(Color.parseColor("#7F91AA"))
            setTextColor(Color.WHITE)
            textSize = 15f
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            background = null
            setPadding(16.dp(context), 0, 16.dp(context), 0)
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
            setTextColor(Color.parseColor("#D6E2F0"))
            textSize = 15f
            setTypeface(null, Typeface.NORMAL)
            setPadding(16.dp(context), 0, 16.dp(context), 0)
            includeFontPadding = false
        }
    }

    private fun createInputCard(
        content: View
    ): MaterialCardView {
        val context = content.context
        return MaterialCardView(context).apply {
            radius = 14.dp(context).toFloat()
            strokeWidth = 1.dp(context)
            strokeColor = Color.parseColor("#223A57")
            setCardBackgroundColor(Color.parseColor("#16314D"))
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
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setAllCaps(false)
            cornerRadius = 16.dp(context)
            setBackgroundColor(Color.parseColor("#2196F3"))
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
            setTextColor(Color.parseColor("#8FA4BE"))
            textSize = 15f
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
            setTextColor(Color.WHITE)
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
            setTextColor(Color.WHITE)
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

    private fun Int.dp(context: android.content.Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }
}
