package com.aqua.aqualight.ui.common.devicevisual.dosing

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.children
import androidx.core.view.isVisible

/** Bridges the shared Compose Dosing visuals into legacy View identity surfaces. */
object DosingDeviceVisualViewBinder {

    fun bindIdentity(
        container: ViewGroup,
        fallbackView: View,
        pumpCount: Int = DOSING_PRO_4_PUMP_COUNT,
        contentDescription: String
    ) {
        fallbackView.isVisible = false
        ensureComposeView(
            container = container,
            tag = IDENTITY_VIEW_TAG,
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        ).apply {
            isVisible = true
            this.contentDescription = contentDescription
            setContent {
                DosingDeviceIdentityVisual(
                    pumpCount = normalizedPumpCount(pumpCount),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    fun clearIdentity(
        container: ViewGroup,
        fallbackView: View
    ) {
        composeView(container, IDENTITY_VIEW_TAG)?.isVisible = false
        fallbackView.isVisible = true
    }

    fun bindPumpHead(
        container: ViewGroup,
        fallbackView: View
    ) {
        fallbackView.isVisible = false
        ensureComposeView(
            container = container,
            tag = PUMP_HEAD_VIEW_TAG,
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        ).apply {
            isVisible = true
            contentDescription = null
            setContent {
                DosingPumpHeadMarker(modifier = Modifier.fillMaxSize())
            }
        }
    }

    private fun ensureComposeView(
        container: ViewGroup,
        tag: String,
        importantForAccessibility: Int
    ): ComposeView {
        return composeView(container, tag) ?: ComposeView(container.context).apply {
            this.tag = tag
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isClickable = false
            isFocusable = false
            this.importantForAccessibility = importantForAccessibility
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            container.addView(this)
        }
    }

    private fun composeView(
        container: ViewGroup,
        tag: String
    ): ComposeView? = container.children
        .filterIsInstance<ComposeView>()
        .firstOrNull { child -> child.tag == tag }

    private fun normalizedPumpCount(pumpCount: Int): Int =
        if (pumpCount == DOSING_PRO_2_PUMP_COUNT) {
            DOSING_PRO_2_PUMP_COUNT
        } else {
            DOSING_PRO_4_PUMP_COUNT
        }

    private const val DOSING_PRO_2_PUMP_COUNT = 2
    private const val DOSING_PRO_4_PUMP_COUNT = 4
    private const val IDENTITY_VIEW_TAG = "aqua_dosing_identity_visual"
    private const val PUMP_HEAD_VIEW_TAG = "aqua_dosing_pump_head_visual"
}
