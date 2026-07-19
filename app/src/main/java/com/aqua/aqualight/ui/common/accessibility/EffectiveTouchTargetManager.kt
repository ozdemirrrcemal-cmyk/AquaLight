package com.aqua.aqualight.ui.common.accessibility

import android.graphics.Rect
import android.view.MotionEvent
import android.view.TouchDelegate
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlin.math.max

/**
 * Expands undersized interactive views to an effective 48 x 48 dp target without changing their
 * measured or rendered geometry.
 *
 * A single composite [TouchDelegate] is installed per suitable ancestor. When expanded regions
 * overlap, the target whose original visual centre is closest to the pointer wins deterministically.
 * Accessibility node bounds use the same effective rectangle, so TalkBack focus matches touch.
 */
object EffectiveTouchTargetManager {

    private const val MIN_TARGET_DP = 48f

    private val attachments = WeakHashMap<View, RootAttachment>()
    private val delegateGroups = WeakHashMap<ViewGroup, CompositeTouchDelegate>()
    private val effectiveBoundsInScreen = WeakHashMap<View, Rect>()
    private val accessibilityDelegates = WeakHashMap<View, AccessibilityDelegateCompat>()

    fun attach(root: View) {
        if (attachments.containsKey(root)) return

        val attachment = RootAttachment(root)
        attachments[root] = attachment
        attachment.attach()
    }

    fun detach(root: View) {
        attachments.remove(root)?.detach()
    }

    /** Recomputes targets immediately when the hierarchy has already been measured. */
    fun refresh(root: View) {
        val attachment = attachments[root] ?: RootAttachment(root).also { created ->
            attachments[root] = created
            created.attach()
        }
        attachment.refreshNow()
    }

    fun effectiveBoundsInScreen(view: View): Rect? =
        effectiveBoundsInScreen[view]?.let(::Rect)

