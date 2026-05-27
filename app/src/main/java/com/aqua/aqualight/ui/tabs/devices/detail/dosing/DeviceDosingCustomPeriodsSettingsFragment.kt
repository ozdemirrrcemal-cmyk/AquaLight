package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
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
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.bottomsheet.DosingBottomSheets
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

        bindHeader()
        bindSelectedPumpIndicator()
        bindClicks()
        bindDoseWatcher()
        bindInitialValues()
    }

    private fun bindHeader() {
        binding.tvTitle.text =
            "Custom Periods"

        binding.btnBack.setOnClickListener {
            if (!saveInProgress) {
                findNavController().navigateUp()
            }
        }
    }

    private fun bindSelectedPumpIndicator() {
        binding.selectedIndicatorPump1.visibility =
            if (channelIndex == 0) {
                View.VISIBLE
            } else {
                View.GONE
            }

        binding.selectedIndicatorPump2.visibility =
            if (channelIndex == 1) {
                View.VISIBLE
            } else {
                View.GONE
            }

        binding.selectedIndicatorPump3.visibility =
            if (channelIndex == 2) {
                View.VISIBLE
            } else {
                View.GONE
            }

        binding.selectedIndicatorPump4.visibility =
            if (channelIndex == 3) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    private fun bindInitialValues() {
        periods.clear()

        renderSummary()
        renderPeriods()
    }

    private fun bindClicks() {
        binding.btnCancel.setOnClickListener {
            if (!saveInProgress) {
                findNavController().navigateUp()
            }
        }

        binding.btnAddPeriod.setOnClickListener {
            if (!saveInProgress) {
                showPeriodEditor(
                    periodIndex = null,
                    period = createDefaultPeriod()
                )
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

    private fun createDefaultPeriod(): CustomPeriodUi {
        return if (periods.isEmpty()) {
            CustomPeriodUi(
                startTime = "10:00",
                endTime = "12:00",
                doseCount = 2
            )
        } else {
            CustomPeriodUi(
                startTime = "18:00",
                endTime = "22:00",
                doseCount = 4
            )
        }
    }

    private fun showPeriodEditor(
        periodIndex: Int?,
        period: CustomPeriodUi
    ) {
        if (
            periodIndex == null &&
            periods.size >= MAX_PERIOD_COUNT
        ) {
            showSnackBar(
                message = "You can add up to $MAX_PERIOD_COUNT periods.",
                type = BaseActivity.SnackType.WARNING
            )
            return
        }

        hideKeyboard()

        DosingBottomSheets.showCustomPeriodEditor(
            context = requireContext(),
            title = if (periodIndex == null) {
                "Add Period"
            } else {
                "Edit Period"
            },
            initialStartTime = period.startTime,
            initialEndTime = period.endTime,
            initialDoseCount = period.doseCount,
            maxDoseCount = MAX_TOTAL_DOSE_COUNT,
            validator = { startTime, endTime, doseCount ->
                validatePeriodDraft(
                    periodIndex = periodIndex,
                    startTime = startTime,
                    endTime = endTime,
                    doseCount = doseCount
                )
            },
            onValidationError = { message ->
                showSnackBar(
                    message = message,
                    type = BaseActivity.SnackType.WARNING
                )
            },
            onDone = { result ->
                val newPeriod =
                    CustomPeriodUi(
                        startTime = result.startTime,
                        endTime = result.endTime,
                        doseCount = result.doseCount
                    )

                if (periodIndex == null) {
                    periods.add(
                        newPeriod
                    )
                } else {
                    periods[periodIndex] =
                        newPeriod
                }

                renderSummary()
                renderPeriods()
            }
        )
    }

    private fun renderPeriods() {
        binding.periodsContainer.removeAllViews()

        if (periods.isEmpty()) {
            binding.periodsContainer.addView(
                createEmptyPeriodsCard()
            )
        } else {
            periods.forEachIndexed { index, period ->
                binding.periodsContainer.addView(
                    createPeriodCard(
                        index = index,
                        period = period
                    )
                )
            }
        }
    }

    private fun createEmptyPeriodsCard(): View {
        val card =
            MaterialCardView(
                requireContext()
            ).apply {
                radius =
                    dp(
                        value = 22f
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
                        value = 1f
                    )

                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
            }

        val root =
            LinearLayout(
                requireContext()
            ).apply {
                orientation =
                    LinearLayout.VERTICAL

                gravity =
                    Gravity.CENTER

                setPadding(
                    dp(16f),
                    dp(18f),
                    dp(16f),
                    dp(18f)
                )
            }

        val title =
            TextView(
                requireContext()
            ).apply {
                text =
                    "No periods added yet"

                gravity =
                    Gravity.CENTER

                includeFontPadding =
                    false

                setTextColor(
                    Color.WHITE
                )

                textSize =
                    14f

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )
            }

        val description =
            TextView(
                requireContext()
            ).apply {
                text =
                    "Tap Add Period to define a dosing window."

                gravity =
                    Gravity.CENTER

                includeFontPadding =
                    false

                setTextColor(
                    Color.parseColor("#9AA7BD")
                )

                textSize =
                    12f

                setPadding(
                    0,
                    dp(6f),
                    0,
                    0
                )
            }

        root.addView(
            title
        )

        root.addView(
            description
        )

        card.addView(
            root
        )

        return card
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
                        value = 22f
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
                        value = 1f
                    )

                alpha =
                    if (saveInProgress) {
                        0.55f
                    } else {
                        1f
                    }

                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin =
                            if (index == 0) {
                                0
                            } else {
                                dp(
                                    value = 10f
                                )
                            }
                    }

                setOnClickListener {
                    if (!saveInProgress) {
                        showPeriodEditor(
                            periodIndex = index,
                            period = period
                        )
                    }
                }
            }

        val root =
            LinearLayout(
                requireContext()
            ).apply {
                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(16f),
                    dp(15f),
                    dp(16f),
                    dp(15f)
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

                includeFontPadding =
                    false

                setTextColor(
                    Color.WHITE
                )

                textSize =
                    15f

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )

                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
            }

        val doseBadge =
            createPeriodBadge(
                text = "${period.doseCount} doses"
            )

        titleRow.addView(
            title
        )

        titleRow.addView(
            doseBadge
        )

        val timeText =
            TextView(
                requireContext()
            ).apply {
                text =
                    "${period.startTime} - ${period.endTime}"

                includeFontPadding =
                    false

                setTextColor(
                    Color.WHITE
                )

                textSize =
                    18f

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )

                setPadding(
                    0,
                    dp(12f),
                    0,
                    0
                )
            }

        val perDose =
            calculatePerDoseMl()

        val infoText =
            TextView(
                requireContext()
            ).apply {
                text =
                    "${formatMl(perDose)} each · Tap to edit"

                includeFontPadding =
                    false

                setTextColor(
                    Color.parseColor("#9AA7BD")
                )

                textSize =
                    12f

                setPadding(
                    0,
                    dp(6f),
                    0,
                    0
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

                setTextColor(
                    Color.WHITE
                )

                backgroundTintList =
                    ColorStateList.valueOf(
                        Color.parseColor("#7F1D2D")
                    )

                cornerRadius =
                    dp(
                        value = 14f
                    )

                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(
                            value = 40f
                        )
                    ).apply {
                        topMargin =
                            dp(
                                value = 14f
                            )
                    }

                setOnClickListener {
                    if (saveInProgress) {
                        return@setOnClickListener
                    }

                    periods.removeAt(
                        index
                    )

                    renderSummary()
                    renderPeriods()
                }
            }

        root.addView(
            titleRow
        )

        root.addView(
            timeText
        )

        root.addView(
            infoText
        )

        root.addView(
            deleteButton
        )

        card.addView(
            root
        )

        return card
    }

    private fun createPeriodBadge(
        text: String
    ): MaterialCardView {
        val badge =
            MaterialCardView(
                requireContext()
            ).apply {
                radius =
                    dp(
                        value = 13f
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
                        value = 1f
                    )

                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        dp(
                            value = 34f
                        )
                    )
            }

        val badgeText =
            TextView(
                requireContext()
            ).apply {
                this.text =
                    text

                gravity =
                    Gravity.CENTER

                includeFontPadding =
                    false

                setTextColor(
                    Color.parseColor("#F43F5E")
                )

                textSize =
                    12f

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )

                setPadding(
                    dp(12f),
                    0,
                    dp(12f),
                    0
                )
            }

        badge.addView(
            badgeText
        )

        return badge
    }

    private fun validatePeriodDraft(
        periodIndex: Int?,
        startTime: String,
        endTime: String,
        doseCount: Int
    ): String? {
        if (
            timeToMinutes(
                value = endTime
            ) <= timeToMinutes(
                value = startTime
            )
        ) {
            return "End time must be later than start time."
        }

        val totalDoseCountExcludingCurrent =
            periods
                .filterIndexed { index, _ ->
                    index != periodIndex
                }
                .sumOf { period ->
                    period.doseCount
                }

        if (totalDoseCountExcludingCurrent + doseCount > MAX_TOTAL_DOSE_COUNT) {
            return "Total dose count cannot exceed $MAX_TOTAL_DOSE_COUNT doses per day."
        }

        return null
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

        binding.tvPeriodLimitValue.text =
            "${periods.size} / $MAX_PERIOD_COUNT"
    }

    private fun handleSaveClick() {
        if (saveInProgress) {
            return
        }

        hideKeyboard()

        val dailyDoseMl =
            readDailyDoseMl()

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
                message = "End time must be later than start time.",
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

            findNavController().navigateUp()
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
                "Save Custom Periods"
            }
    }

    private fun calculatePerDoseMl(): Float {
        val dailyDoseMl =
            readDailyDoseMl() ?: 0f

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

    private fun readDailyDoseMl(): Float? {
        return binding.etDailyDoseMl.text
            ?.toString()
            ?.trim()
            ?.replace(
                oldValue = ",",
                newValue = "."
            )
            ?.toFloatOrNull()
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
    ): Int {
        return (
            value *
                resources.displayMetrics.density
            ).toInt()
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
        private const val MAX_TOTAL_DOSE_COUNT = 24
    }
}