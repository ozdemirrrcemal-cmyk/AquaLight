package com.aqua.aqualight.ui.common.devicecard

import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.ItemDeviceCompactCardBinding

object DeviceCompactCardBinder {

    fun bind(
        binding: ItemDeviceCompactCardBinding,
        item: DeviceCompactCardUi
    ) {
        val context = binding.root.context
        val name = item.displayName.trim().ifBlank {
            context.getString(R.string.device_menu_default_title)
        }
        val serial = item.serialText.trim().ifBlank {
            item.deviceUid.ifBlank { context.getString(R.string.device_runtime_unknown) }
        }
        val supporting = item.supportingText.trim()
        val presenceText = context.getString(
            if (item.statusStyle == DeviceCompactStatusStyle.ONLINE) {
                R.string.device_runtime_online
            } else {
                R.string.device_runtime_offline
            }
        )

        binding.tvDeviceName.text = name
        binding.tvSerialNumber.text = context.getString(
            R.string.device_runtime_serial_format,
            serial
        )
        binding.tvTankName.text = supporting
        binding.tvTankName.isVisible = supporting.isNotBlank()

        binding.ivDeviceIcon.setImageResource(item.iconRes)
        binding.ivDeviceIcon.imageTintList = null
        binding.ivDeviceIcon.clearColorFilter()
        binding.ivDeviceIcon.contentDescription = name

        binding.ivPresenceIcon.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                context,
                if (item.statusStyle == DeviceCompactStatusStyle.ONLINE) {
                    R.color.device_presence_online
                } else {
                    R.color.device_presence_offline
                }
            )
        )
        binding.ivPresenceIcon.contentDescription = presenceText
        binding.ivPresenceIcon.isVisible = !item.showAction

        binding.tvCardAction.text = item.actionText
        binding.tvCardAction.isVisible = item.showAction && item.actionText.isNotBlank()

        binding.trailingContainer.isVisible =
            binding.ivPresenceIcon.isVisible || binding.tvCardAction.isVisible

        binding.root.contentDescription = buildString {
            append(
                context.getString(
                    R.string.device_compact_content_description_base,
                    name,
                    serial
                )
            )
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
}
