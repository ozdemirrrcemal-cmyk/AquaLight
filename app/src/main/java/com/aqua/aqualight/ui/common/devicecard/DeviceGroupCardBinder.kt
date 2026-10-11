package com.aqua.aqualight.ui.common.devicecard

import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.isVisible
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.groups.TankControlGroupDevice
import com.aqua.aqualight.databinding.ItemDeviceGroupCardBinding
import com.aqua.aqualight.ui.common.devicepresence.toDeviceConnectionVisualState

/** Shared two-column card for both available and selected group devices. */
internal object DeviceGroupCardBinder {
    fun bind(binding: ItemDeviceGroupCardBinding, item: TankControlGroupDevice,
        selected: Boolean, enabled: Boolean, onMove: () -> Boolean) {
        val context = binding.root.context
        val presence = item.device.availability.toDeviceConnectionVisualState()
        val color = ContextCompat.getColor(context, presence.tintColorRes)
        binding.tvDeviceName.text = item.device.displayName
        binding.tvModel.text = item.productLabel
        binding.tvPresence.setText(presence.statusLabelRes)
        binding.tvPresence.setTextColor(color)
        ViewCompat.setBackgroundTintList(binding.presenceDot, ColorStateList.valueOf(color))
        binding.card.strokeColor = ContextCompat.getColor(context,
            if (selected) R.color.aqua_accent_primary else R.color.aqua_card_device_outline)
        binding.card.strokeWidth = context.resources.getDimensionPixelSize(
            if (selected) R.dimen.aqua_size_2 else R.dimen.aqua_size_1)
        binding.root.isEnabled = enabled
        binding.ivDragGrip.isVisible = enabled
        binding.root.contentDescription = listOf(item.device.displayName, item.productLabel,
            item.device.serialText, context.getString(presence.accessibilityLabelRes)).joinToString(", ")
        val action = AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK
        ViewCompat.removeAccessibilityAction(binding.root, action.id)
        val actionLabel = if (selected) R.string.tank_group_remove_accessibility
            else R.string.tank_group_add_accessibility
        if (enabled) ViewCompat.replaceAccessibilityAction(binding.root, action, context.getString(actionLabel)) {
            _, _ -> onMove()
        }
    }
}
