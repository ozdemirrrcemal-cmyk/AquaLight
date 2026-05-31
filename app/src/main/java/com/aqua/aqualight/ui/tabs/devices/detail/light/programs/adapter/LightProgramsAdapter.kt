package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.ItemLightProgramCardBinding
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.LightProgramListItem

class LightProgramsAdapter(
    private val onProgramClick: (LightProgramListItem) -> Unit,
    private val onProgramLongClick: (LightProgramListItem) -> Unit,
    private val onProgramEnabledChanged: (LightProgramListItem, Boolean) -> Unit
) : ListAdapter<LightProgramListItem, LightProgramsAdapter.ProgramViewHolder>(
    LightProgramDiffCallback
) {

    private var activeProgramId: String? = null

    fun submitPrograms(
        programs: List<LightProgramListItem>,
        activeProgramId: String?
    ) {
        val oldActiveProgramId = this.activeProgramId
        this.activeProgramId = activeProgramId

        submitList(programs.toList()) {
            notifyActiveProgramChanged(
                oldActiveProgramId = oldActiveProgramId,
                newActiveProgramId = activeProgramId
            )
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ProgramViewHolder {
        val binding =
            ItemLightProgramCardBinding.inflate(
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
        val program = getItem(position)

        holder.bind(
            program = program,
            isActiveProgram = program.id == activeProgramId
        )
    }

    private fun notifyActiveProgramChanged(
        oldActiveProgramId: String?,
        newActiveProgramId: String?
    ) {
        if (oldActiveProgramId == newActiveProgramId) {
            return
        }

        notifyProgramChangedById(
            programId = oldActiveProgramId
        )

        notifyProgramChangedById(
            programId = newActiveProgramId
        )
    }

    private fun notifyProgramChangedById(
        programId: String?
    ) {
        if (programId.isNullOrBlank()) {
            return
        }

        val index =
            currentList.indexOfFirst { program ->
                program.id == programId
            }

        if (index >= 0) {
            notifyItemChanged(index)
        }
    }

    class ProgramViewHolder(
        private val binding: ItemLightProgramCardBinding,
        private val onProgramClick: (LightProgramListItem) -> Unit,
        private val onProgramLongClick: (LightProgramListItem) -> Unit,
        private val onProgramEnabledChanged: (LightProgramListItem, Boolean) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            program: LightProgramListItem,
            isActiveProgram: Boolean
        ) = with(binding) {
            tvProgramCardTitle.text = program.title
            tvProgramCardSubtitle.text = program.subtitle
            tvProgramCardStartTime.text = program.startTime
            tvProgramCardRamp.text = program.rampLabel
            tvProgramCardEndTime.text = program.endTime
            tvProgramCardRepeat.text = program.repeatLabel

            tvProgramCardPeak.text = program.peakLabel
            tvProgramCardRed.text = program.redLabel
            tvProgramCardGreen.text = program.greenLabel
            tvProgramCardBlue.text = program.blueLabel
            tvProgramCardWhite.text = program.whiteLabel

            switchProgramCardEnabled.setOnCheckedChangeListener(null)
            switchProgramCardEnabled.isChecked = program.isEnabled
            switchProgramCardEnabled.setOnCheckedChangeListener { _, isChecked ->
                onProgramEnabledChanged(
                    program,
                    isChecked
                )
            }

            cardProgramItem.strokeColor =
                root.context.getColor(
                    if (isActiveProgram) {
                        R.color.light_accent
                    } else {
                        R.color.light_stroke
                    }
                )

            cardProgramItem.setCardBackgroundColor(
                root.context.getColor(
                    if (isActiveProgram) {
                        R.color.light_surface
                    } else {
                        R.color.light_surface_deep
                    }
                )
            )

            programCardMiniCurve.alpha =
                if (program.isEnabled) {
                    ENABLED_ITEM_ALPHA
                } else {
                    DISABLED_CURVE_ALPHA
                }

            programCardContent.alpha =
                if (program.isEnabled) {
                    ENABLED_ITEM_ALPHA
                } else {
                    DISABLED_ITEM_ALPHA
                }

            cardProgramItem.setOnClickListener {
                onProgramClick(
                    program
                )
            }

            cardProgramItem.setOnLongClickListener {
                onProgramLongClick(
                    program
                )

                true
            }
        }

        private companion object {
            private const val ENABLED_ITEM_ALPHA = 1f
            private const val DISABLED_ITEM_ALPHA = 0.68f
            private const val DISABLED_CURVE_ALPHA = 0.55f
        }
    }

    private object LightProgramDiffCallback :
        DiffUtil.ItemCallback<LightProgramListItem>() {

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