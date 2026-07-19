package com.aqua.aqualight.ui.common.accessibility

import android.graphics.Rect
import android.view.MotionEvent
import android.view.TouchDelegate
import android.view.View
import android.view.ViewGroup
import com.aqua.aqualight.R
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Expands a control's tappable area without changing its measured or visible size.
 *
 * The [host] must be an ancestor of the target and large enough to contain the requested target.
 * Multiple controls can safely share the same host; overlapping expanded areas are resolved by
 * choosing the control whose visual centre is closest to the initial touch.
 */
fun View.ensureMinimumTouchTarget(
    host: ViewGroup,
    minimumSizeDp: Int = DEFAULT_MINIMUM_TOUCH_TARGET_DP
) {
    host.post {
        if (!isAttachedToWindow || visibility != View.VISIBLE || !isEnabled) {
            return@post
        }

        val minimumSizePx = (
            minimumSizeDp * resources.displayMetrics.density
        ).roundToInt()

        val bounds = Rect().also { rect ->
            getDrawingRect(rect)
            host.offsetDescendantRectToMyCoords(this, rect)
            rect.expandToMinimum(
                minimumSizePx = minimumSizePx,
                hostWidth = host.width,
                hostHeight = host.height
            )
        }

        val delegateGroup =
            (host.getTag(R.id.aqua_touch_delegate_group) as? CompositeTouchDelegate)
                ?: CompositeTouchDelegate(host).also { group ->
                    host.setTag(R.id.aqua_touch_delegate_group, group)
                    host.touchDelegate = group
                }

        delegateGroup.register(
            target = this,
            bounds = bounds
        )
        setTag(
            R.id.aqua_effective_touch_target_bounds,
            Rect(bounds)
        )
    }
}

private fun Rect.expandToMinimum(
    minimumSizePx: Int,
    hostWidth: Int,
    hostHeight: Int
) {
    val horizontalGrowth = max(0, minimumSizePx - width())
    val verticalGrowth = max(0, minimumSizePx - height())

    left -= horizontalGrowth / 2
    right += horizontalGrowth - horizontalGrowth / 2
    top -= verticalGrowth / 2
    bottom += verticalGrowth - verticalGrowth / 2

    if (left < 0) {
        right -= left
        left = 0
    }
    if (right > hostWidth) {
        left -= right - hostWidth
        right = hostWidth
    }
    if (top < 0) {
        bottom -= top
        top = 0
    }
    if (bottom > hostHeight) {
        top -= bottom - hostHeight
        bottom = hostHeight
    }

    left = left.coerceAtLeast(0)
    top = top.coerceAtLeast(0)
}

private class CompositeTouchDelegate(
    host: View
) : TouchDelegate(Rect(), host) {

    private data class Entry(
        val target: View,
        val bounds: Rect,
        val delegate: TouchDelegate
    )

    private val entries = linkedMapOf<View, Entry>()
    private var activeEntry: Entry? = null

    fun register(
        target: View,
        bounds: Rect
    ) {
        entries[target] = Entry(
            target = target,
            bounds = Rect(bounds),
            delegate = TouchDelegate(Rect(bounds), target)
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            activeEntry = entries.values
                .asSequence()
                .filter { entry ->
                    entry.target.visibility == View.VISIBLE &&
                        entry.target.isEnabled &&
                        entry.bounds.contains(event.x.toInt(), event.y.toInt())
                }
                .minByOrNull { entry ->
                    val dx = event.x - entry.bounds.exactCenterX()
                    val dy = event.y - entry.bounds.exactCenterY()
                    dx * dx + dy * dy
                }
        }

        val handled = activeEntry?.delegate?.onTouchEvent(event) ?: false

        if (
            event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            activeEntry = null
        }

        return handled
    }

    private companion object {
        const val DEFAULT_MINIMUM_TOUCH_TARGET_DP = 48
    }
}

private const val DEFAULT_MINIMUM_TOUCH_TARGET_DP = 48
