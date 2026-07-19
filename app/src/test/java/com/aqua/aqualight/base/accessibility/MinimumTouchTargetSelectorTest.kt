package com.aqua.aqualight.base.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MinimumTouchTargetSelectorTest {

    @Test
    fun visualHitWinsOverAnotherTargetsExpandedArea() {
        val targets = listOf(
            target(visual = rect(20, 30, 40, 50), expanded = rect(6, 16, 54, 64)),
            target(visual = rect(50, 30, 70, 50), expanded = rect(36, 16, 84, 64))
        )

        assertEquals(1, MinimumTouchTargetSelector.selectIndex(targets, x = 50, y = 40))
    }

    @Test
    fun nearestVisualTargetOwnsAnExpandedOverlapGap() {
        val targets = listOf(
            target(visual = rect(20, 30, 40, 50), expanded = rect(6, 16, 54, 64)),
            target(visual = rect(50, 30, 70, 50), expanded = rect(36, 16, 84, 64))
        )

        assertEquals(1, MinimumTouchTargetSelector.selectIndex(targets, x = 45, y = 40))
    }

    @Test
    fun smallerVisualTargetWinsWhenClickableViewsAreNested() {
        val targets = listOf(
            target(visual = rect(10, 10, 70, 70), expanded = rect(10, 10, 70, 70)),
            target(visual = rect(25, 25, 45, 45), expanded = rect(11, 11, 59, 59))
        )

        assertEquals(1, MinimumTouchTargetSelector.selectIndex(targets, x = 30, y = 30))
    }

    @Test
    fun pointOutsideEveryExpandedAreaHasNoTarget() {
        val targets = listOf(
            target(visual = rect(20, 30, 40, 50), expanded = rect(6, 16, 54, 64))
        )

        assertNull(MinimumTouchTargetSelector.selectIndex(targets, x = 100, y = 100))
    }

    private fun target(
        visual: TouchTargetRect,
        expanded: TouchTargetRect
    ): TouchTargetGeometry {
        return TouchTargetGeometry(
            visualBounds = visual,
            expandedBounds = expanded
        )
    }

    private fun rect(left: Int, top: Int, right: Int, bottom: Int): TouchTargetRect {
        return TouchTargetRect(left, top, right, bottom)
    }
}
