package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.databinding.ItemLightProgramCardBinding
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.LightProgramListItem

class LightProgramsAdapter(
    private val onProgramClick: (LightProgramListItem) -> Unit,
    private val onProgramLongClick: (LightProgramListItem) -> Unit,
    private val onProgramEnabledChanged: (LightProgramListItem, Boolean) -> Unit
) : ListAdapter<LightProgramListItem, LightProgramsAdapter.ProgramViewHolder>(
    DiffCallback
) {

    private var activeProgramId: String? = null

    fun submitPrograms(
        programs: List<LightProgramListItem>,
        activeProgramId: String?
    ) {
        this.activeProgramId = activeProgramId
        submitList(programs)
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

        return ProgramViewHolder(
            binding = binding,
            onProgramClick = onProgramClick,
            onProgramLongClick = onProgramLongClick,
            onProgramEnabledChanged = onProgramEnabledChanged
        )
    }

    override fun onBindViewHolder(
        holder: ProgramViewHolder,
        position: Int
    ) {
        holder.bind(
            item = getItem(position),
            isActiveProgram = getItem(position).id == activeProgramId
        )
    }

    class ProgramViewHolder(
        private val binding: ItemLightProgramCardBinding,
        private val onProgramClick: (LightProgramListItem) -> Unit,
        private val onProgramLongClick: (LightProgramListItem) -> Unit,
        private val onProgramEnabledChanged: (LightProgramListItem, Boolean) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: LightProgramListItem,
            isActiveProgram: Boolean
        ) = with(binding) {
            tvProgramCardTitle.text = item.title
            tvProgramCardSubtitle.text = item.subtitle

            tvProgramCardSubtitle.visibility =
                if (item.subtitle.isBlank()) {
                    View.GONE
                } else {
                    View.VISIBLE
                }

            tvProgramCardStartTime.text = item.startTimeLabel
            tvProgramCardRamp.text = item.rampLabel
            tvProgramCardEndTime.text = item.endTimeLabel
            tvProgramCardRepeat.text = item.repeatLabel

            tvProgramCardPeak.text = item.peakLabel
            tvProgramCardRed.text = item.redLabel
            tvProgramCardGreen.text = item.greenLabel
            tvProgramCardBlue.text = item.blueLabel
            tvProgramCardWhite.text = item.whiteLabel

            programCardChannelsRow.visibility =
                if (
                    item.peakLabel.isBlank() &&
                    item.redLabel.isBlank() &&
                    item.greenLabel.isBlank() &&
                    item.blueLabel.isBlank() &&
                    item.whiteLabel.isBlank()
                ) {
                    View.GONE
                } else {
                    View.VISIBLE
                }

            viewProgramCardMiniCurve.submitData(
                data = item.curveData
            )

            switchProgramCardEnabled.setOnCheckedChangeListener(null)
            switchProgramCardEnabled.isChecked = item.isEnabled
            switchProgramCardEnabled.setOnCheckedChangeListener { _, isChecked ->
                onProgramEnabledChanged(
                    item,
                    isChecked
                )
            }

            cardProgramItem.alpha =
                if (isActiveProgram || item.isEnabled) {
                    ENABLED_ALPHA
                } else {
                    DISABLED_ALPHA
                }

            root.setOnClickListener {
                onProgramClick(item)
            }

            root.setOnLongClickListener {
                onProgramLongClick(item)
                true
            }
        }

        private companion object {
            private const val ENABLED_ALPHA = 1f
            private const val DISABLED_ALPHA = 0.68f
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<LightProgramListItem>() {

        override fun areItemsTheSame(
            oldItem: LightProgramListItem,
            newItem: LightProgramListItem
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: LightProgramListItem,
            newItem: LightProgramListItem
        ): Boolean {
            return oldItem == newItem
        }
    }
}