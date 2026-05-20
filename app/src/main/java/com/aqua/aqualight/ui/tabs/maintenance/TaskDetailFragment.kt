package com.aqua.aqualight.ui.tabs.maintenance

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTaskDetailBinding
import com.aqua.aqualight.databinding.ItemTaskDetailRowBinding
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.maintenance.model.CareTaskSource
import com.aqua.aqualight.ui.tabs.maintenance.model.CareTaskStatus
import com.aqua.aqualight.ui.tabs.maintenance.model.CareTaskUi
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TaskDetailFragment :
Fragment(R.layout.fragment_task_detail) {

  private var _binding: FragmentTaskDetailBinding? = null
  private val binding get() = _binding!!

  private val maintenanceViewModel: MaintenanceViewModel by activityViewModels()
  private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

  private var taskId: Long = -1L
  private var currentTask: CareTaskUi? = null

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?
  ) {
    super.onViewCreated(
      view,
      savedInstanceState
    )

    _binding = FragmentTaskDetailBinding.bind(view)

    taskId = requireArguments().getLong(
      "taskId",
      -1L
    )

    if (taskId <= 0L) {
      findNavController().popBackStack()
      return
    }

    setupClickListeners()
    observeTanks()
    observeTask()
  }

  private fun setupClickListeners() {
    binding.btnBack.setOnClickListener {
      findNavController().popBackStack()
    }

    binding.btnEditTask.setOnClickListener {
      val task = currentTask ?: return@setOnClickListener

      if (
        task.source == CareTaskSource.MANUAL &&
        task.status == CareTaskStatus.PENDING
      ) {
        openEditTaskScreen(task)
      }
    }

    binding.btnDeleteTask.setOnClickListener {
      val task = currentTask ?: return@setOnClickListener

      if (task.source == CareTaskSource.MANUAL) {
        showDeleteTaskDialog(task)
      }
    }

    binding.btnCompleteTask.setOnClickListener {
      val task = currentTask ?: return@setOnClickListener

      if (task.status == CareTaskStatus.PENDING) {
        showCompleteTaskDialog(task)
      }
    }
  }

  private fun observeTanks() {
    aquariumTankViewModel.tanks.observe(viewLifecycleOwner) {
      tanks ->
      maintenanceViewModel.setTanks(tanks)
    }
  }

  private fun observeTask() {
    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        maintenanceViewModel.taskByIdFlow(taskId).collect {
          task ->
          if (task == null) {
            return@collect
          }

          currentTask = task
          renderTask(task)
        }
      }
    }
  }

  private fun renderTask(
    task: CareTaskUi
  ) {
    val accentColor = Color.parseColor(task.accentColor)

    binding.ivTaskIcon.setImageResource(task.iconRes)

    binding.iconContainer.background = createIconBackground(
      color = accentColor
    )

    binding.tvTaskTitle.text = task.title

    binding.tvSourceBadge.text = task.sourceLabel
    binding.tvSourceBadge.background = createSourceBadgeBackground(
      source = task.source
    )

    binding.tvSourceBadge.setTextColor(
      if (task.source == CareTaskSource.AUTOMATIC) {
        Color.parseColor("#5FD6B4")
      } else {
        Color.parseColor("#B8C7D9")
      }
    )

    binding.tvStatusValue.text = buildStatusText(task)

    bindRow(
      row = binding.rowTaskType,
      label = "Task Type",
      value = buildTaskTypeText(task)
    )

    bindRow(
      row = binding.rowAquarium,
      label = "Aquarium",
      value = task.tankName
    )

    bindRow(
      row = binding.rowScheduledDate,
      label = "Scheduled Date",
      value = formatDate(task.dueAtMillis)
    )

    bindRow(
      row = binding.rowScheduledTime,
      label = "Scheduled Time",
      value = formatTime(task.dueAtMillis)
    )

    bindRow(
      row = binding.rowRepeat,
      label = "Repeat",
      value = if (task.repeatEnabled) {
        "Every ${task.repeatIntervalDays.coerceAtLeast(1)} days"
      } else {
        "Off"
      }
    )

    bindRow(
      row = binding.rowReminder,
      label = "Reminder",
      value = if (task.reminderEnabled) {
        "On"
      } else {
        "Off"
      }
    )

    bindRow(
      row = binding.rowMissedReminder,
      label = "Missed Reminder",
      value = if (task.reminderEnabled && task.missedReminderEnabled) {
        "Every ${task.missedReminderDays.coerceAtLeast(1)} days if missed"
      } else {
        "Off"
      }
    )

    bindRow(
      row = binding.rowCreatedDate,
      label = "Created Date",
      value = formatDateTime(task.createdAtMillis)
    )

    binding.tvDescriptionValue.text = task.description.ifBlank {
      "No description"
    }

    val hasNote = task.note.isNotBlank()
    binding.tvNoteLabel.isVisible = hasNote
    binding.noteCard.isVisible = hasNote
    binding.tvNoteValue.text = task.note

    renderActionState(task)
  }

  private fun renderActionState(
    task: CareTaskUi
  ) {
    val isManual = task.source == CareTaskSource.MANUAL
    val isPending = task.status == CareTaskStatus.PENDING

    binding.btnCompleteTask.isVisible = isPending

    binding.btnEditTask.isVisible = isManual && isPending
    binding.btnDeleteTask.isVisible = isManual

    binding.tvAutoTaskInfo.isVisible = !isManual
  }

  private fun bindRow(
    row: ItemTaskDetailRowBinding,
    label: String,
    value: String
  ) {
    row.tvRowLabel.text = label
    row.tvRowValue.text = value
  }

  private fun buildTaskTypeText(
    task: CareTaskUi
  ): String {
    return if (
      task.waterChangePercent != null &&
      task.waterChangePercent > 0
    ) {
      "${task.typeTitle} • ${task.waterChangePercent}%"
    } else {
      task.typeTitle
    }
  }

  private fun buildStatusText(
    task: CareTaskUi
  ): String {
    return when (task.status) {
      CareTaskStatus.PENDING -> {
        if (task.isOverdue) {
          "Pending • Overdue"
        } else {
          "Pending"
        }
      }

      CareTaskStatus.COMPLETED -> {
        val completedAt = task.completedAtMillis

        if (completedAt == null || completedAt <= 0L) {
          "Completed"
        } else {
          "Completed • ${formatDateTime(completedAt)}"
        }
      }
    }
  }

  private fun openEditTaskScreen(
    task: CareTaskUi
  ) {
    findNavController().navigate(
      R.id.action_taskDetailFragment_to_addCareTaskFragment,
      bundleOf(
        "taskId" to task.id
      )
    )
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

        findNavController().popBackStack()
      }
    )
  }

  private fun showDeleteTaskDialog(
    task: CareTaskUi
  ) {
    DialogManager.showConfirmDialog(
      context = requireContext(),
      type = DialogType.WARNING,
      title = "Delete Task?",
      message = "\"${task.title}\" will be permanently deleted.",
      confirmTextResId = R.string.confirm,
      cancelTextResId = R.string.cancel,
      onConfirm = {
        viewLifecycleOwner.lifecycleScope.launch {
          runCatching {
            maintenanceViewModel.deleteManualTask(
              taskId = task.id
            )
          }.onSuccess {
            findNavController().popBackStack()
          }.onFailure {
            DialogManager.showInfoDialog(
              context = requireContext(),
              type = DialogType.ERROR,
              title = "Delete Failed",
              message = "Task could not be deleted. Please try again."
            )
          }
        }
      }
    )
  }

  private fun createIconBackground(
    color: Int
  ): GradientDrawable {
    return GradientDrawable().apply {
      shape = GradientDrawable.RECTANGLE
      cornerRadius = 16.dp().toFloat()
      setColor(
        applyAlpha(
          color = color,
          alpha = 0.24f
        )
      )
      setStroke(
        1.dp(),
        applyAlpha(
          color = color,
          alpha = 0.65f
        )
      )
    }
  }

  private fun createSourceBadgeBackground(
    source: CareTaskSource
  ): GradientDrawable {
    val color = if (source == CareTaskSource.AUTOMATIC) {
      Color.parseColor("#12382F")
    } else {
      Color.parseColor("#1C3252")
    }

    val strokeColor = if (source == CareTaskSource.AUTOMATIC) {
      Color.parseColor("#2B6F5A")
    } else {
      Color.parseColor("#2A4566")
    }

    return GradientDrawable().apply {
      shape = GradientDrawable.RECTANGLE
      cornerRadius = 11.dp().toFloat()
      setColor(color)
      setStroke(
        1.dp(),
        strokeColor
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

  private fun formatDate(
    millis: Long
  ): String {
    return SimpleDateFormat(
      "dd.MM.yyyy",
      Locale.getDefault()
    ).format(Date(millis))
  }

  private fun formatTime(
    millis: Long
  ): String {
    return SimpleDateFormat(
      "HH:mm",
      Locale.getDefault()
    ).format(Date(millis))
  }

  private fun formatDateTime(
    millis: Long
  ): String {
    return SimpleDateFormat(
      "dd.MM.yyyy HH:mm",
      Locale.getDefault()
    ).format(Date(millis))
  }

  private fun Int.dp(): Int {
    return (this * resources.displayMetrics.density).toInt()
  }

  override fun onDestroyView() {
    _binding = null
    super.onDestroyView()
  }
}