    private fun installAccessibilityBoundsDelegate(view: View) {
        if (accessibilityDelegates.containsKey(view)) return

        val delegate = object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                effectiveBoundsInScreen[host]?.let(info::setBoundsInScreen)
            }
        }
        accessibilityDelegates[view] = delegate
        ViewCompat.setAccessibilityDelegate(view, delegate)
    }

    private fun minimumTargetPx(root: View): Int =
        (MIN_TARGET_DP * root.resources.displayMetrics.density).toInt().coerceAtLeast(1)

    private fun collectCandidates(root: View, minimumPx: Int): List<View> {
        val candidates = mutableListOf<View>()

        fun visit(view: View) {
            if (
                view !== root &&
                view.visibility == View.VISIBLE &&
                view.isEnabled &&
                view.alpha > 0f &&
                view.isClickable &&
                view.width > 0 &&
                view.height > 0 &&
                (view.width < minimumPx || view.height < minimumPx)
            ) {
                candidates += view
            }

            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    visit(view.getChildAt(index))
                }
            }
        }

        visit(root)
        return candidates
    }

    private fun findDelegateHost(target: View, root: View, minimumPx: Int): ViewGroup? {
        var candidate = target.parent as? ViewGroup
        var fallback: ViewGroup? = candidate

        while (candidate != null) {
            fallback = candidate
            val canContainTarget = candidate.width >= minimumPx && candidate.height >= minimumPx
            if (canContainTarget && !candidate.isClickable) return candidate
            if (candidate === root) break
            candidate = candidate.parent as? ViewGroup
        }

        return fallback?.takeIf { host -> host.width > 0 && host.height > 0 }
    }

    private fun effectiveBounds(
        target: View,
        host: ViewGroup,
        minimumPx: Int
    ): Rect {
        val visualBounds = Rect(0, 0, target.width, target.height)
        host.offsetDescendantRectToMyCoords(target, visualBounds)

        val desiredWidth = max(visualBounds.width(), minimumPx).coerceAtMost(host.width)
        val desiredHeight = max(visualBounds.height(), minimumPx).coerceAtMost(host.height)

        val maxLeft = (host.width - desiredWidth).coerceAtLeast(0)
        val maxTop = (host.height - desiredHeight).coerceAtLeast(0)
        val left = (visualBounds.centerX() - desiredWidth / 2).coerceIn(0, maxLeft)
        val top = (visualBounds.centerY() - desiredHeight / 2).coerceIn(0, maxTop)

        return Rect(left, top, left + desiredWidth, top + desiredHeight)
    }

    private fun toScreenBounds(host: ViewGroup, bounds: Rect): Rect {
        val location = IntArray(2)
        host.getLocationOnScreen(location)
        return Rect(bounds).apply {
            offset(location[0], location[1])
        }
    }

    private class RootAttachment(root: View) :
        ViewTreeObserver.OnGlobalLayoutListener,
        View.OnAttachStateChangeListener {

        private val rootReference = WeakReference(root)
        private var refreshScheduled = false
        private val installedHosts = linkedSetOf<ViewGroup>()
        private val installedTargets = linkedSetOf<View>()

        fun attach() {
            val root = rootReference.get() ?: return
            root.addOnAttachStateChangeListener(this)
            if (root.viewTreeObserver.isAlive) {
                root.viewTreeObserver.addOnGlobalLayoutListener(this)
            }
            scheduleRefresh()
        }

        fun detach() {
            val root = rootReference.get()
            if (root != null) {
                root.removeOnAttachStateChangeListener(this)
                if (root.viewTreeObserver.isAlive) {
                    root.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            }

            installedHosts.forEach { host ->
                delegateGroups.remove(host)?.restoreOriginalDelegate()
            }
            installedTargets.forEach(effectiveBoundsInScreen::remove)
            installedHosts.clear()
            installedTargets.clear()
        }

        override fun onGlobalLayout() {
            scheduleRefresh()
        }

        override fun onViewAttachedToWindow(view: View) {
            if (view.viewTreeObserver.isAlive) {
                view.viewTreeObserver.removeOnGlobalLayoutListener(this)
                view.viewTreeObserver.addOnGlobalLayoutListener(this)
            }
            scheduleRefresh()
        }

        override fun onViewDetachedFromWindow(view: View) {
            if (view.viewTreeObserver.isAlive) {
                view.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        }

        fun refreshNow() {
            val root = rootReference.get() ?: return
            if (root.width <= 0 || root.height <= 0) return

            val minimumPx = minimumTargetPx(root)
            val targetsByHost = linkedMapOf<ViewGroup, MutableList<TargetEntry>>()
            val currentTargets = linkedSetOf<View>()

            collectCandidates(root, minimumPx).forEach { target ->
                val host = findDelegateHost(target, root, minimumPx) ?: return@forEach
                val bounds = effectiveBounds(target, host, minimumPx)
                val entry = TargetEntry(
                    target = target,
                    bounds = bounds,
                    visualCenterX = bounds.centerX(),
                    visualCenterY = bounds.centerY()
                )
                targetsByHost.getOrPut(host, ::mutableListOf) += entry
                effectiveBoundsInScreen[target] = toScreenBounds(host, bounds)
                installAccessibilityBoundsDelegate(target)
                currentTargets += target
            }

            installedTargets.filterNot(currentTargets::contains).forEach(effectiveBoundsInScreen::remove)
            installedTargets.clear()
            installedTargets += currentTargets

            val currentHosts = targetsByHost.keys
            installedHosts.filterNot(currentHosts::contains).forEach { host ->
                delegateGroups.remove(host)?.restoreOriginalDelegate()
            }

            targetsByHost.forEach { (host, entries) ->
                val group = delegateGroups[host] ?: CompositeTouchDelegate(host).also { created ->
                    delegateGroups[host] = created
                    host.touchDelegate = created
                }
                group.update(entries)
            }

            installedHosts.clear()
            installedHosts += currentHosts
        }

        private fun scheduleRefresh() {
            if (refreshScheduled) return
            val root = rootReference.get() ?: return
            refreshScheduled = true
            root.post {
                refreshScheduled = false
                refreshNow()
            }
        }
    }

    private data class TargetEntry(
        val target: View,
        val bounds: Rect,
        val visualCenterX: Int,
        val visualCenterY: Int
    ) {
        val delegate = TouchDelegate(bounds, target)

        fun squaredDistanceTo(x: Int, y: Int): Long {
            val dx = x.toLong() - visualCenterX
            val dy = y.toLong() - visualCenterY
            return dx * dx + dy * dy
        }
    }

    private class CompositeTouchDelegate(
        private val host: ViewGroup
    ) : TouchDelegate(Rect(), host) {

        private val originalDelegate: TouchDelegate? = host.touchDelegate
        private var entries: List<TargetEntry> = emptyList()
        private var activeEntry: TargetEntry? = null

        fun update(updatedEntries: List<TargetEntry>) {
            entries = updatedEntries.toList()
            activeEntry = null
        }

        fun restoreOriginalDelegate() {
            if (host.touchDelegate === this) {
                host.touchDelegate = originalDelegate
            }
            entries = emptyList()
            activeEntry = null
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                val x = event.x.toInt()
                val y = event.y.toInt()
                activeEntry = entries
                    .asSequence()
                    .filter { entry -> entry.bounds.contains(x, y) }
                    .minByOrNull { entry -> entry.squaredDistanceTo(x, y) }
            }

            val handled = activeEntry?.delegate?.onTouchEvent(event)
                ?: originalDelegate?.onTouchEvent(event)
                ?: false

            if (
                event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                activeEntry = null
            }

            return handled
        }
    }
}
