package com.aqua.aqualight.ui.common.devicecard

import android.content.res.ColorStateList
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
        val presenceText = if (item.statusStyle == DeviceCompactStatusStyle.ONLINE) {
            "Online"
        } else {
            "Offline"
        }

        binding.tvDeviceName.text = name
        binding.tvSerialNumber.text = "Serial: $serial"
        binding.tvTankName.text = supporting
        binding.tvTankName.isVisible = supporting.isNotBlank()

        binding.ivDeviceIcon.setImageResource(item.iconRes)
        binding.ivDeviceIcon.imageTintList = null
        binding.ivDeviceIcon.clearColorFilter()
        binding.ivDeviceIcon.contentDescription = name

        binding.ivPresenceIcon.imageTintList = ColorStateList.valueOf(
            presenceIconColor(item.statusStyle)
        )
        binding.ivPresenceIcon.contentDescription = presenceText
        binding.ivPresenceIcon.isVisible = !item.showAction

        binding.tvCardAction.text = item.actionText
        binding.tvCardAction.isVisible = item.showAction && item.actionText.isNotBlank()

        binding.trailingContainer.isVisible =
            binding.ivPresenceIcon.isVisible || binding.tvCardAction.isVisible

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
            } else {
                append(", ")
                append(presenceText)
            }
        }
    }

    private fun presenceIconColor(
        style: DeviceCompactStatusStyle
    ): Int {
        return when (style) {
            DeviceCompactStatusStyle.ONLINE -> Color.parseColor("#5FD6B4")
            DeviceCompactStatusStyle.CONNECTING,
            DeviceCompactStatusStyle.WARNING,
            DeviceCompactStatusStyle.OFFLINE -> Color.parseColor("#7B8794")
        }
    }
}
