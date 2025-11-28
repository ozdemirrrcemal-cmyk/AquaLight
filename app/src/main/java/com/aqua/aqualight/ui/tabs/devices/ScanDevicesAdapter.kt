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
            // Sol kutu: AquaName
            binding.tvAquaName.text = device.aquaName

            // Ortadaki isim: Name
            binding.tvName.text = device.name

            // Sağ: ID
            binding.tvId.text = device.id.toString()

            binding.root.setOnClickListener { onClick(device) }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<DiscoveredDevice>() {
            override fun areItemsTheSame(
                oldItem: DiscoveredDevice,
                newItem: DiscoveredDevice
            ): Boolean = oldItem.id == newItem.id

            override fun areContentsTheSame(
                oldItem: DiscoveredDevice,
                newItem: DiscoveredDevice
            ): Boolean = oldItem == newItem
        }
    }
}