package com.aqua.aqualight.ui.tabs.maintenance

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentAquariumMaintenanceBinding
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.maintenance.model.CareTaskUi
import com.aqua.aqualight.ui.tabs.maintenance.model.MaintenanceTab
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.aqua.aqualight.ui.tabs.aquarium.careprofile.CareProfileCalculator
import com.aqua.aqualight.ui.tabs.aquarium.model.SavedAquariumTank

class AquariumMaintenanceFragment :
Fragment(R.layout.fragment_aquarium_maintenance) {

  private var _binding: FragmentAquariumMaintenanceBinding? = null
  private val binding get() = _binding!!

  private val maintenanceViewModel: MaintenanceViewModel by activityViewModels()
  private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

  private lateinit var adapter: CareTaskAdapter
  private var currentSelectedTab: MaintenanceTab = MaintenanceTab.ALL
  private var latestTanks: List<SavedAquariumTank> = emptyList()
  private var careProfileTargetTankId: Long = 0L

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?
  ) {
    super.onViewCreated(
      view,
      savedInstanceState
    )

    _binding = FragmentAquariumMaintenanceBinding.bind(view)

    setupRecycler()
    setupClickListeners()
    observeTanks()
    observeSelectedTab()
    observeCareTasks()
  }

  private fun setupRecycler() {
    adapter = CareTaskAdapter(
      onCompleteClick = {
        task ->
        showCompleteTaskDialog(task)
      },
      onTaskClick = {
        task ->
        openTaskDetailScreen(task)
      }
    )

    binding.rvCareTasks.layoutManager = LinearLayoutManager(
      requireContext()
    )

    binding.rvCareTasks.adapter = adapter
  }

  private fun setupClickListeners() {
    binding.tabAll.setOnClickListener {
      maintenanceViewModel.selectTab(
        MaintenanceTab.ALL
      )
    }

    binding.tabToday.setOnClickListener {
      maintenanceViewModel.selectTab(
        MaintenanceTab.TODAY
      )
    }

    binding.tabUpcoming.setOnClickListener {
      maintenanceViewModel.selectTab(
        MaintenanceTab.UPCOMING
      )
    }

    binding.tabHistory.setOnClickListener {
      maintenanceViewModel.selectTab(
        MaintenanceTab.HISTORY
      )
    }

    binding.btnAddCareTask.setOnClickListener {
      openAddCareTaskScreen()
    }

    binding.btnEmptyAddCareTask.setOnClickListener {
      openAddCareTaskScreen()
    }

    binding.cardCareProfileWarning.setOnClickListener {
      openCareProfileTargetTankSettings()
    }
  }

  private fun observeTanks() {
    aquariumTankViewModel.tanks.observe(viewLifecycleOwner) {
      tanks ->
      latestTanks = tanks

      maintenanceViewModel.setTanks(tanks)
      renderCareProfileWarning()
    }
  }

  private fun observeSelectedTab() {
    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        maintenanceViewModel.selectedTab.collect {
          selectedTab ->
          currentSelectedTab = selectedTab
          renderSelectedTab(selectedTab)
          renderEmptyText(selectedTab)
          renderCareProfileWarning()
        }
      }
    }
  }

  private fun observeCareTasks() {
    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        maintenanceViewModel.taskItems.collect {
          tasks ->

          if (currentSelectedTab == MaintenanceTab.HISTORY) {
            adapter.submitCareTasks(
              tasks = emptyList(),
              showDateHeaders = false
            )

            renderHistoryTimeline(tasks)
          } else {
            adapter.submitCareTasks(
              tasks = tasks,
              showDateHeaders = true
            )

            binding.historyTimelineContainer.removeAllViews()
          }

          renderTaskListState(tasks)
        }
      }
    }
  }

  private fun renderCareProfileWarning() {
  if (_binding == null) {
    return
  }

  if (
    currentSelectedTab == MaintenanceTab.HISTORY ||
    latestTanks.isEmpty()
  ) {
    binding.cardCareProfileWarning.isVisible = false
    careProfileTargetTankId = 0L
    return
  }

  val incompleteProfiles = latestTanks
    .map { tank ->
      tank to CareProfileCalculator.calculate(tank)
    }
    .filter { (_, result) ->
      result.percent < 100
    }

  if (incompleteProfiles.isEmpty()) {
    binding.cardCareProfileWarning.isVisible = false
    careProfileTargetTankId = 0L
    return
  }

  val targetProfile = incompleteProfiles.minBy { (_, result) ->
    result.percent
  }

  val targetTank = targetProfile.first
  val targetResult = targetProfile.second

  careProfileTargetTankId = targetTank.id

  binding.cardCareProfileWarning.isVisible = true
  binding.tvCareProfileWarningPercent.text = "${targetResult.percent}%"

  if (incompleteProfiles.size == 1) {
    binding.tvCareProfileWarningTitle.text =
      "Complete ${targetTank.name.ifBlank { "Aquarium" }} profile"

    binding.tvCareProfileWarningMessage.text =
      "Add missing tank details for better automatic tasks."
  } else {
    binding.tvCareProfileWarningTitle.text =
      "Complete Care Profiles"

    binding.tvCareProfileWarningMessage.text =
      "${incompleteProfiles.size} aquariums need more details for better automatic tasks."
  }
}

