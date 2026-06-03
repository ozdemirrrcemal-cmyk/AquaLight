package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.databinding.ItemLightProgramCardBinding
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.LightProgramListItem

class LightProgramsAdapter(
    private val onProgramClick: (LightProgramListItem) -> Unit,
    private val onProgramOptionsClick: (LightProgramListItem) -> Unit
) : RecyclerView.Adapter<LightProgramsAdapter.ProgramViewHolder>() {

    private val items = mutableListOf<LightProgramListItem>()

    fun submitList(newItems: List<LightProgramListItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ProgramViewHolder {
        val binding = ItemLightProgramCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ProgramViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ProgramViewHolder,
        position: Int
    ) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ProgramViewHolder(
        private val binding: ItemLightProgramCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: LightProgramListItem) {
            binding.tvProgramName.text = item.name
            binding.tvProgramSubtitle.text = item.subtitle
            binding.tvProgramStart.text = item.startTime
            binding.tvProgramEnd.text = item.endTime
            binding.tvProgramRamp.text = item.rampText
            binding.tvProgramPoints.text = item.pointText
            binding.tvProgramPeak.text = item.peakText

            binding.tvProgramState.text = if (item.isActive) {
                "ACTIVE"
            } else {
                "PAUSED"
            }

            binding.channelSummaryContainer.getChildAt(0).let {
                (it as android.widget.TextView).text = "R${item.red}"
            }
            binding.channelSummaryContainer.getChildAt(1).let {
                (it as android.widget.TextView).text = "G${item.green}"
            }
            binding.channelSummaryContainer.getChildAt(2).let {
                (it as android.widget.TextView).text = "B${item.blue}"
            }
            binding.channelSummaryContainer.getChildAt(3).let {
                (it as android.widget.TextView).text = "W${item.white}"
            }

            binding.programCardRoot.setOnClickListener {
                onProgramClick(item)
            }

            binding.programCardRoot.setOnLongClickListener {
                onProgramOptionsClick(item)
                true
            }

            binding.btnProgramMore.setOnClickListener {
                onProgramOptionsClick(item)
            }
        }
    }
}