package com.aqua.aqualight.ui.tabs.devices

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.databinding.ItemScanDeviceBinding

class ScanDevicesAdapter(
    private val onClick: (DiscoveredDevice) -> Unit
) : ListAdapter<DiscoveredDevice, ScanDevicesAdapter.DeviceViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemScanDeviceBinding.inflate(inflater, parent, false)
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DeviceViewHolder(
        private val binding: ItemScanDeviceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(device: DiscoveredDevice) {
            val aqua = device.aquaName?.takeIf { it.isNotBlank() } ?: "-"
            val name = device.name.ifBlank { "Device" }

            binding.tvAquaName.text = aqua
            binding.tvName.text = name

            // Seri formatı: AN-NNNNN
            binding.tvId.text = buildSerial(aqua, name, device.id)

            binding.root.setOnClickListener { onClick(device) }
        }

        private fun buildSerial(aqua: String, name: String, id: Long): String {
            val a = aqua.firstOrNull()?.uppercaseChar() ?: 'X'
            val n = name.firstOrNull()?.uppercaseChar() ?: 'X'
            return "$a$n-$id"
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<DiscoveredDevice>() {
            override fun areItemsTheSame(
                oldItem: DiscoveredDevice,
                newItem: DiscoveredDevice
            ): Boolean =
                oldItem.id == newItem.id && oldItem.ip == newItem.ip

            override fun areContentsTheSame(
                oldItem: DiscoveredDevice,
                newItem: DiscoveredDevice
            ): Boolean = oldItem == newItem
        }
    }
}