package com.aqua.aqualight.ui.tabs.devices

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
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
                SELECTED_STROKE_WIDTH_DP.dp()
            } else {
                DEFAULT_STROKE_WIDTH_DP.dp()
            }
            binding.root.strokeColor = Color.parseColor(
                if (item.isSelected) SELECTED_STROKE_COLOR else DEFAULT_STROKE_COLOR
            )

            binding.root.setOnClickListener {
                onDeviceClick(item)
            }

            binding.root.setOnLongClickListener {
                onDeviceLongClick(item)
                true
            }
        }

        private fun Int.dp(): Int {
            return (this * itemView.resources.displayMetrics.density).toInt()
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

    private companion object {
        const val DEFAULT_STROKE_WIDTH_DP = 1
        const val SELECTED_STROKE_WIDTH_DP = 3
        const val DEFAULT_STROKE_COLOR = "#22354D"
        const val SELECTED_STROKE_COLOR = "#5FD6B4"
    }
}
