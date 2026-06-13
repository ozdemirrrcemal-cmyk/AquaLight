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

        binding.tvSerialNumber.text =
            context.getString(
                R.string.device_card_serial_format,
                serialValue
            )

        val tankValue =
            item.tankText
                .trim()
                .ifBlank {
                    context.getString(
                        R.string.device_card_not_assigned
                    )
                }

        binding.tvTankName.text =
            context.getString(
                R.string.device_card_tank_format,
                tankValue
            )

        binding.tvTankName.isVisible =
            item.showTankText

        binding.ivDeviceIcon.setImageResource(
            item.iconRes
        )

        binding.ivDeviceIcon.imageTintList =
            null

        binding.ivDeviceIcon.clearColorFilter()

        binding.ivDeviceIcon.contentDescription =
            deviceName

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

        binding.root.contentDescription =
            buildString {
                append(deviceName)
                append(", ")
                append(
                    context.getString(
                        R.string.device_card_serial_format,
                        serialValue
                    )
                )

                if (item.showTankText) {
                    append(", ")
                    append(
                        context.getString(
                            R.string.device_card_tank_format,
                            tankValue
                        )
                    )
                }

                append(
                    if (item.isOnline) {
                        ", Online"
                    } else {
                        ", Offline"
                    }
                )
            }
    }
}
