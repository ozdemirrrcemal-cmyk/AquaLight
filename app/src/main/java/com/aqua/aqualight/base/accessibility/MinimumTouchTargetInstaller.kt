package com.aqua.aqualight.base.accessibility

import android.graphics.Rect
import android.view.MotionEvent
import android.view.TouchDelegate
import android.view.View
import android.view.ViewGroup
import com.aqua.aqualight.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Expands undersized clickable hit areas to 48dp without changing rendered width, height,
 * padding, color, shape or spacing. Every window owns one replaceable delegate on its content
 * host, so fragment callbacks cannot stack competing delegates or retain stale target views.
 */
object MinimumTouchTargetInstaller {

    private const val MIN_TOUCH_TARGET_DP = 48

    fun install(root: View) {
        root.post {
            if (!root.isAttachedToWindow) return@post

            val host = resolveWindowContentHost(root) ?: return@post
            installOnLayoutReady(host)
        }
    }

    private fun installOnLayoutReady(host: ViewGroup) {
        val clickableViews = collectClickableViews(host)
        val layoutPending = host.width <= 0 ||
            host.height <= 0 ||
            host.isLayoutRequested ||
            clickableViews.any { child ->
                child !== host &&
                    (child.width <= 0 || child.height <= 0 || child.isLayoutRequested)
            }

        if (!layoutPending) {
            rebuildDelegate(host, clickableViews)
            return
        }

        val listener = object : View.OnLayoutChangeListener {
            override fun onLayoutChange(
                view: View,
                left: Int,
                top: Int,
                right: Int,
                bottom: Int,
                oldLeft: Int,
                oldTop: Int,
                oldRight: Int,
                oldBottom: Int
            ) {
                host.removeOnLayoutChangeListener(this)
                if (!host.isAttachedToWindow || host.width <= 0 || host.height <= 0) return
                rebuildDelegate(host, collectClickableViews(host))
            }
        }

        host.addOnLayoutChangeListener(listener)
        host.requestLayout()
    }

    private fun rebuildDelegate(
        host: ViewGroup,
        clickableViews: List<View>
    ) {
        val minimumPixels = (
            MIN_TOUCH_TARGET_DP * host.resources.displayMetrics.density
        ).roundToInt()

        val entries = clickableViews
            .asSequence()
            .filter { child ->
                child !== host &&
                    (child.width < minimumPixels || child.height < minimumPixels)
            }
            .mapNotNull { child ->
                createTouchEntry(
                    host = host,
                    child = child,
                    minimumPixels = minimumPixels
                )
            }
            .toList()

        replaceOwnedDelegate(host, entries)
    }

    private fun resolveWindowContentHost(root: View): ViewGroup? {
        val windowRoot = root.rootView
        val contentHost = windowRoot.findViewById<View>(android.R.id.content) as? ViewGroup
        return contentHost ?: windowRoot as? ViewGroup ?: root as? ViewGroup
    }

    private fun replaceOwnedDelegate(
        host: ViewGroup,
        entries: List<TouchEntry>
    ) {
        val installedOwner = host.getTag(R.id.aqua_minimum_touch_target_delegate_owner)

        if (entries.isEmpty()) {
            if (installedOwner is CompositeTouchDelegate) {
                host.touchDelegate = null
                host.setTag(R.id.aqua_minimum_touch_target_delegate_owner, null)
            }
            return
        }

        val delegate = CompositeTouchDelegate(host, entries)
        host.touchDelegate = delegate
        host.setTag(R.id.aqua_minimum_touch_target_delegate_owner, delegate)
    }

