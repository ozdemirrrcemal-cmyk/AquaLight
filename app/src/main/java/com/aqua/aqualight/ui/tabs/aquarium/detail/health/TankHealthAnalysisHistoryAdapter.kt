package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.ItemTankHealthAnalysisHistoryRecordBinding

internal class TankHealthAnalysisHistoryAdapter(
    private val onRecordClick: (TankHealthAnalysisHistoryRecord) -> Unit
) : RecyclerView.Adapter<TankHealthAnalysisHistoryAdapter.RecordViewHolder>() {

    private var items: List<TankHealthAnalysisHistoryRecord> = emptyList()

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
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun submitItems(nextItems: List<TankHealthAnalysisHistoryRecord>) {
        items = nextItems
        notifyDataSetChanged()
    }

    internal class RecordViewHolder(
        private val binding: ItemTankHealthAnalysisHistoryRecordBinding,
        private val onRecordClick: (TankHealthAnalysisHistoryRecord) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TankHealthAnalysisHistoryRecord) {
            val context = binding.root.context
            binding.tvDate.text = item.dateText
            binding.tvTime.text = item.timeText
            binding.tvPhValue.text = item.phValueText
            binding.tvNo3Value.text = item.nitrateValueText
            binding.tvTemperatureValue.text = item.temperatureValueText

            val recorded = context.getString(R.string.tank_health_analysis_recorded)
            val notMeasured = context.getString(R.string.tank_health_status_not_measured)
            binding.tvPhStatus.text = if (item.phMeasured) recorded else notMeasured
            binding.tvNo3Status.text = if (item.nitrateMeasured) recorded else notMeasured
            binding.tvTemperatureStatus.text =
                if (item.temperatureMeasured) recorded else notMeasured

            val statusColor = ContextCompat.getColor(context, R.color.aqua_content_muted)
            binding.tvPhStatus.setTextColor(statusColor)
            binding.tvNo3Status.setTextColor(statusColor)
            binding.tvTemperatureStatus.setTextColor(statusColor)

            binding.root.setOnClickListener { onRecordClick(item) }
        }
    }
}

internal data class TankHealthAnalysisHistoryRecord(
    val analysisId: Long,
    val dateText: String,
    val timeText: String,
    val phValueText: String,
    val nitrateValueText: String,
    val temperatureValueText: String,
    val phMeasured: Boolean,
    val nitrateMeasured: Boolean,
    val temperatureMeasured: Boolean
)
