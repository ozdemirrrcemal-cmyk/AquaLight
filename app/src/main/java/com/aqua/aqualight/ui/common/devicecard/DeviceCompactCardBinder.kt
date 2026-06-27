package com.aqua.aqualight.ui.common.devicecard

import android.graphics.Color
import androidx.core.view.isVisible
import com.aqua.aqualight.databinding.ItemDeviceCompactCardBinding

object DeviceCompactCardBinder {

    fun bind(
        binding: ItemDeviceCompactCardBinding,
        item: DeviceCompactCardUi
    ) {
        val name = item.displayName.trim().ifBlank { "Device" }
        val serial = item.serialText.trim().ifBlank { item.deviceUid.ifBlank { "Unknown" } }
        val supporting = item.supportingText.trim()

        binding.tvDeviceName.text = name
        binding.tvSerialNumber.text = "UID: $serial"
        binding.tvTankName.text = supporting
        binding.tvTankName.isVisible = supporting.isNotBlank()

        binding.ivDeviceIcon.setImageResource(item.iconRes)
        binding.ivDeviceIcon.imageTintList = null
        binding.ivDeviceIcon.clearColorFilter()
        binding.ivDeviceIcon.contentDescription = name

        binding.tvStatusChip.text = item.statusText.trim().ifBlank { "UNKNOWN" }
        binding.tvStatusChip.setTextColor(statusTextColor(item.statusStyle))

        binding.tvStatusChip.isVisible = !item.showAction
        binding.tvCardAction.text = item.actionText
        binding.tvCardAction.isVisible = item.showAction && item.actionText.isNotBlank()

        binding.trailingContainer.isVisible =
            binding.tvStatusChip.isVisible || binding.tvCardAction.isVisible

        binding.root.contentDescription = buildString {
            append(name)
            append(", UID: ")
            append(serial)
            if (supporting.isNotBlank()) {
                append(", ")
                append(supporting)
            }
            if (item.showAction && item.actionText.isNotBlank()) {
                append(", ")
                append(item.actionText)
            } else if (item.statusText.isNotBlank()) {
                append(", ")
                append(item.statusText)
            }
        }
    }

    private fun statusTextColor(
        style: DeviceCompactStatusStyle
    ): Int {
        return when (style) {
            DeviceCompactStatusStyle.ONLINE -> Color.parseColor("#5FD6B4")
            DeviceCompactStatusStyle.CONNECTING -> Color.parseColor("#8BCAFF")
            DeviceCompactStatusStyle.WARNING -> Color.parseColor("#F9C74F")
            DeviceCompactStatusStyle.OFFLINE -> Color.parseColor("#D85C5C")
        }
    }
}
