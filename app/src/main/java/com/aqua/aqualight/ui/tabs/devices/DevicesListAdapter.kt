package com.aqua.aqualight.ui.tabs.devices

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.ItemDeviceCompactCardBinding
import com.aqua.aqualight.ui.common.devicecard.DeviceCardIconMapper
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactCardBinder
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactCardUi
import com.aqua.aqualight.ui.tabs.devices.model.DeviceCardUi

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
        val binding =
            ItemDeviceCompactCardBinding.inflate(
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
        private val binding: ItemDeviceCompactCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: DeviceCardUi
        ) {
            val context =
                binding.root.context

            val isSelected =
                selectedIds.contains(
                    item.id
                )

            val compactCard =
                item.compactCard ?: item.toFallbackCompactCard()

            DeviceCompactCardBinder.bind(
                binding = binding,
                item = compactCard
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
                    onDeviceClick(
                        item
                    )
                }
            }

            binding.root.setOnLongClickListener {
                val firstSelection =
                    selectedIds.isEmpty()

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

    private fun DeviceCardUi.toFallbackCompactCard(): DeviceCompactCardUi {
        return DeviceCompactCardUi(
            deviceId = id,
            displayName = displayName,
            serialText = serial.ifBlank {
                identityText.substringBefore(
                    delimiter = " • "
                )
            },
            tankText = tankName,
            showTankText = true,
            iconRes = DeviceCardIconMapper.iconFor(
                category = category
            ),
            isOnline = isOnline
        )
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
