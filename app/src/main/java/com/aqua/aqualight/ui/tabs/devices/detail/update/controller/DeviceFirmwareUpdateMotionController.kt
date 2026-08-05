package com.aqua.aqualight.ui.tabs.devices.detail.update.controller

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceFirmwareUpdateBinding
import com.aqua.aqualight.ui.tabs.devices.detail.update.DeviceFirmwareUpdateMode
import com.aqua.aqualight.ui.tabs.devices.detail.update.DeviceFirmwareUpdateUiState
import com.aqua.aqualight.ui.tabs.devices.detail.update.presentation.mapper.DeviceFirmwareUpdateProgressPresentationMapper

/** Owns view-lifecycle animations and respects the system animator setting. */
internal class DeviceFirmwareUpdateMotionController(
    private val fragment: Fragment,
    private val binding: FragmentDeviceFirmwareUpdateBinding
) {
    private var pulseAnimator: AnimatorSet? = null
    private var animatedSuccessKey = ""

    fun animateHeroEntrance() {
        if (!ValueAnimator.areAnimatorsEnabled()) return
        val translation = fragment.resources.getDimension(
            R.dimen.aqua_firmware_update_hero_entrance_translation
        )
        cancelHeroAnimations()

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

    fun animateSuccessOnce(state: DeviceFirmwareUpdateUiState) {
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

    fun updatePulse(mode: DeviceFirmwareUpdateMode) {
        if (DeviceFirmwareUpdateProgressPresentationMapper.shouldPulse(mode)) {
            startPulseAnimation()
        } else {
            stopPulseAnimation()
        }
    }

    fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        binding.updateProgressHero.apply {
            scaleX = 1f
            scaleY = 1f
        }
    }

    fun release() {
        stopPulseAnimation()
        binding.ivUpdateStateIcon.animate().cancel()
        cancelHeroAnimations()
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

    private fun cancelHeroAnimations() {
        binding.updateHeroIconContainer.animate().cancel()
        binding.tvUpdateHeroTitle.animate().cancel()
        binding.tvUpdateHeroSummary.animate().cancel()
    }

    private companion object {
        const val HERO_ANIMATION_DURATION_MILLIS = 420L
        const val HERO_TITLE_ANIMATION_DELAY_MILLIS = 70L
        const val HERO_SUMMARY_ANIMATION_DELAY_MILLIS = 120L
        const val HERO_START_ALPHA = 0f
        const val HERO_START_SCALE = 0.82f
        const val SUCCESS_ANIMATION_DURATION_MILLIS = 420L
        const val SUCCESS_ICON_START_SCALE = 0.72f
        const val PULSE_DURATION_MILLIS = 900L
        const val PULSE_SCALE = 1.035f
    }
}
