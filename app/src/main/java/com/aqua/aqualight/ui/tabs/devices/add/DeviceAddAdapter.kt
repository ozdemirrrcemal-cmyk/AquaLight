package com.aqua.aqualight.ui.tabs.devices.add

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.data.devices.DeviceSerialFormatter
import com.aqua.aqualight.data.devices.add.DeviceAddCandidate
import com.aqua.aqualight.data.devices.add.DeviceAddSource
import com.aqua.aqualight.databinding.ItemDeviceAddCandidateBinding
import com.aqua.aqualight.ui.tabs.devices.model.DeviceIconMapper

class DeviceAddAdapter(
    private val onCandidateClick: (DeviceAddCandidate) -> Unit
) : ListAdapter<DeviceAddCandidate, DeviceAddAdapter.DeviceAddViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): DeviceAddViewHolder {
        val binding = ItemDeviceAddCandidateBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return DeviceAddViewHolder(
            binding = binding
        )
    }

    override fun onBindViewHolder(
        holder: DeviceAddViewHolder,
        position: Int
    ) {
        holder.bind(
            item = getItem(position)
        )
    }

    inner class DeviceAddViewHolder(
        private val binding: ItemDeviceAddCandidateBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: DeviceAddCandidate
        ) {
            binding.ivDeviceIcon.setImageResource(
                DeviceIconMapper.iconFor(item.deviceType)
            )

            binding.ivDeviceIcon.contentDescription = item.displayName

            binding.tvDeviceName.text = item.displayName.ifBlank {
                "Device"
            }

            binding.tvDeviceSerial.text = visibleSerial(
                item = item
            )

            binding.rowCandidate.setOnClickListener {
                onCandidateClick(item)
            }
        }

        private fun visibleSerial(
            item: DeviceAddCandidate
        ): String {
            return when (item.source) {
                DeviceAddSource.SETUP_AP -> {
                    val setupId = item.setupShortId
                        .orEmpty()
                        .trim()

                    if (setupId.isBlank()) {
                        "Setup mode"
                    } else {
                        "Setup ID: ${setupId.uppercase()}"
                    }
                }

                DeviceAddSource.LOCAL_NETWORK -> {
                    val localDevice = item.localDevice

                    if (localDevice == null) {
                        "Device"
                    } else {
                        DeviceSerialFormatter.buildSerial(
                            aquaName = item.familyName,
                            name = item.displayName,
                            id = localDevice.id,
                            firmwareSerial = localDevice.firmwareSerial.orEmpty(),
                            deviceUid = localDevice.deviceUid.orEmpty(),
                            macAddress = localDevice.macAddress.orEmpty()
                        )
                    }
                }
            }
        }
    }

    private companion object {
        val DiffCallback = object : DiffUtil.ItemCallback<DeviceAddCandidate>() {
            override fun areItemsTheSame(
                oldItem: DeviceAddCandidate,
                newItem: DeviceAddCandidate
            ): Boolean {
                return oldItem.key == newItem.key
            }

            override fun areContentsTheSame(
                oldItem: DeviceAddCandidate,
                newItem: DeviceAddCandidate
            ): Boolean {
                return oldItem == newItem
            }
        }
    }
}