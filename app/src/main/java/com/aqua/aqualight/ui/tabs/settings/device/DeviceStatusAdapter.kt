package com.aqua.aqualight.ui.tabs.settings.device

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.ItemDeviceStatusBinding
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactVisualKind
import com.aqua.aqualight.ui.common.devicevisual.dosing.DosingDeviceVisualViewBinder
import com.aqua.aqualight.ui.common.text.resolve

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
                context.getString(R.string.device_unknown)
            }
            val presenceText = context.getString(
                if (item.isOnline) R.string.device_online else R.string.device_offline
            )

            binding.tvDeviceName.text = name
            bindDeviceVisual(item, name)
            binding.tvIp.text = item.ip.ifBlank { context.getString(R.string.device_unknown) }
            binding.tvSerialTitle.setText(R.string.device_label_serial)
            binding.tvSerial.text = serial
            binding.tvLastSeen.text = context.resolve(item.lastSeenText)

            binding.ivPresenceIcon.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    context,
                    if (item.isOnline) R.color.aqua_accent_positive
                    else R.color.aqua_device_status_adapter_color
                )
            )
            binding.ivPresenceIcon.contentDescription = presenceText
            binding.root.contentDescription = context.getString(
                R.string.device_status_accessibility,
                name,
                serial,
                presenceText
            )
        }

        private fun bindDeviceVisual(
            item: DeviceStatusItem,
            name: String
        ) {
            if (item.visualKind == DeviceCompactVisualKind.DOSING_IDENTITY) {
                DosingDeviceVisualViewBinder.bindIdentity(
                    container = binding.deviceIconContainer,
                    fallbackView = binding.ivDeviceIcon,
                    contentDescription = name
                )
            } else {
                DosingDeviceVisualViewBinder.clearIdentity(
                    container = binding.deviceIconContainer,
                    fallbackView = binding.ivDeviceIcon
                )
                binding.ivDeviceIcon.setImageResource(item.iconRes)
                binding.ivDeviceIcon.imageTintList = null
                binding.ivDeviceIcon.clearColorFilter()
                binding.ivDeviceIcon.contentDescription = name
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
