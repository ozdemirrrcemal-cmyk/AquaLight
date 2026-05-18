package com.aqua.aqualight.ui.tabs.maintenance

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.databinding.FragmentAddCareTaskBinding
import com.aqua.aqualight.ui.tabs.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.ui.tabs.maintenance.model.CareTaskType
import com.aqua.aqualight.ui.tabs.maintenance.model.CareTaskTypeCatalog
import com.aqua.aqualight.ui.tabs.maintenance.model.CareTaskTypeUi
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AddCareTaskFragment :
  Fragment(R.layout.fragment_add_care_task) {

  private var _binding: FragmentAddCareTaskBinding? = null
  private val binding get() = _binding!!

  private val maintenanceViewModel: MaintenanceViewModel by viewModels(
    ownerProducer = {
      requireParentFragment()
    }
  )

  private var selectedType: CareTaskType = CareTaskType.WATER_CHANGE
  private var selectedTankId: Long = 0L
  private var selectedWaterChangePercent: Int = 20

  private val selectedCalendar: Calendar = Calendar.getInstance().apply {
    add(Calendar.HOUR_OF_DAY, 1)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
  }

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?
  ) {
    super.onViewCreated(
      view,
      savedInstanceState
    )

    _binding = FragmentAddCareTaskBinding.bind(view)

    setupInitialUi()
    setupClickListeners()
    observeTanks()
    renderTaskTypes()
    renderWaterChangePercentOptions()
    updateDateTimeText()
    updateDynamicSections()
  }

  private fun setupInitialUi() {
    binding.switchReminder.isChecked = true
    binding.switchMissedReminder.isChecked = true
  }

  private fun setupClickListeners() {
    binding.btnBack.setOnClickListener {
      closeForm()
    }

    binding.rowDueDate.setOnClickListener {
      showDatePicker()
    }

    binding.rowDueTime.setOnClickListener {
      showTimePicker()
    }

    binding.switchRepeat.setOnCheckedChangeListener { _, _ ->
      updateDynamicSections()
    }

    binding.switchReminder.setOnCheckedChangeListener { _, _ ->
      updateDynamicSections()
    }

    binding.switchMissedReminder.setOnCheckedChangeListener { _, _ ->
      updateDynamicSections()
    }

    binding.btnSaveTask.setOnClickListener {
      saveTask()
    }
  }

  private fun observeTanks() {
    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        maintenanceViewModel.tanks.collect { tanks ->
          if (selectedTankId == 0L && tanks.isNotEmpty()) {
            selectedTankId = tanks.first().id
          }

          renderTankOptions(tanks)
        }
      }
    }
  }

  private fun renderTaskTypes() {
    binding.typeOptionsContainer.removeAllViews()

    CareTaskTypeCatalog.categories.forEach { category ->
      val items = CareTaskTypeCatalog.byCategory(category)

      if (items.isEmpty()) {
        return@forEach
      }

      val categoryTitle = TextView(requireContext()).apply {
        text = category
        textSize = 12.5f
        setTextColor(Color.parseColor("#8FA4BE"))
        setTypeface(null, Typeface.BOLD)
        includeFontPadding = false

        val params = LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.topMargin = 4.dp()
        params.bottomMargin = 9.dp()
        layoutParams = params
      }

      val grid = GridLayout(requireContext()).apply {
        columnCount = 2

        layoutParams = LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        )
      }

      items.forEach { item ->
        grid.addView(
          createTaskTypeCard(item)
        )
      }

      binding.typeOptionsContainer.addView(categoryTitle)
      binding.typeOptionsContainer.addView(grid)
    }
  }

  private fun createTaskTypeCard(
    item: CareTaskTypeUi
  ): View {
    val selected = item.type == selectedType
    val accentColor = Color.parseColor(item.accentColor)

    val card = MaterialCardView(requireContext()).apply {
      radius = 17.dp().toFloat()
      strokeWidth = 1.dp()
      strokeColor = if (selected) {
        accentColor
      } else {
        Color.parseColor("#223A57")
      }
      setCardBackgroundColor(
        if (selected) {
          Color.parseColor("#1C3D63")
        } else {
          Color.parseColor("#10233A")
        }
      )
      cardElevation = 0f
      isClickable = true
      isFocusable = true

      val params = GridLayout.LayoutParams().apply {
        width = 0
        height = 62.dp()
        columnSpec = GridLayout.spec(
          GridLayout.UNDEFINED,
          1f
        )
        setMargins(
          0,
          0,
          8.dp(),
          8.dp()
        )
      }

      layoutParams = params

      setOnClickListener {
        selectedType = item.type
        renderTaskTypes()
        updateDynamicSections()
      }
    }

    val row = LinearLayout(requireContext()).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      setPadding(
        10.dp(),
        8.dp(),
        10.dp(),
        8.dp()
      )
    }

    val iconContainer = FrameLayout(requireContext()).apply {
      background = createIconBackground(
        color = accentColor,
        selected = selected
      )

      layoutParams = LinearLayout.LayoutParams(
        38.dp(),
        38.dp()
      )
    }

    val icon = ImageView(requireContext()).apply {
      setImageResource(item.iconRes)
      setColorFilter(Color.WHITE)
      scaleType = ImageView.ScaleType.CENTER_INSIDE

      val params = FrameLayout.LayoutParams(
        22.dp(),
        22.dp(),
        Gravity.CENTER
      )

      layoutParams = params
    }

    iconContainer.addView(icon)

    val title = TextView(requireContext()).apply {
      text = item.title
      textSize = 12.5f
      setTextColor(Color.WHITE)
      setTypeface(
        null,
        if (selected) Typeface.BOLD else Typeface.NORMAL
      )
      includeFontPadding = false
      maxLines = 2

      val params = LinearLayout.LayoutParams(
        0,
        LinearLayout.LayoutParams.WRAP_CONTENT,
        1f
      )
      params.marginStart = 10.dp()
      layoutParams = params
    }

    row.addView(iconContainer)
    row.addView(title)

    card.addView(row)

    return card
  }

  private fun renderWaterChangePercentOptions() {
    binding.percentOptionsContainer.removeAllViews()

    val percentages = listOf(
      10,
      20,
      30,
      40,
      50,
      75,
      100
    )

    val grid = GridLayout(requireContext()).apply {
      columnCount = 4
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )
    }

    percentages.forEach { percent ->
      grid.addView(
        createPercentChip(percent)
      )
    }

    binding.percentOptionsContainer.addView(grid)
  }

  private fun createPercentChip(
    percent: Int
  ): View {
    val selected = percent == selectedWaterChangePercent

    return TextView(requireContext()).apply {
      text = "$percent%"
      gravity = Gravity.CENTER
      textSize = 13f
      setTextColor(Color.WHITE)
      setTypeface(
        null,
        if (selected) Typeface.BOLD else Typeface.NORMAL
      )
      includeFontPadding = false

      background = createRoundedDrawable(
        color = if (selected) "#1C3D63" else "#10233A",
        radiusPx = 16.dp(),
        strokeColor = if (selected) "#2196F3" else "#223A57",
        strokeWidthPx = 1.dp()
      )

      val params = GridLayout.LayoutParams().apply {
        width = 0
        height = 44.dp()
        columnSpec = GridLayout.spec(
          GridLayout.UNDEFINED,
          1f
        )
        setMargins(
          0,
          0,
          8.dp(),
          8.dp()
        )
      }

      layoutParams = params

      setOnClickListener {
        selectedWaterChangePercent = percent
        renderWaterChangePercentOptions()
      }
    }
  }

  private fun renderTankOptions(
    tanks: List<SavedAquariumTank>
  ) {
    binding.tankOptionsContainer.removeAllViews()

    if (tanks.isEmpty()) {
      val emptyText = TextView(requireContext()).apply {
        text = "Create an aquarium first to add care tasks."
        textSize = 13f
        setTextColor(Color.parseColor("#8FA4BE"))
        includeFontPadding = false
      }

      binding.tankOptionsContainer.addView(emptyText)
      return
    }

    tanks.forEach { tank ->
      binding.tankOptionsContainer.addView(
        createTankCard(tank)
      )
    }
  }

  private fun createTankCard(
    tank: SavedAquariumTank
  ): View {
    val selected = tank.id == selectedTankId

    val card = MaterialCardView(requireContext()).apply {
      radius = 17.dp().toFloat()
      strokeWidth = 1.dp()
      strokeColor = if (selected) {
        Color.parseColor("#2196F3")
      } else {
        Color.parseColor("#223A57")
      }
      setCardBackgroundColor(
        if (selected) {
          Color.parseColor("#1C3D63")
        } else {
          Color.parseColor("#10233A")
        }
      )
      cardElevation = 0f
      isClickable = true
      isFocusable = true

      val params = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        56.dp()
      )
      params.bottomMargin = 9.dp()
      layoutParams = params

      setOnClickListener {
        selectedTankId = tank.id
        maintenanceViewModel.tanks.value.let { tanks ->
          renderTankOptions(tanks)
        }
      }
    }

    val row = LinearLayout(requireContext()).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      setPadding(
        14.dp(),
        10.dp(),
        14.dp(),
        10.dp()
      )
    }

    val title = TextView(requireContext()).apply {
      text = tank.name.ifBlank {
        "Unnamed aquarium"
      }
      textSize = 14f
      setTextColor(Color.WHITE)
      setTypeface(
        null,
        if (selected) Typeface.BOLD else Typeface.NORMAL
      )
      includeFontPadding = false

      layoutParams = LinearLayout.LayoutParams(
        0,
        LinearLayout.LayoutParams.WRAP_CONTENT,
        1f
      )
    }

    val check = TextView(requireContext()).apply {
      text = if (selected) {
        "Selected"
      } else {
        ""
      }
      textSize = 12f
      setTextColor(Color.parseColor("#5FD6B4"))
      setTypeface(null, Typeface.BOLD)
      includeFontPadding = false
    }

    row.addView(title)
    row.addView(check)

    card.addView(row)

    return card
  }

  private fun updateDynamicSections() {
    binding.waterChangePercentSection.isVisible =
      selectedType == CareTaskType.WATER_CHANGE

    binding.customTitleSection.isVisible =
      selectedType == CareTaskType.CUSTOM

    binding.repeatDaysContainer.isVisible =
      binding.switchRepeat.isChecked

    binding.missedReminderSection.isVisible =
      binding.switchReminder.isChecked

    binding.missedReminderDaysContainer.isVisible =
      binding.switchReminder.isChecked &&
        binding.switchMissedReminder.isChecked
  }

  private fun showDatePicker() {
    DatePickerDialog(
      requireContext(),
      { _, year, month, dayOfMonth ->
        selectedCalendar.set(Calendar.YEAR, year)
        selectedCalendar.set(Calendar.MONTH, month)
        selectedCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
        updateDateTimeText()
      },
      selectedCalendar.get(Calendar.YEAR),
      selectedCalendar.get(Calendar.MONTH),
      selectedCalendar.get(Calendar.DAY_OF_MONTH)
    ).show()
  }

  private fun showTimePicker() {
    TimePickerDialog(
      requireContext(),
      { _, hourOfDay, minute ->
        selectedCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
        selectedCalendar.set(Calendar.MINUTE, minute)
        selectedCalendar.set(Calendar.SECOND, 0)
        selectedCalendar.set(Calendar.MILLISECOND, 0)
        updateDateTimeText()
      },
      selectedCalendar.get(Calendar.HOUR_OF_DAY),
      selectedCalendar.get(Calendar.MINUTE),
      true
    ).show()
  }

  private fun updateDateTimeText() {
    binding.tvDueDateValue.text = SimpleDateFormat(
      "dd MMM yyyy",
      Locale.getDefault()
    ).format(Date(selectedCalendar.timeInMillis))

    binding.tvDueTimeValue.text = SimpleDateFormat(
      "HH:mm",
      Locale.getDefault()
    ).format(Date(selectedCalendar.timeInMillis))
  }

  private fun saveTask() {
    if (selectedTankId == 0L) {
      showSnackBar(
        message = "Please select an aquarium.",
        type = BaseActivity.SnackType.WARNING
      )
      return
    }

    val typeUi = CareTaskTypeCatalog.get(selectedType)

    val customTitle = binding.etCustomTitle.text
      .toString()
      .trim()

    if (selectedType == CareTaskType.CUSTOM && customTitle.length < 2) {
      showSnackBar(
        message = "Custom task title must be at least 2 characters.",
        type = BaseActivity.SnackType.WARNING
      )
      return
    }

    val repeatDays = binding.etRepeatDays.text
      .toString()
      .toIntOrNull()
      ?.coerceAtLeast(1)
      ?: 7

    val missedDays = binding.etMissedReminderDays.text
      .toString()
      .toIntOrNull()
      ?.coerceAtLeast(1)
      ?: 3

    val title = if (selectedType == CareTaskType.CUSTOM) {
      customTitle
    } else {
      typeUi.title
    }

    val waterPercent = if (selectedType == CareTaskType.WATER_CHANGE) {
      selectedWaterChangePercent
    } else {
      null
    }

    viewLifecycleOwner.lifecycleScope.launch {
      maintenanceViewModel.addManualTask(
        tankId = selectedTankId,
        title = title,
        description = typeUi.defaultDescription,
        type = selectedType,
        dueAtMillis = selectedCalendar.timeInMillis,
        repeatEnabled = binding.switchRepeat.isChecked,
        repeatIntervalDays = repeatDays,
        reminderEnabled = binding.switchReminder.isChecked,
        missedReminderEnabled = binding.switchReminder.isChecked &&
          binding.switchMissedReminder.isChecked,
        missedReminderDays = missedDays,
        waterChangePercent = waterPercent,
        note = binding.etNote.text
          .toString()
          .trim()
      )

      closeForm()
    }
  }

  private fun closeForm() {
    (parentFragment as? AquariumMaintenanceFragment)?.closeAddCareTaskFlow()
  }

  private fun showSnackBar(
    message: String,
    type: BaseActivity.SnackType
  ) {
    (activity as? BaseActivity)?.showSnackBar(
      message = message,
      type = type
    )
  }

  private fun createIconBackground(
    color: Int,
    selected: Boolean
  ): GradientDrawable {
    return GradientDrawable().apply {
      shape = GradientDrawable.RECTANGLE
      cornerRadius = 14.dp().toFloat()
      setColor(
        applyAlpha(
          color = color,
          alpha = if (selected) 0.34f else 0.22f
        )
      )
      setStroke(
        1.dp(),
        applyAlpha(
          color = color,
          alpha = if (selected) 0.9f else 0.55f
        )
      )
    }
  }

  private fun createRoundedDrawable(
    color: String,
    radiusPx: Int,
    strokeColor: String,
    strokeWidthPx: Int
  ): GradientDrawable {
    return GradientDrawable().apply {
      shape = GradientDrawable.RECTANGLE
      setColor(Color.parseColor(color))
      cornerRadius = radiusPx.toFloat()
      setStroke(
        strokeWidthPx,
        Color.parseColor(strokeColor)
      )
    }
  }

  private fun applyAlpha(
    color: Int,
    alpha: Float
  ): Int {
    return Color.argb(
      (255 * alpha).toInt(),
      Color.red(color),
      Color.green(color),
      Color.blue(color)
    )
  }

  private fun Int.dp(): Int {
    return (this * resources.displayMetrics.density).toInt()
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}