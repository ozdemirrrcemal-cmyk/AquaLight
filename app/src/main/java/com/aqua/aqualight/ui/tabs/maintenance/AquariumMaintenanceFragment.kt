package com.aqua.aqualight.ui.tabs.maintenance

import android.graphics.Color
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
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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

class AquariumMaintenanceFragment :
  Fragment(R.layout.fragment_aquarium_maintenance) {

  private var _binding: FragmentAquariumMaintenanceBinding? = null
  private val binding get() = _binding!!

  private val maintenanceViewModel: MaintenanceViewModel by activityViewModels()
  private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

  private lateinit var adapter: CareTaskAdapter
  private var currentSelectedTab: MaintenanceTab = MaintenanceTab.ALL

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
      onCompleteClick = { task ->
        showCompleteTaskDialog(task)
      },
      onTaskClick = { task ->
        showTaskDetailsPlaceholder(task)
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
  }

  private fun observeTanks() {
    aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
      maintenanceViewModel.setTanks(tanks)
    }
  }

  private fun observeSelectedTab() {
    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        maintenanceViewModel.selectedTab.collect { selectedTab ->
          renderSelectedTab(selectedTab)
          renderEmptyText(selectedTab)
        }
      }
    }
  }

  private fun observeCareTasks() {
    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        maintenanceViewModel.taskItems.collect { tasks ->
          adapter.submitList(tasks)
          renderTaskListState(tasks)
        }
      }
    }
  }

  private fun renderTaskListState(
    tasks: List<CareTaskUi>
  ) {
    val hasTasks = tasks.isNotEmpty()

    if (currentSelectedTab == MaintenanceTab.HISTORY) {
      binding.rvCareTasks.isVisible = false
      binding.historyScrollView.isVisible = hasTasks
      binding.emptyStateContainer.isVisible = !hasTasks

      if (hasTasks) {
        renderHistoryTimeline(tasks)
      } else {
        binding.historyTimelineContainer.removeAllViews()
      }

      return
    }

    binding.historyScrollView.isVisible = false
    binding.rvCareTasks.isVisible = hasTasks
    binding.emptyStateContainer.isVisible = !hasTasks
  }

  private fun renderSelectedTab(
    selectedTab: MaintenanceTab
  ) {
    currentSelectedTab = selectedTab

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

    val groupedTasks = tasks.groupBy { task ->
      getHistoryDateKey(task.completedAtMillis ?: task.dueAtMillis)
    }

    groupedTasks.forEach { (_, dayTasks) ->
      val firstTask = dayTasks.firstOrNull() ?: return@forEach

      binding.historyTimelineContainer.addView(
        createHistoryDateHeader(
          millis = firstTask.completedAtMillis ?: firstTask.dueAtMillis
        )
      )

      dayTasks.forEach { task ->
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
      params.bottomMargin = 10.dp()
      layoutParams = params
    }

    val nodeBox = FrameLayout(requireContext()).apply {
      layoutParams = LinearLayout.LayoutParams(
        48.dp(),
        42.dp()
      )
    }

    val node = View(requireContext()).apply {
      setBackgroundResource(R.drawable.bg_history_timeline_node)

      val params = FrameLayout.LayoutParams(
        24.dp(),
        24.dp(),
        Gravity.CENTER
      )
      layoutParams = params
    }

    nodeBox.addView(node)

    val dateText = TextView(requireContext()).apply {
      text = formatHistoryDate(millis)
      textSize = 15f
      setTextColor(Color.WHITE)
      setTypeface(null, Typeface.BOLD)
      includeFontPadding = false

      layoutParams = LinearLayout.LayoutParams(
        0,
        LinearLayout.LayoutParams.WRAP_CONTENT,
        1f
      )
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

      val params = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )
      params.bottomMargin = 14.dp()
      layoutParams = params
    }

    val lineBox = FrameLayout(requireContext()).apply {
      layoutParams = LinearLayout.LayoutParams(
        48.dp(),
        LinearLayout.LayoutParams.MATCH_PARENT
      )
    }

    val line = View(requireContext()).apply {
      setBackgroundColor(Color.parseColor("#2A4566"))

      val params = FrameLayout.LayoutParams(
        2.dp(),
        FrameLayout.LayoutParams.MATCH_PARENT,
        Gravity.CENTER_HORIZONTAL
      )
      layoutParams = params
    }

    lineBox.addView(line)

    row.addView(lineBox)
    row.addView(
      createHistoryTaskCard(task)
    )

    return row
  }

  private fun createHistoryTaskCard(
    task: CareTaskUi
  ): View {
    val card = MaterialCardView(requireContext()).apply {
      radius = 20.dp().toFloat()
      strokeWidth = 1.dp()
      strokeColor = Color.parseColor("#2B6F5A")
      setCardBackgroundColor(Color.parseColor("#12382F"))
      cardElevation = 0f
      useCompatPadding = false

      layoutParams = LinearLayout.LayoutParams(
        0,
        LinearLayout.LayoutParams.WRAP_CONTENT,
        1f
      )
    }

    val row = LinearLayout(requireContext()).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL

      setPadding(
        14.dp(),
        14.dp(),
        14.dp(),
        14.dp()
      )
    }

    val iconBox = FrameLayout(requireContext()).apply {
      background = createHistoryIconBackground(
        color = Color.parseColor(task.accentColor)
      )

      layoutParams = LinearLayout.LayoutParams(
        50.dp(),
        50.dp()
      )
    }

    val icon = ImageView(requireContext()).apply {
      setImageResource(task.iconRes)
      setColorFilter(Color.WHITE)

      val params = FrameLayout.LayoutParams(
        28.dp(),
        28.dp(),
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
      params.marginStart = 14.dp()
      params.marginEnd = 10.dp()
      layoutParams = params
    }

    val titleText = TextView(requireContext()).apply {
      text = task.title
      textSize = 14.5f
      setTextColor(Color.WHITE)
      setTypeface(null, Typeface.BOLD)
      includeFontPadding = false
      maxLines = 1
      ellipsize = TextUtils.TruncateAt.END
    }

    val metaText = TextView(requireContext()).apply {
      text = buildHistoryMetaText(task)
      textSize = 12.5f
      setTextColor(Color.parseColor("#B8C7D9"))
      includeFontPadding = false
      maxLines = 1
      ellipsize = TextUtils.TruncateAt.END

      val params = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )
      params.topMargin = 7.dp()
      layoutParams = params
    }

    val completedText = TextView(requireContext()).apply {
      text = "Completed"
      textSize = 12.5f
      setTextColor(Color.parseColor("#5FD6B4"))
      setTypeface(null, Typeface.BOLD)
      includeFontPadding = false

      val params = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )
      params.topMargin = 7.dp()
      layoutParams = params
    }

    textBox.addView(titleText)
    textBox.addView(metaText)
    textBox.addView(completedText)

    val checkBox = FrameLayout(requireContext()).apply {
      setBackgroundResource(R.drawable.bg_history_task_completed_icon)

      layoutParams = LinearLayout.LayoutParams(
        38.dp(),
        38.dp()
      )
    }

    val checkIcon = ImageView(requireContext()).apply {
      setImageResource(R.drawable.ic_check_20)
      setColorFilter(Color.parseColor("#5FD6B4"))

      val params = FrameLayout.LayoutParams(
        20.dp(),
        20.dp(),
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

  private fun showTaskDetailsPlaceholder(
    task: CareTaskUi
  ) {
    Toast.makeText(
      requireContext(),
      task.title,
      Toast.LENGTH_SHORT
    ).show()
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
      cornerRadius = 17.dp().toFloat()
      setColor(
        applyAlpha(
          color = color,
          alpha = 0.28f
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
}