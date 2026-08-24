package com.aqua.aqualight.ui.common.devicecard

import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.ItemDeviceCompactCardBinding
import com.aqua.aqualight.ui.common.dosing.pump.DosingPumpProductVisualBinder

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
        val presenceText = context.getString(
            if (item.statusStyle == DeviceCompactStatusStyle.ONLINE) {
                R.string.device_online
            } else {
                R.string.device_offline
            }
        )
        val checkingText = context.getString(R.string.device_menu_checking_accessibility)

        binding.tvDeviceName.text = name
        binding.tvSerialNumber.text = context.getString(R.string.device_serial_value, serial)
        binding.tvTankName.text = supporting
        binding.tvTankName.isVisible = supporting.isNotBlank()

        DosingPumpProductVisualBinder.bind(
            pumpView = binding.dosingPumpVisual,
            fallbackImageView = binding.ivDeviceIcon,
            dosingChannelCount = item.dosingChannelCount,
            fallbackIconRes = item.iconRes,
            contentDescription = name
        )

        binding.ivPresenceIcon.imageTintList = ColorStateList.valueOf(
            presenceIconColor(binding, item.statusStyle)
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

    private fun presenceIconColor(
        binding: ItemDeviceCompactCardBinding,
        style: DeviceCompactStatusStyle
    ): Int {
        val colorRes = when (style) {
            DeviceCompactStatusStyle.ONLINE -> R.color.aqua_accent_positive
            DeviceCompactStatusStyle.CONNECTING,
            DeviceCompactStatusStyle.WARNING,
            DeviceCompactStatusStyle.OFFLINE -> R.color.aqua_device_compact_card_binder_color
        }
        return ContextCompat.getColor(binding.root.context, colorRes)
    }
}
