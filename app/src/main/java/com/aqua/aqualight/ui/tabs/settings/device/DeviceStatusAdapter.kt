package com.aqua.aqualight.ui.tabs.settings.device

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.ItemDeviceStatusBinding
import com.aqua.aqualight.ui.tabs.devices.DeviceCardUi
import com.aqua.aqualight.ui.tabs.devices.DeviceIconResolver

class DeviceStatusAdapter :
    RecyclerView.Adapter<DeviceStatusAdapter.DeviceViewHolder>() {

    private val items =
        mutableListOf<DeviceCardUi>()

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

            // -------------------------------------------------
            // DEVICE NAME
            // -------------------------------------------------

            binding.tvDeviceName.text =
                item.aquaName.ifBlank {
                    item.name
                }

            // -------------------------------------------------
            // DEVICE INFO
            // -------------------------------------------------

            binding.tvIp.text =
                "IP: ${item.ip}"

            binding.tvSerial.text =
                "Serial: ${item.serial}"

            binding.tvFirmware.text =
    if (item.firmwareBuild.isNotBlank()) {
        "FW: ${item.firmwareBuild}"
    } else {
        "FW: Unknown"
    }

            // -------------------------------------------------
            // DEVICE ICON
            // -------------------------------------------------

            val iconRes =
                DeviceIconResolver.resolve(
                    item.aquaName
                )

            binding.ivDeviceIcon
                .setImageResource(iconRes)

            // -------------------------------------------------
            // ONLINE STATUS
            // -------------------------------------------------

            if (item.isOnline) {

                binding.tvStatus.text =
                    "ONLINE"

                binding.viewStatusDot
                    .setBackgroundResource(
                        R.drawable.bg_online_dot
                    )

            } else {

                binding.tvStatus.text =
                    "OFFLINE"

                binding.viewStatusDot
                    .setBackgroundResource(
                        R.drawable.bg_offline_dot
                    )
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): DeviceViewHolder {

        val binding =
            ItemDeviceStatusBinding.inflate(
                LayoutInflater.from(parent.context),
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
            items[position]
        )
    }

    override fun getItemCount(): Int {

        return items.size
    }
}