private fun openCareProfileTargetTankSettings() {
  if (careProfileTargetTankId <= 0L) {
    return
  }

  findNavController().navigate(
    R.id.tankSettingsFragment,
    bundleOf(
      "tankId" to careProfileTargetTankId
    )
  )
}

  private fun renderTaskListState(
    tasks: List<CareTaskUi>
  ) {
    val hasTasks = tasks.isNotEmpty()
    val isHistory = currentSelectedTab == MaintenanceTab.HISTORY

    binding.rvCareTasks.isVisible = hasTasks && !isHistory
    binding.historyScrollView.isVisible = hasTasks && isHistory
    binding.emptyStateContainer.isVisible = !hasTasks
  }

  private fun renderSelectedTab(
    selectedTab: MaintenanceTab
  ) {
    renderTab(
      tabView = binding.tabAll,
      selected = selectedTab == MaintenanceTab.ALL
    )

    renderTab(
      tabView = binding.tabToday,
      selected = selectedTab == MaintenanceTab.TODAY
    )

    renderTab(
      tabView = binding.tabUpcoming,
      selected = selectedTab == MaintenanceTab.UPCOMING
    )

    renderTab(
      tabView = binding.tabHistory,
      selected = selectedTab == MaintenanceTab.HISTORY
    )
  }

  private fun renderTab(
    tabView: TextView,
    selected: Boolean
  ) {
    tabView.setBackgroundResource(
      if (selected) {
        R.drawable.bg_maintenance_tab_selected
      } else {
        R.drawable.bg_maintenance_tab_unselected
      }
    )

    tabView.setTextColor(
      if (selected) {
        Color.WHITE
      } else {
        Color.parseColor("#8FA4BE")
      }
    )

    tabView.setTypeface(
      null,
      if (selected) {
        Typeface.BOLD
      } else {
        Typeface.NORMAL
      }
    )
  }

  private fun renderEmptyText(
    selectedTab: MaintenanceTab
  ) {
    when (selectedTab) {
      MaintenanceTab.ALL -> {
        binding.tvEmptyMaintenanceTitle.text = "No care tasks yet"
        binding.tvEmptyMaintenanceMessage.text =
        "Add manual reminders or let AquaLight create smart care tasks based on your aquarium setup."
      }

      MaintenanceTab.TODAY -> {
        binding.tvEmptyMaintenanceTitle.text = "Nothing due today"
        binding.tvEmptyMaintenanceMessage.text =
        "There are no care tasks scheduled for today."
      }

      MaintenanceTab.UPCOMING -> {
        binding.tvEmptyMaintenanceTitle.text = "No upcoming tasks"
        binding.tvEmptyMaintenanceMessage.text =
        "Future care reminders will appear here."
      }

      MaintenanceTab.HISTORY -> {
        binding.tvEmptyMaintenanceTitle.text = "No completed tasks"
        binding.tvEmptyMaintenanceMessage.text =
        "Completed maintenance tasks will be listed here."
      }
    }
  }

  private fun renderHistoryTimeline(
    tasks: List<CareTaskUi>
  ) {
    binding.historyTimelineContainer.removeAllViews()

    if (tasks.isEmpty()) {
      return
    }

    val groupedTasks = tasks
    .sortedByDescending {
      task ->
      task.completedAtMillis ?: task.dueAtMillis
    }
    .groupBy {
      task ->
      getHistoryDateKey(
        task.completedAtMillis ?: task.dueAtMillis
      )
    }

    groupedTasks.forEach {
      (_, dayTasks) ->
      val firstTask = dayTasks.firstOrNull() ?: return@forEach
      val dateMillis = firstTask.completedAtMillis ?: firstTask.dueAtMillis

      binding.historyTimelineContainer.addView(
        createHistoryDateHeader(
          millis = dateMillis
        )
      )

      dayTasks.forEach {
        task ->
        binding.historyTimelineContainer.addView(
          createHistoryTaskRow(task)
        )
      }
    }
  }

  private fun createHistoryDateHeader(
    millis: Long
  ): View {
    val row = LinearLayout(requireContext()).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL

      val params = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )
      params.topMargin = 4.dp()
      params.bottomMargin = 12.dp()
      layoutParams = params
    }

    val nodeBox = FrameLayout(requireContext()).apply {
      layoutParams = LinearLayout.LayoutParams(
        HISTORY_AXIS_WIDTH_DP.dp(),
        30.dp()
      )
    }

    val outerNode = View(requireContext()).apply {
      background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.TRANSPARENT)
        setStroke(
          3.dp(),
          Color.parseColor("#5FD6B4")
        )
      }

      val params = FrameLayout.LayoutParams(
        20.dp(),
        20.dp(),
        Gravity.CENTER
      )
      layoutParams = params
    }

    val innerNode = View(requireContext()).apply {
      background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.parseColor("#5FD6B4"))
      }

      val params = FrameLayout.LayoutParams(
        8.dp(),
        8.dp(),
        Gravity.CENTER
      )
      layoutParams = params
    }

    nodeBox.addView(outerNode)
    nodeBox.addView(innerNode)

    val dateText = TextView(requireContext()).apply {
      text = formatHistoryDate(millis)
      textSize = 11.5f
      setTextColor(Color.WHITE)
      setTypeface(null, Typeface.NORMAL)
      includeFontPadding = false

      val params = LinearLayout.LayoutParams(
        0,
        LinearLayout.LayoutParams.WRAP_CONTENT,
        1f
      )
      params.marginStart = 4.dp()
      layoutParams = params
    }

    val todayText = TextView(requireContext()).apply {
      text = if (isToday(millis)) {
        "Today"
      } else {
        ""
      }

      textSize = 12.5f
      setTextColor(Color.parseColor("#5FD6B4"))
      setTypeface(null, Typeface.BOLD)
      includeFontPadding = false
    }

    row.addView(nodeBox)
    row.addView(dateText)
    row.addView(todayText)

    return row
  }

  private fun createHistoryTaskRow(
    task: CareTaskUi
  ): View {
    val row = LinearLayout(requireContext()).apply {
      orientation = LinearLayout.HORIZONTAL

      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )
    }

    val lineBox = FrameLayout(requireContext()).apply {
      layoutParams = LinearLayout.LayoutParams(
        HISTORY_AXIS_WIDTH_DP.dp(),
        LinearLayout.LayoutParams.MATCH_PARENT
      )
    }

    val dashedLine = createDashedTimelineLine().apply {
      val params = FrameLayout.LayoutParams(
        2.dp(),
        FrameLayout.LayoutParams.MATCH_PARENT,
        Gravity.CENTER_HORIZONTAL
      )
      layoutParams = params
    }

    lineBox.addView(dashedLine)

    row.addView(lineBox)
    row.addView(
      createHistoryTaskCard(task)
    )

    return row
  }

  private fun createDashedTimelineLine(): View {
    return object : View(requireContext()) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.parseColor("#3A4654")
      strokeWidth = 2.dp().toFloat()
      style = Paint.Style.STROKE
      strokeCap = Paint.Cap.ROUND
      pathEffect = DashPathEffect(
        floatArrayOf(
          8.dp().toFloat(),
          9.dp().toFloat()
        ),
        0f
      )
    }

    override fun onDraw(canvas: Canvas) {
      super.onDraw(canvas)

      val centerX = width / 2f

      canvas.drawLine(
        centerX,
        0f,
        centerX,
        height.toFloat(),
        linePaint
      )
    }
  }
}

