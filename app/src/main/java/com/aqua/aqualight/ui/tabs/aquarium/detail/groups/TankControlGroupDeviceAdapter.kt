package com.aqua.aqualight.ui.tabs.aquarium.detail.groups

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.application.devices.groups.TankControlGroupDevice
import com.aqua.aqualight.databinding.ItemDeviceGroupCardBinding
import com.aqua.aqualight.databinding.ItemDeviceGroupSlotBinding
import com.aqua.aqualight.ui.common.devicecard.DeviceGroupCardBinder

internal sealed interface GroupDeviceRow {
    data class Device(val item: TankControlGroupDevice, val enabled: Boolean) : GroupDeviceRow
    data class Slot(val index: Int) : GroupDeviceRow
}

internal class TankControlGroupDeviceAdapter(
    private val selected: Boolean,
    private val onMove: (String) -> Boolean,
    private val onDrag: (View, String) -> Boolean
) : ListAdapter<GroupDeviceRow, RecyclerView.ViewHolder>(Diff) {
    override fun getItemViewType(position: Int): Int = if (getItem(position) is GroupDeviceRow.Slot) SLOT else DEVICE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == SLOT) SlotHolder(ItemDeviceGroupSlotBinding.inflate(inflater, parent, false).root)
            else DeviceHolder(ItemDeviceGroupCardBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is DeviceHolder) holder.bind(getItem(position) as GroupDeviceRow.Device)
    }

    private inner class DeviceHolder(
        private val binding: ItemDeviceGroupCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: GroupDeviceRow.Device) {
            DeviceGroupCardBinder.bind(binding, row.item, selected, row.enabled) { onMove(row.item.deviceUid) }
            binding.root.isLongClickable = row.enabled
            binding.root.setOnLongClickListener { row.enabled && onDrag(it, row.item.deviceUid) }
        }
    }

    private class SlotHolder(view: View) : RecyclerView.ViewHolder(view)

    private object Diff : DiffUtil.ItemCallback<GroupDeviceRow>() {
        override fun areItemsTheSame(oldItem: GroupDeviceRow, newItem: GroupDeviceRow): Boolean = when {
            oldItem is GroupDeviceRow.Device && newItem is GroupDeviceRow.Device ->
                oldItem.item.deviceUid == newItem.item.deviceUid
            oldItem is GroupDeviceRow.Slot && newItem is GroupDeviceRow.Slot -> oldItem.index == newItem.index
            else -> false
        }
        override fun areContentsTheSame(oldItem: GroupDeviceRow, newItem: GroupDeviceRow): Boolean = oldItem == newItem
    }

    private companion object {
        const val SLOT = 0
        const val DEVICE = 1
    }
}
