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

/** Re-creatable 24-hour wall-clock picker for schedules and other product time-of-day fields. */
class AquaTimePickerBottomSheet : BottomSheetDialogFragment(
    R.layout.bottom_sheet_aqua_time_picker
) {

    private var resultSent = false
    private var selectedHour = 0
    private var selectedMinute = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = requireArguments()
        selectedHour = restoreTimePickerSelection(
            savedValue = savedInstanceState?.savedInt(STATE_SELECTED_HOUR),
            initialValue = args.getInt(ARG_INITIAL_HOUR),
            range = HOUR_RANGE
        )
        selectedMinute = restoreTimePickerSelection(
            savedValue = savedInstanceState?.savedInt(STATE_SELECTED_MINUTE),
            initialValue = args.getInt(ARG_INITIAL_MINUTE),
            range = MINUTE_RANGE
        )

        view.findViewById<TextView>(R.id.tvAquaTimePickerTitle).text =
            args.getString(ARG_TITLE).orEmpty()
        view.findViewById<TextView>(R.id.tvAquaTimePickerMessage).text =
            args.getString(ARG_MESSAGE).orEmpty()

        val selection = view.findViewById<TextView>(R.id.tvAquaTimePickerSelection)
        fun renderSelection() {
            val formatted = LocaleFormatter.formatTimeOfDay24Hour(
                context = requireContext(),
                minutesOfDay = timePickerMinutesOfDay(selectedHour, selectedMinute)
            )
            selection.text = formatted
            selection.contentDescription = getString(
                R.string.common_time_picker_selected_time_description,
                formatted
            )
        }

        bindWheel(
            recyclerView = view.findViewById(R.id.rvAquaTimePickerHour),
            values = HOUR_RANGE,
            initialValue = selectedHour,
            valueDescriptionRes = R.string.common_time_picker_hour_value_description,
            onSelected = { hour ->
                selectedHour = hour
                renderSelection()
            }
        )
        bindWheel(
            recyclerView = view.findViewById(R.id.rvAquaTimePickerMinute),
            values = MINUTE_RANGE,
            initialValue = selectedMinute,
            valueDescriptionRes = R.string.common_time_picker_minute_value_description,
            onSelected = { minute ->
                selectedMinute = minute
                renderSelection()
            }
        )

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
        renderSelection()
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
        values: IntRange,
        initialValue: Int,
        @StringRes valueDescriptionRes: Int,
        onSelected: (Int) -> Unit
    ) {
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
        recyclerView.post {
            selectPosition(adapter.positionOf(initialValue))
        }
    }

    private fun publish(result: String) {
        if (resultSent) return
        resultSent = true
        val args = requireArguments()
        parentFragmentManager.setFragmentResult(
            args.getString(ARG_REQUEST_KEY).orEmpty(),
            bundleOf(
                RESULT_KEY to result,
                RESULT_HOUR_OF_DAY to selectedHour,
                RESULT_MINUTE to selectedMinute,
                RESULT_MINUTES_OF_DAY to timePickerMinutesOfDay(
                    selectedHour,
                    selectedMinute
                ),
                RESULT_PAYLOAD_ID to args.getString(ARG_PAYLOAD_ID).orEmpty()
            )
        )
    }

    data class Request(
        val title: String,
        val message: String,
        val initialHour: Int,
        val initialMinute: Int,
        val confirmText: String,
        val cancelText: String,
        val resultTarget: ResultTarget
    ) {
        init {
            require(initialHour in HOUR_RANGE) { "initialHour must be between 0 and 23." }
            require(initialMinute in MINUTE_RANGE) { "initialMinute must be between 0 and 59." }
            require(resultTarget.requestKey.isNotBlank()) { "requestKey must not be blank." }
        }
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
        const val RESULT_PAYLOAD_ID = "aqua_time_picker_payload_id"
        const val RESULT_SELECTED = "selected"
        const val RESULT_CANCELLED = "cancelled"

        private const val ARG_TITLE = "arg_title"
        private const val ARG_MESSAGE = "arg_message"
        private const val ARG_INITIAL_HOUR = "arg_initial_hour"
        private const val ARG_INITIAL_MINUTE = "arg_initial_minute"
        private const val ARG_CONFIRM_TEXT = "arg_confirm_text"
        private const val ARG_CANCEL_TEXT = "arg_cancel_text"
        private const val ARG_REQUEST_KEY = "arg_request_key"
        private const val ARG_PAYLOAD_ID = "arg_payload_id"
        private const val STATE_SELECTED_HOUR = "state_selected_hour"
        private const val STATE_SELECTED_MINUTE = "state_selected_minute"
        private const val TAG_PREFIX = "AquaTimePickerBottomSheet:"
        private val HOUR_RANGE = 0..23
        private val MINUTE_RANGE = 0..59

        fun newInstance(request: Request): AquaTimePickerBottomSheet =
            AquaTimePickerBottomSheet().apply {
                arguments = bundleOf(
                    ARG_TITLE to request.title,
                    ARG_MESSAGE to request.message,
                    ARG_INITIAL_HOUR to request.initialHour,
                    ARG_INITIAL_MINUTE to request.initialMinute,
                    ARG_CONFIRM_TEXT to request.confirmText,
                    ARG_CANCEL_TEXT to request.cancelText,
                    ARG_REQUEST_KEY to request.resultTarget.requestKey,
                    ARG_PAYLOAD_ID to request.resultTarget.payloadId
                )
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

private class AquaTimeWheelAdapter(
    private val values: IntRange,
    @StringRes private val valueDescriptionRes: Int,
    locale: Locale,
    private val onValueClick: (Int) -> Unit
) : RecyclerView.Adapter<AquaTimeWheelAdapter.ValueViewHolder>() {

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

    override fun getItemCount(): Int = values.count()

    fun positionOf(value: Int): Int = value.coerceIn(values) - values.first

    fun valueAt(position: Int): Int = values.first + position.coerceIn(0, itemCount - 1)

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
): Int = (savedValue ?: initialValue).coerceIn(range)

internal fun timePickerMinutesOfDay(hour: Int, minute: Int): Int {
    require(hour in 0..23) { "hour must be between 0 and 23." }
    require(minute in 0..59) { "minute must be between 0 and 59." }
    return hour * 60 + minute
}
