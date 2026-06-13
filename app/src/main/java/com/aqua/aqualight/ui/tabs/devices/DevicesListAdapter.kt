package com.aqua.aqualight.ui.tabs.devices

import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
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

        return DeviceViewHolder(
            binding = binding
        )
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

            val deviceName = item.displayName.ifBlank {
                "Device"
            }

            val tankName = item.tankName.ifBlank {
                "Not assigned to a tank"
            }

            binding.tvDeviceName.text = deviceName
            binding.tvProductMeta.text = item.productMetaText.ifBlank {
                item.familyName
            }

            binding.tvDeviceIdentity.text = item.identityText.ifBlank {
                item.serial
            }
            binding.tvDeviceIdentity.isVisible = binding.tvDeviceIdentity.text.isNotBlank()

            binding.tvNetworkMeta.text = item.networkText.ifBlank {
                item.ip
            }
            binding.tvNetworkMeta.isVisible = binding.tvNetworkMeta.text.isNotBlank()

            binding.tvTankName.text = tankName

            binding.ivDeviceIcon.setImageResource(
                DeviceIconMapper.iconFor(item.category)
            )

            binding.ivDeviceIcon.contentDescription = deviceName

            binding.root.contentDescription = buildString {
                append(deviceName)
                append(", ")
                append(tankName)

                if (item.identityText.isNotBlank()) {
                    append(", ")
                    append(item.identityText)
                }

                append(
                    if (item.isOnline) {
                        ", Online"
                    } else {
                        ", Offline"
                    }
                )
            }

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

                binding.card.strokeWidth = DEFAULT_STROKE_WIDTH
                binding.card.strokeColor = ContextCompat.getColor(
                    context,
                    R.color.settings_card_stroke
                )
            }

            binding.root.setOnClickListener {
                if (isSelectionMode) {
                    toggleSelection(
                        item = item
                    )
                } else {
                    onDeviceClick(item)
                }
            }

            binding.root.setOnLongClickListener {
                val firstSelection = selectedIds.isEmpty()

                toggleSelection(
                    item = item
                )

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

        onSelectionChanged(
            selectedIds.size
        )

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
        const val DEFAULT_CARD_ALPHA = 1f
        const val DEFAULT_STROKE_WIDTH = 1
        const val SELECTED_STROKE_WIDTH = 3

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