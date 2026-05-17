package com.aqua.aqualight.ui.tabs.aquarium

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.ItemAquariumTankBinding
import com.aqua.aqualight.ui.tabs.aquarium.model.SavedAquariumTank
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class AquariumTankAdapter(
    private val onTankClick: (SavedAquariumTank) -> Unit,
    private val onTankLongClick: (SavedAquariumTank) -> Unit
) : ListAdapter<SavedAquariumTank, AquariumTankAdapter.TankViewHolder>(DiffCallback) {

    private var isDeleteMode: Boolean = false
    private var selectedTankIds: Set<Long> = emptySet()

    fun setDeleteMode(
        enabled: Boolean,
        selectedIds: Set<Long>
    ) {
        isDeleteMode = enabled
        selectedTankIds = selectedIds.toSet()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): TankViewHolder {
        val binding = ItemAquariumTankBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return TankViewHolder(
            binding = binding,
            onTankClick = onTankClick,
            onTankLongClick = onTankLongClick
        )
    }

    override fun onBindViewHolder(
        holder: TankViewHolder,
        position: Int
    ) {
        val tank = getItem(position)

        holder.bind(
            tank = tank,
            isDeleteMode = isDeleteMode,
            isSelected = selectedTankIds.contains(tank.id)
        )
    }

    class TankViewHolder(
        private val binding: ItemAquariumTankBinding,
        private val onTankClick: (SavedAquariumTank) -> Unit,
        private val onTankLongClick: (SavedAquariumTank) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            tank: SavedAquariumTank,
            isDeleteMode: Boolean,
            isSelected: Boolean
        ) {
            binding.tvTankName.text = tank.name
            binding.tvTankDay.text = getTankDayText(tank.setupDateMillis)

            binding.tvCareInfo.text = getCareInfoText()
            binding.tvTankSize.text = getTankSizeText(tank)
            binding.tvSetupDate.text = getSetupDateText(tank.setupDateMillis)

            if (!tank.photoUri.isNullOrBlank()) {
                binding.imgTankPhoto.load(Uri.parse(tank.photoUri)) {
                    placeholder(R.drawable.nature_aquarium)
                    error(R.drawable.nature_aquarium)
                    crossfade(true)
                }
            } else {
                binding.imgTankPhoto.setImageResource(R.drawable.nature_aquarium)
            }

            binding.selectionCircle.isVisible = isDeleteMode

            binding.selectionCircle.setImageResource(
                if (isSelected) {
                    R.drawable.ic_check
                } else {
                    0
                }
            )

            binding.selectionCircle.setBackgroundResource(
                if (isSelected) {
                    R.drawable.bg_tank_selection_selected
                } else {
                    R.drawable.bg_tank_selection_unselected
                }
            )

            binding.root.alpha = if (isDeleteMode && !isSelected) {
                0.72f
            } else {
                1f
            }

            binding.root.setOnClickListener {
                onTankClick(tank)
            }

            binding.root.setOnLongClickListener {
                onTankLongClick(tank)
                true
            }
        }

        private fun getCareInfoText(): String {
            return "Last Water Change: Today | Last Trim: Today"
        }

        private fun getTankSizeText(
            tank: SavedAquariumTank
        ): String {
            return "${tank.widthCm}W x ${tank.lengthCm}L x ${tank.heightCm}H"
        }

        private fun getSetupDateText(
            setupDateMillis: Long?
        ): String {
            if (setupDateMillis == null) {
                return "Setup Date: -"
            }

            val formatter = SimpleDateFormat(
                "yyyy/MM/dd",
                Locale.getDefault()
            )

            return "Setup Date: ${formatter.format(Date(setupDateMillis))}"
        }

        private fun getTankDayText(
            setupDateMillis: Long?
        ): String {
            val setupMillis = setupDateMillis ?: System.currentTimeMillis()
            val nowMillis = System.currentTimeMillis()

            val day = TimeUnit.MILLISECONDS
                .toDays(nowMillis - setupMillis)
                .coerceAtLeast(0)

            return "Day $day"
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<SavedAquariumTank>() {

        override fun areItemsTheSame(
            oldItem: SavedAquariumTank,
            newItem: SavedAquariumTank
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: SavedAquariumTank,
            newItem: SavedAquariumTank
        ): Boolean {
            return oldItem == newItem
        }
    }
}