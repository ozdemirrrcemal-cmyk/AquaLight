package com.aqua.aqualight.base.accessibility

import android.graphics.Rect
import android.view.MotionEvent
import android.view.TouchDelegate
import android.view.View
import android.view.ViewGroup
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Expands undersized clickable hit areas to 48dp without changing rendered width, height,
 * padding, color, shape or spacing. The visual design therefore remains pixel-identical.
 */
object MinimumTouchTargetInstaller {

    private const val MIN_TOUCH_TARGET_DP = 48

    fun install(root: View) {
        root.post {
            val host = root as? ViewGroup ?: return@post
            if (host.width <= 0 || host.height <= 0) return@post

            val minimumPixels = (
                MIN_TOUCH_TARGET_DP * host.resources.displayMetrics.density
            ).roundToInt()

            val entries = collectClickableViews(host)
                .asSequence()
                .filter { child ->
                    child !== host &&
                        (child.width < minimumPixels || child.height < minimumPixels)
                }
                .sortedBy { child -> child.width.toLong() * child.height.toLong() }
                .mapNotNull { child ->
                    createTouchEntry(
                        host = host,
                        child = child,
                        minimumPixels = minimumPixels
                    )
                }
                .toList()

            if (entries.isNotEmpty()) {
                host.touchDelegate = CompositeTouchDelegate(host, entries)
            }
        }
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
        val bounds = Rect()
        child.getDrawingRect(bounds)
        host.offsetDescendantRectToMyCoords(child, bounds)

        val horizontalExpansion = max(0, minimumPixels - bounds.width())
        val verticalExpansion = max(0, minimumPixels - bounds.height())

        bounds.left -= horizontalExpansion / 2
        bounds.right += horizontalExpansion - horizontalExpansion / 2
        bounds.top -= verticalExpansion / 2
        bounds.bottom += verticalExpansion - verticalExpansion / 2

        bounds.left = max(0, bounds.left)
        bounds.top = max(0, bounds.top)
        bounds.right = min(host.width, bounds.right)
        bounds.bottom = min(host.height, bounds.bottom)

        return if (bounds.width() <= 0 || bounds.height() <= 0) {
            null
        } else {
            TouchEntry(
                bounds = bounds,
                delegate = TouchDelegate(bounds, child)
            )
        }
    }

    private data class TouchEntry(
        val bounds: Rect,
        val delegate: TouchDelegate
    )

    private class CompositeTouchDelegate(
        host: View,
        private val entries: List<TouchEntry>
    ) : TouchDelegate(Rect(), host) {

        private var activeDelegate: TouchDelegate? = null

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                activeDelegate = entries.firstOrNull { entry ->
                    entry.bounds.contains(event.x.toInt(), event.y.toInt())
                }?.delegate
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
