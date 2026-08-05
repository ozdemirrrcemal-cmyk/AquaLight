package com.aqua.aqualight.ui.tabs.devices.detail.update

import android.animation.ValueAnimator
import androidx.fragment.app.Fragment
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.aqua.aqualight.databinding.FragmentDeviceFirmwareUpdateBinding
import com.aqua.aqualight.ui.tabs.devices.detail.update.controller.DeviceFirmwareUpdateMotionController
import com.aqua.aqualight.ui.tabs.devices.detail.update.presentation.renderer.DeviceFirmwareUpdateContentRenderer
import com.aqua.aqualight.ui.tabs.devices.detail.update.presentation.renderer.DeviceFirmwareUpdateStatusRenderer

/** Coordinates focused view renderers for the firmware update screen. */
internal class DeviceFirmwareUpdateScreenRenderer(
    fragment: Fragment,
    private val binding: FragmentDeviceFirmwareUpdateBinding
) {
    private val motion = DeviceFirmwareUpdateMotionController(fragment, binding)
    private val status = DeviceFirmwareUpdateStatusRenderer(fragment, binding, motion)
    private val content = DeviceFirmwareUpdateContentRenderer(fragment, binding, motion)
    private var lastTransitionMode: DeviceFirmwareUpdateMode? = null

    fun render(state: DeviceFirmwareUpdateUiState) {
        val modeChanged = lastTransitionMode != state.mode
        if (modeChanged && ValueAnimator.areAnimatorsEnabled()) {
            TransitionManager.beginDelayedTransition(
                binding.firmwareUpdateContent,
                AutoTransition().setDuration(STATE_TRANSITION_DURATION_MILLIS)
            )
        }
        lastTransitionMode = state.mode

        status.renderHero(state, modeChanged)
        content.render(state)
        status.renderAction(state)
        status.announceStateChange(state, content.progressDetail(state))
    }

    fun release() {
        motion.release()
        content.release()
    }

    private companion object {
        const val STATE_TRANSITION_DURATION_MILLIS = 180L
    }
}
