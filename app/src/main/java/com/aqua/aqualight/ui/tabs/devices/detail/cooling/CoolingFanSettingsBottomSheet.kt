package com.aqua.aqualight.ui.tabs.devices.detail.cooling

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.BottomSheetCoolingFanSettingsBinding
import com.aqua.aqualight.ui.tabs.devices.detail.DeviceVisualSpec
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.util.Locale
import kotlin.math.roundToInt

class CoolingFanSettingsBottomSheet(
    private val fragment: Fragment,
    private val visualSpec: DeviceVisualSpec,
    private val fan: CoolingDeviceRepository.FanChannelData,
    private val rule: CoolingDeviceRepository.CoolRuleData?,
    private val sensors: List<CoolingDeviceRepository.TemperatureSensorData>,
    private val onSave: (
        draft: CoolingFanSettingsDraft,
        sheet: CoolingFanSettingsBottomSheet
    ) -> Unit
) {

    data class CoolingFanSettingsDraft(
        val fanIndex: Int,
        val ruleIndex: Int?,
        val fanName: String,
        val fanMode: CoolingDeviceRepository.FanRegime,
        val startCooling: Float,
        val fullPower: Float,
        val minimumPowerPercent: Int,
        val maximumPowerPercent: Int,
        val selectedSensorIndexes: List<Int>
    )

    private lateinit var dialog: BottomSheetDialog
    private lateinit var binding: BottomSheetCoolingFanSettingsBinding

    private var selectedMode: CoolingDeviceRepository.FanRegime = fan.regime

    private var startCooling: Float = rule?.tMin ?: 24f
    private var fullPower: Float = rule?.tMax ?: 30f

    private var minimumPowerPercent: Int = normalizePercent(
        value = fan.vMin
    ).roundToInt()

    private var maximumPowerPercent: Int = normalizePercent(
        value = fan.vMax
    ).roundToInt()

    private val selectedSensorIndexes = mutableSetOf<Int>()

    private var isSaving: Boolean = false

    fun show() {
        val context = fragment.requireContext()

        prepareInitialValues()

        binding = BottomSheetCoolingFanSettingsBinding.inflate(
            fragment.layoutInflater
        )

        dialog = BottomSheetDialog(
            context
        )

        dialog.setContentView(
            binding.root
        )

        applyVisualStyle()
        bindInitialTexts()
        bindModeButtons()
        bindStepperButtons()
        bindActions()
        renderSensorOptions()
        refreshValueTexts()

        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            )

            bottomSheet?.background = ColorDrawable(
                Color.TRANSPARENT
            )

            bottomSheet?.let { sheet ->
                val behavior = BottomSheetBehavior.from(
                    sheet
                )

                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }

        dialog.show()
    }

    private fun prepareInitialValues() {
        if (fullPower <= startCooling) {
            fullPower = startCooling + 0.5f
        }

        minimumPowerPercent = minimumPowerPercent.coerceIn(
            0,
            95
        )

        maximumPowerPercent = maximumPowerPercent.coerceIn(
            5,
            100
        )

        if (maximumPowerPercent <= minimumPowerPercent) {
            maximumPowerPercent = (minimumPowerPercent + 5).coerceAtMost(
                100
            )
        }

        selectedSensorIndexes.clear()

        val flags = rule?.selectedTemperatureFlags.orEmpty()

        sensors.forEach { sensor ->
            if (flags.getOrNull(sensor.index) == true) {
                selectedSensorIndexes.add(
                    sensor.index
                )
            }
        }
    }

    private fun applyVisualStyle() {
        binding.sheetCard.setCardBackgroundColor(
            Color.parseColor("#0B1727")
        )

        binding.sheetCard.strokeColor =
            visualSpec.cardStrokeColor

        binding.viewSheetAccent.setBackgroundColor(
            visualSpec.accentColor
        )

        binding.tvFanOutputChip.background = roundedDrawable(
            color = visualSpec.buttonColor,
            radiusDp = 100,
            context = fragment.requireContext()
        )

        binding.cardFanName.setCardBackgroundColor(
            Color.parseColor("#101F33")
        )

        binding.cardFanMode.setCardBackgroundColor(
            Color.parseColor("#101F33")
        )

        binding.cardTemperatureRangeReal.setCardBackgroundColor(
            Color.parseColor("#101F33")
        )

        binding.cardFanPowerRange.setCardBackgroundColor(
            Color.parseColor("#101F33")
        )

        binding.cardSensors.setCardBackgroundColor(
            Color.parseColor("#101F33")
        )

        binding.etFanName.setTextColor(
            Color.parseColor("#E8EEF7")
        )

        binding.etFanName.setHintTextColor(
            Color.parseColor("#92A1B4")
        )

        styleSmallRoundButton(
            binding.btnStartCoolingMinus
        )
        styleSmallRoundButton(
            binding.btnStartCoolingPlus
        )
        styleSmallRoundButton(
            binding.btnFullPowerMinus
        )
        styleSmallRoundButton(
            binding.btnFullPowerPlus
        )
        styleSmallRoundButton(
            binding.btnMinimumPowerMinus
        )
        styleSmallRoundButton(
            binding.btnMinimumPowerPlus
        )
        styleSmallRoundButton(
            binding.btnMaximumPowerMinus
        )
        styleSmallRoundButton(
            binding.btnMaximumPowerPlus
        )

        binding.btnCancel.backgroundTintList = ColorStateList.valueOf(
            Color.parseColor("#15263A")
        )

        binding.btnCancel.setTextColor(
            Color.parseColor("#D5DEEA")
        )

        binding.btnCancel.strokeColor = ColorStateList.valueOf(
            Color.parseColor("#2A3E59")
        )

        binding.btnSave.backgroundTintList = ColorStateList.valueOf(
            visualSpec.buttonColor
        )

        binding.btnSave.setTextColor(
            visualSpec.buttonTextColor
        )
    }

    private fun bindInitialTexts() {
        binding.tvSheetTitle.text = "${fan.name} Cooling"
        binding.tvSheetSubtitle.text = "Fan automation settings"

        binding.tvFanOutputChip.text = formatFanOutput(
            value = fan.vNow
        )

        binding.etFanName.setText(
            fan.name
        )

        binding.etFanName.setSelection(
            binding.etFanName.text?.length ?: 0
        )
    }

    private fun bindModeButtons() {
        binding.modeToggleGroup.check(
            modeId(
                mode = selectedMode
            )
        )

        binding.modeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) {
                return@addOnButtonCheckedListener
            }

            selectedMode = when (checkedId) {
                R.id.btnModeAuto -> CoolingDeviceRepository.FanRegime.AUTO
                R.id.btnModeOn -> CoolingDeviceRepository.FanRegime.ON
                R.id.btnModeOff -> CoolingDeviceRepository.FanRegime.OFF
                else -> selectedMode
            }

            refreshModeButtons()
        }

        refreshModeButtons()
    }

    private fun bindStepperButtons() {
        binding.btnStartCoolingMinus.setOnClickListener {
            startCooling = (startCooling - 0.5f).coerceAtLeast(
                0f
            )

            if (fullPower <= startCooling) {
                fullPower = startCooling + 0.5f
            }

            refreshValueTexts()
        }

        binding.btnStartCoolingPlus.setOnClickListener {
            startCooling = (startCooling + 0.5f).coerceAtMost(
                80f
            )

            if (fullPower <= startCooling) {
                fullPower = startCooling + 0.5f
            }

            refreshValueTexts()
        }

        binding.btnFullPowerMinus.setOnClickListener {
            fullPower = (fullPower - 0.5f).coerceAtLeast(
                startCooling + 0.5f
            )

            refreshValueTexts()
        }

        binding.btnFullPowerPlus.setOnClickListener {
            fullPower = (fullPower + 0.5f).coerceAtMost(
                90f
            )

            refreshValueTexts()
        }

        binding.btnMinimumPowerMinus.setOnClickListener {
            minimumPowerPercent = (minimumPowerPercent - 5).coerceAtLeast(
                0
            )

            if (minimumPowerPercent >= maximumPowerPercent) {
                minimumPowerPercent = maximumPowerPercent - 5
            }

            minimumPowerPercent = minimumPowerPercent.coerceAtLeast(
                0
            )

            refreshValueTexts()
        }

        binding.btnMinimumPowerPlus.setOnClickListener {
            minimumPowerPercent = (minimumPowerPercent + 5).coerceAtMost(
                95
            )

            if (minimumPowerPercent >= maximumPowerPercent) {
                maximumPowerPercent = (minimumPowerPercent + 5).coerceAtMost(
                    100
                )
            }

            refreshValueTexts()
        }

        binding.btnMaximumPowerMinus.setOnClickListener {
            maximumPowerPercent = (maximumPowerPercent - 5).coerceAtLeast(
                minimumPowerPercent + 5
            )

            refreshValueTexts()
        }

        binding.btnMaximumPowerPlus.setOnClickListener {
            maximumPowerPercent = (maximumPowerPercent + 5).coerceAtMost(
                100
            )

            refreshValueTexts()
        }
    }

    private fun bindActions() {
        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnSave.setOnClickListener {
            save()
        }
    }

    private fun refreshModeButtons() {
        updateModeButton(
            button = binding.btnModeAuto,
            mode = CoolingDeviceRepository.FanRegime.AUTO
        )

        updateModeButton(
            button = binding.btnModeOn,
            mode = CoolingDeviceRepository.FanRegime.ON
        )

        updateModeButton(
            button = binding.btnModeOff,
            mode = CoolingDeviceRepository.FanRegime.OFF
        )
    }

    private fun updateModeButton(
        button: MaterialButton,
        mode: CoolingDeviceRepository.FanRegime
    ) {
        val selected = selectedMode == mode

        val selectedColor = when (mode) {
            CoolingDeviceRepository.FanRegime.AUTO -> visualSpec.buttonColor
            CoolingDeviceRepository.FanRegime.ON -> Color.parseColor("#2EAE74")
            CoolingDeviceRepository.FanRegime.OFF -> Color.parseColor("#7A3344")
        }

        button.backgroundTintList = ColorStateList.valueOf(
            if (selected) {
                selectedColor
            } else {
                Color.parseColor("#14243A")
            }
        )

        button.setTextColor(
            if (selected) {
                Color.WHITE
            } else {
                Color.parseColor("#B8C5D8")
            }
        )

        button.strokeColor = ColorStateList.valueOf(
            if (selected) {
                selectedColor
            } else {
                Color.parseColor("#2A3E59")
            }
        )
    }

    private fun renderSensorOptions() {
        binding.sensorsContainer.removeAllViews()

        if (sensors.isEmpty()) {
            binding.sensorsContainer.addView(
                TextView(fragment.requireContext()).apply {
                    text = "No sensor found"
                    setTextColor(
                        Color.parseColor("#9FAABB")
                    )
                    textSize = 14f
                }
            )
            return
        }

        sensors.forEach { sensor ->
            binding.sensorsContainer.addView(
                createSensorOption(
                    context = fragment.requireContext(),
                    sensor = sensor
                )
            )
        }
    }

    private fun createSensorOption(
        context: Context,
        sensor: CoolingDeviceRepository.TemperatureSensorData
    ): View {
        val selected = selectedSensorIndexes.contains(
            sensor.index
        )

        val card = MaterialCardView(context).apply {
            setCardBackgroundColor(
                if (selected) {
                    visualSpec.accentDarkColor
                } else {
                    Color.parseColor("#101F33")
                }
            )

            strokeColor = if (selected) {
                visualSpec.accentColor
            } else {
                Color.parseColor("#243A57")
            }

            strokeWidth = 1.dp(context)
            radius = 16.dp(context).toFloat()
            cardElevation = 0f
            isClickable = true
            isFocusable = true
            foreground = selectableForeground(
                context = context
            )

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                48.dp(context)
            ).apply {
                bottomMargin = 8.dp(context)
            }

            setOnClickListener {
                if (selectedSensorIndexes.contains(sensor.index)) {
                    selectedSensorIndexes.remove(sensor.index)
                } else {
                    selectedSensorIndexes.add(sensor.index)
                }

                renderSensorOptions()
            }
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(
                14.dp(context),
                0,
                14.dp(context),
                0
            )
        }

        val name = TextView(context).apply {
            text = sensor.name
            setTextColor(
                Color.parseColor("#E8EEF7")
            )
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        }

        val check = TextView(context).apply {
            text = if (selected) {
                "✓"
            } else {
                ""
            }

            setTextColor(
                visualSpec.accentColor
            )
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
        }

        row.addView(
            name,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        row.addView(
            check,
            LinearLayout.LayoutParams(
                32.dp(context),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        card.addView(
            row
        )

        return card
    }

    private fun refreshValueTexts() {
        binding.tvStartCoolingValue.text = String.format(
            Locale.US,
            "%.1f °C",
            startCooling
        )

        binding.tvFullPowerValue.text = String.format(
            Locale.US,
            "%.1f °C",
            fullPower
        )

        binding.tvMinimumPowerValue.text = "$minimumPowerPercent%"
        binding.tvMaximumPowerValue.text = "$maximumPowerPercent%"
    }

    private fun save() {
        if (isSaving) {
            return
        }

        binding.tvSheetError.visibility = View.GONE

        val fanName = binding.etFanName.text
            ?.toString()
            ?.trim()
            .orEmpty()

        if (fanName.isBlank()) {
            showError(
                text = "Fan name cannot be empty."
            )
            return
        }

        if (fullPower <= startCooling) {
            showError(
                text = "Full Power must be higher than Start Cooling."
            )
            return
        }

        if (maximumPowerPercent <= minimumPowerPercent) {
            showError(
                text = "Maximum Power must be higher than Minimum Power."
            )
            return
        }

        if (
            selectedMode == CoolingDeviceRepository.FanRegime.AUTO &&
            selectedSensorIndexes.isEmpty()
        ) {
            showError(
                text = "Select at least one sensor for Auto mode."
            )
            return
        }

        setSaving(
            saving = true
        )

        onSave(
            CoolingFanSettingsDraft(
                fanIndex = fan.index,
                ruleIndex = rule?.index,
                fanName = fanName,
                fanMode = selectedMode,
                startCooling = startCooling,
                fullPower = fullPower,
                minimumPowerPercent = minimumPowerPercent,
                maximumPowerPercent = maximumPowerPercent,
                selectedSensorIndexes = selectedSensorIndexes.sorted()
            ),
            this
        )
    }

    private fun showError(
        text: String
    ) {
        binding.tvSheetError.text = text
        binding.tvSheetError.visibility = View.VISIBLE
    }

    fun showSaveError(
        message: String
    ) {
        setSaving(
            saving = false
        )

        showError(
            text = message
        )
    }

    fun closeAfterSave() {
        dialog.dismiss()
    }

    private fun setSaving(
        saving: Boolean
    ) {
        isSaving = saving

        dialog.setCancelable(
            !saving
        )

        dialog.setCanceledOnTouchOutside(
            !saving
        )

        setChildrenEnabled(
            view = binding.sheetRoot,
            enabled = !saving
        )

        binding.btnSave.isEnabled = !saving
        binding.btnCancel.isEnabled = !saving

        binding.btnSave.text = if (saving) {
            "Saving..."
        } else {
            "Save"
        }
    }

    private fun setChildrenEnabled(
        view: View,
        enabled: Boolean
    ) {
        view.isEnabled = enabled

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                setChildrenEnabled(
                    view = view.getChildAt(index),
                    enabled = enabled
                )
            }
        }
    }

    private fun modeId(
        mode: CoolingDeviceRepository.FanRegime
    ): Int {
        return when (mode) {
            CoolingDeviceRepository.FanRegime.AUTO -> R.id.btnModeAuto
            CoolingDeviceRepository.FanRegime.ON -> R.id.btnModeOn
            CoolingDeviceRepository.FanRegime.OFF -> R.id.btnModeOff
        }
    }

    private fun styleSmallRoundButton(
        button: MaterialButton
    ) {
        button.backgroundTintList = ColorStateList.valueOf(
            Color.parseColor("#14243A")
        )

        button.setTextColor(
            Color.parseColor("#E8EEF7")
        )

        button.strokeWidth = 1.dp(
            fragment.requireContext()
        )

        button.strokeColor = ColorStateList.valueOf(
            Color.parseColor("#2A3E59")
        )
    }

    private fun normalizePercent(
        value: Float
    ): Float {
        return if (value <= 1f) {
            value.coerceIn(
                0f,
                1f
            ) * 100f
        } else {
            value.coerceIn(
                0f,
                100f
            )
        }
    }

    private fun formatFanOutput(
        value: Float?
    ): String {
        if (value == null || value < 0f) {
            return "--"
        }

        return "${normalizePercent(value).roundToInt()}%"
    }

    private fun roundedDrawable(
        color: Int,
        radiusDp: Int,
        context: Context
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(
                color
            )

            cornerRadius = radiusDp.dp(
                context = context
            ).toFloat()
        }
    }

    private fun selectableForeground(
        context: Context
    ): Drawable? {
        val typedValue = TypedValue()

        context.theme.resolveAttribute(
            android.R.attr.selectableItemBackground,
            typedValue,
            true
        )

        return ContextCompat.getDrawable(
            context,
            typedValue.resourceId
        )
    }

    private fun Int.dp(
        context: Context
    ): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }
}