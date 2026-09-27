package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.ItemTankHealthAnalysisHistoryRecordBinding

internal class TankHealthAnalysisHistoryAdapter(
    private val onRecordClick: (TankHealthAnalysisHistoryRecord) -> Unit
) : ListAdapter<
    TankHealthAnalysisHistoryRecord,
    TankHealthAnalysisHistoryAdapter.RecordViewHolder
>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder =
        RecordViewHolder(
            binding = ItemTankHealthAnalysisHistoryRecordBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            ),
            onRecordClick = onRecordClick
        )

    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun submitItems(nextItems: List<TankHealthAnalysisHistoryRecord>) {
        submitList(nextItems)
    }

    internal class RecordViewHolder(
        private val binding: ItemTankHealthAnalysisHistoryRecordBinding,
        private val onRecordClick: (TankHealthAnalysisHistoryRecord) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TankHealthAnalysisHistoryRecord) {
            val context = binding.root.context
            binding.tvDate.text = item.dateText
            binding.tvTime.text = item.timeText
            binding.metricsRow.isVisible = item.metrics.isNotEmpty()
            val statusColor = ContextCompat.getColor(context, R.color.aqua_content_muted)
            val cards = listOf(
                binding.metricCardFirst,
                binding.metricCardSecond,
                binding.metricCardThird
            )
            val labels = listOf(
                binding.tvMetricFirstLabel,
                binding.tvMetricSecondLabel,
                binding.tvMetricThirdLabel
            )
            val values = listOf(
                binding.tvMetricFirstValue,
                binding.tvMetricSecondValue,
                binding.tvMetricThirdValue
            )
            val statuses = listOf(
                binding.tvMetricFirstStatus,
                binding.tvMetricSecondStatus,
                binding.tvMetricThirdStatus
            )
            cards.forEachIndexed { index, card ->
                val metric = item.metrics.getOrNull(index)
                card.isVisible = metric != null
                if (metric != null) {
                    labels[index].text = metric.labelText
                    values[index].text = metric.valueText
                    statuses[index].text = context.getString(R.string.tank_health_analysis_recorded)
                    statuses[index].setTextColor(statusColor)
                }
            }

            binding.root.contentDescription = buildString {
                append(item.dateText)
                append(' ')
                append(item.timeText)
                item.metrics.forEach { metric ->
                    append(", ")
                    append(metric.labelText)
                    append(' ')
                    append(metric.valueText)
                }
            }

            binding.root.setOnClickListener { onRecordClick(item) }
        }
    }

    private companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<TankHealthAnalysisHistoryRecord>() {
            override fun areItemsTheSame(
                oldItem: TankHealthAnalysisHistoryRecord,
                newItem: TankHealthAnalysisHistoryRecord
            ): Boolean = oldItem.analysisId == newItem.analysisId

            override fun areContentsTheSame(
                oldItem: TankHealthAnalysisHistoryRecord,
                newItem: TankHealthAnalysisHistoryRecord
            ): Boolean = oldItem == newItem
        }
    }
}

internal data class TankHealthAnalysisHistoryRecord(
    val analysisId: Long,
    val dateText: String,
    val timeText: String,
    val metrics: List<TankHealthHistoryMetric>
)

internal data class TankHealthHistoryMetric(
    val labelText: String,
    val valueText: String
)
