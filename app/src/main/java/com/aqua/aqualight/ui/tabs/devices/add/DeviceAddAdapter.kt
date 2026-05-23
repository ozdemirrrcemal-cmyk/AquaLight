package com.aqua.aqualight.ui.tabs.devices.add

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.databinding.ItemDeviceAddCandidateBinding
import com.aqua.aqualight.ui.tabs.devices.model.DeviceIconMapper
import com.aqua.aqualight.data.devices.add.DeviceAddCandidate
import com.aqua.aqualight.data.devices.add.DeviceAddSource

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

            binding.tvDeviceName.text = item.displayName
            binding.tvDeviceFamily.text = item.familyName
            binding.tvDeviceState.text = item.stateText
            binding.btnCandidateAction.text = item.actionText

            val stateColor = when (item.source) {
                DeviceAddSource.SETUP_AP -> Color.parseColor("#6EA8FF")
                DeviceAddSource.LOCAL_NETWORK -> Color.parseColor("#5FD6B4")
            }

            binding.tvDeviceState.setTextColor(stateColor)

            binding.root.setOnClickListener {
                onCandidateClick(item)
            }

            binding.btnCandidateAction.setOnClickListener {
                onCandidateClick(item)
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