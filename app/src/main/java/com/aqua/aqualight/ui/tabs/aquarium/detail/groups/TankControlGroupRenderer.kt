package com.aqua.aqualight.ui.tabs.aquarium.detail.groups

import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTankControlGroupCreateBinding

internal class TankControlGroupRenderer(
    private val binding: FragmentTankControlGroupCreateBinding,
    private val group: TankControlGroupDeviceAdapter,
    private val devices: TankControlGroupDeviceAdapter
) {
    private val context get() = binding.root.context

    fun render(state: TankControlGroupCreateUiState) {
        val interactive = !state.source.isLoading && state.source.tankExists && !state.loadFailed
        val rows = state.selected.map { GroupDeviceRow.Device(it, interactive) }.toMutableList<GroupDeviceRow>()
        while (rows.size < MINIMUM_DEVICES) rows.add(GroupDeviceRow.Slot(rows.size))
        group.submitList(rows)
        devices.submitList(state.available.map { GroupDeviceRow.Device(it, interactive && it.compatibility != null) })
        binding.tvGroupCount.text = context.getString(R.string.tank_group_device_count, state.selected.size)
        binding.tvGroupHint.setText(if (state.selected.isEmpty())
            R.string.tank_group_drop_hint else R.string.tank_group_remove_hint)
        binding.tvDevicesTitle.setText(if (state.selected.isEmpty())
            R.string.tank_group_devices_title else R.string.tank_group_compatible_title)
        binding.tvDevicesCount.text = context.getString(R.string.tank_group_count, state.available.size)
        binding.tvDevicesHint.isVisible = interactive && state.available.isNotEmpty()
        binding.rvDevices.isVisible = interactive && state.available.isNotEmpty()
        val first = state.selected.firstOrNull { it.compatibility != null }
        binding.compatibilityCard.isVisible = first != null
        binding.tvCompatibilityModel.text = first?.productLabel.orEmpty()
        binding.tvHiddenCount.isVisible = interactive && state.hiddenCount > 0
        binding.tvHiddenCount.text = context.getString(R.string.tank_group_hidden_count, state.hiddenCount)
        renderEmpty(state, interactive)
        renderFooter(state)
    }

    private fun renderEmpty(state: TankControlGroupCreateUiState, interactive: Boolean) {
        binding.emptyState.isVisible = !interactive || state.available.isEmpty()
        binding.progressDevices.isVisible = state.source.isLoading && !state.loadFailed
        binding.emptyLightIconContainer.isVisible = interactive && state.selected.isEmpty()
        binding.ivComplete.isVisible = interactive && state.isReady && state.available.isEmpty()
        binding.tvEmptyBody.isVisible = interactive && state.selected.isEmpty()
        val title = when {
            state.loadFailed -> R.string.tank_group_load_failed
            state.source.isLoading -> R.string.tank_group_loading
            !state.source.tankExists -> R.string.tank_group_tank_missing
            state.selected.isEmpty() -> R.string.tank_group_empty_title
            state.isReady -> R.string.tank_group_all_selected
            else -> R.string.tank_group_no_compatible
        }
        binding.tvEmptyTitle.setText(title)
    }

    private fun renderFooter(state: TankControlGroupCreateUiState) {
        binding.tvSelectionStatus.text = if (state.isReady)
            context.getString(R.string.tank_group_ready_count, state.selected.size)
            else context.getString(R.string.tank_group_minimum_two)
        binding.ivSelectionStatus.setImageResource(if (state.isReady)
            R.drawable.ic_device_group_ready else R.drawable.ic_info)
        binding.ivSelectionStatus.imageTintList = if (state.isReady) null else
            ColorStateList.valueOf(ContextCompat.getColor(context, R.color.aqua_content_muted))
        // Presentation only. The persistent creation action is added in the next stage.
        binding.btnCreate.isEnabled = state.isReady
    }

    private companion object {
        const val MINIMUM_DEVICES = 2
    }
}
