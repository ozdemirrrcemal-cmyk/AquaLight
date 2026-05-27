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
import com.aqua.aqualight.databinding.FragmentDeviceDosingCustomPeriodsSettingsBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class DeviceDosingCustomPeriodsSettingsFragment :
    Fragment(R.layout.fragment_device_dosing_custom_periods_settings) {

    private var _binding: FragmentDeviceDosingCustomPeriodsSettingsBinding? = null
    private val binding get() = _binding!!

    private val periods: MutableList<CustomPeriodUi> =
        mutableListOf()

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
        bindInitialValues()
    }

    private fun bindInitialValues() {
        periods.clear()

        periods.add(
            CustomPeriodUi(
                startTime = "10:00",
                endTime = "12:00",
                doseCount = 2
            )
        )

        renderSummary()
        renderPeriods()
    }

    private fun bindClicks() {
        binding.btnBack.setOnClickListener {
            if (!saveInProgress) {
                findNavController().navigateUp()
            }
        }

        binding.btnCancel.setOnClickListener {
            if (!saveInProgress) {
                findNavController().navigateUp()
            }
        }

        binding.btnAddPeriod.setOnClickListener {
            if (!saveInProgress) {
                addDefaultPeriod()
            }
        }

        binding.btnSave.setOnClickListener {
            handleSaveClick()
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

    private fun addDefaultPeriod() {
        if (periods.size >= MAX_PERIOD_COUNT) {
            showSnackBar(
                message = "You can add up to $MAX_PERIOD_COUNT periods.",
                type = BaseActivity.SnackType.WARNING
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

                isEnabled =
                    !saveInProgress

                alpha =
                    if (saveInProgress) {
                        0.55f
                    } else {
                        1f
                    }

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
                    if (saveInProgress) {
                        return@setOnClickListener
                    }

                    if (periods.size <= 1) {
                        showSnackBar(
                            message = "At least one period is required.",
                            type = BaseActivity.SnackType.WARNING
                        )
                        return@setOnClickListener
                    }

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
                if (saveInProgress) {
                    return@createSmallActionButton
                }

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
                if (saveInProgress) {
                    return@createSmallActionButton
                }

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
                if (saveInProgress) {
                    return@createCountButton
                }

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
                if (saveInProgress) {
                    return@createCountButton
                }

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

            isEnabled =
                !saveInProgress

            alpha =
                if (saveInProgress) {
                    0.55f
                } else {
                    1f
                }

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

            isEnabled =
                !saveInProgress

            alpha =
                if (saveInProgress) {
                    0.55f
                } else {
                    1f
                }

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

    private fun handleSaveClick() {
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
            showSnackBar(
                message = "Please enter a valid daily dose.",
                type = BaseActivity.SnackType.WARNING
            )
            return
        }

        if (periods.isEmpty()) {
            showSnackBar(
                message = "Please add at least one dosing period.",
                type = BaseActivity.SnackType.WARNING
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
            showSnackBar(
                message = "Period end time must be later than start time.",
                type = BaseActivity.SnackType.WARNING
            )
            return
        }

        saveInProgress =
            true

        renderSavingState()
        renderPeriods()

        setLoading(
            show = true
        )

        viewLifecycleOwner.lifecycleScope.launch {
            delay(
                timeMillis = 700L
            )

            setLoading(
                show = false
            )

            saveInProgress =
                false

            if (_binding == null) {
                return@launch
            }

            renderSavingState()
            renderPeriods()

            showSnackBar(
                message = "Custom periods save will be connected after screen design is finalized.",
                type = BaseActivity.SnackType.NORMAL
            )
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

        binding.btnCancel.alpha =
            if (saveInProgress) {
                0.55f
            } else {
                1f
            }

        binding.btnAddPeriod.alpha =
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

    private fun timeToMinutes(
        value: String
    ): Int {
        val parts =
            value.ifBlank {
                "00:00"
            }.split(":")

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

    private fun formatMl(
        value: Float
    ): String {
        return if (value % 1f == 0f) {
            "${value.toInt()} ml"
        } else {
            val amount =
                String.format(
                    Locale.US,
                    "%.3f",
                    value
                ).trimEnd(
                    '0'
                ).trimEnd(
                    '.'
                )

            "$amount ml"
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

    private fun setLoading(
        show: Boolean
    ) {
        (activity as? BaseActivity)?.showLoading(
            show = show
        )
    }

    private fun showSnackBar(
        message: String,
        type: BaseActivity.SnackType = BaseActivity.SnackType.NORMAL
    ) {
        (activity as? BaseActivity)?.showSnackBar(
            message = message,
            type = type
        )
    }

    private fun dp(
        value: Float
    ): Float {
        return value * resources.displayMetrics.density
    }

    override fun onDestroyView() {
        if (saveInProgress) {
            setLoading(
                show = false
            )
        }

        saveInProgress =
            false

        _binding =
            null

        super.onDestroyView()
    }

    private data class CustomPeriodUi(
        val startTime: String,
        val endTime: String,
        val doseCount: Int
    )

    companion object {
        private const val ARG_DEVICE_ID = "deviceId"
        private const val ARG_DEVICE_IP = "deviceIp"
        private const val ARG_DEVICE_TITLE = "deviceTitle"
        private const val ARG_CHANNEL_INDEX = "channelIndex"
        private const val MAX_PERIOD_COUNT = 4
    }
}