private fun createHistoryTaskCard(
  task: CareTaskUi
): View {
  val card = MaterialCardView(requireContext()).apply {
    radius = 18.dp().toFloat()
    strokeWidth = 1.dp()
    strokeColor = Color.parseColor("#2B6F5A")
    setCardBackgroundColor(Color.parseColor("#12382F"))
    cardElevation = 0f
    useCompatPadding = false

    val params = LinearLayout.LayoutParams(
      0,
      LinearLayout.LayoutParams.WRAP_CONTENT,
      1f
    )
    params.bottomMargin = 12.dp()
    layoutParams = params
  }

  val row = LinearLayout(requireContext()).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL

    setPadding(
      13.dp(),
      12.dp(),
      12.dp(),
      12.dp()
    )
  }

  val iconBox = FrameLayout(requireContext()).apply {
    background = createHistoryIconBackground(
      color = Color.parseColor(task.accentColor)
    )

    layoutParams = LinearLayout.LayoutParams(
      40.dp(),
      40.dp()
    )
  }

  val icon = ImageView(requireContext()).apply {
    setImageResource(task.iconRes)
    setColorFilter(Color.WHITE)

    val params = FrameLayout.LayoutParams(
      20.dp(),
      20.dp(),
      Gravity.CENTER
    )
    layoutParams = params
  }

  iconBox.addView(icon)

  val textBox = LinearLayout(requireContext()).apply {
    orientation = LinearLayout.VERTICAL

    val params = LinearLayout.LayoutParams(
      0,
      LinearLayout.LayoutParams.WRAP_CONTENT,
      1f
    )
    params.marginStart = 12.dp()
    params.marginEnd = 8.dp()
    layoutParams = params
  }

  val titleText = TextView(requireContext()).apply {
    text = task.title
    textSize = 14f
    setTextColor(Color.WHITE)
    setTypeface(null, Typeface.BOLD)
    includeFontPadding = false
    maxLines = 1
    ellipsize = TextUtils.TruncateAt.END
  }

  val metaText = TextView(requireContext()).apply {
    text = buildHistoryMetaText(task)
    textSize = 12f
    setTextColor(Color.parseColor("#B8C7D9"))
    includeFontPadding = false
    maxLines = 1
    ellipsize = TextUtils.TruncateAt.END

    val params = LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      LinearLayout.LayoutParams.WRAP_CONTENT
    )
    params.topMargin = 5.dp()
    layoutParams = params
  }

  val completedText = TextView(requireContext()).apply {
    text = "Completed"
    textSize = 12f
    setTextColor(Color.parseColor("#5FD6B4"))
    setTypeface(null, Typeface.BOLD)
    includeFontPadding = false

    val params = LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      LinearLayout.LayoutParams.WRAP_CONTENT
    )
    params.topMargin = 5.dp()
    layoutParams = params
  }

  textBox.addView(titleText)
  textBox.addView(metaText)
  textBox.addView(completedText)

  val checkBox = FrameLayout(requireContext()).apply {
    background = GradientDrawable().apply {
      shape = GradientDrawable.OVAL
      setColor(Color.TRANSPARENT)
      setStroke(
        1.dp(),
        Color.parseColor("#3FAE87")
      )
    }

    layoutParams = LinearLayout.LayoutParams(
      34.dp(),
      34.dp()
    )
  }

  val checkIcon = ImageView(requireContext()).apply {
    setImageResource(R.drawable.ic_check_20)
    setColorFilter(Color.parseColor("#5FD6B4"))

    val params = FrameLayout.LayoutParams(
      18.dp(),
      18.dp(),
      Gravity.CENTER
    )
    layoutParams = params
  }

  checkBox.addView(checkIcon)

  row.addView(iconBox)
  row.addView(textBox)
  row.addView(checkBox)

  card.addView(row)

  return card
}

