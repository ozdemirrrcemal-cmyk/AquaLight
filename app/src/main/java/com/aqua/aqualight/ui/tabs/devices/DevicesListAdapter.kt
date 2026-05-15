package com.aqua.aqualight.ui.tabs.devices

import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.databinding.ItemDeviceCardBinding
import com.aqua.aqualight.ui.model.DeviceCardUi

class DevicesListAdapter(
    private val onSelectionModeStart: () -> Unit,
    private val onSelectionChanged: (Int) -> Unit,
    private val onDeviceClick: (DeviceCardUi) -> Unit
) : ListAdapter<DeviceCardUi, DevicesListAdapter.DeviceViewHolder>(DiffCallback) {

    private val selectedIds = mutableSetOf<Long>()
    var isSelectionMode = false
        private set

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DeviceViewHolder(private val binding: ItemDeviceCardBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DeviceCardUi) {
            val ctx = binding.root.context
            val isSelected = selectedIds.contains(item.id)

            // Device name
            binding.tvDeviceName.text = if (item.aquaName.isNotBlank()) item.aquaName else item.name

            // Icon from enum
            binding.ivDeviceIcon.setImageResource(item.type.iconRes)

            // Online status
            val statusColorRes = if (item.isOnline) com.aqua.aqualight.R.color.dialog_icon_success
                                 else com.aqua.aqualight.R.color.settings_text_secondary
            binding.ivStatus.setColorFilter(ContextCompat.getColor(ctx, statusColorRes), PorterDuff.Mode.SRC_IN)

            // Selection UI
            if (isSelected) {
                binding.root.alpha = 1f
                binding.card.strokeWidth = 4
                binding.card.strokeColor = ContextCompat.getColor(ctx, com.aqua.aqualight.R.color.dialog_icon_success)
            } else {
                binding.root.alpha = 0.88f
                binding.card.strokeWidth = 0
                binding.card.strokeColor = ContextCompat.getColor(ctx, android.R.color.transparent)
            }

            // Click listeners
            binding.root.setOnClickListener {
                if (isSelectionMode) toggleSelection(item) else onDeviceClick(item)
            }

            binding.root.setOnLongClickListener {
                val firstSelection = selectedIds.isEmpty()
                toggleSelection(item)
                if (firstSelection) {
                    isSelectionMode = true
                    onSelectionModeStart()
                }
                true
            }
        }
    }

    private fun toggleSelection(item: DeviceCardUi) {
        if (selectedIds.contains(item.id)) selectedIds.remove(item.id) else selectedIds.add(item.id)
        onSelectionChanged(selectedIds.size)
        notifyDataSetChanged()
    }

    fun getSelectedIds(): Set<Long> = selectedIds.toSet()

    fun exitSelectionMode() {
        selectedIds.clear()
        isSelectionMode = false
        notifyDataSetChanged()
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<DeviceCardUi>() {
            override fun areItemsTheSame(oldItem: DeviceCardUi, newItem: DeviceCardUi) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: DeviceCardUi, newItem: DeviceCardUi) = oldItem == newItem
        }
    }
}