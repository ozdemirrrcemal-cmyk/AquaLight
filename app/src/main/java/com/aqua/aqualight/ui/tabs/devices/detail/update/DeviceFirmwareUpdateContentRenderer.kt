package com.aqua.aqualight.ui.tabs.devices.detail.update

import android.content.res.ColorStateList
import android.text.format.Formatter
import android.view.LayoutInflater
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceFirmwareReleaseContent
import com.aqua.aqualight.databinding.FragmentDeviceFirmwareUpdateBinding
import com.aqua.aqualight.databinding.LayoutDeviceFirmwareReleaseItemBinding
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootPresentationMapper

/** Renders progress, verified release content and operational requirements. */
@Suppress("TooManyFunctions")
internal class DeviceFirmwareUpdateContentRenderer(
    private val fragment: Fragment,
    private val binding: FragmentDeviceFirmwareUpdateBinding,
    private val motion: DeviceFirmwareUpdateMotionController
) {
    private var renderedReleaseContent: DeviceFirmwareReleaseContent? = null
    private var requirementsRendered = false

    fun render(state: DeviceFirmwareUpdateUiState) {
        renderProgress(state)
        renderReleaseNotes(state)
        renderRequirements(state)
    }

    fun progressDetail(state: DeviceFirmwareUpdateUiState): CharSequence = when {
        state.failure != null -> string(
            DeviceRootPresentationMapper.otaFailureMessageRes(state.failure.reason)
        )
        state.mode == DeviceFirmwareUpdateMode.IN_PROGRESS && state.contentLength > 0L ->
            string(
                R.string.device_settings_update_progress_bytes,
                Formatter.formatShortFileSize(
                    fragment.requireContext(),
                    state.bytesWritten
                ),
                Formatter.formatShortFileSize(
                    fragment.requireContext(),
                    state.contentLength
                )
            )
        state.mode.isActive -> string(R.string.device_settings_update_progress_waiting)
        else -> ""
    }

    fun release() {
        renderedReleaseContent = null
        requirementsRendered = false
    }

    private fun renderProgress(state: DeviceFirmwareUpdateUiState) {
        val showCard = state.mode != DeviceFirmwareUpdateMode.AVAILABLE
        binding.updateProgressCard.isVisible = showCard
        if (!showCard) {
            motion.stopPulseAnimation()
            return
        }

        val icon = DeviceFirmwareUpdateProgressPresentationMapper.stateIcon(state.mode)
        binding.ivUpdateStateIcon.apply {
            isVisible = icon != null
            icon?.let { presentation ->
                setImageResource(presentation.drawableRes)
                setIconTint(this, presentation.colorRes)
            }
        }
        binding.progressTextGroup.isVisible = icon == null

        renderProgressIndicator(state, icon)
        binding.tvUpdatePhase.setText(
            DeviceFirmwareUpdateProgressPresentationMapper.phaseTextRes(state)
        )
        binding.tvUpdateProgressDetail.apply {
            val detail = progressDetail(state)
            isVisible = detail.isNotBlank()
            text = detail
        }

        if (state.mode == DeviceFirmwareUpdateMode.SUCCEEDED) {
            motion.animateSuccessOnce(state)
        }
    }

    private fun renderProgressIndicator(
        state: DeviceFirmwareUpdateUiState,
        icon: DeviceFirmwareUpdateIconPresentation?
    ) {
        val showIndicator = state.mode !in TERMINAL_ERROR_MODES
        val phaseTextRes = DeviceFirmwareUpdateProgressPresentationMapper.phaseTextRes(state)
        binding.progressFirmwareUpdate.isVisible = showIndicator
        binding.tvUpdateProgressPercent.apply {
            isVisible = icon == null && state.mode !in INDETERMINATE_MODES
            text = string(
                R.string.device_settings_update_progress_percent,
                state.progressPercent
            )
        }
        binding.tvUpdateProgressCaption.apply {
            isVisible = icon == null
            setText(DeviceFirmwareUpdateHeroPresentationMapper.statusTextRes(state))
        }

        if (showIndicator) {
            val indeterminate = state.mode in INDETERMINATE_MODES
            binding.progressFirmwareUpdate.isIndeterminate = indeterminate
            if (!indeterminate) {
                binding.progressFirmwareUpdate.setProgressCompat(state.progressPercent, true)
            }
            binding.progressFirmwareUpdate.contentDescription = string(phaseTextRes)
        }
        motion.updatePulse(state.mode)
    }

    private fun renderReleaseNotes(state: DeviceFirmwareUpdateUiState) {
        val eligible = state.mode in RELEASE_CONTENT_MODES
        val content = state.releaseContent
        val showReleaseContent = eligible && content.isPresent
        val showUnavailableNotice = eligible && !content.isPresent &&
            state.targetVersion.isNotBlank()

        binding.releaseNotesCard.isVisible = showReleaseContent
        binding.releaseNotesUnavailableCard.isVisible = showUnavailableNotice
        if (!showReleaseContent) {
            renderedReleaseContent = null
            return
        }
        if (renderedReleaseContent == content) return
        renderedReleaseContent = content

        binding.tvReleaseTitle.apply {
            isVisible = content.title.isNotBlank()
            text = content.title
        }
        binding.tvReleaseSummary.apply {
            isVisible = content.summary.isNotBlank()
            text = content.summary
        }
        renderReleaseItems(
            container = binding.releaseChangesContainer,
            items = content.changes,
            iconRes = R.drawable.ic_check_24,
            iconColorRes = R.color.aqua_accent_positive,
            descriptionRes = R.string.device_settings_update_release_change_description
        )
        binding.tvReleaseChangesHeading.isVisible = content.changes.isNotEmpty()

        renderReleaseItems(
            container = binding.releaseWarningsContainer,
            items = content.warnings,
            iconRes = R.drawable.ic_warning,
            iconColorRes = R.color.aqua_content_warning,
            descriptionRes = R.string.device_settings_update_release_warning_description
        )
        binding.tvReleaseWarningsHeading.isVisible = content.warnings.isNotEmpty()
    }

    private fun renderRequirements(state: DeviceFirmwareUpdateUiState) {
        val shouldShow = state.mode == DeviceFirmwareUpdateMode.AVAILABLE || state.mode.isActive
        binding.updateRequirementsCard.isVisible = shouldShow
        if (!shouldShow || requirementsRendered) return

        renderReleaseItems(
            container = binding.updateRequirementsContainer,
            items = listOf(
                string(R.string.device_settings_update_power_warning),
                string(R.string.device_settings_update_requirement_network),
                string(R.string.device_settings_update_requirement_restart),
                string(R.string.device_settings_update_requirement_signed_package)
            ),
            iconRes = R.drawable.ic_check_24,
            iconColorRes = R.color.aqua_accent_positive,
            descriptionRes = R.string.device_settings_update_requirement_description
        )
        requirementsRendered = true
    }

    private fun renderReleaseItems(
        container: android.widget.LinearLayout,
        items: List<String>,
        @DrawableRes iconRes: Int,
        @ColorRes iconColorRes: Int,
        @StringRes descriptionRes: Int
    ) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(container.context)
        items.forEach { item ->
            val itemBinding = LayoutDeviceFirmwareReleaseItemBinding.inflate(
                inflater,
                container,
                false
            )
            itemBinding.ivReleaseItemIcon.setImageResource(iconRes)
            setIconTint(itemBinding.ivReleaseItemIcon, iconColorRes)
            itemBinding.tvReleaseItemText.apply {
                text = item
                contentDescription = string(descriptionRes, item)
            }
            container.addView(itemBinding.root)
        }
    }

    private fun setIconTint(view: AppCompatImageView, @ColorRes colorRes: Int) {
        ImageViewCompat.setImageTintList(
            view,
            ColorStateList.valueOf(
                ContextCompat.getColor(fragment.requireContext(), colorRes)
            )
        )
    }

    private fun string(@StringRes stringRes: Int, vararg args: Any): String =
        fragment.getString(stringRes, *args)

    private companion object {
        val TERMINAL_ERROR_MODES = setOf(
            DeviceFirmwareUpdateMode.FAILED,
            DeviceFirmwareUpdateMode.UNSUPPORTED
        )
        val INDETERMINATE_MODES = setOf(
            DeviceFirmwareUpdateMode.LOADING,
            DeviceFirmwareUpdateMode.CHECKING,
            DeviceFirmwareUpdateMode.STARTING
        )
        val RELEASE_CONTENT_MODES = setOf(
            DeviceFirmwareUpdateMode.AVAILABLE,
            DeviceFirmwareUpdateMode.STARTING,
            DeviceFirmwareUpdateMode.IN_PROGRESS,
            DeviceFirmwareUpdateMode.RECOVERING,
            DeviceFirmwareUpdateMode.RESTARTING,
            DeviceFirmwareUpdateMode.SUCCEEDED,
            DeviceFirmwareUpdateMode.UP_TO_DATE,
            DeviceFirmwareUpdateMode.FAILED
        )
    }
}
