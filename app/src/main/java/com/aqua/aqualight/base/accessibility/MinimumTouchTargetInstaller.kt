package com.aqua.aqualight.base.accessibility

import android.graphics.Rect
import android.view.MotionEvent
import android.view.TouchDelegate
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.aqua.aqualight.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Expands undersized clickable hit areas to 48dp without changing rendered width, height,
 * padding, color, shape or spacing.
 *
 * Every window content host owns one AquaLight installation. A delegate already owned by the
 * framework or another library is retained as the upstream delegate, receives first refusal for
 * every gesture and is restored unchanged whenever AquaLight has no expanded targets. A global
 * layout observer keeps dynamic and recycled view hierarchies current without replacing a foreign
 * hierarchy listener or stacking delegates from fragment callbacks.
 */
object MinimumTouchTargetInstaller {

    private const val MIN_TOUCH_TARGET_DP = 48

    fun install(root: View) {
        root.post {
            if (!root.isAttachedToWindow) return@post

            val host = resolveWindowContentHost(root) ?: return@post
            installationFor(host).scheduleRebuild()
        }
    }

    private fun installationFor(host: ViewGroup): Installation {
        val existing = host.getTag(
            R.id.aqua_minimum_touch_target_delegate_owner
        ) as? Installation
        if (existing != null) {
            existing.ensureObserving()
            return existing
        }

        return Installation(host).also { installation ->
            host.setTag(
                R.id.aqua_minimum_touch_target_delegate_owner,
                installation
            )
        }
    }

    private fun resolveWindowContentHost(root: View): ViewGroup? {
        val windowRoot = root.rootView
        val contentHost = windowRoot.findViewById<View>(android.R.id.content) as? ViewGroup
        return contentHost ?: windowRoot as? ViewGroup ?: root as? ViewGroup
    }

    private fun collectTouchEntries(host: ViewGroup): List<TouchEntry>? {
        val clickableViews = collectClickableViews(host)
        val layoutPending = host.width <= 0 ||
            host.height <= 0 ||
            host.isLayoutRequested ||
            clickableViews.any { child ->
                child !== host &&
                    (child.width <= 0 || child.height <= 0 || child.isLayoutRequested)
            }

        if (layoutPending) return null

        val minimumPixels = (
            MIN_TOUCH_TARGET_DP * host.resources.displayMetrics.density
        ).roundToInt()

        return clickableViews
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
            target = child,
            geometry = TouchTargetGeometry(
                visualBounds = visualBounds.toTargetRect(),
                expandedBounds = expandedBounds.toTargetRect()
            )
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
        val target: View,
        val geometry: TouchTargetGeometry
    )

