package com.aqua.aqualight.ui.tabs.settings.device

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.databinding.ItemDeviceStatusBinding
import com.aqua.aqualight.ui.tabs.devices.model.DeviceCardUi

class DeviceStatusAdapter(
    private val onDeviceClick: ((DeviceCardUi) -> Unit)? = null
) : RecyclerView.Adapter<DeviceStatusAdapter.DeviceViewHolder>() {

    private val items = mutableListOf<DeviceCardUi>()

    fun submitList(list: List<DeviceCardUi>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class DeviceViewHolder(private val binding: ItemDeviceStatusBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DeviceCardUi) {
            // Device name
            binding.tvDeviceName.text = item.aquaName.ifBlank { item.name }

            // Icon from enum
            binding.ivDeviceIcon.setImageResource(item.type.iconRes)

            // Device info
            binding.tvIp.text = "IP: ${item.ip}"
            binding.tvSerial.text = "Serial: ${item.serial}"
            binding.tvFirmware.text = if (item.firmwareBuild.isBlank()) "Firmware: Unknown"
                                      else "Firmware: ${item.firmwareBuild}"

            // Last seen
            binding.tvLastSeen.text = "Last seen: ${item.lastSeenText}"

            // Online status
            if (item.isOnline) {
                binding.tvStatus.text = "ONLINE"
                binding.viewStatusDot.setBackgroundResource(com.aqua.aqualight.R.drawable.bg_online_dot)
            } else {
                binding.tvStatus.text = "OFFLINE"
                binding.viewStatusDot.setBackgroundResource(com.aqua.aqualight.R.drawable.bg_offline_dot)
            }

            // Kart tıklanma olayı (opsiyonel)
            binding.cardDevice.setOnClickListener {
                onDeviceClick?.invoke(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceStatusBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}