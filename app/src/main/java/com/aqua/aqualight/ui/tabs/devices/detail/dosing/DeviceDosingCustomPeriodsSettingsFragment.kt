package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.devices.dosing.CustomDosingPeriodCommand
import com.aqua.aqualight.data.devices.dosing.EspDosingCommandClient
import com.aqua.aqualight.data.devices.dosing.EspDosingSettingsClient
import com.aqua.aqualight.databinding.FragmentDeviceDosingCustomPeriodsSettingsBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

class DeviceDosingCustomPeriodsSettingsFragment :
    Fragment(R.layout.fragment_device_dosing_custom_periods_settings) {

    private var _binding: FragmentDeviceDosingCustomPeriodsSettingsBinding? = null
    private val binding get() = _binding!!

    private val periods: MutableList<CustomPeriodUi> =
        mutableListOf()

    private var selectedWeekDays: List<Boolean> =
        List(
            size = 7
        ) {
            true
        }

    private var oldTimerIndexesForChannel: List<Int> =
        emptyList()

    private var channelGpioPwmForSave: String =
        "-"

    private var channelCalibrationYeMsPerMlForSave: Long =
        -1L

    private var saveInProgress: Boolean =
        false

    private val channelIndex: Int
        get() = requireArguments().getInt(
            ARG_CHANNEL_INDEX,
            0
        ).coerceIn(
            minimumValue = 0,
            maximumValue = 3
        )

    private val deviceIp: String
        get() = requireArguments().getString(ARG_DEVICE_IP).orEmpty()

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding =
            FragmentDeviceDosingCustomPeriodsSettingsBinding.bind(
                view
            )

        bindClicks()
        bindDoseWatcher()
        loadCurrentValues()
    }

    private fun bindClicks() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnCancel.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnAddPeriod.setOnClickListener {
            addDefaultPeriod()
        }

        binding.btnSave.setOnClickListener {
            saveCustomPeriods()
        }
    }

    private fun bindDoseWatcher() {
        binding.etDailyDoseMl.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    renderSummary()
                    renderPeriods()
                }

                override fun afterTextChanged(
                    s: Editable?
                ) = Unit
            }
        )
    }

    private fun loadCurrentValues() {
        viewLifecycleOwner.lifecycleScope.launch {
            val snapshot =
                EspDosingSettingsClient.readChannelSettingsSnapshot(
                    deviceIp = deviceIp,
                    channelIndex = channelIndex
                )

            if (_binding == null || snapshot == null) {
                return@launch
            }

            channelGpioPwmForSave =
                snapshot.channel.gpioPwm

            channelCalibrationYeMsPerMlForSave =
                snapshot.channel.calibrationYeMsPerMl

            oldTimerIndexesForChannel =
                snapshot.timersForChannel.map { timer ->
                    timer.timerIndex
                }

            val activeTimers =
                snapshot.timersForChannel
                    .filter { timer ->
                        timer.enabled
                    }
                    .sortedBy { timer ->
                        timer.timerIndex
                    }

            val dailyDose =
                activeTimers.sumOf { timer ->
                    (
                        timer.doseMl *
                            timer.count.coerceAtLeast(
                                minimumValue = 1
                            )
                        ).toDouble()
                }.toFloat()

            if (dailyDose > 0f) {
                binding.etDailyDoseMl.setText(
                    formatDoseInput(
                        value = dailyDose
                    )
                )
            }

            val timerWithDays =
                activeTimers.firstOrNull { timer ->
                    timer.weekDays.any { selected ->
                        selected
                    }
                }

            selectedWeekDays =
                timerWithDays?.weekDays ?: List(
                    size = 7
                ) {
                    true
                }

            periods.clear()

            activeTimers.forEach { timer ->
                periods.add(
                    timerToPeriodUi(
                        timeStart = timer.timeStart,
                        intervalOff = timer.intervalOff,
                        doseMl = timer.doseMl,
                        count = timer.count,
                        calibrationYeMsPerMl = snapshot.channel.calibrationYeMsPerMl
                    )
                )
            }

            if (periods.isEmpty()) {
                periods.add(
                    CustomPeriodUi(
                        startTime = "10:00",
                        endTime = "12:00",
                        doseCount = 2
                    )
                )
            }

            renderSummary()
            renderPeriods()
        }
    }

    private fun addDefaultPeriod() {
        if (periods.size >= MAX_PERIOD_COUNT) {
            showMessage(
                message = "You can add up to $MAX_PERIOD_COUNT periods."
            )
            return
        }

        periods.add(
            CustomPeriodUi(
                startTime = "18:00",
                endTime = "22:00",
                doseCount = 4
            )
        )

        renderSummary()
        renderPeriods()
    }

    private fun renderPeriods() {
        binding.periodsContainer.removeAllViews()

        periods.forEachIndexed { index, period ->
            binding.periodsContainer.addView(
                createPeriodCard(
                    index = index,
                    period = period
                )
            )
        }
    }

    private fun createPeriodCard(
        index: Int,
        period: CustomPeriodUi
    ): View {
        val card =
            MaterialCardView(
                requireContext()
            ).apply {
                radius =
                    dp(
                        value = 20f
                    )

                cardElevation =
                    0f

                setCardBackgroundColor(
                    android.graphics.Color.parseColor("#101426")
                )

                strokeColor =
                    android.graphics.Color.parseColor("#24314F")

                strokeWidth =
                    dp(
                        value = 1f
                    ).toInt()

                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin =
                            dp(
                                value = 10f
                            ).toInt()
                    }
            }

        val root =
            LinearLayout(
                requireContext()
            ).apply {
                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(16f).toInt(),
                    dp(14f).toInt(),
                    dp(16f).toInt(),
                    dp(14f).toInt()
                )
            }

        val titleRow =
            LinearLayout(
                requireContext()
            ).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL
            }

        val title =
            TextView(
                requireContext()
            ).apply {
                text =
                    "Period ${index + 1}"

                setTextColor(
                    android.graphics.Color.WHITE
                )

                textSize =
                    15f

                setTypeface(
                    typeface,
                    android.graphics.Typeface.BOLD
                )

                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
            }

        val deleteButton =
            MaterialButton(
                requireContext()
            ).apply {
                text =
                    "Delete"

                isAllCaps =
                    false

                textSize =
                    12f

                minHeight =
                    0

                setPadding(
                    dp(12f).toInt(),
                    0,
                    dp(12f).toInt(),
                    0
                )

                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        dp(38f).toInt()
                    )

                setOnClickListener {
                    periods.removeAt(
                        index
                    )

                    renderSummary()
                    renderPeriods()
                }
            }

        titleRow.addView(
            title
        )

        titleRow.addView(
            deleteButton
        )

        val timeRow =
            LinearLayout(
                requireContext()
            ).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                setPadding(
                    0,
                    dp(14f).toInt(),
                    0,
                    0
                )
            }

        val startButton =
            createSmallActionButton(
                text = period.startTime
            ) {
                showTimePicker(
                    currentValue = period.startTime
                ) { newTime ->
                    periods[index] =
                        period.copy(
                            startTime = newTime
                        )

                    renderSummary()
                    renderPeriods()
                }
            }

        val endButton =
            createSmallActionButton(
                text = period.endTime
            ) {
                showTimePicker(
                    currentValue = period.endTime
                ) { newTime ->
                    periods[index] =
                        period.copy(
                            endTime = newTime
                        )

                    renderSummary()
                    renderPeriods()
                }
            }

        timeRow.addView(
            startButton
        )

        timeRow.addView(
            endButton
        )

        val countRow =
            LinearLayout(
                requireContext()
            ).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    0,
                    dp(14f).toInt(),
                    0,
                    0
                )
            }

        val countText =
            TextView(
                requireContext()
            ).apply {
                text =
                    "${period.doseCount} doses"

                setTextColor(
                    android.graphics.Color.WHITE
                )

                textSize =
                    14f

                gravity =
                    Gravity.CENTER

                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
            }

        val minus =
            createCountButton(
                text = "-"
            ) {
                if (period.doseCount > 1) {
                    periods[index] =
                        period.copy(
                            doseCount = period.doseCount - 1
                        )

                    renderSummary()
                    renderPeriods()
                }
            }

        val plus =
            createCountButton(
                text = "+"
            ) {
                periods[index] =
                    period.copy(
                        doseCount = period.doseCount + 1
                    )

                renderSummary()
                renderPeriods()
            }

        countRow.addView(
            minus
        )

        countRow.addView(
            countText
        )

        countRow.addView(
            plus
        )

        val perDose =
            calculatePerDoseMl()

        val info =
            TextView(
                requireContext()
            ).apply {
                text =
                    "${period.startTime} - ${period.endTime} · ${period.doseCount} doses · ${formatMl(perDose)} each"

                setTextColor(
                    android.graphics.Color.parseColor("#9AA7BD")
                )

                textSize =
                    12f

                setPadding(
                    0,
                    dp(12f).toInt(),
                    0,
                    0
                )
            }

        root.addView(
            titleRow
        )

        root.addView(
            timeRow
        )

        root.addView(
            countRow
        )

        root.addView(
            info
        )

        card.addView(
            root
        )

        return card
    }

    private fun createSmallActionButton(
        text: String,
        onClick: () -> Unit
    ): MaterialButton {
        return MaterialButton(
            requireContext()
        ).apply {
            this.text =
                text

            isAllCaps =
                false

            textSize =
                14f

            layoutParams =
                LinearLayout.LayoutParams(
                    0,
                    dp(44f).toInt(),
                    1f
                ).apply {
                    marginEnd =
                        dp(8f).toInt()
                }

            setOnClickListener {
                onClick()
            }
        }
    }

    private fun createCountButton(
        text: String,
        onClick: () -> Unit
    ): MaterialButton {
        return MaterialButton(
            requireContext()
        ).apply {
            this.text =
                text

            isAllCaps =
                false

            textSize =
                18f

            layoutParams =
                LinearLayout.LayoutParams(
                    dp(48f).toInt(),
                    dp(42f).toInt()
                )

            setOnClickListener {
                onClick()
            }
        }
    }

    private fun renderSummary() {
        val totalDoseCount =
            periods.sumOf { period ->
                period.doseCount
            }

        val perDose =
            calculatePerDoseMl()

        binding.tvSummaryValue.text =
            "$totalDoseCount doses/day · ${formatMl(perDose)} each"
    }

    private fun saveCustomPeriods() {
        if (saveInProgress) {
            return
        }

        hideKeyboard()

        val dailyDoseMl =
            binding.etDailyDoseMl.text
                ?.toString()
                ?.trim()
                ?.replace(
                    oldValue = ",",
                    newValue = "."
                )
                ?.toFloatOrNull()

        if (
            dailyDoseMl == null ||
            dailyDoseMl <= 0f
        ) {
            showMessage(
                message = "Please enter a valid daily dose."
            )
            return
        }

        if (periods.isEmpty()) {
            showMessage(
                message = "Please add at least one dosing period."
            )
            return
        }

        if (channelGpioPwmForSave.isBlank() || channelGpioPwmForSave == "-") {
            showMessage(
                message = "Channel output could not be found."
            )
            return
        }

        if (channelCalibrationYeMsPerMlForSave <= 0L) {
            showMessage(
                message = "Please calibrate this channel first."
            )
            return
        }

        val invalidPeriod =
            periods.firstOrNull { period ->
                timeToMinutes(
                    value = period.endTime
                ) <= timeToMinutes(
                    value = period.startTime
                )
            }

        if (invalidPeriod != null) {
            showMessage(
                message = "Period end time must be later than start time."
            )
            return
        }

        saveInProgress =
            true

        renderSavingState()

        viewLifecycleOwner.lifecycleScope.launch {
            val saved =
                EspDosingCommandClient.saveCustomPeriodsSchedule(
                    deviceIp = deviceIp,
                    channelIndex = channelIndex,
                    channelGpioPwm = channelGpioPwmForSave,
                    channelCalibrationYeMsPerMl = channelCalibrationYeMsPerMlForSave,
                    dailyDoseMl = dailyDoseMl,
                    periods = periods.map { period ->
                        CustomDosingPeriodCommand(
                            startTime = period.startTime,
                            endTime = period.endTime,
                            doseCount = period.doseCount
                        )
                    },
                    weekDays = selectedWeekDays,
                    oldTimerIndexesForChannel = oldTimerIndexesForChannel,
                    enabled = true
                )

            saveInProgress =
                false

            if (_binding == null) {
                return@launch
            }

            renderSavingState()

            if (saved) {
                showMessage(
                    message = "Custom periods saved."
                )

                findNavController().navigateUp()
            } else {
                showMessage(
                    message = "Custom periods could not be saved."
                )
            }
        }
    }

    private fun renderSavingState() {
        binding.btnSave.isEnabled =
            !saveInProgress

        binding.btnCancel.isEnabled =
            !saveInProgress

        binding.btnAddPeriod.isEnabled =
            !saveInProgress

        binding.etDailyDoseMl.isEnabled =
            !saveInProgress

        binding.btnSave.alpha =
            if (saveInProgress) {
                0.55f
            } else {
                1f
            }

        binding.btnSave.text =
            if (saveInProgress) {
                "Saving..."
            } else {
                "Save periods"
            }
    }

    private fun showTimePicker(
        currentValue: String,
        onSelected: (String) -> Unit
    ) {
        hideKeyboard()

        val parts =
    currentValue.split(":")

        val hour =
            parts.getOrNull(
                index = 0
            )?.toIntOrNull()
                ?.coerceIn(
                    minimumValue = 0,
                    maximumValue = 23
                ) ?: 0

        val minute =
            parts.getOrNull(
                index = 1
            )?.toIntOrNull()
                ?.coerceIn(
                    minimumValue = 0,
                    maximumValue = 59
                ) ?: 0

        TimePickerDialog(
            requireContext(),
            { _, selectedHour, selectedMinute ->
                onSelected(
                    String.format(
                        Locale.US,
                        "%02d:%02d",
                        selectedHour,
                        selectedMinute
                    )
                )
            },
            hour,
            minute,
            true
        ).show()
    }

    private fun timerToPeriodUi(
        timeStart: String,
        intervalOff: String,
        doseMl: Float,
        count: Int,
        calibrationYeMsPerMl: Long
    ): CustomPeriodUi {
        val startMinutes =
            timeToMinutes(
                value = timeStart
            )

        val safeCount =
            count.coerceAtLeast(
                minimumValue = 1
            )

        val doseRunMs =
            (
                doseMl.toDouble() *
                    calibrationYeMsPerMl.coerceAtLeast(
                        minimumValue = 1L
                    ).toDouble()
                ).roundToInt()
                .coerceAtLeast(
                    minimumValue = 1
                )

        val spacingMs =
            parseDurationMillis(
                value = intervalOff
            ) + doseRunMs

        val periodDurationMinutes =
            ((spacingMs * safeCount) / 60_000f)
                .roundToInt()
                .coerceAtLeast(
                    minimumValue = 1
                )

        val endMinutes =
            (startMinutes + periodDurationMinutes).coerceAtMost(
                maximumValue = 23 * 60 + 59
            )

        return CustomPeriodUi(
            startTime = minutesToTime(
                minutes = startMinutes
            ),
            endTime = minutesToTime(
                minutes = endMinutes
            ),
            doseCount = safeCount
        )
    }

    private fun calculatePerDoseMl(): Float {
        val dailyDoseMl =
            binding.etDailyDoseMl.text
                ?.toString()
                ?.trim()
                ?.replace(
                    oldValue = ",",
                    newValue = "."
                )
                ?.toFloatOrNull()
                ?: 0f

        val totalDoseCount =
            periods.sumOf { period ->
                period.doseCount
            }

        if (
            dailyDoseMl <= 0f ||
            totalDoseCount <= 0
        ) {
            return 0f
        }

        return dailyDoseMl / totalDoseCount.toFloat()
    }

    private fun parseDurationMillis(
        value: String
    ): Int {
        val clean =
            value.ifBlank {
                "00:00"
            }

        val mainPart =
            clean.substringBefore(
                delimiter = "."
            )

        val millisPart =
            clean.substringAfter(
                delimiter = ".",
                missingDelimiterValue = "0"
            ).take(
                n = 3
            ).padEnd(
                length = 3,
                padChar = '0'
            ).toIntOrNull() ?: 0

        val parts =
            mainPart.split(
                delimiter = ":"
            )

        val hours =
            parts.getOrNull(
                index = 0
            )?.toIntOrNull() ?: 0

        val minutes =
            parts.getOrNull(
                index = 1
            )?.toIntOrNull() ?: 0

        val seconds =
            parts.getOrNull(
                index = 2
            )?.toIntOrNull() ?: 0

        return (
            hours * 3_600_000 +
                minutes * 60_000 +
                seconds * 1_000 +
                millisPart
            )
    }

    private fun timeToMinutes(
        value: String
    ): Int {
        val parts =
            value.ifBlank {
                "00:00"
            }.split(
                delimiter = ":"
            )

        val hour =
            parts.getOrNull(
                index = 0
            )?.toIntOrNull()
                ?.coerceIn(
                    minimumValue = 0,
                    maximumValue = 23
                ) ?: 0

        val minute =
            parts.getOrNull(
                index = 1
            )?.toIntOrNull()
                ?.coerceIn(
                    minimumValue = 0,
                    maximumValue = 59
                ) ?: 0

        return hour * 60 + minute
    }

    private fun minutesToTime(
        minutes: Int
    ): String {
        val safeMinutes =
            minutes.coerceIn(
                minimumValue = 0,
                maximumValue = 23 * 60 + 59
            )

        return String.format(
            Locale.US,
            "%02d:%02d",
            safeMinutes / 60,
            safeMinutes % 60
        )
    }

    private fun formatDoseInput(
        value: Float
    ): String {
        return if (value % 1f == 0f) {
            value.toInt().toString()
        } else {
            String.format(
                Locale.US,
                "%.2f",
                value
            )
        }
    }

    private fun formatMl(
        value: Float
    ): String {
        return if (value % 1f == 0f) {
            "${value.toInt()} ml"
        } else {
            String.format(
                Locale.US,
                "%.3f ml",
                value
            ).trimEnd(
                '0'
            ).trimEnd(
                '.'
            )
        }
    }

    private fun hideKeyboard() {
        val inputMethodManager =
            requireContext().getSystemService(
                Context.INPUT_METHOD_SERVICE
            ) as InputMethodManager

        inputMethodManager.hideSoftInputFromWindow(
            binding.root.windowToken,
            0
        )

        binding.etDailyDoseMl.clearFocus()
        binding.root.clearFocus()
    }

    private fun showMessage(
        message: String
    ) {
        (activity as? BaseActivity)?.showSnackBar(
            message = message,
            type = BaseActivity.SnackType.NORMAL
        )
    }

    private fun dp(
        value: Float
    ): Float {
        return value * resources.displayMetrics.density
    }

    override fun onDestroyView() {
        _binding = null

        super.onDestroyView()
    }

    private data class CustomPeriodUi(
        val startTime: String,
        val endTime: String,
        val doseCount: Int
    )

    companion object {
        private const val ARG_DEVICE_IP = "deviceIp"
        private const val ARG_CHANNEL_INDEX = "channelIndex"
        private const val MAX_PERIOD_COUNT = 4
    }
}