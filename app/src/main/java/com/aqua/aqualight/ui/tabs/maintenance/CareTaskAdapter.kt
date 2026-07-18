package com.aqua.aqualight.ui.tabs.maintenance

import com.aqua.aqualight.ui.common.text.setTextSizeResource
import androidx.core.content.ContextCompat
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.ItemCareTaskBinding
import com.aqua.aqualight.application.care.CareTaskSource
import com.aqua.aqualight.application.care.CareTaskStatus
import com.aqua.aqualight.ui.tabs.maintenance.model.CareTaskUi
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CareTaskAdapter(
  private val context: Context,
  private val onTaskClick: (CareTaskUi) -> Unit
) : ListAdapter<CareTaskAdapter.CareTaskListItem, RecyclerView.ViewHolder>(
  CareTaskListDiffCallback
) {

  fun submitCareTasks(
    tasks: List<CareTaskUi>,
    showDateHeaders: Boolean
  ) {
    val rows = if (showDateHeaders) {
      createRowsWithDateHeaders(tasks)
    } else {
      tasks.map {
        task ->
        CareTaskListItem.TaskItem(task)
      }
    }

    submitList(rows)
  }

  override fun getItemViewType(
    position: Int
  ): Int {
    return when (getItem(position)) {
      is CareTaskListItem.DateHeader -> VIEW_TYPE_DATE_HEADER
      is CareTaskListItem.TaskItem -> VIEW_TYPE_TASK
    }
  }

  override fun onCreateViewHolder(
    parent: ViewGroup,
    viewType: Int
  ): RecyclerView.ViewHolder {
    return when (viewType) {
      VIEW_TYPE_DATE_HEADER -> {
        val view = LayoutInflater.from(parent.context).inflate(
          R.layout.item_care_task_date_header,
          parent,
          false
        )

        DateHeaderViewHolder(view)
      } else -> {
        val binding = ItemCareTaskBinding.inflate(
          LayoutInflater.from(parent.context),
          parent,
          false
        )

        CareTaskViewHolder(binding)
      }
    }
  }

  override fun onBindViewHolder(
    holder: RecyclerView.ViewHolder,
    position: Int
  ) {
    when (val item = getItem(position)) {
      is CareTaskListItem.DateHeader -> {
        (holder as DateHeaderViewHolder).bind(item.title)
      }

      is CareTaskListItem.TaskItem -> {
        (holder as CareTaskViewHolder).bind(item.task)
      }
    }
  }

  private fun createRowsWithDateHeaders(
    tasks: List<CareTaskUi>
  ): List<CareTaskListItem> {
    val rows = mutableListOf<CareTaskListItem>()

    val groupedTasks = tasks
    .sortedBy {
      task ->
      getTaskHeaderMillis(task)
    }
    .groupBy {
      task ->
      formatDateKey(
        getTaskHeaderMillis(task)
      )
    }

    groupedTasks.forEach {
      (_, dayTasks) ->
      val firstTask = dayTasks.firstOrNull() ?: return@forEach
      val headerMillis = getTaskHeaderMillis(firstTask)

      rows.add(
        CareTaskListItem.DateHeader(
          key = formatDateKey(headerMillis),
          title = formatDateHeaderWithRelative(headerMillis)
        )
      )

      dayTasks.forEach {
        task ->
        rows.add(
          CareTaskListItem.TaskItem(task)
        )
      }
    }

    return rows
  }

  private fun getTaskHeaderMillis(
    task: CareTaskUi
  ): Long {
    return if (task.status == CareTaskStatus.COMPLETED) {
      task.completedAtMillis ?: task.dueAtMillis
    } else {
      task.dueAtMillis
    }
  }

  private fun formatDateHeaderWithRelative(
    millis: Long
  ): String {
    val dateText = SimpleDateFormat(
      "dd.MM.yyyy",
      Locale.getDefault()
    ).format(Date(millis))

    return when {
      isToday(millis) -> {
        context.getString(
          R.string.maintenance_date_header_relative,
          dateText,
          context.getString(R.string.maintenance_today)
        )
      }

      isTomorrow(millis) -> {
        context.getString(
          R.string.maintenance_date_header_relative,
          dateText,
          context.getString(R.string.maintenance_tomorrow)
        )
      }

      isYesterday(millis) -> {
        context.getString(
          R.string.maintenance_date_header_relative,
          dateText,
          context.getString(R.string.maintenance_yesterday)
        )
      } else -> {
        dateText
      }
    }
  }

  private fun formatDateKey(
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

  private fun isTomorrow(
    millis: Long
  ): Boolean {
    val target = Calendar.getInstance().apply {
      timeInMillis = millis
    }

    val tomorrow = Calendar.getInstance().apply {
      add(Calendar.DAY_OF_YEAR, 1)
    }

    return target.get(Calendar.YEAR) == tomorrow.get(Calendar.YEAR) &&
    target.get(Calendar.DAY_OF_YEAR) == tomorrow.get(Calendar.DAY_OF_YEAR)
  }

  private fun isYesterday(
    millis: Long
  ): Boolean {
    val target = Calendar.getInstance().apply {
      timeInMillis = millis
    }

    val yesterday = Calendar.getInstance().apply {
      add(Calendar.DAY_OF_YEAR, -1)
    }

    return target.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
    target.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)
  }

  inner class DateHeaderViewHolder(
    itemView: View
  ) : RecyclerView.ViewHolder(itemView) {

    private val titleText: TextView = itemView.findViewById(
      R.id.tvDateHeaderTitle
    )

    fun bind(
      title: String
    ) {
      titleText.text = title
      titleText.setTextSizeResource(R.dimen.aqua_text_size_micro_plus)
      titleText.setTypeface(null, Typeface.NORMAL)
      titleText.setTextColor(Color.WHITE)
    }
  }

  inner class CareTaskViewHolder(
    private val binding: ItemCareTaskBinding
  ) : RecyclerView.ViewHolder(binding.root) {

    fun bind(
      item: CareTaskUi
    ) {
      val accentColor = item.accentColor

      binding.ivTaskIcon.setImageResource(item.iconRes)

      binding.iconContainer.background = createIconBackground(
        color = accentColor
      )

      binding.tvTaskTitle.text = item.title
      binding.tvTaskTitle.setTextSizeResource(R.dimen.aqua_text_size_body_precise)
      binding.tvTaskTitle.setTextColor(Color.WHITE)
      binding.tvTaskTitle.setTypeface(
        null,
        if (item.status == CareTaskStatus.COMPLETED) {
          Typeface.NORMAL
        } else {
          Typeface.BOLD
        }
      )

      binding.tvSourceBadge.text = item.sourceLabel
      binding.tvSourceBadge.setTextSizeResource(R.dimen.aqua_text_size_badge_compact)
      binding.tvSourceBadge.background = createSourceBadgeBackground(
        source = item.source
      )

      binding.tvSourceBadge.setTextColor(
        if (item.source == CareTaskSource.AUTOMATIC) {
          ContextCompat.getColor(binding.root.context, R.color.aqua_palette_hex_5fd6b4)
        } else {
          ContextCompat.getColor(binding.root.context, R.color.aqua_palette_hex_b8c7d9)
        }
      )

      binding.tvTaskMeta.text = buildScheduleText(item)
      binding.tvTaskMeta.setTextSizeResource(R.dimen.aqua_text_size_micro_plus)
      binding.tvTaskMeta.setTextColor(
        if (item.isOverdue && item.status == CareTaskStatus.PENDING) {
          ContextCompat.getColor(binding.root.context, R.color.aqua_palette_hex_d85c5c)
        } else {
          ContextCompat.getColor(binding.root.context, R.color.aqua_palette_hex_b8c7d9)
        }
      )

      binding.tvTaskSecondary.text = item.tankName.ifBlank {
        context.getString(R.string.maintenance_aquarium_label)
      }
      binding.tvTaskSecondary.setTextSizeResource(R.dimen.aqua_text_size_micro_plus)
      binding.tvTaskSecondary.setTextColor(
        ContextCompat.getColor(binding.root.context, R.color.aqua_palette_hex_b8c7d9)
      )

      binding.tvTaskDescription.text = item.description
      binding.tvTaskDescription.setTextSizeResource(R.dimen.aqua_text_size_body_precise_small)
      binding.tvTaskDescription.setTextColor(
        ContextCompat.getColor(binding.root.context, R.color.aqua_palette_hex_8fa4be)
      )
      binding.tvTaskDescription.isVisible =
      item.description.isNotBlank()

      binding.tvCompletedBadge.isVisible =
      item.status == CareTaskStatus.COMPLETED

      binding.root.setOnClickListener {
        onTaskClick(item)
      }
    }

    private fun buildScheduleText(
      item: CareTaskUi
    ): String {
      val baseText = item.primaryTimeText.ifBlank {
        context.getString(R.string.maintenance_schedule_text_unavailable)
      }

      return if (item.isOverdue && item.status == CareTaskStatus.PENDING) {
        context.getString(
          R.string.maintenance_schedule_overdue_suffix,
          baseText
        )
      } else {
        baseText
      }
    }

    private fun createIconBackground(
      color: Int
    ): GradientDrawable {
      return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = binding.root.resources.getDimensionPixelOffset(R.dimen.aqua_size_14).toFloat()
        setColor(
          applyAlpha(
            color = color,
            alpha = 0.24f
          )
        )
        setStroke(
          binding.root.resources.getDimensionPixelOffset(R.dimen.aqua_size_1),
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
        ContextCompat.getColor(binding.root.context, R.color.aqua_palette_hex_12382f)
      } else {
        ContextCompat.getColor(binding.root.context, R.color.aqua_palette_hex_1c3252)
      }

      val strokeColor = if (source == CareTaskSource.AUTOMATIC) {
        ContextCompat.getColor(binding.root.context, R.color.aqua_palette_hex_2b6f5a)
      } else {
        ContextCompat.getColor(binding.root.context, R.color.aqua_palette_hex_2a4566)
      }

      return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = binding.root.resources.getDimensionPixelOffset(R.dimen.aqua_size_11).toFloat()
        setColor(color)
        setStroke(
          binding.root.resources.getDimensionPixelOffset(R.dimen.aqua_size_1),
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
  }

  sealed class CareTaskListItem {

    data class DateHeader(
      val key: String,
      val title: String
    ) : CareTaskListItem()

    data class TaskItem(
      val task: CareTaskUi
    ) : CareTaskListItem()
    }

    private object CareTaskListDiffCallback :
    DiffUtil.ItemCallback<CareTaskListItem>() {

      override fun areItemsTheSame(
        oldItem: CareTaskListItem,
        newItem: CareTaskListItem
      ): Boolean {
        return when {
          oldItem is CareTaskListItem.DateHeader &&
          newItem is CareTaskListItem.DateHeader -> {
            oldItem.key == newItem.key
          }

          oldItem is CareTaskListItem.TaskItem &&
          newItem is CareTaskListItem.TaskItem -> {
            oldItem.task.id == newItem.task.id
          } else -> false
        }
      }

      override fun areContentsTheSame(
        oldItem: CareTaskListItem,
        newItem: CareTaskListItem
      ): Boolean {
        return oldItem == newItem
      }
    }

    companion object {
      private const val VIEW_TYPE_DATE_HEADER = 1
      private const val VIEW_TYPE_TASK = 2
    }
  }
