@file:Suppress("LongMethod", "MagicNumber", "TooManyFunctions")

package com.aqua.aqualight.ui.common.bottomsheet

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.core.view.isGone
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.R
import com.aqua.aqualight.i18n.LocaleFormatter
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import java.text.NumberFormat
import java.util.Locale

/** Re-creatable picker for wall-clock times and minute-of-hour schedule offsets. */
class AquaTimePickerBottomSheet : BottomSheetDialogFragment(
    R.layout.bottom_sheet_aqua_time_picker
) {

    private var resultSent = false
    private var selectedHour = 0
    private var selectedMinute = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = requireArguments()
        val selectionMode = SelectionMode.valueOf(
            requireNotNull(args.getString(ARG_SELECTION_MODE)) {
                "selectionMode is required."
            }
        )
        val allowEndOfDay = args.getBoolean(ARG_ALLOW_END_OF_DAY)
        val selectableMinutesOfDay = args.getIntArray(ARG_SELECTABLE_MINUTES_OF_DAY)?.toList()
        val hourValues = timePickerHourValues(selectableMinutesOfDay, allowEndOfDay)
        selectedHour = if (selectionMode == SelectionMode.MINUTE_OF_HOUR) {
            0
        } else {
            restoreTimePickerSelection(
                savedValue = savedInstanceState?.savedInt(STATE_SELECTED_HOUR),
                initialValue = args.getInt(ARG_INITIAL_HOUR),
                values = hourValues
            )
        }
        val minuteValues = if (selectionMode == SelectionMode.MINUTE_OF_HOUR) {
            MINUTE_RANGE.toList()
        } else {
            timePickerMinuteValues(selectableMinutesOfDay, selectedHour)
        }
        selectedMinute = restoreTimePickerSelection(
            savedValue = savedInstanceState?.savedInt(STATE_SELECTED_MINUTE),
            initialValue = args.getInt(ARG_INITIAL_MINUTE),
            values = minuteValues
        )

        view.findViewById<TextView>(R.id.tvAquaTimePickerTitle).text =
            args.getString(ARG_TITLE).orEmpty()
        view.findViewById<TextView>(R.id.tvAquaTimePickerMessage).text =
            args.getString(ARG_MESSAGE).orEmpty()

        val selection = view.findViewById<TextView>(R.id.tvAquaTimePickerSelection)
        val hourWheel = view.findViewById<RecyclerView>(R.id.rvAquaTimePickerHour)
        val minuteWheel = view.findViewById<RecyclerView>(R.id.rvAquaTimePickerMinute)
        configureSelectionMode(view, selectionMode)

        val minuteBinding = bindWheel(
            recyclerView = minuteWheel,
            values = minuteValues,
            initialValue = selectedMinute,
            valueDescriptionRes = R.string.common_time_picker_minute_value_description,
            onSelected = { minute ->
                selectedMinute = minute
                renderSelection(selection, selectionMode)
            }
        )

        if (selectionMode == SelectionMode.TIME_OF_DAY) {
            bindWheel(
                recyclerView = hourWheel,
                values = hourValues,
                initialValue = selectedHour,
                valueDescriptionRes = R.string.common_time_picker_hour_value_description,
                onSelected = { hour ->
                    selectedHour = hour
                    val allowedMinutes = timePickerMinuteValues(selectableMinutesOfDay, hour)
                    selectedMinute = restoreTimePickerSelection(
                        savedValue = selectedMinute,
                        initialValue = allowedMinutes.first(),
                        values = allowedMinutes
                    )
                    minuteBinding.replaceValues(allowedMinutes, selectedMinute)
                    renderSelection(selection, selectionMode)
                }
            )
        }

        view.findViewById<MaterialButton>(R.id.btnAquaTimePickerCancel).apply {
            text = args.getString(ARG_CANCEL_TEXT).orEmpty()
            setOnClickListener {
                publish(RESULT_CANCELLED)
                dismiss()
            }
        }
        view.findViewById<MaterialButton>(R.id.btnAquaTimePickerConfirm).apply {
            text = args.getString(ARG_CONFIRM_TEXT).orEmpty()
            setOnClickListener {
                publish(RESULT_SELECTED)
                dismiss()
            }
        }
        renderSelection(selection, selectionMode)
    }

    private fun renderSelection(selection: TextView, selectionMode: SelectionMode) {
        val formatted = when (selectionMode) {
            SelectionMode.TIME_OF_DAY -> if (selectedHour == END_OF_DAY_HOUR) {
                END_OF_DAY_DISPLAY
            } else {
                LocaleFormatter.formatTimeOfDay24Hour(
                    context = requireContext(),
                    minutesOfDay = timePickerMinutesOfDay(selectedHour, selectedMinute)
                )
            }
            SelectionMode.MINUTE_OF_HOUR -> getString(
                R.string.common_time_picker_minute_of_hour_preview,
                selectedMinute
            )
        }
        selection.text = formatted
        selection.contentDescription = when (selectionMode) {
            SelectionMode.TIME_OF_DAY -> getString(
                R.string.common_time_picker_selected_time_description,
                formatted
            )
            SelectionMode.MINUTE_OF_HOUR -> getString(
                R.string.common_time_picker_selected_minute_of_hour_description,
                selectedMinute
            )
        }
    }

    private fun configureSelectionMode(view: View, selectionMode: SelectionMode) {
        val minuteOnly = selectionMode == SelectionMode.MINUTE_OF_HOUR
        view.findViewById<View>(R.id.tvAquaTimePickerHourLabel).isGone = minuteOnly
        view.findViewById<View>(R.id.spaceAquaTimePickerLabels).isGone = minuteOnly
        view.findViewById<View>(R.id.rvAquaTimePickerHour).isGone = minuteOnly
        view.findViewById<View>(R.id.tvAquaTimePickerSeparator).isGone = minuteOnly
        view.findViewById<TextView>(R.id.tvAquaTimePickerHint).setText(
            if (minuteOnly) {
                R.string.common_time_picker_minute_of_hour_hint
            } else {
                R.string.common_time_picker_24_hour_format
            }
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_SELECTED_HOUR, selectedHour)
        outState.putInt(STATE_SELECTED_MINUTE, selectedMinute)
        super.onSaveInstanceState(outState)
    }

    override fun onCancel(dialog: DialogInterface) {
        publish(RESULT_CANCELLED)
        super.onCancel(dialog)
    }

    private fun bindWheel(
        recyclerView: RecyclerView,
        values: List<Int>,
        initialValue: Int,
        @StringRes valueDescriptionRes: Int,
        onSelected: (Int) -> Unit
    ): AquaTimeWheelBinding {
        val layoutManager = LinearLayoutManager(requireContext())
        val snapHelper = LinearSnapHelper()
        lateinit var selectPosition: (Int) -> Unit
        val adapter = AquaTimeWheelAdapter(
            values = values,
            valueDescriptionRes = valueDescriptionRes,
            locale = LocaleFormatter.appLocale(requireContext()),
            onValueClick = { position -> selectPosition(position) }
        )

        fun applySnappedSelection() {
            val snappedView = snapHelper.findSnapView(layoutManager) ?: return
            val position = layoutManager.getPosition(snappedView)
                .takeIf { candidate -> candidate != RecyclerView.NO_POSITION }
                ?: return
            adapter.setSelectedPosition(position)
            onSelected(adapter.valueAt(position))
        }

        selectPosition = { position ->
            layoutManager.scrollToPositionWithOffset(position, 0)
            recyclerView.post(::applySnappedSelection)
        }

        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter
        recyclerView.itemAnimator = null
        recyclerView.addOnItemTouchListener(
            object : RecyclerView.SimpleOnItemTouchListener() {
                override fun onInterceptTouchEvent(
                    recyclerView: RecyclerView,
                    event: MotionEvent
                ): Boolean {
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> recyclerView.parent
                            ?.requestDisallowInterceptTouchEvent(true)

                        MotionEvent.ACTION_UP,
                        MotionEvent.ACTION_CANCEL -> recyclerView.parent
                            ?.requestDisallowInterceptTouchEvent(false)
                    }
                    return false
                }
            }
        )
        snapHelper.attachToRecyclerView(recyclerView)
        recyclerView.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        applySnappedSelection()
                    }
                }
            }
        )
        val selectValue: (Int) -> Unit = { value -> selectPosition(adapter.positionOf(value)) }
        recyclerView.post { selectValue(initialValue) }
        return AquaTimeWheelBinding(
            replaceValues = { updatedValues, selectedValue ->
                adapter.replaceValues(updatedValues)
                recyclerView.post { selectValue(selectedValue) }
            }
        )
    }

    private fun publish(result: String) {
        if (resultSent) return
        resultSent = true
        val args = requireArguments()
        val allowEndOfDay = args.getBoolean(ARG_ALLOW_END_OF_DAY)
        parentFragmentManager.setFragmentResult(
            args.getString(ARG_REQUEST_KEY).orEmpty(),
            bundleOf(
                RESULT_KEY to result,
                RESULT_HOUR_OF_DAY to selectedHour,
                RESULT_MINUTE to selectedMinute,
                RESULT_MINUTES_OF_DAY to timePickerMinutesOfDay(
                    selectedHour,
                    selectedMinute,
                    allowEndOfDay = allowEndOfDay
                ),
                RESULT_SELECTION_MODE to args.getString(ARG_SELECTION_MODE).orEmpty(),
                RESULT_PAYLOAD_ID to args.getString(ARG_PAYLOAD_ID).orEmpty()
            )
        )
    }

    data class Request(
        val title: String,
        val message: String,
        val initialHour: Int,
        val initialMinute: Int,
        val selectionMode: SelectionMode = SelectionMode.TIME_OF_DAY,
        val allowEndOfDay: Boolean = false,
        val selectableMinutesOfDay: List<Int>? = null,
        val confirmText: String,
        val cancelText: String,
        val resultTarget: ResultTarget
    ) {
        init {
            val allowedHours = if (allowEndOfDay) END_OF_DAY_HOUR_RANGE else HOUR_RANGE
            require(initialHour in allowedHours) {
                "initialHour must be inside the configured time-picker range."
            }
            require(initialMinute in MINUTE_RANGE) { "initialMinute must be between 0 and 59." }
            require(!allowEndOfDay || initialHour != END_OF_DAY_HOUR || initialMinute == 0) {
                "24:00 is the only valid end-of-day value."
            }
            require(selectionMode != SelectionMode.MINUTE_OF_HOUR || initialHour == 0) {
                "Minute-of-hour selection must use initialHour 0."
            }
            require(selectionMode != SelectionMode.MINUTE_OF_HOUR || !allowEndOfDay) {
                "End-of-day selection is valid only for time-of-day mode."
            }
            selectableMinutesOfDay?.let { selectable ->
                val maximum = if (allowEndOfDay) MINUTES_PER_DAY else LAST_MINUTE_OF_DAY
                require(selectionMode == SelectionMode.TIME_OF_DAY) {
                    "Selectable wall-clock times require time-of-day mode."
                }
                require(selectable.isNotEmpty()) { "Selectable wall-clock times cannot be empty." }
                require(selectable.all { minute -> minute in 0..maximum }) {
                    "Selectable wall-clock times must stay inside the configured day."
                }
                require(
                    timePickerMinutesOfDay(initialHour, initialMinute, allowEndOfDay) in selectable
                ) {
                    "The initial wall-clock time must be selectable."
                }
            }
            require(resultTarget.requestKey.isNotBlank()) { "requestKey must not be blank." }
        }
    }

    enum class SelectionMode {
        TIME_OF_DAY,
        MINUTE_OF_HOUR
    }

    data class ResultTarget(
        val requestKey: String,
        val payloadId: String = ""
    )

    companion object {
        const val RESULT_KEY = "aqua_time_picker_result"
        const val RESULT_HOUR_OF_DAY = "aqua_time_picker_hour_of_day"
        const val RESULT_MINUTE = "aqua_time_picker_minute"
        const val RESULT_MINUTES_OF_DAY = "aqua_time_picker_minutes_of_day"
        const val RESULT_SELECTION_MODE = "aqua_time_picker_selection_mode"
        const val RESULT_PAYLOAD_ID = "aqua_time_picker_payload_id"
        const val RESULT_SELECTED = "selected"
        const val RESULT_CANCELLED = "cancelled"

        private const val ARG_TITLE = "arg_title"
        private const val ARG_MESSAGE = "arg_message"
        private const val ARG_INITIAL_HOUR = "arg_initial_hour"
        private const val ARG_INITIAL_MINUTE = "arg_initial_minute"
        private const val ARG_SELECTION_MODE = "arg_selection_mode"
        private const val ARG_ALLOW_END_OF_DAY = "arg_allow_end_of_day"
        private const val ARG_SELECTABLE_MINUTES_OF_DAY = "arg_selectable_minutes_of_day"
        private const val ARG_CONFIRM_TEXT = "arg_confirm_text"
        private const val ARG_CANCEL_TEXT = "arg_cancel_text"
        private const val ARG_REQUEST_KEY = "arg_request_key"
        private const val ARG_PAYLOAD_ID = "arg_payload_id"
        private const val STATE_SELECTED_HOUR = "state_selected_hour"
        private const val STATE_SELECTED_MINUTE = "state_selected_minute"
        private const val TAG_PREFIX = "AquaTimePickerBottomSheet:"
        private const val END_OF_DAY_HOUR = 24
        private const val END_OF_DAY_DISPLAY = "24:00"
        private const val MINUTES_PER_HOUR = 60
        private const val MINUTES_PER_DAY = END_OF_DAY_HOUR * MINUTES_PER_HOUR
        private const val LAST_MINUTE_OF_DAY = MINUTES_PER_DAY - 1
        private val HOUR_RANGE = 0..23
        private val END_OF_DAY_HOUR_RANGE = 0..END_OF_DAY_HOUR
        private val MINUTE_RANGE = 0..59

        fun newInstance(request: Request): AquaTimePickerBottomSheet =
            AquaTimePickerBottomSheet().apply {
                arguments = bundleOf(
                    ARG_TITLE to request.title,
                    ARG_MESSAGE to request.message,
                    ARG_INITIAL_HOUR to request.initialHour,
                    ARG_INITIAL_MINUTE to request.initialMinute,
                    ARG_SELECTION_MODE to request.selectionMode.name,
                    ARG_ALLOW_END_OF_DAY to request.allowEndOfDay,
                    ARG_CONFIRM_TEXT to request.confirmText,
                    ARG_CANCEL_TEXT to request.cancelText,
                    ARG_REQUEST_KEY to request.resultTarget.requestKey,
                    ARG_PAYLOAD_ID to request.resultTarget.payloadId
                ).apply {
                    request.selectableMinutesOfDay?.let { selectable ->
                        putIntArray(
                            ARG_SELECTABLE_MINUTES_OF_DAY,
                            selectable.distinct().sorted().toIntArray()
                        )
                    }
                }
            }

        fun show(fragmentManager: FragmentManager, request: Request) {
            val tag = TAG_PREFIX + request.resultTarget.requestKey
            if (fragmentManager.findFragmentByTag(tag) != null || fragmentManager.isStateSaved) {
                return
            }
            newInstance(request).show(fragmentManager, tag)
        }
    }
}

