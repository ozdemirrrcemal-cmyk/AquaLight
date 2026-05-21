package com.aqua.aqualight.ui.tabs.aquarium

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
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
import java.util.concurrent.TimeUnit

class AquariumTankAdapter(
    private val onTankClick: (SavedAquariumTank) -> Unit
) : ListAdapter<SavedAquariumTank, AquariumTankAdapter.TankViewHolder>(DiffCallback) {

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
            onTankClick = onTankClick
        )
    }

    override fun onBindViewHolder(
        holder: TankViewHolder,
        position: Int
    ) {
        holder.bind(
            tank = getItem(position)
        )
    }

    class TankViewHolder(
        private val binding: ItemAquariumTankBinding,
        private val onTankClick: (SavedAquariumTank) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            tank: SavedAquariumTank
        ) {
            binding.tvTankName.text = tank.name
            binding.tvTankDay.text = getTankDayText(tank.setupDateMillis)
            binding.tvTankSubtitle.text = buildTankSubtitle(tank)

            if (!tank.photoUri.isNullOrBlank()) {
                binding.imgTankPhoto.load(Uri.parse(tank.photoUri)) {
                    placeholder(R.drawable.nature_aquarium)
                    error(R.drawable.nature_aquarium)
                    crossfade(true)
                }
            } else {
                binding.imgTankPhoto.setImageResource(R.drawable.nature_aquarium)
            }

            binding.root.setOnClickListener {
                onTankClick(tank)
            }
        }

        private fun buildTankSubtitle(
            tank: SavedAquariumTank
        ): String {
            val parts = mutableListOf<String>()

            if (tank.tankStyle.isNotBlank()) {
                parts.add(tank.tankStyle)
            }

            if (tank.tankType.isNotBlank()) {
                parts.add(tank.tankType)
            }

            if (tank.plants.isNotEmpty()) {
                parts.add("${tank.plants.size} plants")
            }

            if (tank.materials.isNotEmpty()) {
                parts.add("${tank.materials.size} materials")
            }

            return if (parts.isEmpty()) {
                "No maintenance record"
            } else {
                parts.joinToString(" · ")
            }
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