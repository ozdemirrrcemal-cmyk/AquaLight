package com.aqua.aqualight.ui.tabs.aquarium.detail.groups

import android.view.DragEvent
import android.view.View
import androidx.core.widget.NestedScrollView
import com.aqua.aqualight.R

/** Keeps both drop areas reachable on short screens and with larger font sizes. */
internal class GroupDragAutoScroller(private val scroll: NestedScrollView) {
    private var velocity = 0
    private var scheduled = false
    private val step = scroll.resources.getDimensionPixelSize(R.dimen.aqua_size_8)
    private val edge = scroll.resources.getDimensionPixelSize(R.dimen.aqua_size_48)
    private val tick = object : Runnable {
        override fun run() {
            if (velocity != 0) {
                scroll.scrollBy(0, velocity)
                scroll.postOnAnimation(this)
            } else scheduled = false
        }
    }

    fun handle(target: View, event: DragEvent) {
        when (event.action) {
            DragEvent.ACTION_DRAG_LOCATION -> update(target, event.y)
            DragEvent.ACTION_DROP, DragEvent.ACTION_DRAG_ENDED, DragEvent.ACTION_DRAG_EXITED -> close()
        }
    }

    private fun update(target: View, y: Float) {
        val targetLocation = IntArray(2)
        val viewportLocation = IntArray(2)
        target.getLocationOnScreen(targetLocation)
        scroll.getLocationOnScreen(viewportLocation)
        val viewportY = targetLocation[Y] + y - viewportLocation[Y]
        velocity = when {
            viewportY < edge -> -step
            viewportY > scroll.height - edge -> step
            else -> 0
        }
        if (!scheduled && velocity != 0) {
            scheduled = true
            scroll.postOnAnimation(tick)
        }
    }

    fun close() {
        velocity = 0
        scheduled = false
        scroll.removeCallbacks(tick)
    }

    private companion object {
        const val Y = 1
    }
}
