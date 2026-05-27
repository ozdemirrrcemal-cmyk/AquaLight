package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
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
import com.aqua.aqualight.databinding.FragmentDeviceDosingTimerModeSettingsBinding
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.bottomsheet.DosingBottomSheets
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class DeviceDosingTimerModeSettingsFragment :
Fragment(R.layout.fragment_device_dosing_timer_mode_settings) {

    private var _binding: FragmentDeviceDosingTimerModeSettingsBinding? = null
    private val binding get() = _binding!!

    private val timerDoses: MutableList<TimerDoseUi> =
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
        FragmentDeviceDosingTimerModeSettingsBinding.bind(
            view
        )

        bindHeader()
        bindSelectedPumpIndicator()
        bindClicks()
        renderTimerDoses()
        renderSummary()
    }

    private fun bindHeader() {
        binding.tvTitle.text =
        "Timer"

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

    private fun bindClicks() {
        binding.btnCancel.setOnClickListener {
            if (!saveInProgress) {
                findNavController().navigateUp()
            }
        }

        binding.btnAddDose.setOnClickListener {
            if (!saveInProgress) {
                showDoseEditor(
                    doseIndex = null,
                    dose = createDefaultDose()
                )
            }
        }

        binding.btnSave.setOnClickListener {
            handleSaveClick()
        }
    }

    private fun createDefaultDose(): TimerDoseUi {
        val baseHour =
        when (timerDoses.size) {
            0 -> 12
            1 -> 14
            2 -> 16
            else -> 18
        }

        return TimerDoseUi(
            startTime = String.format(
                Locale.US,
                "%02d:00",
                baseHour.coerceIn(
                    minimumValue = 0,
                    maximumValue = 23
                )
            ),
            doseMl = 1f
        )
    }

    private fun showDoseEditor(
        doseIndex: Int?,
        dose: TimerDoseUi
    ) {
        if (
            doseIndex == null &&
            timerDoses.size >= MAX_TIMER_DOSE_COUNT
        ) {
            showSnackBar(
                message = "You can add up to $MAX_TIMER_DOSE_COUNT doses.",
                type = BaseActivity.SnackType.WARNING
            )
            return
        }

        hideKeyboard()

        DosingBottomSheets.showTimerDoseEditor(
            context = requireContext(),
            title = if (doseIndex == null) {
                "Add Dose"
            } else {
                "Edit Dose"
            },
            initialStartTime = dose.startTime,
            initialDoseMl = dose.doseMl,
            validator = {
                startTime, doseMl ->
                validateDoseDraft(
                    doseIndex = doseIndex,
                    startTime = startTime,
                    doseMl = doseMl
                )
            },
            onValidationError = {
                message ->
                showSnackBar(
                    message = message,
                    type = BaseActivity.SnackType.WARNING
                )
            },
            onDelete = if (doseIndex == null) {
                null
            } else {
                {
                    timerDoses.removeAt(
                        doseIndex
                    )

                    sortDoses()
                    renderTimerDoses()
                    renderSummary()
                }
            },
            onDone = {
                result ->
                val newDose =
                TimerDoseUi(
                    startTime = result.startTime,
                    doseMl = result.doseMl
                )

                if (doseIndex == null) {
                    timerDoses.add(
                        newDose
                    )
                } else {
                    timerDoses[doseIndex] =
                    newDose
                }

                sortDoses()
                renderTimerDoses()
                renderSummary()
            }
        )
    }

    private fun validateDoseDraft(
        doseIndex: Int?,
        startTime: String,
        doseMl: Float
    ): String? {
        if (doseMl <= 0f) {
            return "Please enter a valid dose quantity."
        }

        val duplicateTimeExists =
        timerDoses.withIndex().any {
            indexedDose ->
            indexedDose.index != doseIndex &&
            indexedDose.value.startTime == startTime
        }

        if (duplicateTimeExists) {
            return "A dose already exists at this time."
        }

        return null
    }

    private fun renderTimerDoses() {
        binding.timerDosesContainer.removeAllViews()

        if (timerDoses.isEmpty()) {
            binding.timerDosesContainer.addView(
                createEmptyDoseCard()
            )
        } else {
            timerDoses.forEachIndexed {
                index, dose ->
                binding.timerDosesContainer.addView(
                    createTimerDoseCard(
                        index = index,
                        dose = dose
                    )
                )
            }
        }
    }

    private fun createEmptyDoseCard(): View {
        val card =
        MaterialCardView(
            requireContext()
        ).apply {
            radius =
            dp(
                value = 18f
            ).toFloat()

            cardElevation =
            0f

            setCardBackgroundColor(
                Color.parseColor("#0B1020")
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
                dp(16f),
                dp(16f),
                dp(16f)
            )
        }

        val title =
        TextView(
            requireContext()
        ).apply {
            text =
            "No doses added yet"

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
            "Tap Add to create an individual dose."

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

    private fun createTimerDoseCard(
        index: Int,
        dose: TimerDoseUi
    ): View {
        val card =
        MaterialCardView(
            requireContext()
        ).apply {
            radius =
            dp(
                value = 18f
            ).toFloat()

            cardElevation =
            0f

            isClickable =
            true

            isFocusable =
            true

            alpha =
            if (saveInProgress) {
                0.55f
            } else {
                1f
            }

            setCardBackgroundColor(
                Color.parseColor("#0B1020")
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
            ).apply {
                topMargin =
                if (index == 0) {
                    0
                } else {
                    dp(
                        value = 8f
                    )
                }
            }

            setOnClickListener {
                if (!saveInProgress) {
                    showDoseEditor(
                        doseIndex = index,
                        dose = dose
                    )
                }
            }
        }

        val row =
        LinearLayout(
            requireContext()
        ).apply {
            orientation =
            LinearLayout.HORIZONTAL

            gravity =
            Gravity.CENTER_VERTICAL

            setPadding(
                dp(14f),
                dp(12f),
                dp(14f),
                dp(12f)
            )
        }

        val textContainer =
        LinearLayout(
            requireContext()
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

        val timeText =
        TextView(
            requireContext()
        ).apply {
            text =
            dose.startTime

            includeFontPadding =
            false

            setTextColor(
                Color.WHITE
            )

            textSize =
            16f

            setTypeface(
                typeface,
                Typeface.BOLD
            )
        }

        val hintText =
        TextView(
            requireContext()
        ).apply {
            text =
            "Tap to edit"

            includeFontPadding =
            false

            setTextColor(
                Color.parseColor("#9AA7BD")
            )

            textSize =
            11f

            setPadding(
                0,
                dp(4f),
                0,
                0
            )
        }

        val doseBadge =
        MaterialCardView(
            requireContext()
        ).apply {
            radius =
            dp(
                value = 14f
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
                dp(
                    value = 92f
                ),
                dp(
                    value = 38f
                )
            ).apply {
                marginStart =
                dp(
                    value = 12f
                )
            }
        }

        val doseText =
        TextView(
            requireContext()
        ).apply {
            text =
            formatMl(
                value = dose.doseMl
            )

            gravity =
            Gravity.CENTER

            includeFontPadding =
            false

            setTextColor(
                Color.parseColor("#F43F5E")
            )

            textSize =
            13f

            setTypeface(
                typeface,
                Typeface.BOLD
            )
        }

        textContainer.addView(
            timeText
        )

        textContainer.addView(
            hintText
        )

        doseBadge.addView(
            doseText
        )

        row.addView(
            textContainer
        )

        row.addView(
            doseBadge
        )

        card.addView(
            row
        )

        return card
    }

    private fun renderSummary() {
        binding.tvTotalDailyDoseValue.text =
        formatMl(
            value = calculateTotalDailyDoseMl()
        )

        binding.tvDoseLimitValue.text =
        "${timerDoses.size} / $MAX_TIMER_DOSE_COUNT"
    }

    private fun handleSaveClick() {
        if (saveInProgress) {
            return
        }

        hideKeyboard()

        if (timerDoses.isEmpty()) {
            showSnackBar(
                message = "Please add at least one dose.",
                type = BaseActivity.SnackType.WARNING
            )
            return
        }

        saveInProgress =
        true

        renderSavingState()
        renderTimerDoses()

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
            renderTimerDoses()

            findNavController().navigateUp()
        }
    }

    private fun renderSavingState() {
        binding.btnSave.isEnabled =
        !saveInProgress

        binding.btnCancel.isEnabled =
        !saveInProgress

        binding.btnAddDose.isEnabled =
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

        binding.btnAddDose.alpha =
        if (saveInProgress) {
            0.55f
        } else {
            1f
        }

        binding.btnSave.text =
        "Save"
    }

    private fun sortDoses() {
        timerDoses.sortBy {
            dose ->
            timeToMinutes(
                value = dose.startTime
            )
        }
    }

    private fun calculateTotalDailyDoseMl(): Float {
        return timerDoses.sumOf {
            dose ->
            dose.doseMl.toDouble()
        }.toFloat()
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

    private data class TimerDoseUi(
        val startTime: String,
        val doseMl: Float
    )

    companion object {
        private const val ARG_DEVICE_ID = "deviceId"
        private const val ARG_DEVICE_IP = "deviceIp"
        private const val ARG_DEVICE_TITLE = "deviceTitle"
        private const val ARG_CHANNEL_INDEX = "channelIndex"

        private const val MAX_TIMER_DOSE_COUNT = 24
    }
}