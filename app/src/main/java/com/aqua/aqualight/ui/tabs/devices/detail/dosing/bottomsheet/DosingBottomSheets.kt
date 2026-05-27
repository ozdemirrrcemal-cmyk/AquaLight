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
import com.google.android.material.card.MaterialCardView
import java.util.Locale

object DosingBottomSheets {

    data class CustomPeriodResult(
        val startTime: String,
        val endTime: String,
        val doseCount: Int
    )

    data class TimerDoseResult(
        val startTime: String,
        val doseMl: Float
    )

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
            ),
            useLeadingZero = true
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
            ),
            useLeadingZero = true
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
        createButtonRow(
            context = context
        )

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
            createHandle(
                context = context
            )
        )

        root.addView(
            createTitle(
                context = context,
                text = title
            )
        )

        root.addView(
            pickerRow
        )

        root.addView(
            buttonRow
        )

        showDialog(
            dialog = dialog,
            root = root
        )
    }

    fun showMinutePicker(
        context: Context,
        title: String,
        initialMinute: Int,
        onMinuteSelected: (minute: Int) -> Unit
    ) {
        val dialog =
        BottomSheetDialog(
            context
        )

        val root =
        createRoot(
            context = context
        )

        val minutePicker =
        createNumberPicker(
            context = context,
            min = 0,
            max = 59,
            value = initialMinute.coerceIn(
                minimumValue = 0,
                maximumValue = 59
            ),
            useLeadingZero = true
        ).apply {
            layoutParams =
            LinearLayout.LayoutParams(
                dp(
                    context = context,
                    value = 120
                ),
                dp(
                    context = context,
                    value = 170
                )
            ).apply {
                gravity =
                Gravity.CENTER_HORIZONTAL

                topMargin =
                dp(
                    context = context,
                    value = 18
                )

                bottomMargin =
                dp(
                    context = context,
                    value = 18
                )
            }
        }

        val buttonRow =
        createButtonRow(
            context = context
        )

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
                onMinuteSelected(
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
            createHandle(
                context = context
            )
        )

        root.addView(
            createTitle(
                context = context,
                text = title
            )
        )

        root.addView(
            minutePicker
        )

        root.addView(
            buttonRow
        )

        showDialog(
            dialog = dialog,
            root = root
        )
    }

    fun showDoseCountPicker(
        context: Context,
        title: String,
        initialDoseCount: Int,
        maxDoseCount: Int,
        onDoseCountSelected: (doseCount: Int) -> Unit
    ) {
        val dialog =
        BottomSheetDialog(
            context
        )

        val root =
        createRoot(
            context = context
        )

        val picker =
        createNumberPicker(
            context = context,
            min = 1,
            max = maxDoseCount.coerceAtLeast(
                minimumValue = 1
            ),
            value = initialDoseCount.coerceIn(
                minimumValue = 1,
                maximumValue = maxDoseCount.coerceAtLeast(
                    minimumValue = 1
                )
            ),
            useLeadingZero = false
        ).apply {
            layoutParams =
            LinearLayout.LayoutParams(
                dp(
                    context = context,
                    value = 120
                ),
                dp(
                    context = context,
                    value = 170
                )
            ).apply {
                gravity =
                Gravity.CENTER_HORIZONTAL

                topMargin =
                dp(
                    context = context,
                    value = 18
                )

                bottomMargin =
                dp(
                    context = context,
                    value = 18
                )
            }
        }

        val buttonRow =
        createButtonRow(
            context = context
        )

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
                onDoseCountSelected(
                    picker.value
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
            createHandle(
                context = context
            )
        )

        root.addView(
            createTitle(
                context = context,
                text = title
            )
        )

        root.addView(
            picker
        )

        root.addView(
            buttonRow
        )

        showDialog(
            dialog = dialog,
            root = root
        )
    }

    fun showCustomPeriodEditor(
        context: Context,
        title: String,
        initialStartTime: String,
        initialEndTime: String,
        initialDoseCount: Int,
        maxDoseCount: Int,
        validator: ((startTime: String, endTime: String, doseCount: Int) -> String?)? = null,
    onValidationError: (message: String) -> Unit = {},
    onDone: (result: CustomPeriodResult) -> Unit
) {
    val dialog =
    BottomSheetDialog(
        context
    )

    var draftStartTime =
    normalizeTime(
        value = initialStartTime
    )

    var draftEndTime =
    normalizeTime(
        value = initialEndTime
    )

    var draftDoseCount =
    initialDoseCount.coerceIn(
        minimumValue = 1,
        maximumValue = maxDoseCount.coerceAtLeast(
            minimumValue = 1
        )
    )

    val root =
    createRoot(
        context = context
    )

    root.addView(
        createHandle(
            context = context
        )
    )

    root.addView(
        createTitle(
            context = context,
            text = title
        )
    )

    root.addView(
        createSubtitle(
            context = context,
            text = "Choose the dosing window and how many doses will be given in this period."
        )
    )

    val startTimeRow =
    createValueRow(
        context = context,
        title = "Start Time",
        description = "When this period begins",
        value = draftStartTime,
        valueWidthDp = 96
    ) {
        valueText ->
        val currentHour =
        parseHour(
            value = draftStartTime
        )

        val currentMinute =
        parseMinute(
            value = draftStartTime
        )

        showTimePicker(
            context = context,
            title = "Start Time",
            initialHour = currentHour,
            initialMinute = currentMinute
        ) {
            hour, minute ->
            draftStartTime =
            formatTime(
                hour = hour,
                minute = minute
            )

            valueText.text =
            draftStartTime
        }
    }

    val endTimeRow =
    createValueRow(
        context = context,
        title = "End Time",
        description = "When this period ends",
        value = draftEndTime,
        valueWidthDp = 96
    ) {
        valueText ->
        val currentHour =
        parseHour(
            value = draftEndTime
        )

        val currentMinute =
        parseMinute(
            value = draftEndTime
        )

        showTimePicker(
            context = context,
            title = "End Time",
            initialHour = currentHour,
            initialMinute = currentMinute
        ) {
            hour, minute ->
            draftEndTime =
            formatTime(
                hour = hour,
                minute = minute
            )

            valueText.text =
            draftEndTime
        }
    }

    val doseCountRow =
    createValueRow(
        context = context,
        title = "Number of Doses",
        description = "Doses inside this period",
        value = draftDoseCount.toString(),
        valueWidthDp = 76
    ) {
        valueText ->
        showDoseCountPicker(
            context = context,
            title = "Number of Doses",
            initialDoseCount = draftDoseCount,
            maxDoseCount = maxDoseCount
        ) {
            selectedDoseCount ->
            draftDoseCount =
            selectedDoseCount

            valueText.text =
            selectedDoseCount.toString()
        }
    }

    val buttonRow =
    createButtonRow(
        context = context
    ).apply {
        setPadding(
            0,
            dp(
                context = context,
                value = 18
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
            val validationMessage =
            validator?.invoke(
                draftStartTime,
                draftEndTime,
                draftDoseCount
            )

            if (validationMessage != null) {
                onValidationError(
                    validationMessage
                )

                return@setOnClickListener
            }

            onDone(
                CustomPeriodResult(
                    startTime = draftStartTime,
                    endTime = draftEndTime,
                    doseCount = draftDoseCount
                )
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
        startTimeRow
    )

    root.addView(
        endTimeRow
    )

    root.addView(
        doseCountRow
    )

    root.addView(
        buttonRow
    )

    showDialog(
        dialog = dialog,
        root = root
    )
}

fun showMlValueEditor(
    context: Context,
    title: String,
    description: String,
    hint: String,
    initialValue: Float?,
    allowClear: Boolean = true,
    onValidationError: (message: String) -> Unit = {},
    onClear: (() -> Unit)? = null,
    onDone: (value: Float) -> Unit
) {
    val dialog =
        BottomSheetDialog(
            context
        )

    val root =
        createRoot(
            context = context
        )

    val inputCard =
        MaterialCardView(
            context
        ).apply {
            radius =
                dp(
                    context = context,
                    value = 18
                ).toFloat()

            cardElevation =
                0f

            setCardBackgroundColor(
                Color.parseColor("#1A2238")
            )

            strokeColor =
                Color.parseColor("#33415F")

            strokeWidth =
                dp(
                    context = context,
                    value = 1
                )

            layoutParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(
                        context = context,
                        value = 56
                    )
                ).apply {
                    topMargin =
                        dp(
                            context = context,
                            value = 16
                        )
                }
        }

    val inputRow =
        LinearLayout(
            context
        ).apply {
            orientation =
                LinearLayout.HORIZONTAL

            gravity =
                Gravity.CENTER_VERTICAL

            setPadding(
                dp(
                    context = context,
                    value = 16
                ),
                0,
                dp(
                    context = context,
                    value = 16
                ),
                0
            )
        }

    val editText =
        EditText(
            context
        ).apply {
            setText(
                initialValue?.let { value ->
                    formatSheetMlInput(
                        value = value
                    )
                }.orEmpty()
            )

            setHint(
                hint
            )

            setSingleLine(
                true
            )

            inputType =
                android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL

            setTextColor(
                Color.WHITE
            )

            setHintTextColor(
                Color.parseColor("#6B7280")
            )

            textSize =
                18f

            typeface =
                Typeface.DEFAULT_BOLD

            gravity =
                Gravity.CENTER_VERTICAL or Gravity.END

            background =
                null

            includeFontPadding =
                false

            layoutParams =
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1f
                )
        }

    val suffix =
        TextView(
            context
        ).apply {
            text =
                "ml"

            setTextColor(
                Color.parseColor("#F43F5E")
            )

            textSize =
                15f

            typeface =
                Typeface.DEFAULT_BOLD

            gravity =
                Gravity.CENTER

            includeFontPadding =
                false

            setPadding(
                dp(
                    context = context,
                    value = 8
                ),
                0,
                0,
                0
            )
        }

    inputRow.addView(
        editText
    )

    inputRow.addView(
        suffix
    )

    inputCard.addView(
        inputRow
    )

    val buttonRow =
        createButtonRow(
            context = context
        ).apply {
            setPadding(
                0,
                dp(
                    context = context,
                    value = 18
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
                val value =
                    editText.text
                        ?.toString()
                        ?.trim()
                        ?.replace(
                            oldValue = ",",
                            newValue = "."
                        )
                        ?.toFloatOrNull()

                if (
                    value == null ||
                    value <= 0f
                ) {
                    onValidationError(
                        "Please enter a valid volume."
                    )

                    return@setOnClickListener
                }

                onDone(
                    value
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
        createHandle(
            context = context
        )
    )

    root.addView(
        createTitle(
            context = context,
            text = title
        )
    )

    root.addView(
        createSubtitle(
            context = context,
            text = description
        )
    )

    root.addView(
        inputCard
    )

    if (
        allowClear &&
        onClear != null
    ) {
        val clearButton =
            createButton(
                context = context,
                text = "Clear value",
                backgroundColor = "#7F1D2D",
                textColor = "#FFFFFF",
                weight = 1f
            ).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(
                            context = context,
                            value = 46
                        )
                    ).apply {
                        topMargin =
                            dp(
                                context = context,
                                value = 12
                            )
                    }

                setOnClickListener {
                    onClear()
                    dialog.dismiss()
                }
            }

        root.addView(
            clearButton
        )
    }

    root.addView(
        buttonRow
    )

    showDialog(
        dialog = dialog,
        root = root
    )

    editText.requestFocus()
}

fun showTimerDoseEditor(
    context: Context,
    title: String,
    initialStartTime: String,
    initialDoseMl: Float,
    validator: ((startTime: String, doseMl: Float) -> String?)? = null,
onValidationError: (message: String) -> Unit = {},
onDelete: (() -> Unit)? = null,
onDone: (result: TimerDoseResult) -> Unit
) {
val dialog =
BottomSheetDialog(
context
)

var draftStartTime =
normalizeTime(
value = initialStartTime
)

val root =
createRoot(
context = context
)

root.addView(
createHandle(
context = context
)
)

root.addView(
createTitle(
context = context,
text = title
)
)

root.addView(
createSubtitle(
context = context,
text = "Set the exact time and amount for this individual dose."
)
)

val startTimeRow =
createValueRow(
context = context,
title = "Start Time",
description = "When this dose will run",
value = draftStartTime,
valueWidthDp = 96
) {
valueText ->
showTimePicker(
context = context,
title = "Start Time",
initialHour = parseHour(
value = draftStartTime
),
initialMinute = parseMinute(
value = draftStartTime
)
) {
hour, minute ->
draftStartTime =
formatTime(
hour = hour,
minute = minute
)

valueText.text =
draftStartTime
}
}

val doseInput =
createDoseAmountInputCard(
context = context,
initialDoseMl = initialDoseMl
)

val buttonRow =
createButtonRow(
context = context
).apply {
setPadding(
0,
dp(
context = context,
value = 18
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
val doseMl =
doseInput.text
?.toString()
?.trim()
?.replace(
oldValue = ",",
newValue = "."
)
?.toFloatOrNull()

if (
doseMl == null ||
doseMl <= 0f
) {
onValidationError(
"Please enter a valid dose quantity."
)
return@setOnClickListener
}

val validationMessage =
validator?.invoke(
draftStartTime,
doseMl
)

if (validationMessage != null) {
onValidationError(
validationMessage
)
return@setOnClickListener
}

onDone(
TimerDoseResult(
startTime = draftStartTime,
doseMl = doseMl
)
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
startTimeRow
)

root.addView(
doseInput.tag as View
)

if (onDelete != null) {
val deleteButton =
createButton(
context = context,
text = "Delete Dose",
backgroundColor = "#7F1D2D",
textColor = "#FFFFFF",
weight = 1f
).apply {
layoutParams =
LinearLayout.LayoutParams(
LinearLayout.LayoutParams.MATCH_PARENT,
dp(
context = context,
value = 46
)
).apply {
topMargin =
dp(
context = context,
value = 14
)
}

setOnClickListener {
onDelete()
dialog.dismiss()
}
}

root.addView(
deleteButton
)
}

root.addView(
buttonRow
)

showDialog(
dialog = dialog,
root = root
)
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
dp(
context = context,
value = 28
).toFloat(),
dp(
context = context,
value = 28
).toFloat(),
dp(
context = context,
value = 28
).toFloat(),
dp(
context = context,
value = 28
).toFloat(),
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


private fun createDoseAmountInputCard(
context: Context,
initialDoseMl: Float
): EditText {
val card =
MaterialCardView(
context
).apply {
radius =
dp(
context = context,
value = 20
).toFloat()

cardElevation =
0f

setCardBackgroundColor(
Color.parseColor("#101426")
)

strokeColor =
Color.parseColor("#24314F")

strokeWidth =
dp(
context = context,
value = 1
)

layoutParams =
LinearLayout.LayoutParams(
LinearLayout.LayoutParams.MATCH_PARENT,
LinearLayout.LayoutParams.WRAP_CONTENT
).apply {
topMargin =
dp(
context = context,
value = 10
)
}
}

val row =
LinearLayout(
context
).apply {
orientation =
LinearLayout.HORIZONTAL

gravity =
Gravity.CENTER_VERTICAL

setPadding(
dp(
context = context,
value = 16
),
dp(
context = context,
value = 14
),
dp(
context = context,
value = 16
),
dp(
context = context,
value = 14
)
)
}

val textContainer =
LinearLayout(
context
).apply {
orientation =
LinearLayout.VERTICAL

layoutParams =
LinearLayout.LayoutParams(
0,
LinearLayout.LayoutParams.WRAP_CONTENT,
1f
)
}

val titleText =
TextView(
context
).apply {
text =
"Dose Quantity"

setTextColor(
Color.WHITE
)

textSize =
15f

typeface =
Typeface.DEFAULT_BOLD

includeFontPadding =
false
}

val descriptionText =
TextView(
context
).apply {
text =
"Amount for this dose"

setTextColor(
Color.parseColor("#9AA7BD")
)

textSize =
12f

includeFontPadding =
false

setPadding(
0,
dp(
context = context,
value = 5
),
0,
0
)
}

val inputContainer =
MaterialCardView(
context
).apply {
radius =
dp(
context = context,
value = 14
).toFloat()

cardElevation =
0f

setCardBackgroundColor(
Color.parseColor("#1A2238")
)

strokeColor =
Color.parseColor("#33415F")

strokeWidth =
dp(
context = context,
value = 1
)

layoutParams =
LinearLayout.LayoutParams(
dp(
context = context,
value = 112
),
dp(
context = context,
value = 42
)
).apply {
marginStart =
dp(
context = context,
value = 12
)
}
}

val inputRow =
LinearLayout(
context
).apply {
orientation =
LinearLayout.HORIZONTAL

gravity =
Gravity.CENTER_VERTICAL

setPadding(
dp(
context = context,
value = 10
),
0,
dp(
context = context,
value = 10
),
0
)
}

val editText =
EditText(
context
).apply {
setText(
formatDoseInput(
value = initialDoseMl
)
)

hint =
"0"

setTextColor(
Color.parseColor("#F43F5E")
)

setHintTextColor(
Color.parseColor("#6B7280")
)

textSize =
14f

typeface =
Typeface.DEFAULT_BOLD

gravity =
Gravity.CENTER_VERTICAL or Gravity.END

includeFontPadding =
false

background =
null

inputType =
android.text.InputType.TYPE_CLASS_NUMBER or
android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL

isSingleLine =
true

setSelectAllOnFocus(
true
)

layoutParams =
LinearLayout.LayoutParams(
0,
ViewGroup.LayoutParams.MATCH_PARENT,
1f
)
}

val suffix =
TextView(
context
).apply {
text =
"ml"

setTextColor(
Color.parseColor("#F43F5E")
)

textSize =
13f

typeface =
Typeface.DEFAULT_BOLD

gravity =
Gravity.CENTER

includeFontPadding =
false

setPadding(
dp(
context = context,
value = 4
),
0,
0,
0
)
}

textContainer.addView(
titleText
)

textContainer.addView(
descriptionText
)

inputRow.addView(
editText
)

inputRow.addView(
suffix
)

inputContainer.addView(
inputRow
)

row.addView(
textContainer
)

row.addView(
inputContainer
)

card.addView(
row
)

editText.tag =
card

return editText
}

private fun formatDoseInput(
value: Float
): String {
return if (value <= 0f) {
""
} else if (value % 1f == 0f) {
value.toInt().toString()
} else {
String.format(
Locale.US,
"%.3f",
value
).trimEnd(
'0'
).trimEnd(
'.'
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

private fun createSubtitle(
context: Context,
text: String
): TextView {
return TextView(
context
).apply {
this.text =
text

setTextColor(
Color.parseColor("#9AA7BD")
)

textSize =
13f

gravity =
Gravity.CENTER

includeFontPadding =
false

setPadding(
0,
dp(
context = context,
value = 8
),
0,
dp(
context = context,
value = 8
)
)

layoutParams =
LinearLayout.LayoutParams(
ViewGroup.LayoutParams.MATCH_PARENT,
ViewGroup.LayoutParams.WRAP_CONTENT
)
}
}

private fun createValueRow(
context: Context,
title: String,
description: String,
value: String,
valueWidthDp: Int,
onClick: (valueText: TextView) -> Unit
): View {
val card =
MaterialCardView(
context
).apply {
radius =
dp(
context = context,
value = 20
).toFloat()

cardElevation =
0f

isClickable =
true

isFocusable =
true

setCardBackgroundColor(
Color.parseColor("#101426")
)

strokeColor =
Color.parseColor("#24314F")

strokeWidth =
dp(
context = context,
value = 1
)

layoutParams =
LinearLayout.LayoutParams(
LinearLayout.LayoutParams.MATCH_PARENT,
LinearLayout.LayoutParams.WRAP_CONTENT
).apply {
topMargin =
dp(
context = context,
value = 10
)
}
}

val row =
LinearLayout(
context
).apply {
orientation =
LinearLayout.HORIZONTAL

gravity =
Gravity.CENTER_VERTICAL

setPadding(
dp(
context = context,
value = 16
),
dp(
context = context,
value = 14
),
dp(
context = context,
value = 16
),
dp(
context = context,
value = 14
)
)
}

val textContainer =
LinearLayout(
context
).apply {
orientation =
LinearLayout.VERTICAL

layoutParams =
LinearLayout.LayoutParams(
0,
LinearLayout.LayoutParams.WRAP_CONTENT,
1f
)
}

val titleText =
TextView(
context
).apply {
this.text =
title

setTextColor(
Color.WHITE
)

textSize =
15f

typeface =
Typeface.DEFAULT_BOLD

includeFontPadding =
false
}

val descriptionText =
TextView(
context
).apply {
this.text =
description

setTextColor(
Color.parseColor("#9AA7BD")
)

textSize =
12f

includeFontPadding =
false

setPadding(
0,
dp(
context = context,
value = 5
),
0,
0
)
}

val valueCard =
MaterialCardView(
context
).apply {
radius =
dp(
context = context,
value = 14
).toFloat()

cardElevation =
0f

setCardBackgroundColor(
Color.parseColor("#1A2238")
)

strokeColor =
Color.parseColor("#33415F")

strokeWidth =
dp(
context = context,
value = 1
)

layoutParams =
LinearLayout.LayoutParams(
dp(
context = context,
value = valueWidthDp
),
dp(
context = context,
value = 42
)
).apply {
marginStart =
dp(
context = context,
value = 12
)
}
}

val valueText =
TextView(
context
).apply {
this.text =
value

setTextColor(
Color.WHITE
)

textSize =
15f

typeface =
Typeface.DEFAULT_BOLD

gravity =
Gravity.CENTER

includeFontPadding =
false

layoutParams =
ViewGroup.LayoutParams(
ViewGroup.LayoutParams.MATCH_PARENT,
ViewGroup.LayoutParams.MATCH_PARENT
)
}

valueCard.addView(
valueText
)

textContainer.addView(
titleText
)

textContainer.addView(
descriptionText
)

row.addView(
textContainer
)

row.addView(
valueCard
)

card.addView(
row
)

card.setOnClickListener {
onClick(
valueText
)
}

return card
}

private fun createNumberPicker(
context: Context,
min: Int,
max: Int,
value: Int,
useLeadingZero: Boolean
): NumberPicker {
return NumberPicker(
context
).apply {
minValue =
min

maxValue =
max

this.value =
value.coerceIn(
minimumValue = min,
maximumValue = max
)

wrapSelectorWheel =
true

if (useLeadingZero) {
setFormatter {
pickerValue ->
String.format(
Locale.US,
"%02d",
pickerValue
)
}
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

private fun createButtonRow(
context: Context
): LinearLayout {
return LinearLayout(
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
Color.parseColor(
textColor
)
)

backgroundTintList =
ColorStateList.valueOf(
Color.parseColor(
backgroundColor
)
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

private fun showDialog(
dialog: BottomSheetDialog,
root: View
) {
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

bottomSheet?.let {
sheet ->
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

private fun normalizeTime(
value: String
): String {
return formatTime(
hour = parseHour(
value = value
),
minute = parseMinute(
value = value
)
)
}

private fun parseHour(
value: String
): Int {
return value.ifBlank {
"00:00"
}.split(":")
.getOrNull(
index = 0
)
?.toIntOrNull()
?.coerceIn(
minimumValue = 0,
maximumValue = 23
) ?: 0
}

private fun parseMinute(
value: String
): Int {
return value.ifBlank {
"00:00"
}.split(":")
.getOrNull(
index = 1
)
?.toIntOrNull()
?.coerceIn(
minimumValue = 0,
maximumValue = 59
) ?: 0
}

private fun formatTime(
hour: Int,
minute: Int
): String {
return String.format(
Locale.US,
"%02d:%02d",
hour.coerceIn(
minimumValue = 0,
maximumValue = 23
),
minute.coerceIn(
minimumValue = 0,
maximumValue = 59
)
)
}

private fun formatSheetMlInput(
    value: Float
): String {
    return if (value % 1f == 0f) {
        value.toInt().toString()
    } else {
        String.format(
            Locale.US,
            "%.1f",
            value
        ).trimEnd(
            '0'
        ).trimEnd(
            '.'
        )
    }
}

private fun dp(
context: Context,
value: Int
): Int {
return (
value *
context.resources.displayMetrics.density
).toInt()
}
}