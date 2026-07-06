package com.aqua.aqualight.ui.tabs.settings.device

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
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
            val name = item.displayName.ifBlank { "Device" }
            val serial = item.serialText.ifBlank { "Unknown" }
            val presenceText = if (item.isOnline) "Online" else "Offline"

            binding.tvDeviceName.text = name
            binding.ivDeviceIcon.setImageResource(item.iconRes)
            binding.ivDeviceIcon.imageTintList = null
            binding.ivDeviceIcon.clearColorFilter()
            binding.ivDeviceIcon.contentDescription = name
            binding.tvIp.text = item.ip.ifBlank { "Unknown" }
            binding.tvSerialTitle.text = "Serial"
            binding.tvSerial.text = serial
            binding.tvLastSeen.text = item.lastSeenText.ifBlank { "-" }

            binding.ivPresenceIcon.imageTintList = ColorStateList.valueOf(
                if (item.isOnline) ONLINE_COLOR else OFFLINE_COLOR
            )
            binding.ivPresenceIcon.contentDescription = presenceText
            binding.root.contentDescription = "$name, Serial: $serial, $presenceText"
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

    private companion object {
        val ONLINE_COLOR: Int = Color.parseColor("#5FD6B4")
        val OFFLINE_COLOR: Int = Color.parseColor("#7B8794")
    }
}
