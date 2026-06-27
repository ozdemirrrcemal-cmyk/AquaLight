package com.aqua.aqualight.ui.tabs.devices

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.ItemDeviceStatusBinding

class DeviceCardAdapter(
    private val onDeviceClick: (DeviceCardUi) -> Unit
) : ListAdapter<DeviceCardUi, DeviceCardAdapter.DeviceViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceStatusBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DeviceViewHolder(binding, onDeviceClick)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DeviceViewHolder(
        private val binding: ItemDeviceStatusBinding,
        private val onDeviceClick: (DeviceCardUi) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DeviceCardUi) {
            binding.tvDeviceName.text = item.title
            binding.tvTankName.text = item.subtitle
            binding.ivDeviceIcon.setImageResource(R.drawable.ic_device_aqua_ster)
            binding.tvIp.text = item.ipText
            binding.tvSerialTitle.text = "Device UID"
            binding.tvSerial.text = item.serialText
            binding.tvFirmwareTitle.text = "Firmware"
            binding.tvFirmware.text = item.firmwareText
            binding.tvLastSeen.text = item.lastSeenText
            binding.tvStatus.text = item.statusLabel

            when (item.statusStyle) {
                DeviceCardUi.StatusStyle.ONLINE -> {
                    binding.tvStatus.setTextColor(Color.parseColor("#39D353"))
                    binding.viewStatusDot.setBackgroundResource(R.drawable.bg_online_dot)
                }
                DeviceCardUi.StatusStyle.CONNECTING -> {
                    binding.tvStatus.setTextColor(Color.parseColor("#8BCAFF"))
                    binding.viewStatusDot.setBackgroundResource(R.drawable.bg_online_dot)
                }
                DeviceCardUi.StatusStyle.WARNING -> {
                    binding.tvStatus.setTextColor(Color.parseColor("#F9C74F"))
                    binding.viewStatusDot.setBackgroundResource(R.drawable.bg_offline_dot)
                }
                DeviceCardUi.StatusStyle.OFFLINE -> {
                    binding.tvStatus.setTextColor(Color.parseColor("#F44336"))
                    binding.viewStatusDot.setBackgroundResource(R.drawable.bg_offline_dot)
                }
            }

            binding.root.setOnClickListener { onDeviceClick(item) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<DeviceCardUi>() {
        override fun areItemsTheSame(oldItem: DeviceCardUi, newItem: DeviceCardUi): Boolean =
            oldItem.deviceUid == newItem.deviceUid

        override fun areContentsTheSame(oldItem: DeviceCardUi, newItem: DeviceCardUi): Boolean =
            oldItem == newItem
    }
}
