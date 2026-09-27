package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.ItemTankHealthAddAnalysisBinding
import com.aqua.aqualight.databinding.ItemTankHealthMetricBinding
import com.aqua.aqualight.databinding.ItemTankHealthWaterQualityHeaderBinding
import java.text.DateFormat
import java.util.Date

internal class TankHealthContentAdapter(
    private val onAddAnalysisClick: () -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<TankHealthContentItem> = buildItems(emptyList())

    override fun getItemViewType(position: Int): Int =
        when (items[position]) {
            is TankHealthContentItem.WaterQualityHeader -> VIEW_TYPE_WATER_QUALITY_HEADER
            is TankHealthContentItem.Metric -> VIEW_TYPE_METRIC
            TankHealthContentItem.AddAnalysis -> VIEW_TYPE_ADD_ANALYSIS
        }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_WATER_QUALITY_HEADER -> WaterQualityHeaderViewHolder(
                ItemTankHealthWaterQualityHeaderBinding.inflate(
                    inflater,
                    parent,
                    false
                )
            )

            VIEW_TYPE_METRIC -> MetricViewHolder(
                ItemTankHealthMetricBinding.inflate(inflater, parent, false)
            )

            VIEW_TYPE_ADD_ANALYSIS -> {
                val binding = ItemTankHealthAddAnalysisBinding.inflate(
                    inflater,
                    parent,
                    false
                )
                binding.root.setOnClickListener { onAddAnalysisClick() }
                StaticViewHolder(binding.root)
            }

            else -> error("Unsupported Tank Health view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        if (holder is MetricViewHolder && item is TankHealthContentItem.Metric) {
            holder.bind(
                item = item.metric,
                metricIndex = metricIndexAt(position)
            )
        }
        if (
            holder is WaterQualityHeaderViewHolder &&
            item is TankHealthContentItem.WaterQualityHeader
        ) {
            holder.bind(item.measuredAtMillis)
        }
    }

    override fun getItemCount(): Int = items.size

    fun submitWaterMetrics(
        metrics: List<TankHealthWaterMetricUiModel>,
        measuredAtMillis: Long? = null
    ) {
        val previous = items
        val next = buildItems(metrics, measuredAtMillis)
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = previous.size
            override fun getNewListSize(): Int = next.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val old = previous[oldItemPosition]
                val new = next[newItemPosition]
                return when {
                    old is TankHealthContentItem.Metric && new is TankHealthContentItem.Metric ->
                        old.metric.id == new.metric.id
                    else -> old.javaClass == new.javaClass
                }
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                previous[oldItemPosition] == next[newItemPosition]
        })
        items = next
        diff.dispatchUpdatesTo(this)
    }

    fun spanSizeForPosition(position: Int): Int =
        if (items[position] is TankHealthContentItem.Metric) {
            METRIC_SPAN_SIZE
        } else {
            GRID_SPAN_COUNT
        }

    private fun metricIndexAt(position: Int): Int =
        items.take(position).count { item -> item is TankHealthContentItem.Metric }

    private class StaticViewHolder(
        root: android.view.View
    ) : RecyclerView.ViewHolder(root)

    private class WaterQualityHeaderViewHolder(
        private val binding: ItemTankHealthWaterQualityHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(measuredAtMillis: Long?) {
            binding.tvLastAnalysis.text = measuredAtMillis?.let { timestamp ->
                val context = binding.root.context
                context.getString(
                    R.string.tank_health_last_analysis_at,
                    DateFormat.getDateTimeInstance(
                        DateFormat.MEDIUM,
                        DateFormat.SHORT,
                        context.resources.configuration.locales[0]
                    )
                        .format(Date(timestamp))
                )
            } ?: binding.root.context.getString(R.string.tank_health_last_analysis)
        }
    }

    private class MetricViewHolder(
        private val binding: ItemTankHealthMetricBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: TankHealthWaterMetricUiModel,
            metricIndex: Int
        ) {
            val context = binding.root.context
            binding.metricLabel.text = buildString {
                append(context.getString(item.labelRes))
                item.symbolRes?.let { symbolRes ->
                    append(" (")
                    append(context.getString(symbolRes))
                    append(")")
                }
            }
            binding.metricValue.text =
                item.valueText ?: context.getString(R.string.tank_health_value_not_measured)
            binding.metricStatus.text =
                item.statusText ?: context.getString(R.string.tank_health_status_not_measured)
            binding.metricStatus.setTextColor(
                ContextCompat.getColor(context, R.color.aqua_content_muted)
            )

            val layoutParams = binding.root.layoutParams as ViewGroup.MarginLayoutParams
            val horizontalSpacing = context.resources.getDimensionPixelSize(R.dimen.aqua_size_3)
            val rowSpacing = context.resources.getDimensionPixelSize(R.dimen.aqua_size_6)
            val column = metricIndex % GRID_SPAN_COUNT

            layoutParams.marginStart = if (column == 0) 0 else horizontalSpacing
            layoutParams.marginEnd =
                if (column == GRID_SPAN_COUNT - 1) 0 else horizontalSpacing
            layoutParams.bottomMargin =
                if (metricIndex < GRID_SPAN_COUNT) rowSpacing else 0
            binding.root.layoutParams = layoutParams
        }
    }

    private sealed interface TankHealthContentItem {
        data class WaterQualityHeader(val measuredAtMillis: Long?) : TankHealthContentItem
        data class Metric(
            val metric: TankHealthWaterMetricUiModel
        ) : TankHealthContentItem
        object AddAnalysis : TankHealthContentItem
    }

    internal companion object {
        const val GRID_SPAN_COUNT = 4

        private const val VIEW_TYPE_WATER_QUALITY_HEADER = 0
        private const val VIEW_TYPE_METRIC = 1
        private const val VIEW_TYPE_ADD_ANALYSIS = 2
        private const val METRIC_SPAN_SIZE = 1

        private fun buildItems(
            metrics: List<TankHealthWaterMetricUiModel>,
            measuredAtMillis: Long? = null
        ): List<TankHealthContentItem> =
            buildList {
                add(TankHealthContentItem.WaterQualityHeader(measuredAtMillis))
                addAll(metrics.map(TankHealthContentItem::Metric))
                add(TankHealthContentItem.AddAnalysis)
            }
    }
}
