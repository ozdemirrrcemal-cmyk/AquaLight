package com.aqua.aqualight.ui.tabs.settings.device

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.ItemDeviceStatusBinding

class DeviceStatusAdapter : RecyclerView.Adapter<DeviceStatusAdapter.DeviceViewHolder>() {

    private val items = mutableListOf<DeviceStatusItem>()

    fun submitList(list: List<DeviceStatusItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class DeviceViewHolder(
        private val binding: ItemDeviceStatusBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DeviceStatusItem) {
            binding.tvDeviceName.text = item.displayName.ifBlank { "Device" }
            binding.tvTankName.text = item.supportingText.ifBlank { "Not connected" }
            binding.ivDeviceIcon.setImageResource(R.drawable.ic_device_aqua_ster)
            binding.tvIp.text = item.ip.ifBlank { "Unknown" }
            binding.tvSerialTitle.text = "Device code"
            binding.tvSerial.text = item.deviceCode.ifBlank { "Unknown" }
            binding.tvFirmwareTitle.text = "Product"
            binding.tvFirmware.text = item.productName.ifBlank { "Unknown" }
            binding.tvLastSeen.text = item.lastSeenText.ifBlank { "-" }

            if (item.isOnline) {
                binding.tvStatus.text = "ONLINE"
                binding.tvStatus.setTextColor(Color.parseColor("#39D353"))
                binding.viewStatusDot.setBackgroundResource(R.drawable.bg_online_dot)
            } else {
                binding.tvStatus.text = "OFFLINE"
                binding.tvStatus.setTextColor(Color.parseColor("#F44336"))
                binding.viewStatusDot.setBackgroundResource(R.drawable.bg_offline_dot)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceStatusBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}