    private fun collectClickableViews(root: View): List<View> {
        val result = mutableListOf<View>()

        fun visit(view: View) {
            if (view.visibility != View.VISIBLE) return

            if (view.isClickable && view.isEnabled) {
                result += view
            }

            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    visit(view.getChildAt(index))
                }
            }
        }

        visit(root)
        return result
    }

    private fun createTouchEntry(
        host: ViewGroup,
        child: View,
        minimumPixels: Int
    ): TouchEntry? {
        val visualBounds = Rect()
        child.getDrawingRect(visualBounds)
        host.offsetDescendantRectToMyCoords(child, visualBounds)

        if (visualBounds.width() <= 0 || visualBounds.height() <= 0) return null

        val expandedBounds = Rect(visualBounds)
        val horizontalExpansion = max(0, minimumPixels - expandedBounds.width())
        val verticalExpansion = max(0, minimumPixels - expandedBounds.height())

        expandedBounds.left -= horizontalExpansion / 2
        expandedBounds.right += horizontalExpansion - horizontalExpansion / 2
        expandedBounds.top -= verticalExpansion / 2
        expandedBounds.bottom += verticalExpansion - verticalExpansion / 2

        expandedBounds.left = max(0, expandedBounds.left)
        expandedBounds.top = max(0, expandedBounds.top)
        expandedBounds.right = min(host.width, expandedBounds.right)
        expandedBounds.bottom = min(host.height, expandedBounds.bottom)

        if (expandedBounds.width() <= 0 || expandedBounds.height() <= 0) return null

        return TouchEntry(
            geometry = TouchTargetGeometry(
                visualBounds = visualBounds.toTargetRect(),
                expandedBounds = expandedBounds.toTargetRect()
            ),
            delegate = TouchDelegate(expandedBounds, child)
        )
    }

    private fun Rect.toTargetRect(): TouchTargetRect {
        return TouchTargetRect(
            left = left,
            top = top,
            right = right,
            bottom = bottom
        )
    }

    private data class TouchEntry(
        val geometry: TouchTargetGeometry,
        val delegate: TouchDelegate
    )

    private class CompositeTouchDelegate(
        host: View,
        private val entries: List<TouchEntry>
    ) : TouchDelegate(Rect(), host) {

        private val targetGeometry = entries.map(TouchEntry::geometry)
        private var activeDelegate: TouchDelegate? = null

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                activeDelegate = MinimumTouchTargetSelector.selectIndex(
                    targets = targetGeometry,
                    x = event.x.toInt(),
                    y = event.y.toInt()
                )?.let { index -> entries[index].delegate }
            }

            val handled = activeDelegate?.onTouchEvent(event) == true

            if (
                event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                activeDelegate = null
            }

            return handled
        }
    }
}

internal data class TouchTargetRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val area: Long
        get() = (right - left).toLong() * (bottom - top).toLong()

    fun contains(x: Int, y: Int): Boolean {
        return x >= left && x < right && y >= top && y < bottom
    }

    fun squaredDistanceTo(x: Int, y: Int): Long {
        val nearestX = x.coerceIn(left, right - 1)
        val nearestY = y.coerceIn(top, bottom - 1)
        val deltaX = (x - nearestX).toLong()
        val deltaY = (y - nearestY).toLong()
        return deltaX * deltaX + deltaY * deltaY
    }
}

internal data class TouchTargetGeometry(
    val visualBounds: TouchTargetRect,
    val expandedBounds: TouchTargetRect
)

/** Deterministically partitions overlapping expanded targets by their real visual geometry. */
internal object MinimumTouchTargetSelector {

    fun selectIndex(
        targets: List<TouchTargetGeometry>,
        x: Int,
        y: Int
    ): Int? {
        return targets.withIndex()
            .filter { indexed -> indexed.value.expandedBounds.contains(x, y) }
            .minWithOrNull(
                compareBy<IndexedValue<TouchTargetGeometry>>(
                    { indexed ->
                        if (indexed.value.visualBounds.contains(x, y)) 0 else 1
                    },
                    { indexed -> indexed.value.visualBounds.squaredDistanceTo(x, y) },
                    { indexed -> indexed.value.visualBounds.area },
                    IndexedValue<TouchTargetGeometry>::index
                )
            )
            ?.index
    }
}