private fun showCompleteTaskDialog(
  task: CareTaskUi
) {
  DialogManager.showConfirmDialog(
    context = requireContext(),
    type = DialogType.SUCCESS,
    title = "Complete Task?",
    message = "\"${task.title}\" will be marked as completed.",
    confirmTextResId = R.string.confirm,
    cancelTextResId = R.string.cancel,
    onConfirm = {
      maintenanceViewModel.completeTask(
        taskId = task.id
      )
    }
  )
}

private fun openAddCareTaskScreen() {
  findNavController().navigate(
    R.id.addCareTaskFragment
  )
}

private fun openTaskDetailScreen(
  task: CareTaskUi
) {
  findNavController().navigate(
    R.id.action_aquariumMaintenanceFragment_to_taskDetailFragment,
    androidx.core.os.bundleOf(
      "taskId" to task.id
    )
  )
}

private fun buildHistoryMetaText(
  task: CareTaskUi
): String {
  val completedAt = task.completedAtMillis ?: task.dueAtMillis

  return buildString {
    append(task.tankName)
    append(" • ")
    append(formatHistoryTime(completedAt))

    if (task.sourceLabel.isNotBlank()) {
      append(" • ")
      append(task.sourceLabel)
    }
  }
}

private fun formatHistoryDate(
  millis: Long
): String {
  return SimpleDateFormat(
    "dd.MM.yyyy",
    Locale.getDefault()
  ).format(Date(millis))
}

private fun formatHistoryTime(
  millis: Long
): String {
  return SimpleDateFormat(
    "HH:mm",
    Locale.getDefault()
  ).format(Date(millis))
}

private fun getHistoryDateKey(
  millis: Long
): String {
  return SimpleDateFormat(
    "yyyyMMdd",
    Locale.getDefault()
  ).format(Date(millis))
}

private fun isToday(
  millis: Long
): Boolean {
  val target = Calendar.getInstance().apply {
    timeInMillis = millis
  }

  val today = Calendar.getInstance()

  return target.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
  target.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
}

private fun createHistoryIconBackground(
  color: Int
): GradientDrawable {
  return GradientDrawable().apply {
    shape = GradientDrawable.RECTANGLE
    cornerRadius = 13.dp().toFloat()
    setColor(
      applyAlpha(
        color = color,
        alpha = 0.26f
      )
    )
    setStroke(
      1.dp(),
      applyAlpha(
        color = color,
        alpha = 0.7f
      )
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
  binding.rvCareTasks.adapter = null
  _binding = null

  super.onDestroyView()
}

companion object {
  private const val HISTORY_AXIS_WIDTH_DP = 42
}
}