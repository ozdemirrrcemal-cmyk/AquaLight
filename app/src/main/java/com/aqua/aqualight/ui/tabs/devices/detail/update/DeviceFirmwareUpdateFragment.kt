package com.aqua.aqualight.ui.tabs.devices.detail.update

import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceFirmwareReleaseContent
import com.aqua.aqualight.application.devices.DeviceOtaProgressPhase
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentDeviceFirmwareUpdateBinding
import com.aqua.aqualight.databinding.LayoutDeviceFirmwareReleaseItemBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootPresentationMapper
import kotlinx.coroutines.launch

/** State-driven OTA route with distinct detail, progress, result and recovery screens. */
@Suppress("TooManyFunctions")
class DeviceFirmwareUpdateFragment : Fragment(R.layout.fragment_device_firmware_update) {

    private val args: DeviceFirmwareUpdateFragmentArgs by navArgs()
    private val viewModel: DeviceFirmwareUpdateViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }

    private var _binding: FragmentDeviceFirmwareUpdateBinding? = null
    private val binding get() = _binding!!
    private var latestState = DeviceFirmwareUpdateUiState()
    private var renderedReleaseContent: DeviceFirmwareReleaseContent? = null
    private var lastTransitionMode: DeviceFirmwareUpdateMode? = null
    private var lastAnnouncementKey: String? = null
    private var animatedSuccessKey: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        require(args.deviceUid.isNotBlank()) {
            "Software update requires a non-blank device UID."
        }
        _binding = FragmentDeviceFirmwareUpdateBinding.bind(view)
        setupHeader()
        setupActions()
        observeUpdate()
        viewModel.bind(args.deviceUid)
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) viewModel.refreshActiveStatus()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.device_settings_firmware_update_title),
                onBackClick = { findNavController().navigateUp() }
            )
        )
    }

    private fun setupActions() {
        binding.btnUpdateAction.setOnClickListener {
            when (latestState.mode) {
                DeviceFirmwareUpdateMode.AVAILABLE -> viewModel.installUpdate()
                DeviceFirmwareUpdateMode.FAILED -> handleFailureAction()
                DeviceFirmwareUpdateMode.SUCCEEDED,
                DeviceFirmwareUpdateMode.UP_TO_DATE,
                DeviceFirmwareUpdateMode.UNSUPPORTED -> findNavController().navigateUp()
                else -> Unit
            }
        }
    }

    private fun handleFailureAction() {
        if (latestState.failure?.recoverable == true) {
            viewModel.retry()
        } else {
            findNavController().navigateUp()
        }
    }

    private fun observeUpdate() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::renderState)
            }
        }
    }

    private fun renderState(state: DeviceFirmwareUpdateUiState) {
        if (_binding == null) return
        beginStateTransition(state)
        latestState = state
        lastTransitionMode = state.mode

        renderScreenComposition(state)
        renderUpdateDetails(state)
        renderProgress(state)
        renderTimeline(state)
        renderCompletion(state)
        renderFailure(state)
        renderReleaseNotes(state)
        renderAction(state)
        announceStateChange(state)
    }

    private fun beginStateTransition(state: DeviceFirmwareUpdateUiState) {
        if (lastTransitionMode == state.mode || !ValueAnimator.areAnimatorsEnabled()) return
        TransitionManager.beginDelayedTransition(
            binding.firmwareUpdateContent,
            AutoTransition().setDuration(STATE_TRANSITION_DURATION_MILLIS)
        )
    }

    private fun renderScreenComposition(state: DeviceFirmwareUpdateUiState) {
        binding.updateDetailsContainer.isVisible = state.mode == DeviceFirmwareUpdateMode.AVAILABLE
        binding.updateProgressCard.isVisible = state.mode in ACTIVE_SCREEN_MODES
        binding.updateTimelineCard.isVisible = state.mode.isActive
        binding.updateCompletionCard.isVisible = state.mode == DeviceFirmwareUpdateMode.SUCCEEDED ||
            state.mode == DeviceFirmwareUpdateMode.UP_TO_DATE
        binding.updateFailureCard.isVisible = state.mode == DeviceFirmwareUpdateMode.FAILED ||
            state.mode == DeviceFirmwareUpdateMode.UNSUPPORTED
        binding.releaseNotesCard.isVisible = state.mode == DeviceFirmwareUpdateMode.AVAILABLE &&
            (state.releaseContent.isPresent || state.targetVersion.isNotBlank())
        binding.updateRequirementsCard.isVisible = state.mode == DeviceFirmwareUpdateMode.AVAILABLE
    }

    private fun renderUpdateDetails(state: DeviceFirmwareUpdateUiState) {
        val unavailable = getString(R.string.common_not_available_em_dash)
        binding.tvUpdateDeviceName.text = state.deviceName.ifBlank {
            getString(R.string.device_settings_update_device_fallback)
        }
        binding.tvInstalledVersion.text = state.currentVersion.ifBlank { unavailable }
        binding.tvTargetVersion.text = state.targetVersion.ifBlank { unavailable }
        binding.tvUpdateStatusBadge.apply {
            setText(state.statusTextRes())
            setTextColor(ContextCompat.getColor(requireContext(), state.statusColorRes()))
        }
    }

    private fun renderProgress(state: DeviceFirmwareUpdateUiState) {
        if (!binding.updateProgressCard.isVisible) return
        val indeterminate = state.mode == DeviceFirmwareUpdateMode.LOADING ||
            state.mode == DeviceFirmwareUpdateMode.CHECKING ||
            state.mode == DeviceFirmwareUpdateMode.STARTING
        binding.progressFirmwareUpdate.isIndeterminate = indeterminate
        if (!indeterminate) {
            binding.progressFirmwareUpdate.setProgressCompat(state.progressPercent, true)
        }
        binding.tvUpdateProgressPercent.apply {
            isVisible = !indeterminate
            text = getString(
                R.string.device_settings_update_progress_percent,
                state.progressPercent
            )
        }
        binding.tvUpdateProgressCaption.setText(state.statusTextRes())
        binding.tvUpdatePhase.setText(state.phaseTextRes())
        binding.tvUpdateProgressDetail.apply {
            val detail = state.progressDetail()
            isVisible = !detail.isNullOrBlank()
            text = detail
        }
        binding.progressFirmwareUpdate.contentDescription = getString(state.phaseTextRes())
    }

    private fun renderTimeline(state: DeviceFirmwareUpdateUiState) {
        if (!binding.updateTimelineCard.isVisible) return
        val activeStep = state.activeTimelineStep()
        renderTimelineStep(
            binding.tvStepPrepareIcon,
            binding.tvStepPrepareTitle,
            TimelineStep.PREPARE,
            activeStep
        )
        renderTimelineStep(
            binding.tvStepDownloadIcon,
            binding.tvStepDownloadTitle,
            TimelineStep.DOWNLOAD,
            activeStep
        )
        renderTimelineStep(
            binding.tvStepInstallIcon,
            binding.tvStepInstallTitle,
            TimelineStep.INSTALL,
            activeStep
        )
        renderTimelineStep(
            binding.tvStepVerifyIcon,
            binding.tvStepVerifyTitle,
            TimelineStep.VERIFY,
            activeStep
        )
        renderTimelineStep(
            binding.tvStepRestartIcon,
            binding.tvStepRestartTitle,
            TimelineStep.RESTART,
            activeStep
        )
    }

    private fun renderTimelineStep(
        iconView: TextView,
        titleView: TextView,
        step: TimelineStep,
        activeStep: TimelineStep
    ) {
        val status = timelineStatus(step, activeStep)
        val color = ContextCompat.getColor(requireContext(), status.colorRes())
        iconView.apply {
            text = null
            setCompoundDrawablesRelativeWithIntrinsicBounds(status.iconRes(), 0, 0, 0)
            TextViewCompat.setCompoundDrawableTintList(this, ColorStateList.valueOf(color))
        }
        titleView.setTextColor(color)
        titleView.alpha = if (status == TimelineStatus.PENDING) PENDING_STEP_ALPHA else 1f
    }

    private fun timelineStatus(
        step: TimelineStep,
        activeStep: TimelineStep
    ): TimelineStatus = when {
        step.ordinal < activeStep.ordinal -> TimelineStatus.COMPLETE
        step == activeStep -> TimelineStatus.ACTIVE
        else -> TimelineStatus.PENDING
    }

    @DrawableRes
    private fun TimelineStatus.iconRes(): Int = when (this) {
        TimelineStatus.COMPLETE -> R.drawable.ic_check_24
        TimelineStatus.ACTIVE -> R.drawable.ic_update_timeline_active_24
        TimelineStatus.PENDING -> R.drawable.ic_update_timeline_pending_24
    }

    @ColorRes
    private fun TimelineStatus.colorRes(): Int = when (this) {
        TimelineStatus.COMPLETE -> R.color.aqua_accent_positive
        TimelineStatus.ACTIVE -> R.color.aqua_accent_primary
        TimelineStatus.PENDING -> R.color.aqua_content_secondary
    }

    private fun renderCompletion(state: DeviceFirmwareUpdateUiState) {
        if (!binding.updateCompletionCard.isVisible) return
        setIconTint(binding.ivUpdateCompleteIcon, R.color.aqua_status_success)
        binding.tvUpdateCompleteTitle.setText(state.statusTextRes())
        binding.tvUpdateCompleteBody.setText(state.phaseTextRes())
        binding.tvUpdateCompleteVersion.text = state.currentVersion.ifBlank {
            state.targetVersion
        }
        if (state.mode == DeviceFirmwareUpdateMode.SUCCEEDED) animateSuccessOnce(state)
    }

    private fun renderFailure(state: DeviceFirmwareUpdateUiState) {
        if (!binding.updateFailureCard.isVisible) return
        val unsupported = state.mode == DeviceFirmwareUpdateMode.UNSUPPORTED
        binding.ivUpdateFailureIcon.setImageResource(
            if (unsupported) R.drawable.ic_warning else R.drawable.ic_error
        )
        setIconTint(
            binding.ivUpdateFailureIcon,
            if (unsupported) R.color.aqua_content_warning else R.color.aqua_status_danger
        )
        binding.tvUpdateFailureTitle.setText(state.statusTextRes())
        binding.tvUpdateFailureBody.text = state.failure?.let {
            getString(DeviceRootPresentationMapper.otaFailureMessageRes(it.reason))
        } ?: getString(state.phaseTextRes())
    }

    private fun renderReleaseNotes(state: DeviceFirmwareUpdateUiState) {
        if (!binding.releaseNotesCard.isVisible) return
        if (renderedReleaseContent == state.releaseContent) return
        renderedReleaseContent = state.releaseContent
        val content = state.releaseContent
        binding.tvReleaseTitle.apply {
            isVisible = content.title.isNotBlank()
            text = content.title
        }
        binding.tvReleaseSummary.text = content.summary.ifBlank {
            getString(R.string.device_settings_update_release_notes_fallback)
        }
        renderReleaseItems(
            binding.releaseChangesContainer,
            content.changes,
            R.drawable.ic_check_24,
            R.color.aqua_accent_positive,
            R.string.device_settings_update_release_change_description
        )
        binding.tvReleaseChangesHeading.isVisible = content.changes.isNotEmpty()
        renderWarnings(content)
    }

    private fun renderWarnings(content: DeviceFirmwareReleaseContent) {
        val warnings = buildList {
            add(getString(R.string.device_settings_update_power_warning))
            addAll(content.warnings)
        }.distinct()
        renderReleaseItems(
            binding.releaseWarningsContainer,
            warnings,
            R.drawable.ic_warning,
            R.color.aqua_content_warning,
            R.string.device_settings_update_release_warning_description
        )
        binding.tvReleaseWarningsHeading.isVisible = warnings.isNotEmpty()
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
                contentDescription = getString(descriptionRes, item)
            }
            container.addView(itemBinding.root)
        }
    }

    private fun renderAction(state: DeviceFirmwareUpdateUiState) {
        val presentation = state.actionPresentation()
        binding.progressUpdateAction.isVisible = presentation.loading
        binding.btnUpdateAction.apply {
            isEnabled = presentation.enabled
            text = if (presentation.loading) null else getString(presentation.textRes)
            contentDescription = getString(presentation.textRes)
        }
        binding.tvUpdateActionHint.isVisible =
            state.mode == DeviceFirmwareUpdateMode.AVAILABLE || state.mode.isActive
    }

    private fun announceStateChange(state: DeviceFirmwareUpdateUiState) {
        val key = "${state.mode}:${state.phase?.name.orEmpty()}:" +
            state.failure?.reason?.name.orEmpty()
        if (lastAnnouncementKey != null && key != lastAnnouncementKey) {
            val announcement = state.progressDetail().takeUnless { it.isNullOrBlank() }
                ?: getString(state.phaseTextRes())
            binding.firmwareUpdateContent.announceForAccessibility(announcement)
        }
        lastAnnouncementKey = key
    }

    private fun animateSuccessOnce(state: DeviceFirmwareUpdateUiState) {
        val key = "${state.deviceUid}:${state.targetVersion}"
        if (animatedSuccessKey == key || !ValueAnimator.areAnimatorsEnabled()) return
        animatedSuccessKey = key
        binding.ivUpdateCompleteIcon.apply {
            scaleX = SUCCESS_ICON_START_SCALE
            scaleY = SUCCESS_ICON_START_SCALE
            alpha = 0f
            animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(SUCCESS_ANIMATION_DURATION_MILLIS)
                .start()
        }
    }

    private fun setIconTint(view: AppCompatImageView, @ColorRes colorRes: Int) {
        ImageViewCompat.setImageTintList(
            view,
            ColorStateList.valueOf(ContextCompat.getColor(requireContext(), colorRes))
        )
    }

    private fun DeviceFirmwareUpdateUiState.activeTimelineStep(): TimelineStep = when {
        mode == DeviceFirmwareUpdateMode.RECOVERING ||
            mode == DeviceFirmwareUpdateMode.RESTARTING -> TimelineStep.RESTART
        phase == DeviceOtaProgressPhase.VERIFYING -> TimelineStep.VERIFY
        phase == DeviceOtaProgressPhase.WRITING -> TimelineStep.INSTALL
        phase == DeviceOtaProgressPhase.DOWNLOADING -> TimelineStep.DOWNLOAD
        else -> TimelineStep.PREPARE
    }

    private fun DeviceFirmwareUpdateUiState.progressDetail(): CharSequence? = when {
        failure != null -> getString(
            DeviceRootPresentationMapper.otaFailureMessageRes(failure.reason)
        )
        mode == DeviceFirmwareUpdateMode.IN_PROGRESS && contentLength > 0L -> getString(
            R.string.device_settings_update_progress_bytes,
            Formatter.formatShortFileSize(requireContext(), bytesWritten),
            Formatter.formatShortFileSize(requireContext(), contentLength)
        )
        mode.isActive -> getString(R.string.device_settings_update_progress_waiting)
        else -> null
    }

    @StringRes
    private fun DeviceFirmwareUpdateUiState.statusTextRes(): Int = when (mode) {
        DeviceFirmwareUpdateMode.LOADING,
        DeviceFirmwareUpdateMode.CHECKING -> R.string.device_settings_update_status_checking
        DeviceFirmwareUpdateMode.AVAILABLE -> availableStatusTextRes()
        DeviceFirmwareUpdateMode.STARTING -> R.string.device_settings_update_status_preparing
        DeviceFirmwareUpdateMode.IN_PROGRESS -> R.string.device_settings_update_status_installing
        DeviceFirmwareUpdateMode.RECOVERING -> R.string.device_settings_update_status_recovering
        DeviceFirmwareUpdateMode.RESTARTING -> R.string.device_settings_update_status_restarting
        DeviceFirmwareUpdateMode.SUCCEEDED -> R.string.device_settings_update_status_succeeded
        DeviceFirmwareUpdateMode.UP_TO_DATE -> R.string.device_settings_update_status_up_to_date
        DeviceFirmwareUpdateMode.FAILED -> R.string.device_settings_update_status_failed
        DeviceFirmwareUpdateMode.UNSUPPORTED -> R.string.device_settings_update_status_unsupported
    }

    @StringRes
    private fun DeviceFirmwareUpdateUiState.availableStatusTextRes(): Int =
        if (releaseContent.mandatory) {
            R.string.device_settings_update_status_required
        } else {
            R.string.device_settings_update_status_available
        }

    @StringRes
    private fun DeviceFirmwareUpdateUiState.phaseTextRes(): Int = when (mode) {
        DeviceFirmwareUpdateMode.LOADING,
        DeviceFirmwareUpdateMode.CHECKING -> R.string.device_settings_update_phase_checking
        DeviceFirmwareUpdateMode.AVAILABLE -> R.string.device_settings_update_phase_ready
        DeviceFirmwareUpdateMode.STARTING -> R.string.device_settings_update_phase_starting
        DeviceFirmwareUpdateMode.IN_PROGRESS -> progressPhaseTextRes()
        DeviceFirmwareUpdateMode.RECOVERING -> R.string.device_settings_update_phase_recovering
        DeviceFirmwareUpdateMode.RESTARTING -> R.string.device_settings_update_phase_restarting
        DeviceFirmwareUpdateMode.SUCCEEDED -> R.string.device_settings_update_phase_succeeded
        DeviceFirmwareUpdateMode.UP_TO_DATE -> R.string.device_settings_update_phase_up_to_date
        DeviceFirmwareUpdateMode.FAILED -> failurePhaseTextRes()
        DeviceFirmwareUpdateMode.UNSUPPORTED -> R.string.device_settings_update_phase_unsupported
    }

    @StringRes
    private fun DeviceFirmwareUpdateUiState.progressPhaseTextRes(): Int = when (phase) {
        DeviceOtaProgressPhase.STARTING -> R.string.device_settings_update_phase_starting
        DeviceOtaProgressPhase.SAFE_MODE -> R.string.device_settings_update_phase_safe_mode
        DeviceOtaProgressPhase.DOWNLOADING -> R.string.device_settings_update_phase_downloading
        DeviceOtaProgressPhase.WRITING -> R.string.device_settings_update_phase_writing
        DeviceOtaProgressPhase.VERIFYING -> R.string.device_settings_update_phase_verifying
        null -> R.string.device_settings_update_phase_starting
    }

    @StringRes
    private fun DeviceFirmwareUpdateUiState.failurePhaseTextRes(): Int =
        if (failure?.recoverable == true) {
            R.string.device_settings_update_phase_failed_recoverable
        } else {
            R.string.device_settings_update_phase_failed_terminal
        }

    @ColorRes
    private fun DeviceFirmwareUpdateUiState.statusColorRes(): Int = when (mode) {
        DeviceFirmwareUpdateMode.FAILED -> R.color.aqua_status_danger
        DeviceFirmwareUpdateMode.UNSUPPORTED -> R.color.aqua_content_warning
        else -> R.color.aqua_accent_positive
    }

    private fun DeviceFirmwareUpdateUiState.actionPresentation(): ActionPresentation =
        when (mode) {
            DeviceFirmwareUpdateMode.LOADING,
            DeviceFirmwareUpdateMode.CHECKING -> ActionPresentation(
                R.string.device_settings_update_action_loading,
                enabled = false,
                loading = true
            )
            DeviceFirmwareUpdateMode.AVAILABLE -> ActionPresentation(
                R.string.device_settings_install_update_action,
                enabled = true
            )
            DeviceFirmwareUpdateMode.STARTING,
            DeviceFirmwareUpdateMode.IN_PROGRESS,
            DeviceFirmwareUpdateMode.RECOVERING,
            DeviceFirmwareUpdateMode.RESTARTING -> ActionPresentation(
                R.string.device_settings_update_active_action,
                enabled = false
            )
            DeviceFirmwareUpdateMode.SUCCEEDED,
            DeviceFirmwareUpdateMode.UP_TO_DATE -> ActionPresentation(
                R.string.device_settings_update_done_action,
                enabled = true
            )
            DeviceFirmwareUpdateMode.FAILED -> ActionPresentation(
                if (failure?.recoverable == true) {
                    R.string.device_settings_retry_update_action
                } else {
                    R.string.device_settings_update_close_action
                },
                enabled = true
            )
            DeviceFirmwareUpdateMode.UNSUPPORTED -> ActionPresentation(
                R.string.device_settings_update_close_action,
                enabled = true
            )
        }

    override fun onDestroyView() {
        binding.ivUpdateCompleteIcon.animate().cancel()
        _binding = null
        super.onDestroyView()
    }

    private data class ActionPresentation(
        @StringRes val textRes: Int,
        val enabled: Boolean,
        val loading: Boolean = false
    )

    private enum class TimelineStep { PREPARE, DOWNLOAD, INSTALL, VERIFY, RESTART }
    private enum class TimelineStatus { COMPLETE, ACTIVE, PENDING }

    private companion object {
        const val STATE_TRANSITION_DURATION_MILLIS = 180L
        const val SUCCESS_ANIMATION_DURATION_MILLIS = 420L
        const val SUCCESS_ICON_START_SCALE = 0.72f
        const val PENDING_STEP_ALPHA = 0.72f
        val ACTIVE_SCREEN_MODES = setOf(
            DeviceFirmwareUpdateMode.LOADING,
            DeviceFirmwareUpdateMode.CHECKING,
            DeviceFirmwareUpdateMode.STARTING,
            DeviceFirmwareUpdateMode.IN_PROGRESS,
            DeviceFirmwareUpdateMode.RECOVERING,
            DeviceFirmwareUpdateMode.RESTARTING
        )
    }
}
