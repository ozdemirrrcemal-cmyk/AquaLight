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
            binding.tvDeviceName.text = item.displayName.ifBlank {
                "Device"
            }

            binding.tvDeviceMeta.text = buildDeviceMeta(
                item = item
            )

            binding.rowCandidate.setOnClickListener {
                onCandidateClick(item)
            }
        }

        private fun buildDeviceMeta(
            item: DeviceAddCandidate
        ): String {
            val familyName = item.familyName.ifBlank {
                "Aqua device"
            }

            return "$familyName · ${visibleSerial(item)}"
        }

        private fun visibleSerial(
            item: DeviceAddCandidate
        ): String {
            val rawId = when (item.source) {
                DeviceAddSource.LOCAL_NETWORK -> {
                    item.localDevice
                        ?.id
                        ?.toString()
                        .orEmpty()
                }

                DeviceAddSource.SETUP_AP -> {
                    item.setupSsid
                        .orEmpty()
                        .substringAfterLast(
                            delimiter = "-",
                            missingDelimiterValue = ""
                        )
                        .trim()
                }
            }.ifBlank {
                item.key
            }

            return DeviceSerialFormatter.buildSerial(
                aquaName = item.familyName,
                name = item.displayName,
                rawId = rawId
            )
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