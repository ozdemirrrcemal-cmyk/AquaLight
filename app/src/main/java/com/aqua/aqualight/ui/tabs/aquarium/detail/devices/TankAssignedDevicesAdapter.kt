package com.aqua.aqualight.ui.tabs.aquarium.detail.devices

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.databinding.ItemDeviceCompactCardBinding
import com.aqua.aqualight.databinding.ItemDosingDeviceSpotlightCardBinding
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactCardBinder
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactCardUi

data class TankAssignedDeviceItem(
    val deviceUid: String,
    val title: String,
    val card: DeviceCompactCardUi,
    val dosingCard: DosingDeviceSpotlightCardUi? = null
)

class TankAssignedDevicesAdapter(
    private val onDeviceClick: (TankAssignedDeviceItem) -> Unit,
    private val onDeviceLongClick: (TankAssignedDeviceItem) -> Unit
) : ListAdapter<TankAssignedDeviceItem, RecyclerView.ViewHolder>(DiffCallback) {

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).dosingCard != null) {
            VIEW_TYPE_DOSING_SPOTLIGHT
        } else {
            VIEW_TYPE_COMPACT
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_DOSING_SPOTLIGHT -> DosingViewHolder(
                binding = ItemDosingDeviceSpotlightCardBinding.inflate(
                    inflater,
                    parent,
                    false
                ),
                onDeviceClick = onDeviceClick,
                onDeviceLongClick = onDeviceLongClick
            )
            else -> CompactViewHolder(
                binding = ItemDeviceCompactCardBinding.inflate(
                    inflater,
                    parent,
                    false
                ),
                onDeviceClick = onDeviceClick,
                onDeviceLongClick = onDeviceLongClick
            )
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int
    ) {
        val item = getItem(position)
        when (holder) {
            is DosingViewHolder -> holder.bind(item)
            is CompactViewHolder -> holder.bind(item)
        }
    }

    private class CompactViewHolder(
        private val binding: ItemDeviceCompactCardBinding,
        private val onDeviceClick: (TankAssignedDeviceItem) -> Unit,
        private val onDeviceLongClick: (TankAssignedDeviceItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TankAssignedDeviceItem) {
            DeviceCompactCardBinder.bind(
                binding = binding,
                item = item.card
            )
            bindInteractions(
                item = item,
                onDeviceClick = onDeviceClick,
                onDeviceLongClick = onDeviceLongClick
            )
        }

        private fun bindInteractions(
            item: TankAssignedDeviceItem,
            onDeviceClick: (TankAssignedDeviceItem) -> Unit,
            onDeviceLongClick: (TankAssignedDeviceItem) -> Unit
        ) {
            binding.root.setOnClickListener {
                onDeviceClick(item)
            }
            binding.root.setOnLongClickListener {
                onDeviceLongClick(item)
                true
            }
        }
    }

    private class DosingViewHolder(
        private val binding: ItemDosingDeviceSpotlightCardBinding,
        private val onDeviceClick: (TankAssignedDeviceItem) -> Unit,
        private val onDeviceLongClick: (TankAssignedDeviceItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var lastSpotlightIndex: Int? = null

        fun bind(item: TankAssignedDeviceItem) {
            val dosingCard = requireNotNull(item.dosingCard)
            val animateSpotlight = lastSpotlightIndex != null &&
                lastSpotlightIndex != dosingCard.selectedIndex

            DosingDeviceSpotlightCardBinder.bind(
                binding = binding,
                item = dosingCard
            )

            if (animateSpotlight) {
                binding.spotlightContent.animate().cancel()
                binding.spotlightContent.alpha = SPOTLIGHT_FADE_START_ALPHA
                binding.spotlightContent.animate()
                    .alpha(1f)
                    .setDuration(SPOTLIGHT_FADE_DURATION_MILLIS)
                    .start()
            } else {
                binding.spotlightContent.alpha = 1f
            }
            lastSpotlightIndex = dosingCard.selectedIndex

            binding.root.setOnClickListener {
                onDeviceClick(item)
            }
            binding.root.setOnLongClickListener {
                onDeviceLongClick(item)
                true
            }
        }

        private companion object {
            const val SPOTLIGHT_FADE_START_ALPHA = 0.25f
            const val SPOTLIGHT_FADE_DURATION_MILLIS = 260L
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<TankAssignedDeviceItem>() {
        override fun areItemsTheSame(
            oldItem: TankAssignedDeviceItem,
            newItem: TankAssignedDeviceItem
        ): Boolean {
            return oldItem.deviceUid == newItem.deviceUid
        }

        override fun areContentsTheSame(
            oldItem: TankAssignedDeviceItem,
            newItem: TankAssignedDeviceItem
        ): Boolean {
            return oldItem == newItem
        }
    }

    private companion object {
        const val VIEW_TYPE_COMPACT = 0
        const val VIEW_TYPE_DOSING_SPOTLIGHT = 1
    }
}
