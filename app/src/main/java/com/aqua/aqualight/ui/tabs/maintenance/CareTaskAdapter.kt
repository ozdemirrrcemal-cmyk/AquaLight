package com.aqua.aqualight.ui.tabs.maintenance

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.databinding.ItemCareTaskBinding
import com.aqua.aqualight.ui.tabs.maintenance.model.CareTaskSource
import com.aqua.aqualight.ui.tabs.maintenance.model.CareTaskStatus
import com.aqua.aqualight.ui.tabs.maintenance.model.CareTaskUi

class CareTaskAdapter(
  private val onCompleteClick: (CareTaskUi) -> Unit,
  private val onTaskClick: (CareTaskUi) -> Unit
) : ListAdapter<CareTaskUi, CareTaskAdapter.CareTaskViewHolder>(
  CareTaskDiffCallback
) {

  override fun onCreateViewHolder(
    parent: ViewGroup,
    viewType: Int
  ): CareTaskViewHolder {
    val binding = ItemCareTaskBinding.inflate(
      LayoutInflater.from(parent.context),
      parent,
      false
    )

    return CareTaskViewHolder(binding)
  }

  override fun onBindViewHolder(
    holder: CareTaskViewHolder,
    position: Int
  ) {
    holder.bind(
      item = getItem(position)
    )
  }

  inner class CareTaskViewHolder(
    private val binding: ItemCareTaskBinding
  ) : RecyclerView.ViewHolder(binding.root) {

    fun bind(
      item: CareTaskUi
    ) {
      val accentColor = Color.parseColor(item.accentColor)

      binding.ivTaskIcon.setImageResource(item.iconRes)

      binding.iconContainer.background = createIconBackground(
        color = accentColor
      )

      binding.tvTaskTitle.text = item.title

      binding.tvTaskMeta.text = buildTaskMetaText(item)

      binding.tvTaskDescription.text = item.description

      binding.tvTaskSecondary.text = item.secondaryText

      binding.tvSourceBadge.text = item.sourceLabel

      binding.tvSourceBadge.background = createSourceBadgeBackground(
        source = item.source
      )

      binding.tvSourceBadge.setTextColor(
        if (item.source == CareTaskSource.AUTOMATIC) {
          Color.parseColor("#5FD6B4")
        } else {
          Color.parseColor("#B8C7D9")
        }
      )

      binding.btnCompleteTask.isVisible =
        item.status == CareTaskStatus.PENDING

      binding.tvCompletedBadge.isVisible =
        item.status == CareTaskStatus.COMPLETED

      binding.tvTaskSecondary.setTextColor(
        when {
          item.status == CareTaskStatus.COMPLETED -> {
            Color.parseColor("#5FD6B4")
          }

          item.isOverdue -> {
            Color.parseColor("#D85C5C")
          }

          else -> {
            Color.parseColor("#5FD6B4")
          }
        }
      )

      binding.tvTaskTitle.setTypeface(
        null,
        if (item.status == CareTaskStatus.COMPLETED) {
          Typeface.NORMAL
        } else {
          Typeface.BOLD
        }
      )

      binding.btnCompleteTask.setOnClickListener {
        onCompleteClick(item)
      }

      binding.root.setOnClickListener {
        onTaskClick(item)
      }
    }

    private fun buildTaskMetaText(
      item: CareTaskUi
    ): String {
      return buildString {
        append(item.tankName)

        if (item.primaryTimeText.isNotBlank()) {
          append(" • ")
          append(item.primaryTimeText)
        }

        if (item.isOverdue) {
          append(" • Overdue")
        }
      }
    }

    private fun createIconBackground(
      color: Int
    ): GradientDrawable {
      return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 17.dp().toFloat()
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
        cornerRadius = 12.dp().toFloat()
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

    private fun Int.dp(): Int {
      return (
        this * binding.root.resources.displayMetrics.density
      ).toInt()
    }
  }

  private object CareTaskDiffCallback : DiffUtil.ItemCallback<CareTaskUi>() {

    override fun areItemsTheSame(
      oldItem: CareTaskUi,
      newItem: CareTaskUi
    ): Boolean {
      return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(
      oldItem: CareTaskUi,
      newItem: CareTaskUi
    ): Boolean {
      return oldItem == newItem
    }
  }
}