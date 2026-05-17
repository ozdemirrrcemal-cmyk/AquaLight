package com.aqua.aqualight.ui.tabs.aquarium.create.steps

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTankInfoBinding
import com.aqua.aqualight.ui.tabs.aquarium.create.CreateTankViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.aqua.aqualight.ui.tabs.aquarium.common.TankStyleBottomSheet
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
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

override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
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

  val setupDateText = formatSetupDate(draft.setupDateMillis)
  binding.tvSetupDateValue.text = setupDateText
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

private fun showSetupDateSheet() {
  val dialog = BottomSheetDialog(requireContext())
  val root = createSheetRoot()

  addSheetHeader(
    root = root,
    title = "Setup Date",
    dialog = dialog
  )

  val calendar = Calendar.getInstance()

  viewModel.tankDraft.setupDateMillis?.let {
    calendar.timeInMillis = it
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

  val pickerRow = LinearLayout(requireContext()).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER
    setPadding(0, 22.dp(), 0, 18.dp())
  }

  val dayPicker = createNumberPicker().apply {
    minValue = 1
    maxValue = 31
    value = calendar.get(Calendar.DAY_OF_MONTH)
  }

  val monthPicker = createNumberPicker().apply {
    minValue = 0
    maxValue = 11
    displayedValues = monthNames
    value = calendar.get(Calendar.MONTH)
  }

  val yearPicker = createNumberPicker().apply {
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    minValue = 2000
    maxValue = currentYear + 10
    value = calendar.get(Calendar.YEAR)
  }

  fun updateDayMax() {
    val tempCalendar = Calendar.getInstance().apply {
      set(Calendar.YEAR, yearPicker.value)
      set(Calendar.MONTH, monthPicker.value)
      set(Calendar.DAY_OF_MONTH, 1)
    }

    val maxDay = tempCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    dayPicker.maxValue = maxDay

    if (dayPicker.value > maxDay) {
      dayPicker.value = maxDay
    }
  }

  monthPicker.setOnValueChangedListener {
    _, _, _ ->
    updateDayMax()
  }

  yearPicker.setOnValueChangedListener {
    _, _, _ ->
    updateDayMax()
  }

  updateDayMax()

  pickerRow.addView(
    dayPicker,
    LinearLayout.LayoutParams(
      0,
      150.dp(),
      1f
    )
  )

  pickerRow.addView(
    monthPicker,
    LinearLayout.LayoutParams(
      0,
      150.dp(),
      1.4f
    )
  )

  pickerRow.addView(
    yearPicker,
    LinearLayout.LayoutParams(
      0,
      150.dp(),
      1f
    )
  )

  root.addView(pickerRow)

  addPrimaryButton(
    root = root,
    text = "Save"
  ) {
    val selectedCalendar = Calendar.getInstance().apply {
      set(Calendar.YEAR, yearPicker.value)
      set(Calendar.MONTH, monthPicker.value)
      set(Calendar.DAY_OF_MONTH, dayPicker.value)
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }

    viewModel.updateSetupDate(selectedCalendar.timeInMillis)
    renderDetails()
    dialog.dismiss()
  }

  addCancelButton(root, dialog)

  showSheet(dialog, root)
}

private fun showSizeSheet() {
  val dialog = BottomSheetDialog(requireContext())
  val root = createSheetRoot()

  addSheetHeader(
    root = root,
    title = "Size",
    dialog = dialog
  )

  val draft = viewModel.tankDraft

  val infoCard = createInfoCard(
    leftText = "Unit",
    rightText = "centimeters"
  )

  root.addView(infoCard)

  val inputRow = LinearLayout(requireContext()).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL

    val params = LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      LinearLayout.LayoutParams.WRAP_CONTENT
    )
    params.topMargin = 18.dp()
    layoutParams = params
  }

  val widthInput = createNumberInputColumn(
    title = "Width",
    value = draft.widthCm.toString()
  )

  val lengthInput = createNumberInputColumn(
    title = "Length",
    value = draft.lengthCm.toString()
  )

  val heightInput = createNumberInputColumn(
    title = "Height",
    value = draft.heightCm.toString()
  )

  inputRow.addView(widthInput.first, weightedColumnParams())
  inputRow.addView(lengthInput.first, weightedColumnParamsWithMargin())
  inputRow.addView(heightInput.first, weightedColumnParamsWithMargin())

  root.addView(inputRow)

  addPrimaryButton(
    root = root,
    text = "Save"
  ) {
    val width = widthInput.second.text.toString().toIntOrNull()
    val length = lengthInput.second.text.toString().toIntOrNull()
    val height = heightInput.second.text.toString().toIntOrNull()

    if (width == null || width <= 0) {
      widthInput.second.error = "Required"
      return@addPrimaryButton
    }

    if (length == null || length <= 0) {
      lengthInput.second.error = "Required"
      return@addPrimaryButton
    }

    if (height == null || height <= 0) {
      heightInput.second.error = "Required"
      return@addPrimaryButton
    }

    viewModel.updateTankSize(
      widthCm = width,
      lengthCm = length,
      heightCm = height
    )

    renderDetails()
    dialog.dismiss()
  }

  addCancelButton(root, dialog)

  showSheet(dialog, root)
}

