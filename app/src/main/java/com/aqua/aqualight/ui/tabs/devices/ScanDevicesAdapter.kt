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
        // Başlık: AquaName / Name / AquaName (Name_xxx)
        val title = when {
            !device.aquaName.isNullOrBlank() && !device.name.isNullOrBlank()
                    && device.aquaName != device.name -> {
                "${device.aquaName} (${device.name})"
            }
            !device.aquaName.isNullOrBlank() -> device.aquaName
            !device.name.isNullOrBlank() -> device.name
            else -> "Aqua device"
        }

        binding.tvName.text = title
        binding.tvIp.text = device.ip

        // Firmware alt satır (boşsa gizle)
        val fw = device.firmwareBuild?.trim().orEmpty()
        if (fw.isNotEmpty()) {
            binding.tvSubtitle.visibility = View.VISIBLE
            binding.tvSubtitle.text = fw
        } else {
            binding.tvSubtitle.visibility = View.GONE
        }

        binding.root.setOnClickListener {
            onClick(device)
        }
    }
}

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<DiscoveredDevice>() {
            override fun areItemsTheSame(
                oldItem: DiscoveredDevice,
                newItem: DiscoveredDevice
            ): Boolean = oldItem.ip == newItem.ip

            override fun areContentsTheSame(
                oldItem: DiscoveredDevice,
                newItem: DiscoveredDevice
            ): Boolean = oldItem == newItem
        }
    }
}