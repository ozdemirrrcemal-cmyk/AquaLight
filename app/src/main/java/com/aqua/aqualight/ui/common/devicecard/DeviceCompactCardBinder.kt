package com.aqua.aqualight.ui.common.devicecard

import android.graphics.PorterDuff
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.ItemDeviceCompactCardBinding

object DeviceCompactCardBinder {

    fun bind(
        binding: ItemDeviceCompactCardBinding,
        item: DeviceCompactCardUi
    ) {
        val context =
            binding.root.context

        val deviceName =
            item.displayName
                .trim()
                .ifBlank {
                    context.getString(
                        R.string.device_card_unknown_device
                    )
                }

        val serialValue =
            item.serialText
                .trim()
                .ifBlank {
                    context.getString(
                        R.string.device_card_serial_unavailable
                    )
                }

        binding.tvDeviceName.text =
            deviceName

        val formattedSerial =
            context.getString(
                R.string.device_card_serial_format,
                serialValue
            )

        binding.tvSerialNumber.text =
            formattedSerial

        val tankValue =
            item.tankText
                .trim()
                .ifBlank {
                    context.getString(
                        R.string.device_card_not_assigned
                    )
                }

        val secondaryText =
            when {
                item.showTankText -> {
                    context.getString(
                        R.string.device_card_tank_format,
                        tankValue
                    )
                }

                item.showSupportingText -> {
                    item.supportingText.trim()
                }

                else -> ""
            }

        binding.tvTankName.text =
            secondaryText

        binding.tvTankName.isVisible =
            secondaryText.isNotBlank()

        binding.ivDeviceIcon.setImageResource(
            item.iconRes
        )

        binding.ivDeviceIcon.imageTintList =
            null

        binding.ivDeviceIcon.clearColorFilter()

        binding.ivDeviceIcon.contentDescription =
            deviceName

        binding.ivConnectionStatus.isVisible =
            item.showConnectionStatus && !item.showAction

        if (item.showConnectionStatus) {
            val statusColorRes =
                if (item.isOnline) {
                    R.color.dialog_icon_success
                } else {
                    R.color.settings_text_secondary
                }

            binding.ivConnectionStatus.setColorFilter(
                ContextCompat.getColor(
                    context,
                    statusColorRes
                ),
                PorterDuff.Mode.SRC_IN
            )
        } else {
            binding.ivConnectionStatus.clearColorFilter()
        }

        val actionValue =
            item.actionText
                .trim()

        binding.tvCardAction.text =
            actionValue

        binding.tvCardAction.isVisible =
            item.showAction && actionValue.isNotBlank()

        binding.trailingContainer.isVisible =
            binding.ivConnectionStatus.isVisible ||
                binding.tvCardAction.isVisible

        binding.root.contentDescription =
            buildString {
                append(deviceName)
                append(", ")
                append(formattedSerial)

                if (secondaryText.isNotBlank()) {
                    append(", ")
                    append(secondaryText)
                }

                if (item.showConnectionStatus) {
                    append(
                        if (item.isOnline) {
                            ", Online"
                        } else {
                            ", Offline"
                        }
                    )
                }

                if (binding.tvCardAction.isVisible) {
                    append(", ")
                    append(actionValue)
                }
            }
    }
}
