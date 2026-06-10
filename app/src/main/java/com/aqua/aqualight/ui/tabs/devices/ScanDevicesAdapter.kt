package com.aqua.aqualight.ui.tabs.devices

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.data.devices.DeviceSerialFormatter
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCatalog
import com.aqua.aqualight.data.devices.discovery.model.DiscoveredAquaDevice
import com.aqua.aqualight.databinding.ItemScanDeviceBinding

class ScanDevicesAdapter(
    private val onClick: (DiscoveredAquaDevice) -> Unit
) : ListAdapter<DiscoveredAquaDevice, ScanDevicesAdapter.DeviceViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): DeviceViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        val binding = ItemScanDeviceBinding.inflate(
            inflater,
            parent,
            false
        )

        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: DeviceViewHolder,
        position: Int
    ) {
        holder.bind(
            device = getItem(position)
        )
    }

    inner class DeviceViewHolder(
        private val binding: ItemScanDeviceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            device: DiscoveredAquaDevice
        ) {
            val definition = AquaDeviceCatalog.findByType(
                type = device.deviceType
            )

            val displayName = definition?.displayName
                ?: device.productModel
                ?: device.name.ifBlank { "Unsupported Device" }

            val familyName = definition?.family?.displayName
                ?: device.productFamily
                ?: device.aquaName.ifBlank { "Unknown" }

            binding.tvAquaName.text = familyName
            binding.tvName.text = displayName

            binding.tvId.text = if (device.isSupported) {
                buildSupportedInfo(
                    device = device,
                    displayName = displayName,
                    familyName = familyName
                )
            } else {
                "Unsupported • Update app required"
            }

            binding.root.alpha = if (device.isSupported) {
                1f
            } else {
                0.55f
            }

            binding.root.setOnClickListener {
                onClick(device)
            }
        }

        private fun buildSupportedInfo(
            device: DiscoveredAquaDevice,
            displayName: String,
            familyName: String
        ): String {
            val serial = DeviceSerialFormatter.buildSerial(
                aquaName = familyName,
                name = displayName,
                id = device.id,
                firmwareSerial = device.serialNumber,
                deviceUid = device.deviceUid,
                macAddress = device.macAddress
            )

            val firmware = device.firmwareBuild
                .takeIf { value -> value.isNotBlank() }
                ?: "Firmware unknown"

            return "$serial • ${device.ip} • $firmware"
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<DiscoveredAquaDevice>() {

            override fun areItemsTheSame(
                oldItem: DiscoveredAquaDevice,
                newItem: DiscoveredAquaDevice
            ): Boolean {
                return oldItem.id == newItem.id &&
                    oldItem.ip == newItem.ip
            }

            override fun areContentsTheSame(
                oldItem: DiscoveredAquaDevice,
                newItem: DiscoveredAquaDevice
            ): Boolean {
                return oldItem == newItem
            }
        }
    }
}