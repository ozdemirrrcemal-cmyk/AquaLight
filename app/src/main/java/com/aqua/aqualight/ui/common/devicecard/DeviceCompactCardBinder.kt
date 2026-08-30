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
            item.deviceUid.ifBlank { context.getString(R.string.device_unknown) }
        }
        val supporting = item.supportingText.trim()
        val presenceText = context.getString(item.statusStyle.accessibilityLabelRes)
        val checkingText = context.getString(R.string.device_menu_checking_accessibility)

        binding.tvDeviceName.text = name
        binding.tvSerialNumber.text = context.getString(R.string.device_serial_value, serial)
        binding.tvTankName.text = supporting
        binding.tvTankName.isVisible = supporting.isNotBlank()

        binding.ivDeviceIcon.setImageResource(item.iconRes)
        binding.ivDeviceIcon.imageTintList = null
        binding.ivDeviceIcon.clearColorFilter()
        binding.ivDeviceIcon.contentDescription = name

        binding.ivPresenceIcon.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(context, item.statusStyle.tintColorRes)
        )
        binding.ivPresenceIcon.contentDescription = presenceText
        binding.ivPresenceIcon.isVisible = !item.isBusy && !item.showAction

        binding.progressCardAction.isVisible = item.isBusy
        binding.progressCardAction.contentDescription = checkingText

        binding.tvCardAction.text = item.actionText
        binding.tvCardAction.isVisible =
            !item.isBusy && item.showAction && item.actionText.isNotBlank()

        binding.trailingContainer.isVisible =
            binding.ivPresenceIcon.isVisible ||
                binding.progressCardAction.isVisible ||
                binding.tvCardAction.isVisible

        binding.root.isEnabled = !item.isBusy
        val trailingText = when {
            item.isBusy -> checkingText
            item.showAction && item.actionText.isNotBlank() -> item.actionText
            else -> presenceText
        }
        binding.root.contentDescription = if (supporting.isBlank()) {
            context.getString(R.string.device_card_accessibility, name, serial, trailingText)
        } else {
            context.getString(
                R.string.device_card_accessibility_with_supporting,
                name,
                serial,
                supporting,
                trailingText
            )
        }
    }
}
