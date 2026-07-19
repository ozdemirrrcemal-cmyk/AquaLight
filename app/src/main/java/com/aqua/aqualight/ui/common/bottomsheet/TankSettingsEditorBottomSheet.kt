package com.aqua.aqualight.ui.common.bottomsheet

import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import com.aqua.aqualight.R
import com.aqua.aqualight.localization.LocaleFormatters
import com.aqua.aqualight.databinding.ContentSheetIdeaBinding
import com.aqua.aqualight.databinding.ContentSheetSetupDateBinding
import com.aqua.aqualight.databinding.ContentSheetTankNameBinding
import com.aqua.aqualight.databinding.ContentSheetTankSizeBinding
import com.aqua.aqualight.databinding.ContentSheetTankStyleBinding
import com.aqua.aqualight.databinding.ContentSheetTankTypeBinding
import com.aqua.aqualight.databinding.DialogSettingsBottomSheetBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.text.DateFormatSymbols
import java.util.Calendar
import kotlin.math.roundToInt

/**
 * Re-creatable editor sheet for the basic tank settings forms.
 *
 * Every input required to rebuild the sheet is stored in arguments. User actions are returned
 * through Fragment Result; the sheet never retains a Fragment, Context or callback lambda.
 */
class TankSettingsEditorBottomSheet : BottomSheetDialogFragment() {

    private var _sheetBinding: DialogSettingsBottomSheetBinding? = null
    private val sheetBinding get() = _sheetBinding!!

    private var resultSent = false
    private var selectedChoice: String = ""
    private var selectedUnit: String = UNIT_CM

    private val mode: Mode
        get() = Mode.valueOf(requireArguments().getString(ARG_MODE).orEmpty())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedChoice = savedInstanceState?.getString(STATE_SELECTED_CHOICE)
            ?: requireArguments().getString(ARG_CURRENT_TEXT).orEmpty()
        selectedUnit = savedInstanceState?.getString(STATE_SELECTED_UNIT)
            ?: normalizeUnit(requireArguments().getString(ARG_CURRENT_UNIT))
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _sheetBinding = DialogSettingsBottomSheetBinding.inflate(inflater, container, false)
        return sheetBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sheetBinding.tvSheetTitle.text = requireArguments().getString(ARG_TITLE).orEmpty()
        sheetBinding.sheetContentContainer.removeAllViews()