private fun showTankTypeSheet() {
  val dialog = BottomSheetDialog(requireContext())
  val root = createSheetRoot()

  addSheetHeader(
    root = root,
    title = "Tank Type",
    dialog = dialog
  )

  val options = listOf(
    "Fish",
    "Shrimp",
    "Planted",
    "Marine",
    "Softies",
    "Mixed Reef",
    "SPS",
    "Coral",
    "Other"
  )

  var selectedType = viewModel.tankDraft.tankType

  val optionViews = mutableListOf<Pair<String, MaterialCardView>>()

  val gridContainer = LinearLayout(requireContext()).apply {
    orientation = LinearLayout.VERTICAL

    val params = LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      LinearLayout.LayoutParams.WRAP_CONTENT
    )
    params.topMargin = 18.dp()
    layoutParams = params
  }

  fun refreshOptions() {
    optionViews.forEach {
      pair ->
      val isSelected = pair.first == selectedType
      pair.second.strokeColor = Color.parseColor(
        if (isSelected) "#2B93F6" else "#223A57"
      )
      pair.second.setCardBackgroundColor(
        Color.parseColor(
          if (isSelected) "#18395A" else "#10233A"
        )
      )
    }
  }

  options.chunked(3).forEach {
    rowOptions ->
    val row = LinearLayout(requireContext()).apply {
      orientation = LinearLayout.HORIZONTAL

      val params = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )
      params.bottomMargin = 12.dp()
      layoutParams = params
    }

    rowOptions.forEachIndexed {
      index, option ->
      val card = createOptionCard(option).apply {
        setOnClickListener {
          selectedType = option
          refreshOptions()
        }
      }

      optionViews.add(option to card)

      val params = LinearLayout.LayoutParams(
        0,
        54.dp(),
        1f
      )

      if (index > 0) {
        params.marginStart = 10.dp()
      }

      row.addView(card, params)
    }

    if (rowOptions.size < 3) {
      repeat(3 - rowOptions.size) {
        val spacer = View(requireContext())
        val params = LinearLayout.LayoutParams(
          0,
          54.dp(),
          1f
        )
        params.marginStart = 10.dp()
        row.addView(spacer, params)
      }
    }

    gridContainer.addView(row)
  }

  root.addView(gridContainer)

  refreshOptions()

  addPrimaryButton(
    root = root,
    text = "Save"
  ) {
    viewModel.updateTankType(selectedType)
    renderDetails()
    dialog.dismiss()
  }

  addCancelButton(root, dialog)

  showSheet(dialog, root)
}

private fun showStyleSheet() {
  TankStyleBottomSheet.show(
    fragment = this,
    currentStyle = viewModel.tankDraft.tankStyle
  ) {
    newStyle ->
    viewModel.updateTankStyle(newStyle)
    renderDetails()
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

private fun formatSetupDate(millis: Long?): String {
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

private fun createSheetRoot(): LinearLayout {
  return LinearLayout(requireContext()).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(
      24.dp(),
      22.dp(),
      24.dp(),
      24.dp()
    )
    background = ContextCompat.getDrawable(
      requireContext(),
      R.drawable.bg_aqua_bottom_sheet
    )
  }
}

private fun addSheetHeader(
  root: LinearLayout,
  title: String,
  dialog: BottomSheetDialog
) {
  val header = FrameLayout(requireContext()).apply {
    layoutParams = LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      46.dp()
    )
  }

  val titleView = TextView(requireContext()).apply {
    text = title
    setTextColor(Color.WHITE)
    textSize = 18f
    gravity = Gravity.CENTER
    setTypeface(null, android.graphics.Typeface.BOLD)

    layoutParams = FrameLayout.LayoutParams(
      FrameLayout.LayoutParams.MATCH_PARENT,
      FrameLayout.LayoutParams.MATCH_PARENT
    )
  }

  val closeView = TextView(requireContext()).apply {
    text = "×"
    setTextColor(Color.WHITE)
    textSize = 34f
    gravity = Gravity.CENTER
    includeFontPadding = false
    setOnClickListener {
      dialog.dismiss()
    }

    val params = FrameLayout.LayoutParams(
      44.dp(),
      44.dp(),
      Gravity.END or Gravity.CENTER_VERTICAL
    )
    layoutParams = params
  }

  header.addView(titleView)
  header.addView(closeView)

  root.addView(header)
}

private fun createInfoCard(
  leftText: String,
  rightText: String
): View {
  val card = MaterialCardView(requireContext()).apply {
    radius = 14.dp().toFloat()
    strokeWidth = 0
    setCardBackgroundColor(Color.parseColor("#16314D"))

    val params = LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      58.dp()
    )
    params.topMargin = 18.dp()
    layoutParams = params
  }

  val row = LinearLayout(requireContext()).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    setPadding(16.dp(), 0, 16.dp(), 0)
  }

  val left = TextView(requireContext()).apply {
    text = leftText
    setTextColor(Color.WHITE)
    textSize = 15f
    setTypeface(null, android.graphics.Typeface.BOLD)
  }

  val right = TextView(requireContext()).apply {
    text = rightText
    setTextColor(Color.parseColor("#8FA4BE"))
    textSize = 15f
    gravity = Gravity.END
  }

  row.addView(
    left,
    LinearLayout.LayoutParams(
      0,
      LinearLayout.LayoutParams.WRAP_CONTENT,
      1f
    )
  )

  row.addView(
    right,
    LinearLayout.LayoutParams(
      0,
      LinearLayout.LayoutParams.WRAP_CONTENT,
      1f
    )
  )

  card.addView(row)

  return card
}

