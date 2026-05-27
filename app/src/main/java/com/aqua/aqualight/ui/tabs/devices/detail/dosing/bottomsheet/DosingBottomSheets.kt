package com.aqua.aqualight.ui.tabs.devices.detail.dosing.bottomsheet

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import java.util.Locale

object DosingBottomSheets {

    fun showTimePicker(
        context: Context,
        title: String,
        initialHour: Int,
        initialMinute: Int,
        onTimeSelected: (hour: Int, minute: Int) -> Unit
    ) {
        val dialog =
            BottomSheetDialog(
                context
            )

        val root =
            createRoot(
                context = context
            )

        val handle =
            createHandle(
                context = context
            )

        val titleView =
            createTitle(
                context = context,
                text = title
            )

        val pickerRow =
            LinearLayout(
                context
            ).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER

                setPadding(
                    0,
                    dp(
                        context = context,
                        value = 18
                    ),
                    0,
                    dp(
                        context = context,
                        value = 18
                    )
                )
            }

        val hourPicker =
            createNumberPicker(
                context = context,
                min = 0,
                max = 23,
                value = initialHour.coerceIn(
                    minimumValue = 0,
                    maximumValue = 23
                )
            )

        val separator =
            TextView(
                context
            ).apply {
                text =
                    ":"

                setTextColor(
                    Color.WHITE
                )

                textSize =
                    28f

                typeface =
                    Typeface.DEFAULT_BOLD

                gravity =
                    Gravity.CENTER

                includeFontPadding =
                    false

                layoutParams =
                    LinearLayout.LayoutParams(
                        dp(
                            context = context,
                            value = 28
                        ),
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
            }

        val minutePicker =
            createNumberPicker(
                context = context,
                min = 0,
                max = 59,
                value = initialMinute.coerceIn(
                    minimumValue = 0,
                    maximumValue = 59
                )
            )

        pickerRow.addView(
            hourPicker
        )

        pickerRow.addView(
            separator
        )

        pickerRow.addView(
            minutePicker
        )

        val buttonRow =
            LinearLayout(
                context
            ).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                setPadding(
                    0,
                    dp(
                        context = context,
                        value = 10
                    ),
                    0,
                    0
                )
            }

        val cancelButton =
            createButton(
                context = context,
                text = "Cancel",
                backgroundColor = "#17253C",
                textColor = "#FFFFFF",
                weight = 1f
            ).apply {
                setOnClickListener {
                    dialog.dismiss()
                }
            }

        val doneButton =
            createButton(
                context = context,
                text = "Done",
                backgroundColor = "#F43F5E",
                textColor = "#FFFFFF",
                weight = 1.35f
            ).apply {
                setOnClickListener {
                    onTimeSelected(
                        hourPicker.value,
                        minutePicker.value
                    )

                    dialog.dismiss()
                }
            }

        buttonRow.addView(
            cancelButton
        )

        buttonRow.addView(
            doneButton
        )

        root.addView(
            handle
        )

        root.addView(
            titleView
        )

        root.addView(
            pickerRow
        )

        root.addView(
            buttonRow
        )

        dialog.setContentView(
            root
        )

        dialog.setOnShowListener {
            val bottomSheet =
                dialog.findViewById<FrameLayout>(
                    com.google.android.material.R.id.design_bottom_sheet
                )

            bottomSheet?.setBackgroundColor(
                Color.TRANSPARENT
            )

            bottomSheet?.let { sheet ->
                BottomSheetBehavior.from(
                    sheet
                ).apply {
                    state =
                        BottomSheetBehavior.STATE_EXPANDED

                    skipCollapsed =
                        true
                }
            }
        }

