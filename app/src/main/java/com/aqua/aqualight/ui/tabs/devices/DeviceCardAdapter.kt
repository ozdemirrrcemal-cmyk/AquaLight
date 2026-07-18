package com.aqua.aqualight.ui.tabs.devices

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.ContextCompat
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.ItemDeviceCompactCardBinding
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactCardBinder

class DeviceCardAdapter(
    private val onDeviceClick: (DeviceCardUi) -> Unit,
    private val onDeviceLongClick: (DeviceCardUi) -> Unit
) : ListAdapter<DeviceCardUi, DeviceCardAdapter.DeviceViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): DeviceViewHolder {
        val binding = ItemDeviceCompactCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return DeviceViewHolder(
            binding = binding,
            onDeviceClick = onDeviceClick,
            onDeviceLongClick = onDeviceLongClick
        )
    }

    override fun onBindViewHolder(
        holder: DeviceViewHolder,
        position: Int
    ) {
        holder.bind(getItem(position))
    }

    class DeviceViewHolder(
        private val binding: ItemDeviceCompactCardBinding,
        private val onDeviceClick: (DeviceCardUi) -> Unit,
        private val onDeviceLongClick: (DeviceCardUi) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DeviceCardUi) {
            DeviceCompactCardBinder.bind(
                binding = binding,
                item = item.card
            )

            binding.root.strokeWidth = if (item.isSelected) {
                itemView.resources.getDimensionPixelSize(R.dimen.aqua_size_3)
            } else {
                itemView.resources.getDimensionPixelSize(R.dimen.aqua_size_1)
            }
            binding.root.strokeColor = ContextCompat.getColor(
                itemView.context,
                if (item.isSelected) R.color.aqua_palette_hex_5fd6b4
                else R.color.aqua_palette_hex_22354d
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

    private object DiffCallback : DiffUtil.ItemCallback<DeviceCardUi>() {
        override fun areItemsTheSame(
            oldItem: DeviceCardUi,
            newItem: DeviceCardUi
        ): Boolean {
            return oldItem.deviceUid == newItem.deviceUid
        }

        override fun areContentsTheSame(
            oldItem: DeviceCardUi,
            newItem: DeviceCardUi
        ): Boolean {
            return oldItem == newItem
        }
    }

}