private fun createNumberInputColumn(
  title: String,
  value: String
): Pair<LinearLayout, EditText> {
  val column = LinearLayout(requireContext()).apply {
    orientation = LinearLayout.VERTICAL
  }

  val label = TextView(requireContext()).apply {
    text = title
    setTextColor(Color.WHITE)
    textSize = 14f
    setTypeface(null, android.graphics.Typeface.BOLD)
  }

  val inputCard = MaterialCardView(requireContext()).apply {
    radius = 14.dp().toFloat()
    strokeWidth = 1.dp()
    strokeColor = Color.parseColor("#223A57")
    setCardBackgroundColor(Color.parseColor("#16314D"))

    val params = LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      56.dp()
    )
    params.topMargin = 10.dp()
    layoutParams = params
  }

  val input = EditText(requireContext()).apply {
    setText(value)
    inputType = InputType.TYPE_CLASS_NUMBER
    setTextColor(Color.WHITE)
    setHintTextColor(Color.parseColor("#7F91AA"))
    textSize = 16f
    setSingleLine(true)
    background = null
    setPadding(14.dp(), 0, 14.dp(), 0)
  }

  inputCard.addView(
    input,
    LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      LinearLayout.LayoutParams.MATCH_PARENT
    )
  )

  column.addView(label)
  column.addView(inputCard)

  return column to input
}

private fun createOptionCard(text: String): MaterialCardView {
  val card = MaterialCardView(requireContext()).apply {
    radius = 14.dp().toFloat()
    strokeWidth = 1.dp()
    strokeColor = Color.parseColor("#223A57")
    setCardBackgroundColor(Color.parseColor("#10233A"))
    isClickable = true
    isFocusable = true
  }

  val textView = TextView(requireContext()).apply {
    this.text = text
    gravity = Gravity.CENTER
    setTextColor(Color.WHITE)
    textSize = 14f
    includeFontPadding = false
  }

  card.addView(
    textView,
    FrameLayout.LayoutParams(
      FrameLayout.LayoutParams.MATCH_PARENT,
      FrameLayout.LayoutParams.MATCH_PARENT
    )
  )

  return card
}

private fun addPrimaryButton(
  root: LinearLayout,
  text: String,
  onClick: () -> Unit
) {
  val button = MaterialButton(requireContext()).apply {
    this.text = text
    textSize = 16f
    setTextColor(Color.WHITE)
    setTypeface(null, android.graphics.Typeface.BOLD)
    cornerRadius = 16.dp()
    setBackgroundColor(Color.parseColor("#2196F3"))
    isAllCaps = false

    val params = LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      56.dp()
    )
    params.topMargin = 24.dp()
    layoutParams = params

    setOnClickListener {
      onClick()
    }
  }

  root.addView(button)
}

private fun addCancelButton(
  root: LinearLayout,
  dialog: BottomSheetDialog
) {
  val cancel = TextView(requireContext()).apply {
    text = "Cancel"
    gravity = Gravity.CENTER
    setTextColor(Color.parseColor("#8FA4BE"))
    textSize = 15f
    setPadding(0, 18.dp(), 0, 0)

    setOnClickListener {
      dialog.dismiss()
    }
  }

  root.addView(
    cancel,
    LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      LinearLayout.LayoutParams.WRAP_CONTENT
    )
  )
}

private fun createNumberPicker(): NumberPicker {
  return NumberPicker(requireContext()).apply {
    descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
    wrapSelectorWheel = true
  }
}

private fun weightedColumnParams(): LinearLayout.LayoutParams {
  return LinearLayout.LayoutParams(
    0,
    LinearLayout.LayoutParams.WRAP_CONTENT,
    1f
  )
}

private fun weightedColumnParamsWithMargin(): LinearLayout.LayoutParams {
  return LinearLayout.LayoutParams(
    0,
    LinearLayout.LayoutParams.WRAP_CONTENT,
    1f
  ).apply {
    marginStart = 10.dp()
  }
}

private fun showSheet(
  dialog: BottomSheetDialog,
  root: LinearLayout
) {
  dialog.setContentView(root)

  dialog.setOnShowListener {
    val bottomSheet = dialog.findViewById<View>(
      com.google.android.material.R.id.design_bottom_sheet
    )

    bottomSheet?.setBackgroundColor(Color.TRANSPARENT)
  }

  dialog.show()
}

private fun Int.dp(): Int {
  return (this * resources.displayMetrics.density).toInt()
}

override fun validateAndSave(): Boolean {
  return true
}

override fun onDestroyView() {
  super.onDestroyView()
  _binding = null
}
}