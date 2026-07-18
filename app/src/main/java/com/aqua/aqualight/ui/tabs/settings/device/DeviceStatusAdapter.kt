package com.aqua.aqualight.ui.tabs.settings.device

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
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
            val context = binding.root.context
            val name = item.displayName.ifBlank {
                context.getString(R.string.device_menu_default_title)
            }
            val serial = item.serialText.ifBlank {
                context.getString(R.string.device_runtime_unknown)
            }
            val presenceText = context.getString(
                if (item.isOnline) R.string.device_runtime_online
                else R.string.device_runtime_offline
            )

            binding.tvDeviceName.text = name
            binding.ivDeviceIcon.setImageResource(item.iconRes)
            binding.ivDeviceIcon.imageTintList = null
            binding.ivDeviceIcon.clearColorFilter()
            binding.ivDeviceIcon.contentDescription = name
            binding.tvIp.text = item.ip.ifBlank {
                context.getString(R.string.device_runtime_unknown)
            }
            binding.tvSerialTitle.text = context.getString(R.string.device_runtime_serial_label)
            binding.tvSerial.text = serial
            binding.tvLastSeen.text = item.lastSeenText.ifBlank { "-" }

            binding.ivPresenceIcon.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    context,
                    if (item.isOnline) R.color.device_presence_online
                    else R.color.device_presence_offline
                )
            )
            binding.ivPresenceIcon.contentDescription = presenceText
            binding.root.contentDescription = context.getString(
                R.string.device_runtime_status_content_description,
                name,
                serial,
                presenceText
            )
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