private data class AquaTimeWheelBinding(
    val replaceValues: (List<Int>, Int) -> Unit
)

private class AquaTimeWheelAdapter(
    values: List<Int>,
    @StringRes private val valueDescriptionRes: Int,
    locale: Locale,
    private val onValueClick: (Int) -> Unit
) : RecyclerView.Adapter<AquaTimeWheelAdapter.ValueViewHolder>() {

    private var values = values.toList()
    private var selectedPosition = RecyclerView.NO_POSITION

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ValueViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.item_aqua_time_picker_wheel_value,
            parent,
            false
        ) as TextView
        return ValueViewHolder(view)
    }

    override fun onBindViewHolder(holder: ValueViewHolder, position: Int) {
        val value = valueAt(position)
        val selected = position == selectedPosition
        holder.value.apply {
            text = clockUnitFormatter.format(value)
            contentDescription = context.getString(valueDescriptionRes, value)
            isSelected = selected
            setTextAppearance(
                if (selected) {
                    R.style.TextAppearance_Aqua_BottomSheet_TimePicker_WheelValue_Selected
                } else {
                    R.style.TextAppearance_Aqua_BottomSheet_TimePicker_WheelValue
                }
            )
            setOnClickListener {
                holder.bindingAdapterPosition
                    .takeIf { adapterPosition -> adapterPosition != RecyclerView.NO_POSITION }
                    ?.let(onValueClick)
            }
        }
    }

    override fun getItemCount(): Int = values.size

    fun positionOf(value: Int): Int {
        val position = values.indexOf(value)
        require(position >= 0) { "Selected wheel value must exist in the current options." }
        return position
    }

    fun valueAt(position: Int): Int = values[position.coerceIn(0, itemCount - 1)]

    fun replaceValues(updatedValues: List<Int>) {
        require(updatedValues.isNotEmpty()) { "Time wheel options cannot be empty." }
        val previousSize = values.size
        val normalizedValues = updatedValues.distinct().sorted()
        values = normalizedValues
        selectedPosition = RecyclerView.NO_POSITION
        val sharedSize = minOf(previousSize, normalizedValues.size)
        if (sharedSize > 0) notifyItemRangeChanged(0, sharedSize)
        if (normalizedValues.size > previousSize) {
            notifyItemRangeInserted(
                previousSize,
                normalizedValues.size - previousSize
            )
        } else if (normalizedValues.size < previousSize) {
            notifyItemRangeRemoved(
                normalizedValues.size,
                previousSize - normalizedValues.size
            )
        }
    }

    fun setSelectedPosition(position: Int) {
        if (position == selectedPosition) return
        val previous = selectedPosition
        selectedPosition = position
        if (previous != RecyclerView.NO_POSITION) notifyItemChanged(previous)
        notifyItemChanged(position)
    }

    class ValueViewHolder(val value: TextView) : RecyclerView.ViewHolder(value)

    private val clockUnitFormatter: NumberFormat = NumberFormat.getIntegerInstance(locale).apply {
        isGroupingUsed = false
        minimumIntegerDigits = 2
        maximumIntegerDigits = 2
    }
}

