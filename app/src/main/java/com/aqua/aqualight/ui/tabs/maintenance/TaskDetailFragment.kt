package com.aqua.aqualight.ui.tabs.maintenance

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import com.aqua.aqualight.databinding.FragmentTaskDetailBinding
import com.aqua.aqualight.databinding.ItemTaskDetailRowBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.AquaHeaderPillTextAction
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.data.care.model.CareTaskSource
import com.aqua.aqualight.data.care.model.CareTaskStatus
import com.aqua.aqualight.ui.tabs.maintenance.model.CareTaskUi
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.navigation.fragment.navArgs

class TaskDetailFragment :
  Fragment(R.layout.fragment_task_detail) {

  private val args: TaskDetailFragmentArgs by navArgs()

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

    taskId = args.taskId

    if (taskId <= 0L) {
      findNavController().popBackStack()
      return
    }

    setupHeader()
    setupClickListeners()
    observeTanks()
    observeTask()
  }

  private fun setupHeader(
    task: CareTaskUi? = currentTask
  ) {
    val canEdit =
      task?.source == CareTaskSource.MANUAL &&
        task.status == CareTaskStatus.PENDING

    binding.appHeader.setupAquaHeader(
      fragment = this,
      config = AquaHeaderConfig(
        titleOverride = getString(R.string.maintenance_task_detail_title),
        onBackClick = {
          findNavController().popBackStack()
        },
        pillTextAction = if (canEdit && task != null) {
          AquaHeaderPillTextAction(
            text = getString(R.string.maintenance_edit),
            backgroundRes = R.drawable.bg_maintenance_tab_selected,
            contentDescription = getString(R.string.maintenance_edit_task),
            onClick = {
              openEditTaskScreen(task)
            }
          )
        } else {
          null
        }
      )
    )
  }

  private fun setupClickListeners() {
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
    aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
      maintenanceViewModel.setTanks(tanks)
    }
  }

  private fun observeTask() {
    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        maintenanceViewModel.taskByIdFlow(taskId).collect { task ->
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
    setupHeader(task)

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
      label = getString(R.string.maintenance_task_type_label),
      value = buildTaskTypeText(task)
    )

    bindRow(
      row = binding.rowAquarium,
      label = getString(R.string.maintenance_aquarium_label),
      value = task.tankName
    )

    bindRow(
      row = binding.rowScheduledDate,
      label = getString(R.string.maintenance_scheduled_date),
      value = formatDate(task.dueAtMillis)
    )

    bindRow(
      row = binding.rowScheduledTime,
      label = getString(R.string.maintenance_scheduled_time),
      value = formatTime(task.dueAtMillis)
    )

    bindRow(
      row = binding.rowRepeat,
      label = getString(R.string.maintenance_repeat_label),
      value = if (task.repeatEnabled) {
        getString(
          R.string.maintenance_every_days,
          task.repeatIntervalDays.coerceAtLeast(1)
        )
      } else {
        getString(R.string.maintenance_off)
      }
    )

    bindRow(
      row = binding.rowReminder,
      label = getString(R.string.maintenance_reminder_label),
      value = if (task.reminderEnabled) {
        getString(R.string.maintenance_on)
      } else {
        getString(R.string.maintenance_off)
      }
    )

    bindRow(
      row = binding.rowMissedReminder,
      label = getString(R.string.maintenance_missed_reminder_detail_label),
      value = if (task.reminderEnabled && task.missedReminderEnabled) {
        getString(
          R.string.maintenance_every_days_if_missed,
          task.missedReminderDays.coerceAtLeast(1)
        )
      } else {
        getString(R.string.maintenance_off)
      }
    )

    bindRow(
      row = binding.rowCreatedDate,
      label = getString(R.string.maintenance_created_date),
      value = formatDateTime(task.createdAtMillis)
    )

    binding.tvDescriptionValue.text = task.description.ifBlank {
      getString(R.string.maintenance_no_description)
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
      getString(
        R.string.maintenance_task_category_with_percent,
        task.typeTitle,
        task.waterChangePercent
      )
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
          getString(R.string.maintenance_status_pending_overdue)
        } else {
          getString(R.string.maintenance_status_pending)
        }
      }

      CareTaskStatus.COMPLETED -> {
        val completedAt = task.completedAtMillis

        if (completedAt == null || completedAt <= 0L) {
          getString(R.string.maintenance_status_completed)
        } else {
          getString(
            R.string.maintenance_status_completed_with_date,
            formatDateTime(completedAt)
          )
        }
      }
    }
  }

  private fun openEditTaskScreen(
    task: CareTaskUi
  ) {
    findNavController().navigate(
      TaskDetailFragmentDirections.actionTaskDetailFragmentToAddCareTaskFragment(
        taskId = task.id
      )
    )
  }

  private fun showCompleteTaskDialog(
    task: CareTaskUi
  ) {
    DialogManager.showConfirmDialog(
      context = requireContext(),
      type = DialogType.SUCCESS,
      title = getString(R.string.maintenance_dialog_complete_task_title),
      message = getString(
        R.string.maintenance_dialog_complete_task_message,
        task.title
      ),
      confirmTextResId = R.string.confirm,
      cancelTextResId = R.string.cancel,
      onConfirm = {
        viewLifecycleOwner.lifecycleScope.launch {
          try {
            showGlobalLoading(true)

            maintenanceViewModel.completeTask(
              taskId = task.id
            ).join()

            findNavController().popBackStack()
          } finally {
            showGlobalLoading(false)
          }
        }
      }
    )
  }

  private fun showDeleteTaskDialog(
    task: CareTaskUi
  ) {
    DialogManager.showConfirmDialog(
      context = requireContext(),
      type = DialogType.WARNING,
      title = getString(R.string.maintenance_dialog_delete_task_title),
      message = getString(
        R.string.maintenance_dialog_delete_task_message,
        task.title
      ),
      confirmTextResId = R.string.confirm,
      cancelTextResId = R.string.cancel,
      onConfirm = {
        viewLifecycleOwner.lifecycleScope.launch {
          var deleteFailed = false

          try {
            showGlobalLoading(true)

            maintenanceViewModel.deleteManualTask(
              taskId = task.id
            )

            findNavController().popBackStack()
          } catch (_: Exception) {
            deleteFailed = true
          } finally {
            showGlobalLoading(false)
          }

          if (deleteFailed && _binding != null) {
            DialogManager.showInfoDialog(
              context = requireContext(),
              type = DialogType.ERROR,
              title = getString(R.string.maintenance_delete_failed_title),
              message = getString(R.string.maintenance_delete_failed_message)
            )
          }
        }
      }
    )
  }

  private fun showGlobalLoading(
    show: Boolean
  ) {
    setFragmentGlobalLoading(show)
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