        when (mode) {
            Mode.NAME -> bindNameEditor()
            Mode.TYPE -> bindTypeEditor()
            Mode.SIZE -> bindSizeEditor()
            Mode.SETUP_DATE -> bindSetupDateEditor()
            Mode.STYLE -> bindStyleEditor()
            Mode.IDEA -> bindIdeaEditor()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_SELECTED_CHOICE, selectedChoice)
        outState.putString(STATE_SELECTED_UNIT, selectedUnit)
        super.onSaveInstanceState(outState)
    }

    override fun onCancel(dialog: DialogInterface) {
        publishResult(status = RESULT_CANCELLED)
        super.onCancel(dialog)
    }

    override fun onDestroyView() {
        _sheetBinding = null
        super.onDestroyView()
    }

    private fun bindNameEditor() {
        val binding = ContentSheetTankNameBinding.inflate(layoutInflater)
        binding.inputTankName.setText(requireArguments().getString(ARG_CURRENT_TEXT).orEmpty())
        binding.btnCancel.setOnClickListener { cancelAndDismiss() }
        binding.btnSave.setOnClickListener {
            val value = binding.inputTankName.text.toString().trim()
            if (value.length < MIN_TANK_NAME_LENGTH) {
                binding.inputTankName.error = requireArguments()
                    .getString(ARG_VALIDATION_MESSAGE)
                    .orEmpty()
                return@setOnClickListener
            }
            publishResult(status = RESULT_SAVED, textValue = value)
            dismiss()
        }
        attachContent(binding.root)
    }

    private fun bindTypeEditor() {
        val binding = ContentSheetTankTypeBinding.inflate(layoutInflater)
        selectedChoice = selectedChoice.ifBlank {
            getString(R.string.aquarium_tank_type_fish)
        }
        val options = listOf(
            binding.optionFish to getString(R.string.aquarium_tank_type_fish),
            binding.optionShrimp to getString(R.string.aquarium_tank_type_shrimp),
            binding.optionPlanted to getString(R.string.aquarium_tank_type_planted),
            binding.optionMarine to getString(R.string.aquarium_tank_type_marine),
            binding.optionSofties to getString(R.string.aquarium_tank_type_softies),
            binding.optionMixedReef to getString(R.string.aquarium_tank_type_mixed_reef),
            binding.optionSps to getString(R.string.aquarium_tank_type_sps),
            binding.optionCoral to getString(R.string.aquarium_tank_type_coral),
            binding.optionOther to getString(R.string.aquarium_tank_type_other)
        )
        bindChoiceOptions(options)
        binding.btnCancel.setOnClickListener { cancelAndDismiss() }
        binding.btnSave.setOnClickListener {
            publishResult(status = RESULT_SAVED, textValue = selectedChoice)
            dismiss()
        }
        attachContent(binding.root)
    }

    private fun bindSizeEditor() {
        val binding = ContentSheetTankSizeBinding.inflate(layoutInflater)
        val locale = LocaleFormatters.currentLocale(requireContext())

        fun unitLabel(): String = getString(
            if (selectedUnit == UNIT_IN) {
                R.string.aquarium_unit_inches
            } else {
                R.string.aquarium_unit_centimeters
            }
        )

        fun formatValue(cmValue: Int): String {
            val value = if (selectedUnit == UNIT_IN) cmValue / CM_PER_INCH else cmValue.toDouble()
            return LocaleFormatters.formatNumber(value, locale)
        }

        fun renderUnit() {
            binding.tvUnitValue.text = unitLabel()
        }

        binding.inputWidth.setText(formatValue(requireArguments().getInt(ARG_WIDTH_CM)))
        binding.inputLength.setText(formatValue(requireArguments().getInt(ARG_LENGTH_CM)))
        binding.inputHeight.setText(formatValue(requireArguments().getInt(ARG_HEIGHT_CM)))
        renderUnit()

        binding.unitRow.setOnClickListener {
            selectedUnit = if (selectedUnit == UNIT_IN) UNIT_CM else UNIT_IN
            renderUnit()
        }
        binding.btnCancel.setOnClickListener { cancelAndDismiss() }
        binding.btnSave.setOnClickListener {
            val width = LocaleFormatters.parseNumber(binding.inputWidth.text, locale)?.toDouble()
            val length = LocaleFormatters.parseNumber(binding.inputLength.text, locale)?.toDouble()
            val height = LocaleFormatters.parseNumber(binding.inputHeight.text, locale)?.toDouble()
            val validationMessage = requireArguments().getString(ARG_VALIDATION_MESSAGE).orEmpty()

            var invalid = false
            if (width == null || width <= 0.0) {
                binding.inputWidth.error = validationMessage
                invalid = true
            }
            if (length == null || length <= 0.0) {
                binding.inputLength.error = validationMessage
                invalid = true
            }
            if (height == null || height <= 0.0) {
                binding.inputHeight.error = validationMessage
                invalid = true
            }
            if (invalid) return@setOnClickListener

            publishResult(
                status = RESULT_SAVED,
                widthCm = toCentimeters(width!!),
                lengthCm = toCentimeters(length!!),
                heightCm = toCentimeters(height!!),
                unit = selectedUnit
            )
            dismiss()
        }
        attachContent(binding.root)
    }

    private fun bindSetupDateEditor() {
        val binding = ContentSheetSetupDateBinding.inflate(layoutInflater)
        val args = requireArguments()
        val minYear = args.getInt(ARG_MIN_YEAR)
        val maxYear = args.getInt(ARG_MAX_YEAR)
        val locale = LocaleFormatters.currentLocale(requireContext())
        val calendar = Calendar.getInstance().apply {
            val currentMillis = args.getLong(ARG_CURRENT_MILLIS, NO_DATE)
            if (currentMillis != NO_DATE) timeInMillis = currentMillis
        }
        val monthNames = Array(MONTH_COUNT) { index ->
            DateFormatSymbols(locale).months[index].replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(locale) else char.toString()
            }
        }

        binding.dayPicker.apply {
            wrapSelectorWheel = false
            minValue = 1
            maxValue = 31
            value = calendar.get(Calendar.DAY_OF_MONTH)
        }
        binding.monthPicker.apply {
            wrapSelectorWheel = false
            minValue = 0
            maxValue = MONTH_COUNT - 1
            displayedValues = monthNames
            value = calendar.get(Calendar.MONTH)
        }
        binding.yearPicker.apply {
            wrapSelectorWheel = false
            this.minValue = minYear
            this.maxValue = maxYear
            value = calendar.get(Calendar.YEAR).coerceIn(minYear, maxYear)
        }

        fun updateDayMaximum() {
            val maxDay = Calendar.getInstance().apply {
                set(Calendar.YEAR, binding.yearPicker.value)
                set(Calendar.MONTH, binding.monthPicker.value)
                set(Calendar.DAY_OF_MONTH, 1)
            }.getActualMaximum(Calendar.DAY_OF_MONTH)
            binding.dayPicker.maxValue = maxDay
            if (binding.dayPicker.value > maxDay) binding.dayPicker.value = maxDay
        }

        binding.monthPicker.setOnValueChangedListener { _, _, _ -> updateDayMaximum() }
        binding.yearPicker.setOnValueChangedListener { _, _, _ -> updateDayMaximum() }
        updateDayMaximum()

        binding.btnCancel.setOnClickListener { cancelAndDismiss() }
        binding.btnSave.setOnClickListener {
            val selectedMillis = Calendar.getInstance().apply {
                set(Calendar.YEAR, binding.yearPicker.value)
                set(Calendar.MONTH, binding.monthPicker.value)
                set(Calendar.DAY_OF_MONTH, binding.dayPicker.value)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            publishResult(status = RESULT_SAVED, millisValue = selectedMillis)
            dismiss()
        }
        attachContent(binding.root)
    }

    private fun bindStyleEditor() {
        val binding = ContentSheetTankStyleBinding.inflate(layoutInflater)
        selectedChoice = selectedChoice.ifBlank {
            getString(R.string.aquarium_text_nature_aquarium)
        }
        binding.inputStyle.setText(selectedChoice)
        val options = listOf(
            binding.optionNatureAquarium to getString(R.string.aquarium_text_nature_aquarium),
            binding.optionIwagumi to getString(R.string.aquarium_style_iwagumi),
            binding.optionDutch to getString(R.string.aquarium_style_dutch),
            binding.optionJungle to getString(R.string.aquarium_style_jungle),
            binding.optionBiotope to getString(R.string.aquarium_style_biotope),
            binding.optionBlackwater to getString(R.string.aquarium_style_blackwater),
            binding.optionForest to getString(R.string.aquarium_style_forest),
            binding.optionMountain to getString(R.string.aquarium_style_mountain),
            binding.optionIsland to getString(R.string.aquarium_style_island)
        )

        fun renderSelection() {
            selectedChoice = binding.inputStyle.text.toString().trim()
            renderChoiceOptions(options)
        }

        options.forEach { (view, value) ->
            view.setOnClickListener {
                selectedChoice = value
                binding.inputStyle.setText(value)
                binding.inputStyle.setSelection(value.length)
                renderSelection()
            }
        }
        binding.inputStyle.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderSelection()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        renderSelection()

        binding.btnCancel.setOnClickListener { cancelAndDismiss() }
        binding.btnSave.setOnClickListener {
            val value = binding.inputStyle.text.toString().trim()
            if (value.isBlank()) {
                binding.inputStyle.error = requireArguments()
                    .getString(ARG_VALIDATION_MESSAGE)
                    .orEmpty()
                return@setOnClickListener
            }
            publishResult(status = RESULT_SAVED, textValue = value)
            dismiss()
        }
        attachContent(binding.root)
    }

    private fun bindIdeaEditor() {
        val binding = ContentSheetIdeaBinding.inflate(layoutInflater)
        binding.inputIdea.setText(requireArguments().getString(ARG_CURRENT_TEXT).orEmpty())
        binding.btnCancel.setOnClickListener { cancelAndDismiss() }
        binding.btnSave.setOnClickListener {
            publishResult(
                status = RESULT_SAVED,
                textValue = binding.inputIdea.text.toString().trim()
            )
            dismiss()
        }
        attachContent(binding.root)
    }

    private fun bindChoiceOptions(options: List<Pair<TextView, String>>) {
        options.forEach { (view, value) ->
            view.setOnClickListener {
                selectedChoice = value
                renderChoiceOptions(options)
            }
        }
        renderChoiceOptions(options)
    }

    private fun renderChoiceOptions(options: List<Pair<TextView, String>>) {
        options.forEach { (view, value) ->
            val selected = value.equals(selectedChoice, ignoreCase = true)
            view.isSelected = selected
            view.setTypeface(
                null,
                if (selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
            )
            view.setBackgroundResource(R.drawable.bg_aqua_selection_row_compact)
        }
    }

    private fun attachContent(content: View) {
        sheetBinding.sheetContentContainer.removeAllViews()
        sheetBinding.sheetContentContainer.addView(content)
    }

    private fun cancelAndDismiss() {
        publishResult(status = RESULT_CANCELLED)
        dismiss()
    }

    private fun toCentimeters(value: Double): Int {
        val centimeters = if (selectedUnit == UNIT_IN) value * CM_PER_INCH else value
        return centimeters.roundToInt().coerceAtLeast(1)
    }

    private fun publishResult(
        status: String,
        textValue: String? = null,
        millisValue: Long? = null,
        widthCm: Int? = null,
        lengthCm: Int? = null,
        heightCm: Int? = null,
        unit: String? = null
    ) {
        if (resultSent) return
        resultSent = true
        val result = bundleOf(
            RESULT_STATUS to status,
            RESULT_MODE to mode.name
        )
        textValue?.let { result.putString(RESULT_TEXT, it) }
        millisValue?.let { result.putLong(RESULT_MILLIS, it) }
        widthCm?.let { result.putInt(RESULT_WIDTH_CM, it) }
        lengthCm?.let { result.putInt(RESULT_LENGTH_CM, it) }
        heightCm?.let { result.putInt(RESULT_HEIGHT_CM, it) }
        unit?.let { result.putString(RESULT_UNIT, it) }
        parentFragmentManager.setFragmentResult(REQUEST_KEY, result)
    }

    enum class Mode {
        NAME,
        TYPE,
        SIZE,
        SETUP_DATE,
        STYLE,
        IDEA
    }

    companion object {
        const val REQUEST_KEY = "tank_settings_editor_result"
        const val RESULT_STATUS = "tank_settings_editor_status"
        const val RESULT_MODE = "tank_settings_editor_mode"
        const val RESULT_TEXT = "tank_settings_editor_text"
        const val RESULT_MILLIS = "tank_settings_editor_millis"
        const val RESULT_WIDTH_CM = "tank_settings_editor_width_cm"
        const val RESULT_LENGTH_CM = "tank_settings_editor_length_cm"
        const val RESULT_HEIGHT_CM = "tank_settings_editor_height_cm"
        const val RESULT_UNIT = "tank_settings_editor_unit"
        const val RESULT_SAVED = "saved"
        const val RESULT_CANCELLED = "cancelled"

        private const val ARG_MODE = "arg_mode"
        private const val ARG_TITLE = "arg_title"
        private const val ARG_CURRENT_TEXT = "arg_current_text"
        private const val ARG_VALIDATION_MESSAGE = "arg_validation_message"
        private const val ARG_CURRENT_MILLIS = "arg_current_millis"
        private const val ARG_MIN_YEAR = "arg_min_year"
        private const val ARG_MAX_YEAR = "arg_max_year"
        private const val ARG_WIDTH_CM = "arg_width_cm"
        private const val ARG_LENGTH_CM = "arg_length_cm"
        private const val ARG_HEIGHT_CM = "arg_height_cm"
        private const val ARG_CURRENT_UNIT = "arg_current_unit"

        private const val STATE_SELECTED_CHOICE = "state_selected_choice"
        private const val STATE_SELECTED_UNIT = "state_selected_unit"
        private const val TAG_PREFIX = "TankSettingsEditorBottomSheet:"
        private const val MIN_TANK_NAME_LENGTH = 2
        private const val MONTH_COUNT = 12
        private const val NO_DATE = Long.MIN_VALUE
        private const val CM_PER_INCH = 2.54
        private const val UNIT_CM = "cm"
        private const val UNIT_IN = "in"

        fun show(
            fragmentManager: FragmentManager,
            mode: Mode,
            title: String,
            currentText: String = "",
            validationMessage: String = "",
            currentMillis: Long? = null,
            minYear: Int = 0,
            maxYear: Int = 0,
            widthCm: Int = 0,
            lengthCm: Int = 0,
            heightCm: Int = 0,
            currentUnit: String = UNIT_CM
        ) {
            val tag = TAG_PREFIX + mode.name
            if (fragmentManager.findFragmentByTag(tag) != null) return
            TankSettingsEditorBottomSheet().apply {
                arguments = bundleOf(
                    ARG_MODE to mode.name,
                    ARG_TITLE to title,
                    ARG_CURRENT_TEXT to currentText,
                    ARG_VALIDATION_MESSAGE to validationMessage,
                    ARG_CURRENT_MILLIS to (currentMillis ?: NO_DATE),
                    ARG_MIN_YEAR to minYear,
                    ARG_MAX_YEAR to maxYear,
                    ARG_WIDTH_CM to widthCm,
                    ARG_LENGTH_CM to lengthCm,
                    ARG_HEIGHT_CM to heightCm,
                    ARG_CURRENT_UNIT to normalizeUnit(currentUnit)
                )
            }.show(fragmentManager, tag)
        }

        private fun normalizeUnit(unit: String?): String {
            return if (unit.equals(UNIT_IN, ignoreCase = true)) UNIT_IN else UNIT_CM
        }
    }
}
