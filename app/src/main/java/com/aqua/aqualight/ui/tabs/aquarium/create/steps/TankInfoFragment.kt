package com.aqua.aqualight.ui.tabs.aquarium.create.steps

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.ContentSheetSetupDateBinding
import com.aqua.aqualight.databinding.ContentSheetTankSizeBinding
import com.aqua.aqualight.databinding.ContentSheetTankStyleBinding
import com.aqua.aqualight.databinding.ContentSheetTankTypeBinding
import com.aqua.aqualight.databinding.DialogSettingsBottomSheetBinding
import com.aqua.aqualight.databinding.FragmentTankInfoBinding
import com.aqua.aqualight.ui.tabs.aquarium.create.CreateTankViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TankInfoFragment : Fragment(R.layout.fragment_tank_info), TankStepFragment {

  private var _binding: FragmentTankInfoBinding? = null
  private val binding get() = _binding!!

  private val viewModel: CreateTankViewModel by viewModels(
    ownerProducer = { requireParentFragment() }
  )

  private val volumeFormatter = DecimalFormat("#.##")

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?
  ) {
    _binding = FragmentTankInfoBinding.bind(view)

    setupClickListeners()
    renderDetails()
  }

  private fun setupClickListeners() {
    binding.rowSetupDate.setOnClickListener {
      showSetupDateSheet()
    }

    binding.rowSize.setOnClickListener {
      showSizeSheet()
    }

    binding.rowVolume.setOnClickListener {
      toggleVolumeUnit()
    }

    binding.rowTankType.setOnClickListener {
      showTankTypeSheet()
    }

    binding.rowStyle.setOnClickListener {
      showStyleSheet()
    }
  }

  private fun renderDetails() {
    val draft = viewModel.tankDraft

    binding.tvSetupDateValue.text = formatSetupDate(draft.setupDateMillis)
    binding.tvSetupDateValue.setTextColor(
      if (draft.setupDateMillis == null) {
        Color.parseColor("#7F91AA")
      } else {
        Color.WHITE
      }
    )

    binding.tvSizeValue.text =
      "${draft.widthCm} W × ${draft.lengthCm} L × ${draft.heightCm} H"

    binding.tvVolumeValue.text = formatVolume()
    binding.tvTankTypeValue.text = draft.tankType

    if (draft.tankStyle.isBlank()) {
      binding.tvStyleValue.text = "Not selected"
      binding.tvStyleValue.setTextColor(Color.parseColor("#7F91AA"))
    } else {
      binding.tvStyleValue.text = draft.tankStyle
      binding.tvStyleValue.setTextColor(Color.WHITE)
    }
  }

  private fun showSettingsBottomSheet(
    title: String,
    contentView: View,
    onDialogReady: ((BottomSheetDialog) -> Unit)? = null
  ) {
    val dialog = BottomSheetDialog(requireContext())
    val sheetBinding = DialogSettingsBottomSheetBinding.inflate(layoutInflater)

    sheetBinding.tvSheetTitle.text = title

    sheetBinding.sheetContentContainer.removeAllViews()
    sheetBinding.sheetContentContainer.addView(contentView)

    dialog.setContentView(sheetBinding.root)

    dialog.setOnShowListener {
      val bottomSheet = dialog.findViewById<FrameLayout>(
        com.google.android.material.R.id.design_bottom_sheet
      )

      bottomSheet?.setBackgroundColor(Color.TRANSPARENT)
    }

    onDialogReady?.invoke(dialog)

    dialog.show()
  }

  private fun showSetupDateSheet() {
    val contentBinding = ContentSheetSetupDateBinding.inflate(
      layoutInflater
    )

    val calendar = Calendar.getInstance().apply {
      viewModel.tankDraft.setupDateMillis?.let {
        timeInMillis = it
      }
    }

    val monthNames = arrayOf(
      "January",
      "February",
      "March",
      "April",
      "May",
      "June",
      "July",
      "August",
      "September",
      "October",
      "November",
      "December"
    )

    contentBinding.dayPicker.apply {
      wrapSelectorWheel = false
      minValue = 1
      maxValue = 31
      value = calendar.get(Calendar.DAY_OF_MONTH)
    }

    contentBinding.monthPicker.apply {
      wrapSelectorWheel = false
      minValue = 0
      maxValue = 11
      displayedValues = monthNames
      value = calendar.get(Calendar.MONTH)
    }

    contentBinding.yearPicker.apply {
      wrapSelectorWheel = false

      val currentYear = Calendar.getInstance().get(Calendar.YEAR)

      minValue = 2000
      maxValue = currentYear + 10
      value = calendar.get(Calendar.YEAR)
    }

    fun updateDayMax() {
      val tempCalendar = Calendar.getInstance().apply {
        set(
          Calendar.YEAR,
          contentBinding.yearPicker.value
        )
        set(
          Calendar.MONTH,
          contentBinding.monthPicker.value
        )
        set(
          Calendar.DAY_OF_MONTH,
          1
        )
      }

      val maxDay = tempCalendar.getActualMaximum(
        Calendar.DAY_OF_MONTH
      )

      contentBinding.dayPicker.maxValue = maxDay

      if (contentBinding.dayPicker.value > maxDay) {
        contentBinding.dayPicker.value = maxDay
      }
    }

    contentBinding.monthPicker.setOnValueChangedListener {
      _, _, _ ->
      updateDayMax()
    }

    contentBinding.yearPicker.setOnValueChangedListener {
      _, _, _ ->
      updateDayMax()
    }

    updateDayMax()

    showSettingsBottomSheet(
      title = "Setup Date",
      contentView = contentBinding.root
    ) {
      dialog ->

      contentBinding.btnCancel.setOnClickListener {
        dialog.dismiss()
      }

      contentBinding.btnSave.setOnClickListener {
        val selectedCalendar = Calendar.getInstance().apply {
          set(
            Calendar.YEAR,
            contentBinding.yearPicker.value
          )
          set(
            Calendar.MONTH,
            contentBinding.monthPicker.value
          )
          set(
            Calendar.DAY_OF_MONTH,
            contentBinding.dayPicker.value
          )
          set(
            Calendar.HOUR_OF_DAY,
            0
          )
          set(
            Calendar.MINUTE,
            0
          )
          set(
            Calendar.SECOND,
            0
          )
          set(
            Calendar.MILLISECOND,
            0
          )
        }

        viewModel.updateSetupDate(
          selectedCalendar.timeInMillis
        )

        renderDetails()
        dialog.dismiss()
      }
    }
  }

  private fun showSizeSheet() {
    val contentBinding = ContentSheetTankSizeBinding.inflate(
      layoutInflater
    )

    val draft = viewModel.tankDraft

    contentBinding.inputWidth.setText(draft.widthCm.toString())
    contentBinding.inputLength.setText(draft.lengthCm.toString())
    contentBinding.inputHeight.setText(draft.heightCm.toString())

    showSettingsBottomSheet(
      title = "Tank Size",
      contentView = contentBinding.root
    ) {
      dialog ->

      contentBinding.btnCancel.setOnClickListener {
        dialog.dismiss()
      }

      contentBinding.btnSave.setOnClickListener {
        val width = contentBinding.inputWidth.text
          .toString()
          .toIntOrNull()

        val length = contentBinding.inputLength.text
          .toString()
          .toIntOrNull()

        val height = contentBinding.inputHeight.text
          .toString()
          .toIntOrNull()

        if (
          width == null ||
          length == null ||
          height == null ||
          width <= 0 ||
          length <= 0 ||
          height <= 0
        ) {
          if (width == null || width <= 0) {
            contentBinding.inputWidth.error = "Required"
          }

          if (length == null || length <= 0) {
            contentBinding.inputLength.error = "Required"
          }

          if (height == null || height <= 0) {
            contentBinding.inputHeight.error = "Required"
          }

          return@setOnClickListener
        }

        viewModel.updateTankSize(
          widthCm = width,
          lengthCm = length,
          heightCm = height
        )

        renderDetails()
        dialog.dismiss()
      }
    }
  }

  private fun showTankTypeSheet() {
    val contentBinding = ContentSheetTankTypeBinding.inflate(
      layoutInflater
    )

    var selectedType = viewModel.tankDraft.tankType.ifBlank {
      "Fish"
    }

    val options = listOf(
      contentBinding.optionFish to "Fish",
      contentBinding.optionShrimp to "Shrimp",
      contentBinding.optionPlanted to "Planted",
      contentBinding.optionMarine to "Marine",
      contentBinding.optionSofties to "Softies",
      contentBinding.optionMixedReef to "Mixed Reef",
      contentBinding.optionSps to "SPS",
      contentBinding.optionCoral to "Coral",
      contentBinding.optionOther to "Other"
    )

    fun renderSelection() {
      options.forEach {
        option ->
        val view = option.first
        val value = option.second

        val selected = value.equals(
          selectedType,
          ignoreCase = true
        )

        view.setTypeface(
          null,
          if (selected) {
            Typeface.BOLD
          } else {
            Typeface.NORMAL
          }
        )

        view.setBackgroundResource(
          if (selected) {
            R.drawable.bg_settings_sheet_grid_option_selected
          } else {
            R.drawable.bg_settings_sheet_grid_option
          }
        )
      }
    }

    options.forEach {
      option ->
      val view = option.first
      val value = option.second

      view.setOnClickListener {
        selectedType = value
        renderSelection()
      }
    }

    renderSelection()

    showSettingsBottomSheet(
      title = "Tank Type",
      contentView = contentBinding.root
    ) {
      dialog ->

      contentBinding.btnCancel.setOnClickListener {
        dialog.dismiss()
      }

      contentBinding.btnSave.setOnClickListener {
        viewModel.updateTankType(selectedType)
        renderDetails()
        dialog.dismiss()
      }
    }
  }

  private fun showStyleSheet() {
    val contentBinding = ContentSheetTankStyleBinding.inflate(
      layoutInflater
    )

    val currentStyle = viewModel.tankDraft.tankStyle.ifBlank {
      "Nature Aquarium"
    }

    contentBinding.inputStyle.setText(currentStyle)

    val options = listOf(
      contentBinding.optionNatureAquarium to "Nature Aquarium",
      contentBinding.optionIwagumi to "Iwagumi",
      contentBinding.optionDutch to "Dutch",
      contentBinding.optionJungle to "Jungle",
      contentBinding.optionBiotope to "Biotope",
      contentBinding.optionBlackwater to "Blackwater",
      contentBinding.optionForest to "Forest",
      contentBinding.optionMountain to "Mountain",
      contentBinding.optionIsland to "Island"
    )

    fun renderSelection() {
      val inputValue = contentBinding.inputStyle.text
        .toString()
        .trim()

      options.forEach {
        option ->
        val view = option.first
        val value = option.second

        val selected = value.equals(
          inputValue,
          ignoreCase = true
        )

        view.setTypeface(
          null,
          if (selected) {
            Typeface.BOLD
          } else {
            Typeface.NORMAL
          }
        )

        view.setBackgroundResource(
          if (selected) {
            R.drawable.bg_settings_sheet_grid_option_selected
          } else {
            R.drawable.bg_settings_sheet_grid_option
          }
        )
      }
    }

    options.forEach {
      option ->
      val view = option.first
      val value = option.second

      view.setOnClickListener {
        contentBinding.inputStyle.setText(value)
        contentBinding.inputStyle.setSelection(
          contentBinding.inputStyle.text?.length ?: 0
        )

        renderSelection()
      }
    }

    contentBinding.inputStyle.addTextChangedListener(
      object : android.text.TextWatcher {
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
          renderSelection()
        }

        override fun afterTextChanged(
          s: android.text.Editable?
        ) = Unit
      }
    )

    renderSelection()

    showSettingsBottomSheet(
      title = "Style",
      contentView = contentBinding.root
    ) {
      dialog ->

      contentBinding.btnCancel.setOnClickListener {
        dialog.dismiss()
      }

      contentBinding.btnSave.setOnClickListener {
        val newStyle = contentBinding.inputStyle.text
          .toString()
          .trim()

        if (newStyle.isBlank()) {
          contentBinding.inputStyle.error = "Required"
          return@setOnClickListener
        }

        viewModel.updateTankStyle(newStyle)
        renderDetails()
        dialog.dismiss()
      }
    }
  }

  private fun toggleVolumeUnit() {
    val currentUnit = viewModel.tankDraft.volumeUnit

    val newUnit = if (currentUnit == "L") {
      "gal"
    } else {
      "L"
    }

    viewModel.updateVolumeUnit(newUnit)
    renderDetails()
  }

  private fun formatSetupDate(
    millis: Long?
  ): String {
    if (millis == null) {
      return "Not selected"
    }

    return SimpleDateFormat(
      "dd MMM yyyy",
      Locale.ENGLISH
    ).format(Date(millis))
  }

  private fun formatVolume(): String {
    val draft = viewModel.tankDraft

    val liters = (
      draft.widthCm *
        draft.lengthCm *
        draft.heightCm
      ) / 1000.0

    return if (draft.volumeUnit == "gal") {
      "${volumeFormatter.format(liters * 0.264172)} gal"
    } else {
      "${volumeFormatter.format(liters)} L"
    }
  }

  override fun validateAndSave(): Boolean {
    return true
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}