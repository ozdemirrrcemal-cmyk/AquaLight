package com.aqua.aqualight.ui.tabs.devices.detail.update

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
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

/** Central full-screen software update route backed by the owner-scoped OTA coordinator. */
@Suppress("TooManyFunctions")
class DeviceFirmwareUpdateFragment : Fragment(R.layout.fragment_device_firmware_update) {

    private val args: DeviceFirmwareUpdateFragmentArgs by navArgs()
    private val viewModel: DeviceFirmwareUpdateViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }

    private var _binding: FragmentDeviceFirmwareUpdateBinding? = null
    private val binding get() = _binding!!
    private var latestState = DeviceFirmwareUpdateUiState()
    private var pulseAnimator: AnimatorSet? = null
    private var renderedReleaseContent: DeviceFirmwareReleaseContent? = null
    private var requirementsRendered = false
    private var lastTransitionMode: DeviceFirmwareUpdateMode? = null
    private var lastAnnouncementKey = ""
    private var animatedSuccessKey = ""

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
                DeviceFirmwareUpdateMode.FAILED -> {
                    if (latestState.failure?.recoverable == true) viewModel.retry()
                    else findNavController().navigateUp()
                }
                DeviceFirmwareUpdateMode.SUCCEEDED,
                DeviceFirmwareUpdateMode.UP_TO_DATE,
                DeviceFirmwareUpdateMode.UNSUPPORTED -> findNavController().navigateUp()
                else -> Unit
            }
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
        val modeChanged = lastTransitionMode != state.mode
        if (modeChanged && ValueAnimator.areAnimatorsEnabled()) {
            TransitionManager.beginDelayedTransition(
                binding.firmwareUpdateContent,
                AutoTransition().setDuration(STATE_TRANSITION_DURATION_MILLIS)
            )
        }
        latestState = state
        lastTransitionMode = state.mode

        renderHero(state, modeChanged)
        renderProgress(state)
        renderReleaseNotes(state)
        renderRequirements(state)
        renderAction(state)
        announceStateChange(state)
    }

    private fun renderHero(
        state: DeviceFirmwareUpdateUiState,
        modeChanged: Boolean
    ) {
        val unavailable = getString(R.string.common_not_available_em_dash)
        val deviceName = state.deviceName.ifBlank {
            getString(R.string.device_settings_update_device_fallback)
        }
        val icon = state.heroIcon()

        binding.tvUpdateStatusBadge.apply {
            setText(state.statusTextRes())
            setTextColor(ContextCompat.getColor(requireContext(), state.statusColorRes()))
            backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), state.statusBadgeBackgroundColorRes())
            )
        }
        binding.ivUpdateHeroIcon.apply {
            setImageResource(icon.drawableRes)
            setIconTint(this, icon.colorRes)
        }
        binding.tvUpdateHeroTitle.setText(state.heroTitleRes())
        binding.tvUpdateHeroSummary.text = state.heroSummary()
        binding.tvUpdateDeviceName.text = getString(
            R.string.device_settings_update_for_device,
            deviceName
        )

        binding.tvInstalledVersion.text = state.currentVersion.ifBlank { unavailable }
        binding.tvTargetVersion.text = state.targetVersion.ifBlank { unavailable }
        binding.targetVersionGroup.isVisible = state.targetVersion.isNotBlank()
        binding.ivVersionArrow.isVisible = state.currentVersion.isNotBlank() &&
            state.targetVersion.isNotBlank()

        if (modeChanged) animateHeroEntrance()
    }

    private fun animateHeroEntrance() {
        if (!ValueAnimator.areAnimatorsEnabled()) return
        val translation = resources.getDimension(
            R.dimen.aqua_firmware_update_hero_entrance_translation
        )
        binding.updateHeroIconContainer.animate().cancel()
        binding.tvUpdateHeroTitle.animate().cancel()
        binding.tvUpdateHeroSummary.animate().cancel()

        binding.updateHeroIconContainer.apply {
            alpha = HERO_START_ALPHA
            scaleX = HERO_START_SCALE
            scaleY = HERO_START_SCALE
            translationY = translation
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(HERO_ANIMATION_DURATION_MILLIS)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
        animateHeroText(
            view = binding.tvUpdateHeroTitle,
            translation = translation,
            delayMillis = HERO_TITLE_ANIMATION_DELAY_MILLIS
        )
        animateHeroText(
            view = binding.tvUpdateHeroSummary,
            translation = translation,
            delayMillis = HERO_SUMMARY_ANIMATION_DELAY_MILLIS
        )
    }

    private fun animateHeroText(
        view: View,
        translation: Float,
        delayMillis: Long
    ) {
        view.alpha = HERO_START_ALPHA
        view.translationY = translation
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(delayMillis)
            .setDuration(HERO_ANIMATION_DURATION_MILLIS)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun renderProgress(state: DeviceFirmwareUpdateUiState) {
        val showCard = state.mode != DeviceFirmwareUpdateMode.AVAILABLE
        binding.updateProgressCard.isVisible = showCard
        if (!showCard) {
            stopPulseAnimation()
            return
        }

        val icon = state.stateIcon()
        binding.ivUpdateStateIcon.apply {
            isVisible = icon != null
            icon?.let { presentation ->
                setImageResource(presentation.drawableRes)
                setIconTint(this, presentation.colorRes)
            }
        }
        binding.progressTextGroup.isVisible = icon == null

        renderProgressIndicator(state, icon)
        binding.tvUpdatePhase.setText(state.phaseTextRes())
        binding.tvUpdateProgressDetail.apply {
            val detail = state.progressDetail()
            isVisible = detail.isNotBlank()
            text = detail
        }

        if (state.mode == DeviceFirmwareUpdateMode.SUCCEEDED) animateSuccessOnce(state)
    }

    private fun renderProgressIndicator(
        state: DeviceFirmwareUpdateUiState,
        icon: StateIconPresentation?
    ) {
        val showIndicator = state.mode !in setOf(
            DeviceFirmwareUpdateMode.FAILED,
            DeviceFirmwareUpdateMode.UNSUPPORTED
        )
        binding.progressFirmwareUpdate.isVisible = showIndicator
        binding.tvUpdateProgressPercent.apply {
            isVisible = icon == null && state.mode !in setOf(
                DeviceFirmwareUpdateMode.LOADING,
                DeviceFirmwareUpdateMode.CHECKING,
                DeviceFirmwareUpdateMode.STARTING
            )
            text = getString(
                R.string.device_settings_update_progress_percent,
                state.progressPercent
            )
        }
        binding.tvUpdateProgressCaption.apply {
            isVisible = icon == null
            setText(state.statusTextRes())
        }

        if (showIndicator) {
            val indeterminate = state.mode == DeviceFirmwareUpdateMode.LOADING ||
                state.mode == DeviceFirmwareUpdateMode.CHECKING ||
                state.mode == DeviceFirmwareUpdateMode.STARTING
            binding.progressFirmwareUpdate.isIndeterminate = indeterminate
            if (!indeterminate) {
                binding.progressFirmwareUpdate.setProgressCompat(state.progressPercent, true)
            }
            binding.progressFirmwareUpdate.contentDescription = getString(
                state.phaseTextRes()
            )
        }

        if (state.mode.shouldPulse) startPulseAnimation() else stopPulseAnimation()
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
                getString(R.string.device_settings_update_power_warning),
                getString(R.string.device_settings_update_requirement_network),
                getString(R.string.device_settings_update_requirement_restart),
                getString(R.string.device_settings_update_requirement_signed_package)
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
            text = if (presentation.loading) "" else getString(presentation.textRes)
            contentDescription = getString(presentation.textRes)
        }
        binding.tvUpdateActionHint.apply {
            val hintRes = when {
                state.mode == DeviceFirmwareUpdateMode.AVAILABLE ->
                    R.string.device_settings_update_action_hint_available
                state.mode.isActive -> R.string.device_settings_update_action_hint_active
                else -> null
            }
            isVisible = hintRes != null
            if (hintRes != null) setText(hintRes)
        }
    }

    private fun announceStateChange(state: DeviceFirmwareUpdateUiState) {
        val key = "${state.mode}:${state.phase.orEmptyName()}:${state.failure?.reason.orEmptyName()}"
        if (lastAnnouncementKey.isNotBlank() && key != lastAnnouncementKey) {
            val detail = state.progressDetail()
            binding.firmwareUpdateContent.announceForAccessibility(
                detail.ifBlank { getString(state.phaseTextRes()) }
            )
        }
        lastAnnouncementKey = key
    }

    private fun animateSuccessOnce(state: DeviceFirmwareUpdateUiState) {
        val key = "${state.deviceUid}:${state.targetVersion}"
        if (animatedSuccessKey == key || !ValueAnimator.areAnimatorsEnabled()) return
        animatedSuccessKey = key
        binding.ivUpdateStateIcon.apply {
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

    private fun startPulseAnimation() {
        if (pulseAnimator?.isRunning == true || !ValueAnimator.areAnimatorsEnabled()) return
        val scaleX = ObjectAnimator.ofFloat(
            binding.updateProgressHero,
            View.SCALE_X,
            1f,
            PULSE_SCALE
        ).asPulseAnimator()
        val scaleY = ObjectAnimator.ofFloat(
            binding.updateProgressHero,
            View.SCALE_Y,
            1f,
            PULSE_SCALE
        ).asPulseAnimator()
        pulseAnimator = AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            start()
        }
    }

    private fun ObjectAnimator.asPulseAnimator(): ObjectAnimator = apply {
        duration = PULSE_DURATION_MILLIS
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
    }

    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        _binding?.updateProgressHero?.apply {
            scaleX = 1f
            scaleY = 1f
        }
    }

    private fun setIconTint(view: AppCompatImageView, @ColorRes colorRes: Int) {
        ImageViewCompat.setImageTintList(
            view,
            ColorStateList.valueOf(ContextCompat.getColor(requireContext(), colorRes))
        )
    }

    private fun DeviceFirmwareUpdateUiState.heroSummary(): CharSequence = when (mode) {
        DeviceFirmwareUpdateMode.LOADING,
        DeviceFirmwareUpdateMode.CHECKING -> getString(
            R.string.device_settings_update_hero_summary_checking
        )
        DeviceFirmwareUpdateMode.AVAILABLE -> when {
            releaseContent.mandatory && targetVersion.isNotBlank() -> getString(
                R.string.device_settings_update_hero_summary_required_version,
                targetVersion
            )
            targetVersion.isNotBlank() -> getString(
                R.string.device_settings_update_hero_summary_available_version,
                targetVersion
            )
            else -> getString(R.string.device_settings_update_hero_summary_available)
        }
        DeviceFirmwareUpdateMode.STARTING -> getString(
            R.string.device_settings_update_hero_summary_starting
        )
        DeviceFirmwareUpdateMode.IN_PROGRESS -> getString(
            R.string.device_settings_update_hero_summary_installing
        )
        DeviceFirmwareUpdateMode.RECOVERING -> getString(
            R.string.device_settings_update_hero_summary_recovering
        )
        DeviceFirmwareUpdateMode.RESTARTING -> getString(
            R.string.device_settings_update_hero_summary_restarting
        )
        DeviceFirmwareUpdateMode.SUCCEEDED -> if (targetVersion.isBlank()) {
            getString(R.string.device_settings_update_hero_summary_succeeded)
        } else {
            getString(
                R.string.device_settings_update_hero_summary_succeeded_version,
                targetVersion
            )
        }
        DeviceFirmwareUpdateMode.UP_TO_DATE -> if (currentVersion.isBlank()) {
            getString(R.string.device_settings_update_hero_summary_up_to_date)
        } else {
            getString(
                R.string.device_settings_update_hero_summary_up_to_date_version,
                currentVersion
            )
        }
        DeviceFirmwareUpdateMode.FAILED -> failure?.let { failure ->
            getString(DeviceRootPresentationMapper.otaFailureMessageRes(failure.reason))
        }.orEmpty().ifBlank {
            getString(R.string.device_settings_update_phase_failed_terminal)
        }
        DeviceFirmwareUpdateMode.UNSUPPORTED -> getString(
            R.string.device_settings_update_hero_summary_unsupported
        )
    }

    private fun DeviceFirmwareUpdateUiState.progressDetail(): CharSequence = when {
        failure != null -> getString(
            DeviceRootPresentationMapper.otaFailureMessageRes(failure.reason)
        )
        mode == DeviceFirmwareUpdateMode.IN_PROGRESS && contentLength > 0L -> getString(
            R.string.device_settings_update_progress_bytes,
            Formatter.formatShortFileSize(requireContext(), bytesWritten),
            Formatter.formatShortFileSize(requireContext(), contentLength)
        )
        mode.isActive -> getString(R.string.device_settings_update_progress_waiting)
        else -> ""
    }

    @StringRes
    private fun DeviceFirmwareUpdateUiState.heroTitleRes(): Int = when (mode) {
        DeviceFirmwareUpdateMode.LOADING,
        DeviceFirmwareUpdateMode.CHECKING -> R.string.device_settings_update_hero_title_checking
        DeviceFirmwareUpdateMode.AVAILABLE -> if (releaseContent.mandatory) {
            R.string.device_settings_update_hero_title_required
        } else {
            R.string.device_settings_update_hero_title_available
        }
        DeviceFirmwareUpdateMode.STARTING -> R.string.device_settings_update_hero_title_starting
        DeviceFirmwareUpdateMode.IN_PROGRESS -> R.string.device_settings_update_hero_title_installing
        DeviceFirmwareUpdateMode.RECOVERING -> R.string.device_settings_update_hero_title_recovering
        DeviceFirmwareUpdateMode.RESTARTING -> R.string.device_settings_update_hero_title_restarting
        DeviceFirmwareUpdateMode.SUCCEEDED -> R.string.device_settings_update_hero_title_succeeded
        DeviceFirmwareUpdateMode.UP_TO_DATE -> R.string.device_settings_update_hero_title_up_to_date
        DeviceFirmwareUpdateMode.FAILED -> R.string.device_settings_update_hero_title_failed
        DeviceFirmwareUpdateMode.UNSUPPORTED -> R.string.device_settings_update_hero_title_unsupported
    }

    @StringRes
    private fun DeviceFirmwareUpdateUiState.statusTextRes(): Int = when (mode) {
        DeviceFirmwareUpdateMode.LOADING,
        DeviceFirmwareUpdateMode.CHECKING -> R.string.device_settings_update_status_checking
        DeviceFirmwareUpdateMode.AVAILABLE -> if (releaseContent.mandatory) {
            R.string.device_settings_update_status_required
        } else {
            R.string.device_settings_update_status_available
        }
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
    private fun DeviceFirmwareUpdateUiState.phaseTextRes(): Int = when (mode) {
        DeviceFirmwareUpdateMode.LOADING,
        DeviceFirmwareUpdateMode.CHECKING -> R.string.device_settings_update_phase_checking
        DeviceFirmwareUpdateMode.AVAILABLE -> R.string.device_settings_update_phase_ready
        DeviceFirmwareUpdateMode.STARTING -> R.string.device_settings_update_phase_starting
        DeviceFirmwareUpdateMode.IN_PROGRESS -> phase.progressPhaseTextRes()
        DeviceFirmwareUpdateMode.RECOVERING -> R.string.device_settings_update_phase_recovering
        DeviceFirmwareUpdateMode.RESTARTING -> R.string.device_settings_update_phase_restarting
        DeviceFirmwareUpdateMode.SUCCEEDED -> R.string.device_settings_update_phase_succeeded
        DeviceFirmwareUpdateMode.UP_TO_DATE -> R.string.device_settings_update_phase_up_to_date
        DeviceFirmwareUpdateMode.FAILED -> if (failure?.recoverable == true) {
            R.string.device_settings_update_phase_failed_recoverable
        } else {
            R.string.device_settings_update_phase_failed_terminal
        }
        DeviceFirmwareUpdateMode.UNSUPPORTED -> R.string.device_settings_update_phase_unsupported
    }

    @StringRes
    private fun DeviceOtaProgressPhase?.progressPhaseTextRes(): Int = when (this) {
        DeviceOtaProgressPhase.STARTING -> R.string.device_settings_update_phase_starting
        DeviceOtaProgressPhase.SAFE_MODE -> R.string.device_settings_update_phase_safe_mode
        DeviceOtaProgressPhase.DOWNLOADING -> R.string.device_settings_update_phase_downloading
        DeviceOtaProgressPhase.WRITING -> R.string.device_settings_update_phase_writing
        DeviceOtaProgressPhase.VERIFYING -> R.string.device_settings_update_phase_verifying
        null -> R.string.device_settings_update_phase_starting
    }

    @ColorRes
    private fun DeviceFirmwareUpdateUiState.statusColorRes(): Int = when (mode) {
        DeviceFirmwareUpdateMode.FAILED -> R.color.aqua_status_danger
        DeviceFirmwareUpdateMode.UNSUPPORTED -> R.color.aqua_content_warning
        DeviceFirmwareUpdateMode.AVAILABLE -> if (releaseContent.mandatory) {
            R.color.aqua_content_warning
        } else {
            R.color.aqua_accent_positive
        }
        DeviceFirmwareUpdateMode.SUCCEEDED,
        DeviceFirmwareUpdateMode.UP_TO_DATE -> R.color.aqua_status_success
        else -> R.color.aqua_accent_primary
    }

    @ColorRes
    private fun DeviceFirmwareUpdateUiState.statusBadgeBackgroundColorRes(): Int = when (mode) {
        DeviceFirmwareUpdateMode.FAILED -> R.color.aqua_aquarium_fragment_button_outline
        DeviceFirmwareUpdateMode.UNSUPPORTED ->
            R.color.aqua_bg_maintenance_profile_percent_warning_fill
        DeviceFirmwareUpdateMode.AVAILABLE -> if (releaseContent.mandatory) {
            R.color.aqua_bg_maintenance_profile_percent_warning_fill
        } else {
            R.color.aqua_surface_positive
        }
        DeviceFirmwareUpdateMode.SUCCEEDED,
        DeviceFirmwareUpdateMode.UP_TO_DATE -> R.color.aqua_surface_positive
        else -> R.color.aqua_surface_action
    }

    private fun DeviceFirmwareUpdateUiState.heroIcon(): StateIconPresentation = when (mode) {
        DeviceFirmwareUpdateMode.SUCCEEDED,
        DeviceFirmwareUpdateMode.UP_TO_DATE -> StateIconPresentation(
            R.drawable.ic_check_24,
            R.color.aqua_status_success
        )
        DeviceFirmwareUpdateMode.FAILED -> StateIconPresentation(
            R.drawable.ic_error,
            R.color.aqua_status_danger
        )
        DeviceFirmwareUpdateMode.UNSUPPORTED -> StateIconPresentation(
            R.drawable.ic_warning,
            R.color.aqua_content_warning
        )
        else -> StateIconPresentation(
            R.drawable.ic_firmware_update,
            R.color.aqua_accent_primary
        )
    }

    private fun DeviceFirmwareUpdateUiState.stateIcon(): StateIconPresentation? = when (mode) {
        DeviceFirmwareUpdateMode.SUCCEEDED,
        DeviceFirmwareUpdateMode.UP_TO_DATE -> StateIconPresentation(
            R.drawable.ic_check_24,
            R.color.aqua_status_success
        )
        DeviceFirmwareUpdateMode.FAILED -> StateIconPresentation(
            R.drawable.ic_error,
            R.color.aqua_status_danger
        )
        DeviceFirmwareUpdateMode.UNSUPPORTED -> StateIconPresentation(
            R.drawable.ic_warning,
            R.color.aqua_content_warning
        )
        else -> null
    }

    private fun DeviceFirmwareUpdateUiState.actionPresentation(): ActionPresentation = when (mode) {
        DeviceFirmwareUpdateMode.LOADING,
        DeviceFirmwareUpdateMode.CHECKING -> ActionPresentation(
            textRes = R.string.device_settings_update_action_loading,
            enabled = false,
            loading = true
        )
        DeviceFirmwareUpdateMode.AVAILABLE -> ActionPresentation(
            R.string.device_settings_update_now_action,
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
            textRes = if (failure?.recoverable == true) {
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

    private fun DeviceOtaProgressPhase?.orEmptyName(): String = this?.name.orEmpty()

    private fun Enum<*>?.orEmptyName(): String = this?.name.orEmpty()

    override fun onDestroyView() {
        stopPulseAnimation()
        binding.ivUpdateStateIcon.animate().cancel()
        binding.updateHeroIconContainer.animate().cancel()
        binding.tvUpdateHeroTitle.animate().cancel()
        binding.tvUpdateHeroSummary.animate().cancel()
        renderedReleaseContent = null
        requirementsRendered = false
        _binding = null
        super.onDestroyView()
    }

    private data class StateIconPresentation(
        @DrawableRes val drawableRes: Int,
        @ColorRes val colorRes: Int
    )

    private data class ActionPresentation(
        @StringRes val textRes: Int,
        val enabled: Boolean,
        val loading: Boolean = false
    )

    private companion object {
        const val STATE_TRANSITION_DURATION_MILLIS = 180L
        const val HERO_ANIMATION_DURATION_MILLIS = 420L
        const val HERO_TITLE_ANIMATION_DELAY_MILLIS = 70L
        const val HERO_SUMMARY_ANIMATION_DELAY_MILLIS = 120L
        const val HERO_START_ALPHA = 0f
        const val HERO_START_SCALE = 0.82f
        const val SUCCESS_ANIMATION_DURATION_MILLIS = 420L
        const val SUCCESS_ICON_START_SCALE = 0.72f
        const val PULSE_DURATION_MILLIS = 900L
        const val PULSE_SCALE = 1.035f
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

private val DeviceFirmwareUpdateMode.shouldPulse: Boolean
    get() = this == DeviceFirmwareUpdateMode.LOADING ||
        this == DeviceFirmwareUpdateMode.CHECKING ||
        this == DeviceFirmwareUpdateMode.STARTING ||
        this == DeviceFirmwareUpdateMode.RECOVERING ||
        this == DeviceFirmwareUpdateMode.RESTARTING
