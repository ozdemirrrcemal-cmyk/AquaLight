package com.aqua.aqualight.ui.tabs.devices

import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.ItemDeviceCardBinding
import com.aqua.aqualight.ui.tabs.devices.model.DeviceCardUi
import com.aqua.aqualight.ui.tabs.devices.model.DeviceIconMapper

class DevicesListAdapter(
    private val onSelectionModeStart: () -> Unit,
    private val onSelectionChanged: (Int) -> Unit,
    private val onDeviceClick: (DeviceCardUi) -> Unit
) : ListAdapter<DeviceCardUi, DevicesListAdapter.DeviceViewHolder>(DiffCallback) {

    private val selectedIds = mutableSetOf<Long>()

    var isSelectionMode = false
        private set

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): DeviceViewHolder {
        val binding = ItemDeviceCardBinding.inflate(
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
            item = getItem(position)
        )
    }

    inner class DeviceViewHolder(
        private val binding: ItemDeviceCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: DeviceCardUi
        ) {
            val context = binding.root.context
            val isSelected = selectedIds.contains(item.id)

            binding.tvDeviceName.text = item.displayName.ifBlank {
                "Device"
            }

            binding.root.contentDescription = buildString {
                append(item.displayName.ifBlank { "Device" })

                if (item.familyName.isNotBlank()) {
                    append(", ")
                    append(item.familyName)
                }

                append(
                    if (item.isOnline) {
                        ", Online"
                    } else {
                        ", Offline"
                    }
                )
            }

            binding.ivDeviceIcon.setImageResource(
                DeviceIconMapper.iconFor(item.deviceType)
            )

            val statusColorRes = if (item.isOnline) {
                R.color.dialog_icon_success
            } else {
                R.color.settings_text_secondary
            }

            binding.ivStatus.setColorFilter(
                ContextCompat.getColor(
                    context,
                    statusColorRes
                ),
                PorterDuff.Mode.SRC_IN
            )

            if (isSelected) {
                binding.root.alpha = 1f
                binding.card.strokeWidth = SELECTED_STROKE_WIDTH
                binding.card.strokeColor = ContextCompat.getColor(
                    context,
                    R.color.dialog_icon_success
                )
            } else {
                binding.root.alpha = DEFAULT_CARD_ALPHA
                binding.card.strokeWidth = 0
                binding.card.strokeColor = ContextCompat.getColor(
                    context,
                    android.R.color.transparent
                )
            }

            binding.root.setOnClickListener {
                if (isSelectionMode) {
                    toggleSelection(item)
                } else {
                    onDeviceClick(item)
                }
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

    private fun toggleSelection(
        item: DeviceCardUi
    ) {
        if (selectedIds.contains(item.id)) {
            selectedIds.remove(item.id)
        } else {
            selectedIds.add(item.id)
        }

        onSelectionChanged(selectedIds.size)
        notifyDataSetChanged()
    }

    fun getSelectedIds(): Set<Long> {
        return selectedIds.toSet()
    }

    fun exitSelectionMode() {
        selectedIds.clear()
        isSelectionMode = false
        notifyDataSetChanged()
    }

    private companion object {
        const val DEFAULT_CARD_ALPHA = 0.88f
        const val SELECTED_STROKE_WIDTH = 4

        val DiffCallback = object : DiffUtil.ItemCallback<DeviceCardUi>() {
            override fun areItemsTheSame(
                oldItem: DeviceCardUi,
                newItem: DeviceCardUi
            ): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(
                oldItem: DeviceCardUi,
                newItem: DeviceCardUi
            ): Boolean {
                return oldItem == newItem
            }
        }
    }
}