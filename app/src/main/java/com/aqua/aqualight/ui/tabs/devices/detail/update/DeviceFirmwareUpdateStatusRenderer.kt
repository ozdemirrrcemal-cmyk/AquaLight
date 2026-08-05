package com.aqua.aqualight.ui.tabs.devices.detail.update

import android.content.res.ColorStateList
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceFirmwareUpdateBinding

/** Renders hero, primary action and accessibility announcements. */
internal class DeviceFirmwareUpdateStatusRenderer(
    private val fragment: Fragment,
    private val binding: FragmentDeviceFirmwareUpdateBinding,
    private val motion: DeviceFirmwareUpdateMotionController
) {
    private var lastAnnouncementKey = ""

    fun renderHero(state: DeviceFirmwareUpdateUiState, modeChanged: Boolean) {
        val unavailable = string(R.string.common_not_available_em_dash)
        val deviceName = state.deviceName.ifBlank {
            string(R.string.device_settings_update_device_fallback)
        }
        val presentation = DeviceFirmwareUpdateHeroPresentationMapper.map(state)

        binding.tvUpdateStatusBadge.apply {
            setText(presentation.statusTextRes)
            setTextColor(color(presentation.statusColorRes))
            backgroundTintList = ColorStateList.valueOf(
                color(presentation.statusBackgroundColorRes)
            )
        }
        binding.ivUpdateHeroIcon.apply {
            setImageResource(presentation.icon.drawableRes)
            setIconTint(this, presentation.icon.colorRes)
        }
        binding.tvUpdateHeroTitle.setText(presentation.titleRes)
        binding.tvUpdateHeroSummary.text = resolve(presentation.summary)
        binding.tvUpdateDeviceName.text = string(
            R.string.device_settings_update_for_device,
            deviceName
        )
        binding.tvInstalledVersion.text = state.currentVersion.ifBlank { unavailable }
        binding.tvTargetVersion.text = state.targetVersion.ifBlank { unavailable }
        binding.targetVersionGroup.isVisible = state.targetVersion.isNotBlank()
        binding.ivVersionArrow.isVisible = state.currentVersion.isNotBlank() &&
            state.targetVersion.isNotBlank()

        if (modeChanged) motion.animateHeroEntrance()
    }

    fun renderAction(state: DeviceFirmwareUpdateUiState) {
        val presentation = DeviceFirmwareUpdateProgressPresentationMapper.action(state)
        binding.progressUpdateAction.isVisible = presentation.loading
        binding.btnUpdateAction.apply {
            isEnabled = presentation.enabled
            text = if (presentation.loading) "" else string(presentation.textRes)
            contentDescription = string(presentation.textRes)
        }
        binding.tvUpdateActionHint.apply {
            val hintRes = DeviceFirmwareUpdateProgressPresentationMapper.actionHintRes(state.mode)
            isVisible = hintRes != null
            if (hintRes != null) setText(hintRes)
        }
    }

    fun announceStateChange(state: DeviceFirmwareUpdateUiState, detail: CharSequence) {
        val key = "${state.mode}:${state.phase.orEmptyName()}:${state.failure?.reason.orEmptyName()}"
        if (lastAnnouncementKey.isNotBlank() && key != lastAnnouncementKey) {
            binding.firmwareUpdateContent.announceForAccessibility(
                detail.ifBlank {
                    string(DeviceFirmwareUpdateProgressPresentationMapper.phaseTextRes(state))
                }
            )
        }
        lastAnnouncementKey = key
    }

    private fun resolve(text: DeviceFirmwareUpdateText): String =
        text.formatArg?.let { formatArg ->
            string(text.stringRes, formatArg)
        } ?: string(text.stringRes)

    private fun setIconTint(view: AppCompatImageView, @ColorRes colorRes: Int) {
        ImageViewCompat.setImageTintList(
            view,
            ColorStateList.valueOf(color(colorRes))
        )
    }

    private fun Enum<*>?.orEmptyName(): String = this?.name.orEmpty()

    private fun string(@StringRes stringRes: Int): String =
        fragment.getString(stringRes)

    private fun string(@StringRes stringRes: Int, formatArg: Any): String =
        fragment.getString(stringRes, formatArg)

    private fun color(@ColorRes colorRes: Int): Int =
        ContextCompat.getColor(fragment.requireContext(), colorRes)
}
