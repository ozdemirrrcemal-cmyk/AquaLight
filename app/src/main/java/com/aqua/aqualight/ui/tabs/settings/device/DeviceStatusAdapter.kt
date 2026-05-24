package com.aqua.aqualight.ui.tabs.settings.device

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.R
import com.aqua.aqualight.data.devices.DeviceSerialFormatter
import com.aqua.aqualight.databinding.ItemDeviceStatusBinding
import com.aqua.aqualight.ui.tabs.devices.model.DeviceCardUi
import com.aqua.aqualight.ui.tabs.devices.model.DeviceIconMapper

class DeviceStatusAdapter(
    private val onDeviceClick: ((DeviceCardUi) -> Unit)? = null
) : RecyclerView.Adapter<DeviceStatusAdapter.DeviceViewHolder>() {

    private val items = mutableListOf<DeviceCardUi>()

    fun submitList(
        list: List<DeviceCardUi>
    ) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class DeviceViewHolder(
        private val binding: ItemDeviceStatusBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: DeviceCardUi
        ) {
            binding.tvDeviceName.text = item.displayName.ifBlank {
                "Device"
            }

            binding.tvTankName.text = item.tankName.ifBlank {
                "Not connected"
            }

            binding.ivDeviceIcon.setImageResource(
                DeviceIconMapper.iconFor(item.deviceType)
            )

            binding.tvIp.text = item.ip.ifBlank {
                "Unknown"
            }

            binding.tvSerial.text = DeviceSerialFormatter.displaySerial(
                serial = item.serial
            ).ifBlank {
                "Unknown"
            }

            binding.tvFirmware.text = if (item.firmwareBuild.isBlank()) {
                "Unknown"
            } else {
                item.firmwareBuild.substringBefore(" (")
            }

            binding.tvLastSeen.text = item.lastSeenText.ifBlank {
                "-"
            }

            if (item.isOnline) {
                binding.tvStatus.text = "ONLINE"

                binding.tvStatus.setTextColor(
                    Color.parseColor("#39D353")
                )

                binding.viewStatusDot.setBackgroundResource(
                    R.drawable.bg_online_dot
                )
            } else {
                binding.tvStatus.text = "OFFLINE"

                binding.tvStatus.setTextColor(
                    Color.parseColor("#F44336")
                )

                binding.viewStatusDot.setBackgroundResource(
                    R.drawable.bg_offline_dot
                )
            }

            binding.cardDevice.setOnClickListener {
                onDeviceClick?.invoke(item)
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): DeviceViewHolder {
        val binding = ItemDeviceStatusBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return DeviceViewHolder(
            binding = binding
        )
    }

    override fun onBindViewHolder(
        holder: DeviceViewHolder,
        position: Int
    ) {
        holder.bind(
            item = items[position]
        )
    }

    override fun getItemCount(): Int {
        return items.size
    }
}