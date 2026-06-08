package com.aqua.aqualight.ui.tabs.aquarium.detail.devices.select

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.databinding.ItemTankDeviceSelectBinding

class TankDeviceSelectAdapter(
    private val onDeviceClick: (TankDeviceSelectItem) -> Unit
) : ListAdapter<TankDeviceSelectItem, TankDeviceSelectAdapter.ViewHolder>(
    DiffCallback
) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding = ItemTankDeviceSelectBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return ViewHolder(
            binding = binding,
            onDeviceClick = onDeviceClick
        )
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        holder.bind(
            item = getItem(position)
        )
    }

    class ViewHolder(
        private val binding: ItemTankDeviceSelectBinding,
        private val onDeviceClick: (TankDeviceSelectItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: TankDeviceSelectItem
        ) {
            binding.tvDeviceInitial.text = item.initial
            binding.tvDeviceTitle.text = item.title
            binding.tvDeviceSubtitle.text = item.subtitle
            binding.tvDeviceStatus.text = item.statusText

            binding.root.setOnClickListener {
                onDeviceClick(item)
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<TankDeviceSelectItem>() {

        override fun areItemsTheSame(
            oldItem: TankDeviceSelectItem,
            newItem: TankDeviceSelectItem
        ): Boolean {
            return oldItem.deviceId == newItem.deviceId
        }

        override fun areContentsTheSame(
            oldItem: TankDeviceSelectItem,
            newItem: TankDeviceSelectItem
        ): Boolean {
            return oldItem == newItem
        }
    }
}