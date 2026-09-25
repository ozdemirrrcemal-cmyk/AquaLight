package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.ItemTankHealthAnalysisHistoryRecordBinding

internal class TankHealthAnalysisHistoryAdapter(
    private val items: List<TankHealthAnalysisHistoryRecord>,
    private val onRecordClick: (TankHealthAnalysisHistoryRecord) -> Unit
) : RecyclerView.Adapter<TankHealthAnalysisHistoryAdapter.RecordViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
        return RecordViewHolder(
            binding = ItemTankHealthAnalysisHistoryRecordBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            ),
            onRecordClick = onRecordClick
        )
    }

    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    internal class RecordViewHolder(
        private val binding: ItemTankHealthAnalysisHistoryRecordBinding,
        private val onRecordClick: (TankHealthAnalysisHistoryRecord) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TankHealthAnalysisHistoryRecord) {
            val context = binding.root.context

            binding.tvDate.setText(item.dateRes)
            binding.tvTime.setText(item.timeRes)
            binding.tvPhValue.setText(item.phValueRes)
            binding.tvNo3Value.setText(item.no3ValueRes)
            binding.tvTemperatureValue.setText(item.temperatureValueRes)
            binding.tvNo3Status.setText(item.no3StatusRes)
            binding.tvNo3Status.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (item.no3StatusRes == R.string.tank_health_status_moderate) {
                        R.color.aqua_content_warning
                    } else {
                        R.color.aqua_status_success
                    }
                )
            )

            val sensorSource = item.temperatureSource == TemperatureSource.SENSOR
            binding.tvSource.setText(
                if (sensorSource) {
                    R.string.tank_health_analysis_source_sensor_badge
                } else {
                    R.string.tank_health_analysis_source_manual_badge
                }
            )
            binding.ivSource.setImageResource(
                if (sensorSource) {
                    R.drawable.ic_tank_health_sensor_24
                } else {
                    R.drawable.ic_tank_health_device_24
                }
            )

            val badgeBackground = ContextCompat.getColor(
                context,
                if (sensorSource) R.color.aqua_surface_positive else R.color.aqua_surface_action
            )
            val badgeOutline = ContextCompat.getColor(
                context,
                if (sensorSource) R.color.aqua_outline_positive else R.color.aqua_card_metric_outline
            )
            val badgeContent = ContextCompat.getColor(
                context,
                if (sensorSource) R.color.aqua_accent_positive else R.color.aqua_card_text_secondary
            )

            binding.sourceBadge.setCardBackgroundColor(badgeBackground)
            binding.sourceBadge.strokeColor = badgeOutline
            binding.tvSource.setTextColor(badgeContent)
            ImageViewCompat.setImageTintList(
                binding.ivSource,
                ColorStateList.valueOf(badgeContent)
            )

            binding.root.setOnClickListener {
                onRecordClick(item)
            }
        }
    }
}

internal data class TankHealthAnalysisHistoryRecord(
    @StringRes val dateRes: Int,
    @StringRes val timeRes: Int,
    @StringRes val phValueRes: Int,
    @StringRes val no3ValueRes: Int,
    @StringRes val temperatureValueRes: Int,
    @StringRes val no3StatusRes: Int,
    val temperatureSource: TemperatureSource
)

internal enum class TemperatureSource {
    SENSOR,
    MANUAL
}
