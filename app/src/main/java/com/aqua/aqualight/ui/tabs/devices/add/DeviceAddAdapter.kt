package com.aqua.aqualight.ui.tabs.devices.add

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.R
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

        return DeviceAddViewHolder(binding)
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
            binding.tvDeviceFamily.text = item.familyName
            binding.tvDeviceState.text = getStateText(item)
            binding.tvConnectionInfo.text = getConnectionInfo(item)
            binding.tvAction.text = item.actionText

            applySourceStyle(item)

            binding.rowCandidate.setOnClickListener {
                onCandidateClick(item)
            }
        }

        private fun applySourceStyle(
            item: DeviceAddCandidate
        ) {
            when (item.source) {
                DeviceAddSource.SETUP_AP -> {
                    binding.tvDeviceState.setBackgroundResource(
                        R.drawable.bg_device_add_status_setup
                    )

                    binding.tvDeviceState.setTextColor(
                        Color.parseColor("#2D7BFF")
                    )

                    binding.tvAction.setTextColor(
                        Color.parseColor("#2D7BFF")
                    )
                }

                DeviceAddSource.LOCAL_NETWORK -> {
                    binding.tvDeviceState.setBackgroundResource(
                        R.drawable.bg_device_add_status_connected
                    )

                    binding.tvDeviceState.setTextColor(
                        Color.parseColor("#67D982")
                    )

                    binding.tvAction.setTextColor(
                        Color.parseColor("#67D982")
                    )
                }
            }
        }

        private fun getStateText(
            item: DeviceAddCandidate
        ): String {
            return when (item.source) {
                DeviceAddSource.SETUP_AP -> {
                    "● Ready for setup"
                }

                DeviceAddSource.LOCAL_NETWORK -> {
                    "● Already connected"
                }
            }
        }

        private fun getConnectionInfo(
            item: DeviceAddCandidate
        ): String {
            return when (item.source) {
                DeviceAddSource.SETUP_AP -> {
                    item.setupSsid.orEmpty()
                }

                DeviceAddSource.LOCAL_NETWORK -> {
                    item.localDevice?.ip.orEmpty()
                }
            }.ifBlank {
                item.familyName
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