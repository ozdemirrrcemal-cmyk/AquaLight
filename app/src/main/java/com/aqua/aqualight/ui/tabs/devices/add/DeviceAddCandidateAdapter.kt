package com.aqua.aqualight.ui.tabs.devices.add

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.ItemDeviceAddCandidateBinding

class DeviceAddCandidateAdapter(
    private val onClick: (DeviceAddCandidateUi) -> Unit
) : ListAdapter<DeviceAddCandidateUi, DeviceAddCandidateAdapter.CandidateViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): CandidateViewHolder {
        val binding = ItemDeviceAddCandidateBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CandidateViewHolder(binding = binding)
    }

    override fun onBindViewHolder(
        holder: CandidateViewHolder,
        position: Int
    ) {
        holder.bind(getItem(position))
    }

    inner class CandidateViewHolder(
        private val binding: ItemDeviceAddCandidateBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(candidate: DeviceAddCandidateUi) {
            binding.tvCandidateTitle.text = candidate.title
            binding.tvCandidateSerial.text = binding.root.context.getString(
                R.string.device_serial_value,
                candidate.serial
            )
            binding.tvCandidateModel.text = candidate.model
            binding.tvCandidateStatus.text = candidate.status

            binding.root.setOnClickListener {
                onClick(candidate)
            }
        }
    }

    private companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<DeviceAddCandidateUi>() {
            override fun areItemsTheSame(
                oldItem: DeviceAddCandidateUi,
                newItem: DeviceAddCandidateUi
            ): Boolean = oldItem.id == newItem.id

            override fun areContentsTheSame(
                oldItem: DeviceAddCandidateUi,
                newItem: DeviceAddCandidateUi
            ): Boolean = oldItem == newItem
        }
    }
}
