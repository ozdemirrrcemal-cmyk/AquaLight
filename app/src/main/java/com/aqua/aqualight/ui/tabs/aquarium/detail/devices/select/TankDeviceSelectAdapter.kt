package com.aqua.aqualight.ui.tabs.aquarium.detail.devices.select

import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.ItemTankDeviceSelectCardBinding

class TankDeviceSelectAdapter(
    private val onDeviceClick: (TankDeviceSelectItem) -> Unit
) : ListAdapter<TankDeviceSelectItem, TankDeviceSelectAdapter.ViewHolder>(
    DiffCallback
) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding =
            ItemTankDeviceSelectCardBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )

        return ViewHolder(
            binding = binding,
            onDeviceClick = onDeviceClick
        )
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        holder.bind(
            item = getItem(position)
        )
    }

    class ViewHolder(
        private val binding: ItemTankDeviceSelectCardBinding,
        private val onDeviceClick: (TankDeviceSelectItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: TankDeviceSelectItem
        ) {
            val context =
                binding.root.context

            val deviceName =
                item.title
                    .trim()
                    .ifBlank {
                        context.getString(
                            R.string.tank_device_select_unknown_device
                        )
                    }

            val serialNumber =
                item.serialNumber
                    .trim()
                    .ifBlank {
                        context.getString(
                            R.string.tank_device_select_serial_unavailable
                        )
                    }

            binding.tvDeviceName.text =
                deviceName

            binding.tvSerialNumber.text =
                serialNumber

            binding.ivDeviceIcon.setImageResource(
                item.iconRes
            )

            binding.ivDeviceIcon.contentDescription =
                deviceName

            val statusColorRes =
                if (item.isOnline) {
                    R.color.dialog_icon_success
                } else {
                    R.color.settings_text_secondary
                }

            binding.ivConnectionStatus.setColorFilter(
                ContextCompat.getColor(
                    context,
                    statusColorRes
                ),
                PorterDuff.Mode.SRC_IN
            )

            binding.root.contentDescription =
                buildString {
                    append(deviceName)
                    append(", ")
                    append(serialNumber)
                    append(
                        if (item.isOnline) {
                            ", Online"
                        } else {
                            ", Offline"
                        }
                    )
                }

            binding.root.setOnClickListener {
                onDeviceClick(item)
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<TankDeviceSelectItem>() {

        override fun areItemsTheSame(
            oldItem: TankDeviceSelectItem,
            newItem: TankDeviceSelectItem
        ): Boolean {
            return oldItem.deviceId == newItem.deviceId
        }

        override fun areContentsTheSame(
            oldItem: TankDeviceSelectItem,
            newItem: TankDeviceSelectItem
        ): Boolean {
            return oldItem == newItem
        }
    }
}