private fun Bundle.savedInt(key: String): Int? =
    takeIf { bundle -> bundle.containsKey(key) }?.getInt(key)

internal fun restoreTimePickerSelection(
    savedValue: Int?,
    initialValue: Int,
    range: IntRange
): Int = restoreTimePickerSelection(savedValue, initialValue, range.toList())

internal fun restoreTimePickerSelection(
    savedValue: Int?,
    initialValue: Int,
    values: List<Int>
): Int {
    require(values.isNotEmpty()) { "Time picker options cannot be empty." }
    val preferred = savedValue ?: initialValue
    return values.minBy { value -> kotlin.math.abs(value - preferred) }
}

internal fun timePickerHourValues(
    selectableMinutesOfDay: List<Int>?,
    allowEndOfDay: Boolean
): List<Int> = selectableMinutesOfDay
    ?.map(::timePickerHour)
    ?.distinct()
    ?.sorted()
    ?: (if (allowEndOfDay) 0..24 else 0..23).toList()

internal fun timePickerMinuteValues(
    selectableMinutesOfDay: List<Int>?,
    hour: Int
): List<Int> = selectableMinutesOfDay
    ?.filter { minuteOfDay -> timePickerHour(minuteOfDay) == hour }
    ?.map(::timePickerMinute)
    ?.distinct()
    ?.sorted()
    ?: if (hour == 24) listOf(0) else (0..59).toList()

private fun timePickerHour(minutesOfDay: Int): Int = if (minutesOfDay == 24 * 60) {
    24
} else {
    minutesOfDay / 60
}

private fun timePickerMinute(minutesOfDay: Int): Int = if (minutesOfDay == 24 * 60) {
    0
} else {
    minutesOfDay % 60
}

internal fun timePickerMinutesOfDay(
    hour: Int,
    minute: Int,
    allowEndOfDay: Boolean = false
): Int {
    if (allowEndOfDay && hour == 24) {
        require(minute == 0) { "24:00 is the only valid end-of-day value." }
        return 24 * 60
    }
    require(hour in 0..23) { "hour must be between 0 and 23." }
    require(minute in 0..59) { "minute must be between 0 and 59." }
    return hour * 60 + minute
}