    private class Installation(
        private val host: ViewGroup
    ) : ViewTreeObserver.OnGlobalLayoutListener, View.OnAttachStateChangeListener {

        private var observedTree: ViewTreeObserver? = null
        private var rebuildScheduled = false
        private var activeEntries: List<TouchEntry> = emptyList()
        private var installedDelegate: CompositeTouchDelegate? = null
        private var foreignDelegate: TouchDelegate? = unwrapForeignDelegate(host.touchDelegate)

        init {
            host.addOnAttachStateChangeListener(this)
            ensureObserving()
        }

        fun ensureObserving() {
            if (!host.isAttachedToWindow) return

            val currentTree = host.viewTreeObserver
            if (observedTree === currentTree) return

            stopObserving()
            if (currentTree.isAlive) {
                currentTree.addOnGlobalLayoutListener(this)
                observedTree = currentTree
            }
        }

        fun scheduleRebuild() {
            ensureObserving()
            if (!host.isAttachedToWindow || rebuildScheduled) return

            rebuildScheduled = true
            host.post {
                rebuildScheduled = false
                if (!host.isAttachedToWindow) return@post
                rebuild()
            }
        }

        override fun onGlobalLayout() {
            scheduleRebuild()
        }

        override fun onViewAttachedToWindow(view: View) {
            ensureObserving()
            scheduleRebuild()
        }

        override fun onViewDetachedFromWindow(view: View) {
            stopObserving()
            restoreForeignDelegateIfOwned()
            activeEntries = emptyList()
            installedDelegate = null
        }

        private fun rebuild() {
            val foreignChanged = captureExternalDelegate()
            val entries = collectTouchEntries(host) ?: return
            val currentDelegate = host.touchDelegate

            val installationStillCurrent = if (entries.isEmpty()) {
                installedDelegate == null && currentDelegate === foreignDelegate
            } else {
                currentDelegate === installedDelegate
            }
            if (!foreignChanged && entries == activeEntries && installationStillCurrent) return

            activeEntries = entries
            if (entries.isEmpty()) {
                restoreForeignDelegateIfOwned()
                installedDelegate = null
                return
            }

            val delegate = CompositeTouchDelegate(
                host = host,
                entries = entries,
                foreignDelegate = foreignDelegate
            )
            installedDelegate = delegate
            host.touchDelegate = delegate
        }

        private fun captureExternalDelegate(): Boolean {
            val currentDelegate = host.touchDelegate
            if (currentDelegate === installedDelegate) return false

            val unwrapped = unwrapForeignDelegate(currentDelegate)
            val changed = unwrapped !== foreignDelegate
            foreignDelegate = unwrapped
            return changed
        }

        private fun restoreForeignDelegateIfOwned() {
            if (host.touchDelegate === installedDelegate) {
                host.touchDelegate = foreignDelegate
            }
        }

        private fun stopObserving() {
            observedTree?.let { observer ->
                if (observer.isAlive) {
                    observer.removeOnGlobalLayoutListener(this)
                }
            }
            observedTree = null
        }
    }

    private class CompositeTouchDelegate(
        host: View,
        private val entries: List<TouchEntry>,
        internal val foreignDelegate: TouchDelegate?
    ) : TouchDelegate(Rect(), host) {

        private enum class ActiveRoute {
            NONE,
            FOREIGN,
            AQUA
        }

        private val targetGeometry = entries.map(TouchEntry::geometry)
        private val aquaDelegates = entries.map { entry ->
            TouchDelegate(entry.geometry.expandedBounds.toRect(), entry.target)
        }
        private var activeRoute = ActiveRoute.NONE
        private var activeAquaDelegate: TouchDelegate? = null

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                resetGesture()
                return beginGesture(event)
            }

            val hadActiveRoute = activeRoute != ActiveRoute.NONE
            val handled = when (activeRoute) {
                ActiveRoute.FOREIGN -> foreignDelegate?.onTouchEvent(event) == true
                ActiveRoute.AQUA -> activeAquaDelegate?.onTouchEvent(event) == true
                ActiveRoute.NONE -> false
            }

            if (
                event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                resetGesture()
            }

            return handled || hadActiveRoute
        }

        private fun beginGesture(event: MotionEvent): Boolean {
            val originalX = event.x
            val originalY = event.y
            if (foreignDelegate?.onTouchEvent(event) == true) {
                activeRoute = ActiveRoute.FOREIGN
                return true
            }

            event.setLocation(originalX, originalY)
            activeAquaDelegate = MinimumTouchTargetSelector.selectIndex(
                targets = targetGeometry,
                x = originalX.toInt(),
                y = originalY.toInt()
            )?.let(aquaDelegates::get)

            val handled = activeAquaDelegate?.onTouchEvent(event) == true
            if (handled) {
                activeRoute = ActiveRoute.AQUA
                return true
            }

            event.setLocation(originalX, originalY)
            resetGesture()
            return false
        }

        private fun resetGesture() {
            activeRoute = ActiveRoute.NONE
            activeAquaDelegate = null
        }
    }

    private fun unwrapForeignDelegate(delegate: TouchDelegate?): TouchDelegate? {
        return (delegate as? CompositeTouchDelegate)?.foreignDelegate ?: delegate
    }

    private fun TouchTargetRect.toRect(): Rect {
        return Rect(left, top, right, bottom)
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
