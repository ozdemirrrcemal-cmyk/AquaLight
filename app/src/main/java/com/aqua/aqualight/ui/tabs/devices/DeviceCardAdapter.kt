package com.aqua.aqualight.ui.tabs.devices

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.databinding.ItemDeviceCompactCardBinding
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactCardBinder
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactCardUi
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactStatusStyle

class DeviceCardAdapter(
    private val onDeviceClick: (DeviceCardUi) -> Unit
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
            onDeviceClick = onDeviceClick
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
        private val onDeviceClick: (DeviceCardUi) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DeviceCardUi) {
            DeviceCompactCardBinder.bind(
                binding = binding,
                item = item.toCompactCardUi()
            )

            binding.root.setOnClickListener {
                onDeviceClick(item)
            }
        }

        private fun DeviceCardUi.toCompactCardUi(): DeviceCompactCardUi {
            return DeviceCompactCardUi(
                deviceUid = deviceUid,
                displayName = title,
                serialText = serialText,
                supportingText = subtitle,
                iconRes = iconRes,
                statusText = statusLabel,
                statusStyle = statusStyle.toCompactStatusStyle()
            )
        }

        private fun DeviceCardUi.StatusStyle.toCompactStatusStyle(): DeviceCompactStatusStyle {
            return when (this) {
                DeviceCardUi.StatusStyle.ONLINE -> DeviceCompactStatusStyle.ONLINE
                DeviceCardUi.StatusStyle.CONNECTING -> DeviceCompactStatusStyle.CONNECTING
                DeviceCardUi.StatusStyle.WARNING -> DeviceCompactStatusStyle.WARNING
                DeviceCardUi.StatusStyle.OFFLINE -> DeviceCompactStatusStyle.OFFLINE
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
