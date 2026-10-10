package com.aqua.aqualight.ui.tabs.aquarium.detail.groups

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.groups.TankControlGroupDevice
import com.aqua.aqualight.databinding.ItemDeviceCompactCardBinding
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactCardBinder
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactSnapshotMapper

internal data class ControlGroupDeviceRow(val item: TankControlGroupDevice, val enabled: Boolean)

internal class TankControlGroupDeviceAdapter(
    private val selected: Boolean,
    private val onClick: (String) -> Unit,
    private val onDrag: (View, String) -> Boolean
) : ListAdapter<ControlGroupDeviceRow, TankControlGroupDeviceAdapter.Holder>(Diff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemDeviceCompactCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        if (selected) binding.root.layoutParams.width = parent.resources.getDimensionPixelSize(R.dimen.aqua_size_270)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemDeviceCompactCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: ControlGroupDeviceRow) {
            val context = binding.root.context
            val item = row.item
            val text = if (item.compatibility == null) context.getString(R.string.tank_group_identity_unavailable)
                else item.productLabel
            DeviceCompactCardBinder.bind(binding, DeviceCompactSnapshotMapper.map(
                item.device, supportingText = text, showAction = row.enabled,
                actionText = context.getString(if (selected) R.string.tank_group_remove else R.string.tank_group_add)
            ))
            binding.root.isEnabled = row.enabled
            binding.root.isLongClickable = row.enabled
            binding.root.setOnClickListener { if (row.enabled) onClick(item.deviceUid) }
            binding.root.setOnLongClickListener { row.enabled && onDrag(it, item.deviceUid) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<ControlGroupDeviceRow>() {
        override fun areItemsTheSame(oldItem: ControlGroupDeviceRow, newItem: ControlGroupDeviceRow) =
            oldItem.item.deviceUid == newItem.item.deviceUid
        override fun areContentsTheSame(oldItem: ControlGroupDeviceRow, newItem: ControlGroupDeviceRow) =
            oldItem == newItem
    }
}
