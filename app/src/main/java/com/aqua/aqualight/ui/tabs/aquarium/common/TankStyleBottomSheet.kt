package com.aqua.aqualight.ui.tabs.aquarium.common

import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.text.TextUtils
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

object TankStyleBottomSheet {

    private val presets = listOf(
        "Nature Aquarium",
        "Iwagumi",
        "Dutch",
        "Jungle",
        "Biotope",
        "Blackwater",
        "Forest",
        "Mountain",
        "Island"
    )

    fun show(
        fragment: Fragment,
        currentStyle: String,
        onSave: (String) -> Unit
    ) {
        val context = fragment.requireContext()
        val dialog = BottomSheetDialog(context)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(fragment, 24),
                dp(fragment, 22),
                dp(fragment, 24),
                dp(fragment, 24)
            )
            background = ContextCompat.getDrawable(
                context,
                R.drawable.bg_aqua_bottom_sheet
            )
        }

        addSheetHeader(
            fragment = fragment,
            root = root,
            title = "Style",
            dialog = dialog
        )

        val inputCard = MaterialCardView(context).apply {
            radius = dp(fragment, 14).toFloat()
            strokeWidth = dp(fragment, 1)
            strokeColor = Color.parseColor("#223A57")
            setCardBackgroundColor(Color.parseColor("#16314D"))
            cardElevation = 0f
            useCompatPadding = false

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(fragment, 56)
            )
            params.topMargin = dp(fragment, 18)
            layoutParams = params
        }

        val styleInput = EditText(context).apply {
            setText(currentStyle)
            hint = "The Nature Aquarium"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setHintTextColor(Color.parseColor("#7F91AA"))
            setTextColor(Color.WHITE)
            textSize = 15f
            setSingleLine(true)
            background = null
            setPadding(
                dp(fragment, 16),
                0,
                dp(fragment, 16),
                0
            )

            if (text.isNotEmpty()) {
                setSelection(text.length)
            }
        }

        inputCard.addView(
            styleInput,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        )

        root.addView(inputCard)

        val helperText = TextView(context).apply {
            text = "Choose a style or write your own aquarium concept."
            setTextColor(Color.parseColor("#8FA4BE"))
            textSize = 13f
            setLineSpacing(
                dp(fragment, 2).toFloat(),
                1.0f
            )

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = dp(fragment, 14)
            layoutParams = params
        }

        root.addView(helperText)

        var selectedStyle = currentStyle

        val optionViews = mutableListOf<Pair<String, MaterialCardView>>()

        val presetContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = dp(fragment, 18)
            layoutParams = params
        }

        fun refreshOptions() {
            optionViews.forEach { pair ->
                val isSelected = pair.first.equals(
                    selectedStyle,
                    ignoreCase = true
                )

                pair.second.strokeColor = Color.parseColor(
                    if (isSelected) "#2B93F6" else "#223A57"
                )

                pair.second.setCardBackgroundColor(
                    Color.parseColor(
                        if (isSelected) "#18395A" else "#10233A"
                    )
                )

                val textView = pair.second.getChildAt(0) as? TextView
                textView?.setTypeface(
                    null,
                    if (isSelected) Typeface.BOLD else Typeface.NORMAL
                )
            }
        }

        presets.chunked(3).forEach { rowItems ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.bottomMargin = dp(fragment, 10)
                layoutParams = params
            }

            rowItems.forEachIndexed { index, item ->
                val card = createOptionCard(
                    fragment = fragment,
                    text = item
                ).apply {
                    setOnClickListener {
                        selectedStyle = item
                        styleInput.setText(item)
                        styleInput.setSelection(styleInput.text.length)
                        refreshOptions()
                    }
                }

                optionViews.add(item to card)

                val params = LinearLayout.LayoutParams(
                    0,
                    dp(fragment, 54),
                    1f
                )

                if (index > 0) {
                    params.marginStart = dp(fragment, 10)
                }

                row.addView(card, params)
            }

            if (rowItems.size < 3) {
                repeat(3 - rowItems.size) {
                    val spacer = View(context)

                    val params = LinearLayout.LayoutParams(
                        0,
                        dp(fragment, 54),
                        1f
                    )
                    params.marginStart = dp(fragment, 10)

                    row.addView(spacer, params)
                }
            }

            presetContainer.addView(row)
        }

        root.addView(presetContainer)

        refreshOptions()

        addPrimaryButton(
            fragment = fragment,
            root = root,
            text = "Save"
        ) {
            val newStyle = styleInput.text
                ?.toString()
                ?.trim()
                .orEmpty()

            onSave(newStyle)
            dialog.dismiss()
        }

        addCancelButton(
            fragment = fragment,
            root = root,
            dialog = dialog
        )

        showSheet(
            dialog = dialog,
            root = root
        )
    }

    private fun addSheetHeader(
        fragment: Fragment,
        root: LinearLayout,
        title: String,
        dialog: BottomSheetDialog
    ) {
        val context = fragment.requireContext()

        val header = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(fragment, 46)
            )
        }

        val titleView = TextView(context).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            includeFontPadding = false

            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val closeView = TextView(context).apply {
            text = "×"
            setTextColor(Color.WHITE)
            textSize = 34f
            gravity = Gravity.CENTER
            includeFontPadding = false

            setOnClickListener {
                dialog.dismiss()
            }

            val params = FrameLayout.LayoutParams(
                dp(fragment, 44),
                dp(fragment, 44),
                Gravity.END or Gravity.CENTER_VERTICAL
            )
            layoutParams = params
        }

        header.addView(titleView)
        header.addView(closeView)

        root.addView(header)
    }

    private fun createOptionCard(
        fragment: Fragment,
        text: String
    ): MaterialCardView {
        val context = fragment.requireContext()

        val card = MaterialCardView(context).apply {
            radius = dp(fragment, 14).toFloat()
            strokeWidth = dp(fragment, 1)
            strokeColor = Color.parseColor("#223A57")
            setCardBackgroundColor(Color.parseColor("#10233A"))
            cardElevation = 0f
            useCompatPadding = false
            isClickable = true
            isFocusable = true
        }

        val textView = TextView(context).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 14f
            includeFontPadding = false
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setLineSpacing(0f, 1.0f)
            setPadding(
                dp(fragment, 6),
                0,
                dp(fragment, 6),
                0
            )
        }

        card.addView(
            textView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        return card
    }

    private fun addPrimaryButton(
        fragment: Fragment,
        root: LinearLayout,
        text: String,
        onClick: () -> Unit
    ) {
        val context = fragment.requireContext()

        val button = MaterialButton(context).apply {
            this.text = text
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            cornerRadius = dp(fragment, 16)
            setBackgroundColor(Color.parseColor("#2196F3"))
            isAllCaps = false

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(fragment, 56)
            )
            params.topMargin = dp(fragment, 24)
            layoutParams = params

            setOnClickListener {
                onClick()
            }
        }

        root.addView(button)
    }

    private fun addCancelButton(
        fragment: Fragment,
        root: LinearLayout,
        dialog: BottomSheetDialog
    ) {
        val context = fragment.requireContext()

        val cancel = TextView(context).apply {
            text = "Cancel"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#8FA4BE"))
            textSize = 15f
            setPadding(
                0,
                dp(fragment, 18),
                0,
                0
            )

            setOnClickListener {
                dialog.dismiss()
            }
        }

        root.addView(
            cancel,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun showSheet(
        dialog: BottomSheetDialog,
        root: LinearLayout
    ) {
        dialog.setContentView(root)

        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            )

            bottomSheet?.setBackgroundColor(Color.TRANSPARENT)
        }

        dialog.show()
    }

    private fun dp(
        fragment: Fragment,
        value: Int
    ): Int {
        return (value * fragment.resources.displayMetrics.density).toInt()
    }
}