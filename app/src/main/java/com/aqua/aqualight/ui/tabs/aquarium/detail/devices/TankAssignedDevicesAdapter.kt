package com.aqua.aqualight.ui.tabs.aquarium.detail.devices

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.databinding.ItemDeviceCompactCardBinding
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactCardBinder
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactCardUi

data class TankAssignedDeviceItem(
    val deviceUid: String,
    val title: String,
    val card: DeviceCompactCardUi
)

class TankAssignedDevicesAdapter(
    private val onDeviceClick: (TankAssignedDeviceItem) -> Unit,
    private val onDeviceLongClick: (TankAssignedDeviceItem) -> Unit
) : ListAdapter<TankAssignedDeviceItem, TankAssignedDevicesAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding = ItemDeviceCompactCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return ViewHolder(
            binding = binding,
            onDeviceClick = onDeviceClick,
            onDeviceLongClick = onDeviceLongClick
        )
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemDeviceCompactCardBinding,
        private val onDeviceClick: (TankAssignedDeviceItem) -> Unit,
        private val onDeviceLongClick: (TankAssignedDeviceItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: TankAssignedDeviceItem
        ) {
            DeviceCompactCardBinder.bind(
                binding = binding,
                item = item.card
            )

            binding.root.setOnClickListener {
                onDeviceClick(item)
            }

            binding.root.setOnLongClickListener {
                onDeviceLongClick(item)
                true
            }
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
}
