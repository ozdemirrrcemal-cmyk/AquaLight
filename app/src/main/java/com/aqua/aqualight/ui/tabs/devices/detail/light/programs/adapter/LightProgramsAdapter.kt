package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.ItemLightProgramCardBinding
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.LightProgramListItem

class LightProgramsAdapter(
    private val onProgramClick: (LightProgramListItem) -> Unit,
    private val onProgramOptionsClick: (LightProgramListItem) -> Unit
) : RecyclerView.Adapter<LightProgramsAdapter.ProgramViewHolder>() {

    private val items = mutableListOf<LightProgramListItem>()

    fun submitList(
        newItems: List<LightProgramListItem>
    ) {
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

    override fun getItemCount(): Int {
        return items.size
    }

    inner class ProgramViewHolder(
        private val binding: ItemLightProgramCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: LightProgramListItem
        ) {
            bindTexts(item)
            bindVisualState(item)
            bindClicks(item)
        }

        private fun bindTexts(
            item: LightProgramListItem
        ) {
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
                "DISABLED"
            }

            binding.tvProgramRed.text = "R${item.red}"
            binding.tvProgramGreen.text = "G${item.green}"
            binding.tvProgramBlue.text = "B${item.blue}"
            binding.tvProgramWhite.text = "W${item.white}"
        }

        private fun bindVisualState(
            item: LightProgramListItem
        ) {
            val context = binding.root.context

            val contentAlpha = if (item.isActive) {
                1f
            } else {
                0.68f
            }

            val chipAlpha = if (item.isActive) {
                1f
            } else {
                0.58f
            }

            val stateTextColor = if (item.isActive) {
                ContextCompat.getColor(
                    context,
                    R.color.light_accent
                )
            } else {
                ContextCompat.getColor(
                    context,
                    R.color.light_text_tertiary
                )
            }

            binding.programCardRoot.alpha = 1f

            binding.viewProgramAccent.alpha = if (item.isActive) {
                1f
            } else {
                0.22f
            }

            binding.tvProgramName.alpha = contentAlpha
            binding.tvProgramSubtitle.alpha = contentAlpha
            binding.tvProgramStart.alpha = contentAlpha
            binding.tvProgramEnd.alpha = contentAlpha
            binding.tvProgramRamp.alpha = contentAlpha
            binding.tvProgramPoints.alpha = contentAlpha
            binding.tvProgramPeak.alpha = contentAlpha

            binding.tvProgramState.alpha = if (item.isActive) {
                1f
            } else {
                0.72f
            }

            binding.tvProgramState.setTextColor(stateTextColor)

            binding.tvProgramState.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    context,
                    if (item.isActive) {
                        R.color.light_accent_soft
                    } else {
                        R.color.light_surface_soft
                    }
                )
            )

            binding.channelSummaryContainer.children.forEach { child ->
                child.alpha = chipAlpha
            }
        }

        private fun bindClicks(
            item: LightProgramListItem
        ) {
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