        dialog.show()
    }

    private fun createRoot(
        context: Context
    ): LinearLayout {
        return LinearLayout(
            context
        ).apply {
            orientation =
                LinearLayout.VERTICAL

            background =
                GradientDrawable().apply {
                    shape =
                        GradientDrawable.RECTANGLE

                    setColor(
                        Color.parseColor("#0B182C")
                    )

                    cornerRadii =
                        floatArrayOf(
                            dp(context, 28).toFloat(),
                            dp(context, 28).toFloat(),
                            dp(context, 28).toFloat(),
                            dp(context, 28).toFloat(),
                            0f,
                            0f,
                            0f,
                            0f
                        )
                }

            setPadding(
                dp(
                    context = context,
                    value = 18
                ),
                dp(
                    context = context,
                    value = 10
                ),
                dp(
                    context = context,
                    value = 18
                ),
                dp(
                    context = context,
                    value = 18
                )
            )

            layoutParams =
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
        }
    }

    private fun createHandle(
        context: Context
    ): View {
        return View(
            context
        ).apply {
            background =
                GradientDrawable().apply {
                    shape =
                        GradientDrawable.RECTANGLE

                    cornerRadius =
                        dp(
                            context = context,
                            value = 3
                        ).toFloat()

                    setColor(
                        Color.parseColor("#33415F")
                    )
                }

            layoutParams =
                LinearLayout.LayoutParams(
                    dp(
                        context = context,
                        value = 44
                    ),
                    dp(
                        context = context,
                        value = 5
                    )
                ).apply {
                    gravity =
                        Gravity.CENTER_HORIZONTAL

                    bottomMargin =
                        dp(
                            context = context,
                            value = 18
                        )
                }
        }
    }

    private fun createTitle(
        context: Context,
        text: String
    ): TextView {
        return TextView(
            context
        ).apply {
            this.text =
                text

            setTextColor(
                Color.WHITE
            )

            textSize =
                20f

            typeface =
                Typeface.DEFAULT_BOLD

            gravity =
                Gravity.CENTER

            includeFontPadding =
                false

            layoutParams =
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
        }
    }

    private fun createNumberPicker(
        context: Context,
        min: Int,
        max: Int,
        value: Int
    ): NumberPicker {
        return NumberPicker(
            context
        ).apply {
            minValue =
                min

            maxValue =
                max

            this.value =
                value

            wrapSelectorWheel =
                true

            setFormatter { pickerValue ->
                String.format(
                    Locale.US,
                    "%02d",
                    pickerValue
                )
            }

            descendantFocusability =
                NumberPicker.FOCUS_BLOCK_DESCENDANTS

            layoutParams =
                LinearLayout.LayoutParams(
                    dp(
                        context = context,
                        value = 96
                    ),
                    dp(
                        context = context,
                        value = 150
                    )
                )

            styleNumberPicker(
                picker = this
            )

            post {
                styleNumberPicker(
                    picker = this
                )
            }
        }
    }

    private fun styleNumberPicker(
        picker: NumberPicker
    ) {
        picker.setBackgroundColor(
            Color.TRANSPARENT
        )

        for (index in 0 until picker.childCount) {
            val child =
                picker.getChildAt(
                    index
                )

            if (child is EditText) {
                child.setTextColor(
                    Color.WHITE
                )

                child.textSize =
                    22f

                child.typeface =
                    Typeface.DEFAULT_BOLD

                child.gravity =
                    Gravity.CENTER

                child.setBackgroundColor(
                    Color.TRANSPARENT
                )

                child.includeFontPadding =
                    false
            }
        }
    }

    private fun createButton(
        context: Context,
        text: String,
        backgroundColor: String,
        textColor: String,
        weight: Float
    ): MaterialButton {
        return MaterialButton(
            context
        ).apply {
            this.text =
                text

            isAllCaps =
                false

            textSize =
                14f

            typeface =
                Typeface.DEFAULT_BOLD

            setTextColor(
                Color.parseColor(textColor)
            )

            backgroundTintList =
                ColorStateList.valueOf(
                    Color.parseColor(backgroundColor)
                )

            cornerRadius =
                dp(
                    context = context,
                    value = 18
                )

            minHeight =
                0

            layoutParams =
                LinearLayout.LayoutParams(
                    0,
                    dp(
                        context = context,
                        value = 48
                    ),
                    weight
                ).apply {
                    marginStart =
                        dp(
                            context = context,
                            value = 6
                        )

                    marginEnd =
                        dp(
                            context = context,
                            value = 6
                        )
                }
        }
    }

    private fun dp(
        context: Context,
        value: Int
    ): Int {
        return (
            value * context.resources.displayMetrics.density
        ).toInt()
    }
}