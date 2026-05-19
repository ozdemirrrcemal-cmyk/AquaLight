package com.aqua.aqualight.ui.tabs.maintenance

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.databinding.FragmentAddCareTaskBinding
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.ui.tabs.maintenance.model.CareTaskType
import com.aqua.aqualight.ui.tabs.maintenance.model.CareTaskTypeCatalog
import com.aqua.aqualight.ui.tabs.maintenance.model.CareTaskTypeUi
import com.google.android.material.bottomsheet.BottomSheetDialog
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

  private val maintenanceViewModel: MaintenanceViewModel by activityViewModels()
  private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

  private var selectedType: CareTaskType? = null
  private var selectedTankId: Long = 0L
  private var selectedWaterChangePercent: Int? = null

  private var latestTanks: List<SavedAquariumTank> = emptyList()

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
    updateDateTimeText()
    updateSelectedTaskTypeUi()
    updateSelectedAquariumUi()
    updateDynamicSections()
    updateSaveButtonState()
  }

  private fun setupInitialUi() {
    binding.switchReminder.isChecked = true
    binding.switchMissedReminder.isChecked = true
    binding.switchRepeat.isChecked = false
  }

  private fun setupClickListeners() {
    binding.btnBack.setOnClickListener {
      closeForm()
    }

    binding.rowTaskType.setOnClickListener {
      showTaskTypeBottomSheet()
    }

    binding.rowAquarium.setOnClickListener {
      showAquariumBottomSheet()
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
    aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
      latestTanks = tanks
      maintenanceViewModel.setTanks(tanks)

      if (selectedTankId != 0L && tanks.none { tank -> tank.id == selectedTankId }) {
        selectedTankId = 0L
      }

      updateSelectedAquariumUi()
      updateSaveButtonState()
    }
  }

  private fun showTaskTypeBottomSheet() {
    val dialog = BottomSheetDialog(requireContext())

    val contentView = LayoutInflater.from(requireContext()).inflate(
      R.layout.bottom_sheet_care_task_type,
      null,
      false
    )

    val container = contentView.findViewById<LinearLayout>(
      R.id.typeOptionsContainer
    )

    renderTaskTypesIntoContainer(
      container = container,
      dialog = dialog
    )

    dialog.setContentView(contentView)

    dialog.setOnShowListener {
      val bottomSheet = dialog.findViewById<View>(
        com.google.android.material.R.id.design_bottom_sheet
      )

      bottomSheet?.setBackgroundColor(Color.TRANSPARENT)
    }

    dialog.show()
  }

  private fun renderTaskTypesIntoContainer(
    container: LinearLayout,
    dialog: BottomSheetDialog
  ) {
    container.removeAllViews()

    CareTaskTypeCatalog.categories.forEach { category ->
      val items = CareTaskTypeCatalog.byCategory(category)

      if (items.isEmpty()) {
        return@forEach
      }

      val categoryTitle = TextView(requireContext()).apply {
        text = category
        textSize = 12f
        setTextColor(Color.parseColor("#8FA4BE"))
        setTypeface(null, Typeface.BOLD)
        includeFontPadding = false

        val params = LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.topMargin = 2.dp()
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
          createTaskTypeCard(
            item = item,
            dialog = dialog
          )
        )
      }

      container.addView(categoryTitle)
      container.addView(grid)
    }
  }

  private fun createTaskTypeCard(
    item: CareTaskTypeUi,
    dialog: BottomSheetDialog
  ): View {
    val selected = item.type == selectedType
    val accentColor = Color.parseColor(item.accentColor)

    val card = MaterialCardView(requireContext()).apply {
      radius = 16.dp().toFloat()
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
        height = 58.dp()
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

        if (item.type != CareTaskType.WATER_CHANGE) {
          selectedWaterChangePercent = null
        }

        updateSelectedTaskTypeUi()
        updateDynamicSections()
        updateSaveButtonState()

        dialog.dismiss()

        if (item.type == CareTaskType.WATER_CHANGE) {
          binding.root.post {
            showWaterChangePercentBottomSheet()
          }
        }
      }
    }

    val row = LinearLayout(requireContext()).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      setPadding(
        9.dp(),
        8.dp(),
        9.dp(),
        8.dp()
      )
    }

    val iconContainer = FrameLayout(requireContext()).apply {
      background = createIconBackground(
        color = accentColor,
        selected = selected
      )

      layoutParams = LinearLayout.LayoutParams(
        36.dp(),
        36.dp()
      )
    }

    val icon = ImageView(requireContext()).apply {
      setImageResource(item.iconRes)
      setColorFilter(Color.WHITE)
      scaleType = ImageView.ScaleType.CENTER_INSIDE

      val params = FrameLayout.LayoutParams(
        20.dp(),
        20.dp(),
        Gravity.CENTER
      )

      layoutParams = params
    }

    iconContainer.addView(icon)

    val title = TextView(requireContext()).apply {
      text = item.title
      textSize = 12.2f
      setTextColor(Color.WHITE)
      setTypeface(
        null,
        if (selected) {
          Typeface.BOLD
        } else {
          Typeface.NORMAL
        }
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

  private fun showWaterChangePercentBottomSheet() {
    val dialog = BottomSheetDialog(requireContext())

    val contentView = LayoutInflater.from(requireContext()).inflate(
      R.layout.bottom_sheet_water_change_percent,
      null,
      false
    )

    val container = contentView.findViewById<LinearLayout>(
      R.id.percentOptionsContainer
    )

    renderWaterChangePercentOptionsIntoContainer(
      container = container,
      dialog = dialog
    )

    dialog.setContentView(contentView)

    dialog.setOnShowListener {
      val bottomSheet = dialog.findViewById<View>(
        com.google.android.material.R.id.design_bottom_sheet
      )

      bottomSheet?.setBackgroundColor(Color.TRANSPARENT)
    }

    dialog.show()
  }

  private fun renderWaterChangePercentOptionsIntoContainer(
    container: LinearLayout,
    dialog: BottomSheetDialog
  ) {
    container.removeAllViews()

    val percentages = listOf(
  10,
  20,
  30,
  40,
  50,
  60,
  70,
  80,
  90,
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
        createPercentChip(
          percent = percent,
          dialog = dialog
        )
      )
    }

    container.addView(grid)
  }

  private fun createPercentChip(
    percent: Int,
    dialog: BottomSheetDialog
  ): View {
    val selected = percent == selectedWaterChangePercent

    return TextView(requireContext()).apply {
      text = "$percent%"
      gravity = Gravity.CENTER
      textSize = 12.8f
      setTextColor(Color.WHITE)
      setTypeface(
        null,
        if (selected) {
          Typeface.BOLD
        } else {
          Typeface.NORMAL
        }
      )
      includeFontPadding = false

      background = createRoundedDrawable(
        color = if (selected) {
          "#1C3D63"
        } else {
          "#10233A"
        },
        radiusPx = 15.dp(),
        strokeColor = if (selected) {
          "#2196F3"
        } else {
          "#223A57"
        },
        strokeWidthPx = 1.dp()
      )

      val params = GridLayout.LayoutParams().apply {
        width = 0
        height = 42.dp()
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
        updateSelectedTaskTypeUi()
        updateSaveButtonState()
        dialog.dismiss()
      }
    }
  }

  private fun showAquariumBottomSheet() {
    if (latestTanks.isEmpty()) {
      showSnackBar(
        message = "Create an aquarium first to add care tasks.",
        type = BaseActivity.SnackType.WARNING
      )
      return
    }

    val dialog = BottomSheetDialog(requireContext())

    val contentView = LayoutInflater.from(requireContext()).inflate(
      R.layout.bottom_sheet_select_aquarium,
      null,
      false
    )

    val container = contentView.findViewById<LinearLayout>(
      R.id.aquariumOptionsContainer
    )

    renderAquariumOptionsIntoContainer(
      container = container,
      dialog = dialog
    )

    dialog.setContentView(contentView)

    dialog.setOnShowListener {
      val bottomSheet = dialog.findViewById<View>(
        com.google.android.material.R.id.design_bottom_sheet
      )

      bottomSheet?.setBackgroundColor(Color.TRANSPARENT)
    }

    dialog.show()
  }

  private fun renderAquariumOptionsIntoContainer(
    container: LinearLayout,
    dialog: BottomSheetDialog
  ) {
    container.removeAllViews()

    latestTanks.forEach { tank ->
      container.addView(
        createAquariumOptionCard(
          tank = tank,
          dialog = dialog
        )
      )
    }
  }

  private fun createAquariumOptionCard(
    tank: SavedAquariumTank,
    dialog: BottomSheetDialog
  ): View {
    val selected = tank.id == selectedTankId

    val card = MaterialCardView(requireContext()).apply {
      radius = 16.dp().toFloat()
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
        52.dp()
      )
      params.bottomMargin = 9.dp()
      layoutParams = params

      setOnClickListener {
        selectedTankId = tank.id
        updateSelectedAquariumUi()
        updateSaveButtonState()
        dialog.dismiss()
      }
    }

    val row = LinearLayout(requireContext()).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      setPadding(
        14.dp(),
        8.dp(),
        14.dp(),
        8.dp()
      )
    }

    val title = TextView(requireContext()).apply {
      text = tank.name.ifBlank {
        "Unnamed aquarium"
      }
      textSize = 13.5f
      setTextColor(Color.WHITE)
      setTypeface(
        null,
        if (selected) {
          Typeface.BOLD
        } else {
          Typeface.NORMAL
        }
      )
      includeFontPadding = false
      maxLines = 1

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
      textSize = 11.5f
      setTextColor(Color.parseColor("#5FD6B4"))
      setTypeface(null, Typeface.BOLD)
      includeFontPadding = false
    }

    row.addView(title)
    row.addView(check)

    card.addView(row)

    return card
  }

  private fun updateSelectedTaskTypeUi() {
    val type = selectedType

    if (type == null) {
      binding.taskTypeIconContainer.isVisible = false
      binding.tvTaskTypeTitle.text = "Select care task type"
      binding.tvTaskTypeTitle.setTextColor(Color.parseColor("#8FA4BE"))
      binding.tvTaskTypeSubtitle.text = "Required"
      binding.tvTaskTypeSubtitle.setTextColor(Color.parseColor("#6F829B"))
      return
    }

    val typeUi = CareTaskTypeCatalog.get(type)
    val category = getCategoryForTaskType(type)
    val accentColor = Color.parseColor(typeUi.accentColor)

    binding.taskTypeIconContainer.isVisible = true
    binding.taskTypeIconContainer.background = createIconBackground(
      color = accentColor,
      selected = true
    )

    binding.ivTaskTypeIcon.setImageResource(typeUi.iconRes)
    binding.ivTaskTypeIcon.setColorFilter(Color.WHITE)

    val title = if (type == CareTaskType.WATER_CHANGE) {
      val percent = selectedWaterChangePercent

      if (percent == null) {
        typeUi.title
      } else {
        "${typeUi.title} ($percent%)"
      }
    } else {
      typeUi.title
    }

    val subtitle = when {
      type == CareTaskType.WATER_CHANGE && selectedWaterChangePercent == null -> {
        "Select water change percentage"
      }

      type == CareTaskType.WATER_CHANGE && selectedWaterChangePercent != null -> {
        "$category • ${selectedWaterChangePercent}%"
      }

      else -> {
        category
      }
    }

    binding.tvTaskTypeTitle.text = title
    binding.tvTaskTypeTitle.setTextColor(Color.WHITE)
    binding.tvTaskTypeSubtitle.text = subtitle
    binding.tvTaskTypeSubtitle.setTextColor(Color.parseColor("#8FA4BE"))
  }

  private fun updateSelectedAquariumUi() {
    val selectedTank = latestTanks.firstOrNull { tank ->
      tank.id == selectedTankId
    }

    if (selectedTank == null) {
      binding.tvAquariumTitle.text = "Select aquarium"
      binding.tvAquariumTitle.setTextColor(Color.parseColor("#8FA4BE"))
      binding.tvAquariumSubtitle.text = "Required"
      binding.tvAquariumSubtitle.setTextColor(Color.parseColor("#6F829B"))
      return
    }

    binding.tvAquariumTitle.text = selectedTank.name.ifBlank {
      "Unnamed aquarium"
    }
    binding.tvAquariumTitle.setTextColor(Color.WHITE)
    binding.tvAquariumSubtitle.text = "Selected aquarium"
    binding.tvAquariumSubtitle.setTextColor(Color.parseColor("#5FD6B4"))
  }

  private fun updateDynamicSections() {
    binding.customTitleSection.isVisible =
      selectedType == CareTaskType.CUSTOM

    binding.repeatDaysContainer.isVisible =
      binding.switchRepeat.isChecked

    binding.missedReminderSection.isVisible =
      binding.switchReminder.isChecked

    binding.missedReminderDaysContainer.isVisible =
      binding.switchReminder.isChecked &&
        binding.switchMissedReminder.isChecked

    updateSaveButtonState()
  }

  private fun updateSaveButtonState() {
    val type = selectedType

    val hasTaskType = type != null
    val hasAquarium = selectedTankId != 0L
    val hasWaterPercent = type != CareTaskType.WATER_CHANGE ||
      selectedWaterChangePercent != null

    val canSave = hasTaskType && hasAquarium && hasWaterPercent

    binding.btnSaveTask.isEnabled = canSave
    binding.btnSaveTask.backgroundTintList = ColorStateList.valueOf(
      Color.parseColor(
        if (canSave) {
          "#2196F3"
        } else {
          "#35506D"
        }
      )
    )
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
    val type = selectedType

    if (type == null) {
      showSnackBar(
        message = "Please select a care task type.",
        type = BaseActivity.SnackType.WARNING
      )
      return
    }

    if (selectedTankId == 0L) {
      showSnackBar(
        message = "Please select an aquarium.",
        type = BaseActivity.SnackType.WARNING
      )
      return
    }

    if (type == CareTaskType.WATER_CHANGE && selectedWaterChangePercent == null) {
      showSnackBar(
        message = "Please select water change percentage.",
        type = BaseActivity.SnackType.WARNING
      )
      return
    }

    val typeUi = CareTaskTypeCatalog.get(type)

    val customTitle = binding.etCustomTitle.text
      .toString()
      .trim()

    if (type == CareTaskType.CUSTOM && customTitle.length < 2) {
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

    val title = if (type == CareTaskType.CUSTOM) {
      customTitle
    } else {
      typeUi.title
    }

    viewLifecycleOwner.lifecycleScope.launch {
      maintenanceViewModel.addManualTask(
        tankId = selectedTankId,
        title = title,
        description = typeUi.defaultDescription,
        type = type,
        dueAtMillis = selectedCalendar.timeInMillis,
        repeatEnabled = binding.switchRepeat.isChecked,
        repeatIntervalDays = repeatDays,
        reminderEnabled = binding.switchReminder.isChecked,
        missedReminderEnabled = binding.switchReminder.isChecked &&
          binding.switchMissedReminder.isChecked,
        missedReminderDays = missedDays,
        waterChangePercent = if (type == CareTaskType.WATER_CHANGE) {
          selectedWaterChangePercent
        } else {
          null
        },
        note = binding.etNote.text
          .toString()
          .trim()
      )

      closeForm()
    }
  }

  private fun getCategoryForTaskType(
    type: CareTaskType
  ): String {
    CareTaskTypeCatalog.categories.forEach { category ->
      val match = CareTaskTypeCatalog.byCategory(category).firstOrNull { item ->
        item.type == type
      }

      if (match != null) {
        return category
      }
    }

    return "Care Task"
  }

  private fun closeForm() {
    findNavController().navigateUp()
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
      cornerRadius = 13.dp().toFloat()
      setColor(
        applyAlpha(
          color = color,
          alpha = if (selected) {
            0.34f
          } else {
            0.22f
          }
        )
      )
      setStroke(
        1.dp(),
        applyAlpha(
          color = color,
          alpha = if (selected) {
            0.9f
          } else {
            0.55f
          }
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