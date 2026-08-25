package com.aqua.aqualight.ui.common.devicevisual.dosing

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.isVisible

/** Small View-system bridge for legacy XML surfaces that consume the shared Compose visuals. */
internal object DosingDeviceVisualViewBinding {

    fun showIdentity(
        container: ViewGroup,
        fallback: View,
        pumpCount: Int = DEFAULT_DOSING_PUMP_COUNT,
        sizeDp: Int = DEFAULT_IDENTITY_SIZE_DP
    ) {
        fallback.isVisible = false
        val identityView = container.findTaggedView<DosingDeviceIdentityVisualView>(IDENTITY_TAG)
            ?: DosingDeviceIdentityVisualView(container.context).apply {
                tag = IDENTITY_TAG
                layoutParams = centeredLayoutParams(container, sizeDp)
                container.addView(this)
            }
        identityView.pumpCount = pumpCount
        identityView.isVisible = true
    }

    fun clearIdentity(
        container: ViewGroup,
        fallback: View
    ) {
        container.findTaggedView<DosingDeviceIdentityVisualView>(IDENTITY_TAG)?.isVisible = false
        fallback.isVisible = true
    }

    fun showPumpHead(
        container: ViewGroup,
        fallback: View,
        sizeDp: Int = DEFAULT_PUMP_HEAD_SIZE_DP
    ) {
        fallback.isVisible = false
        val headView = container.findTaggedView<DosingPumpHeadVisualView>(PUMP_HEAD_TAG)
            ?: DosingPumpHeadVisualView(container.context).apply {
                tag = PUMP_HEAD_TAG
                layoutParams = centeredLayoutParams(container, sizeDp)
                container.addView(this)
            }
        headView.isVisible = true
    }

    private inline fun <reified T : View> ViewGroup.findTaggedView(tagValue: String): T? {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child is T && child.tag == tagValue) return child
        }
        return null
    }

    private fun centeredLayoutParams(container: ViewGroup, sizeDp: Int): ViewGroup.LayoutParams {
        val density = container.resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt()
        return if (container is FrameLayout) {
            FrameLayout.LayoutParams(sizePx, sizePx, Gravity.CENTER)
        } else {
            ViewGroup.LayoutParams(sizePx, sizePx)
        }
    }

    private const val DEFAULT_DOSING_PUMP_COUNT = 4
    private const val DEFAULT_IDENTITY_SIZE_DP = 40
    private const val DEFAULT_PUMP_HEAD_SIZE_DP = 26
    private const val IDENTITY_TAG = "aqua_dosing_identity_visual"
    private const val PUMP_HEAD_TAG = "aqua_dosing_pump_head_visual"
}
