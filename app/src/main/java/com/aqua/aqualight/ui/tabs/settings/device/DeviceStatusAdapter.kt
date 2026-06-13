package com.aqua.aqualight.ui.tabs.settings.device

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.ItemDeviceStatusBinding
import com.aqua.aqualight.ui.tabs.devices.model.DeviceCardUi
import com.aqua.aqualight.ui.common.devicecard.DeviceCardIconMapper

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

            binding.tvTankName.text = buildString {
                append(
                    item.productMetaText.ifBlank {
                        item.familyName
                    }
                )

                val tankName = item.tankName.ifBlank {
                    "Not connected"
                }

                if (tankName.isNotBlank()) {
                    if (isNotBlank()) {
                        append(" • ")
                    }
                    append(tankName)
                }
            }

            binding.ivDeviceIcon.setImageResource(
                DeviceCardIconMapper.iconFor(item.category)
            )

            binding.tvIp.text = item.ip.ifBlank {
                "Unknown"
            }

            binding.tvSerialTitle.text = "Device code"
            binding.tvSerial.text = item.identityText.ifBlank {
                item.serial.ifBlank {
                    "Unknown"
                }
            }

            binding.tvFirmwareTitle.text = "Product"
            binding.tvFirmware.text = item.skuCode.ifBlank {
                item.productKey.storageKey.takeIf { value ->
                    value != "UNKNOWN"
                } ?: item.productId.ifBlank {
                    "Unknown"
                }
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