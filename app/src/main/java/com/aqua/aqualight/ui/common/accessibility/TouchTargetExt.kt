package com.aqua.aqualight.ui.common.accessibility

import android.graphics.Rect
import android.view.MotionEvent
import android.view.TouchDelegate
import android.view.View
import java.util.WeakHashMap
import kotlin.math.ceil

private const val MINIMUM_TOUCH_TARGET_DP = 48

/**
 * Expands a control's actual touch area without changing its measured size or approved visuals.
 * Multiple small controls sharing the same parent are supported by one composite delegate.
 */
fun View.ensureMinimumTouchTarget(minimumSizeDp: Int = MINIMUM_TOUCH_TARGET_DP) {
    require(minimumSizeDp >= MINIMUM_TOUCH_TARGET_DP)
    val parentView = parent as? View ?: return
    if (!TouchTargetRegistration.register(this)) return

    val updateDelegate = {
        if (visibility == View.VISIBLE && width > 0 && height > 0) {
            val minimumPx = ceil(minimumSizeDp * resources.displayMetrics.density).toInt()
            val bounds = Rect().also(::getHitRect)
            val horizontalExpansion = ((minimumPx - bounds.width()).coerceAtLeast(0) + 1) / 2
            val verticalExpansion = ((minimumPx - bounds.height()).coerceAtLeast(0) + 1) / 2
            bounds.inset(-horizontalExpansion, -verticalExpansion)

            val group = (parentView.touchDelegate as? CompositeTouchDelegate)
                ?: CompositeTouchDelegate(parentView).also { parentView.touchDelegate = it }
            group.put(this, bounds)
        }
    }

    addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateDelegate() }
    parentView.post(updateDelegate)
}

private object TouchTargetRegistration {
    private val registeredViews = WeakHashMap<View, Unit>()

    @Synchronized
    fun register(view: View): Boolean {
        if (registeredViews.containsKey(view)) return false
        registeredViews[view] = Unit
        return true
    }
}

private class CompositeTouchDelegate(
    host: View
) : TouchDelegate(Rect(), host) {

    private data class Entry(
        val bounds: Rect,
        val delegate: TouchDelegate
    )

    private val entries = LinkedHashMap<View, Entry>()
    private var activeEntry: Entry? = null

    fun put(view: View, bounds: Rect) {
        entries[view] = Entry(Rect(bounds), TouchDelegate(Rect(bounds), view))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            activeEntry = entries.values.firstOrNull { entry ->
                entry.bounds.contains(event.x.toInt(), event.y.toInt())
            }
        }
        val handled = activeEntry?.delegate?.onTouchEvent(event) == true
        if (
            event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            activeEntry = null
        }
        return handled
    }

    override fun onTouchExplorationHoverEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_HOVER_ENTER) {
            activeEntry = entries.values.firstOrNull { entry ->
                entry.bounds.contains(event.x.toInt(), event.y.toInt())
            }
        }
        val handled = activeEntry?.delegate?.onTouchExplorationHoverEvent(event) == true
        if (event.actionMasked == MotionEvent.ACTION_HOVER_EXIT) {
            activeEntry = null
        }
        return handled
    }
}
