package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.ItemTankHealthAddAnalysisBinding
import com.aqua.aqualight.databinding.ItemTankHealthMaintenanceSectionBinding
import com.aqua.aqualight.databinding.ItemTankHealthMetricBinding
import com.aqua.aqualight.databinding.ItemTankHealthSystemSectionBinding
import com.aqua.aqualight.databinding.ItemTankHealthWaterQualityHeaderBinding

internal class TankHealthContentAdapter :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items: List<TankHealthContentItem> = buildItems()

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            TankHealthContentItem.WaterQualityHeader -> VIEW_TYPE_WATER_QUALITY_HEADER
            is TankHealthContentItem.Metric -> VIEW_TYPE_METRIC
            TankHealthContentItem.AddAnalysis -> VIEW_TYPE_ADD_ANALYSIS
            TankHealthContentItem.MaintenanceSection -> VIEW_TYPE_MAINTENANCE_SECTION
            TankHealthContentItem.SystemSection -> VIEW_TYPE_SYSTEM_SECTION
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_WATER_QUALITY_HEADER -> StaticViewHolder(
                ItemTankHealthWaterQualityHeaderBinding.inflate(
                    inflater,
                    parent,
                    false
                ).root
            )

            VIEW_TYPE_METRIC -> MetricViewHolder(
                ItemTankHealthMetricBinding.inflate(
                    inflater,
                    parent,
                    false
                )
            )

            VIEW_TYPE_ADD_ANALYSIS -> StaticViewHolder(
                ItemTankHealthAddAnalysisBinding.inflate(
                    inflater,
                    parent,
                    false
                ).root
            )

            VIEW_TYPE_MAINTENANCE_SECTION -> StaticViewHolder(
                ItemTankHealthMaintenanceSectionBinding.inflate(
                    inflater,
                    parent,
                    false
                ).root
            )

            VIEW_TYPE_SYSTEM_SECTION -> StaticViewHolder(
                ItemTankHealthSystemSectionBinding.inflate(
                    inflater,
                    parent,
                    false
                ).root
            )

            else -> error("Unsupported Tank Health view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        if (holder is MetricViewHolder && item is TankHealthContentItem.Metric) {
            holder.bind(item)
        }
    }

    override fun getItemCount(): Int = items.size

    fun spanSizeForPosition(position: Int): Int {
        return if (items[position] is TankHealthContentItem.Metric) {
            METRIC_SPAN_SIZE
        } else {
            GRID_SPAN_COUNT
        }
    }

    private class StaticViewHolder(
        root: android.view.View
    ) : RecyclerView.ViewHolder(root)

    private class MetricViewHolder(
        private val binding: ItemTankHealthMetricBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TankHealthContentItem.Metric) {
            val context = binding.root.context
            binding.metricLabel.setText(item.labelRes)
            binding.metricValue.setText(item.valueRes)
            binding.metricStatus.setText(item.statusRes)
            binding.metricStatus.setTextColor(
                ContextCompat.getColor(context, item.statusColorRes)
            )

            val layoutParams = binding.root.layoutParams as ViewGroup.MarginLayoutParams
            val horizontalSpacing = context.resources.getDimensionPixelSize(R.dimen.aqua_size_3)
            val rowSpacing = context.resources.getDimensionPixelSize(R.dimen.aqua_size_6)
            val column = item.index % GRID_SPAN_COUNT

            layoutParams.marginStart = if (column == 0) 0 else horizontalSpacing
            layoutParams.marginEnd =
                if (column == GRID_SPAN_COUNT - 1) 0 else horizontalSpacing
            layoutParams.bottomMargin =
                if (item.index < GRID_SPAN_COUNT) rowSpacing else 0
            binding.root.layoutParams = layoutParams
        }
    }

    private sealed interface TankHealthContentItem {
        object WaterQualityHeader : TankHealthContentItem

        data class Metric(
            val index: Int,
            @StringRes val labelRes: Int,
            @StringRes val valueRes: Int,
            @StringRes val statusRes: Int,
            @ColorRes val statusColorRes: Int
        ) : TankHealthContentItem

        object AddAnalysis : TankHealthContentItem
        object MaintenanceSection : TankHealthContentItem
        object SystemSection : TankHealthContentItem
    }

    internal companion object {
        const val GRID_SPAN_COUNT = 4

        private const val VIEW_TYPE_WATER_QUALITY_HEADER = 0
        private const val VIEW_TYPE_METRIC = 1
        private const val VIEW_TYPE_ADD_ANALYSIS = 2
        private const val VIEW_TYPE_MAINTENANCE_SECTION = 3
        private const val VIEW_TYPE_SYSTEM_SECTION = 4
        private const val METRIC_SPAN_SIZE = 1

        private fun buildItems(): List<TankHealthContentItem> {
            return listOf(
                TankHealthContentItem.WaterQualityHeader,
                metric(0, R.string.tank_health_metric_ph, R.string.tank_health_value_ph),
                metric(
                    1,
                    R.string.tank_health_metric_no3,
                    R.string.tank_health_value_no3,
                    R.string.tank_health_status_moderate,
                    R.color.aqua_content_warning
                ),
                metric(2, R.string.tank_health_metric_no2, R.string.tank_health_value_no2),
                metric(
                    3,
                    R.string.tank_health_metric_nh3_nh4,
                    R.string.tank_health_value_nh3_nh4
                ),
                metric(
                    4,
                    R.string.tank_health_metric_temperature,
                    R.string.tank_health_value_temperature
                ),
                metric(5, R.string.tank_health_metric_gh, R.string.tank_health_value_gh),
                metric(6, R.string.tank_health_metric_kh, R.string.tank_health_value_kh),
                metric(7, R.string.tank_health_metric_po4, R.string.tank_health_value_po4),
                TankHealthContentItem.AddAnalysis,
                TankHealthContentItem.MaintenanceSection,
                TankHealthContentItem.SystemSection
            )
        }

        private fun metric(
            index: Int,
            @StringRes labelRes: Int,
            @StringRes valueRes: Int,
            @StringRes statusRes: Int = R.string.tank_health_status_normal,
            @ColorRes statusColorRes: Int = R.color.aqua_status_success
        ): TankHealthContentItem.Metric {
            return TankHealthContentItem.Metric(
                index = index,
                labelRes = labelRes,
                valueRes = valueRes,
                statusRes = statusRes,
                statusColorRes = statusColorRes
            )
        }
    }

}
