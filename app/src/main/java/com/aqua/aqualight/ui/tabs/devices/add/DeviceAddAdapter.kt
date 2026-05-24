package com.aqua.aqualight.ui.tabs.devices.add

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
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

            binding.tvDeviceName.text = item.displayName
            binding.tvDeviceId.text = shortDeviceId(item)

            binding.rowCandidate.setOnClickListener {
                onCandidateClick(item)
            }
        }

        private fun shortDeviceId(
            item: DeviceAddCandidate
        ): String {
            return when (item.source) {
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
                item.key.takeLast(4)
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