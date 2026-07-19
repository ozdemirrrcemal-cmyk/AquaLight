package com.aqua.aqualight.base.accessibility

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aqua.aqualight.ui.common.feedback.Stage8DialogTestActivity
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MinimumTouchTargetInstrumentedTest {

    @Test
    fun nearestExpandedTargetWinsOverlapGapAndRemovedTargetsAreReleased() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val firstClicks = AtomicInteger(0)
        val secondClicks = AtomicInteger(0)
        val scenario = ActivityScenario.launch(Stage8DialogTestActivity::class.java)

        try {
            scenario.onActivity { activity ->
                val host = activity.findViewById<FrameLayout>(android.R.id.content)
                host.removeAllViews()

                val density = host.resources.displayMetrics.density
                val targetSize = dp(20, density)
                val top = dp(30, density)
                val firstLeft = dp(20, density)
                val secondLeft = dp(50, density)

                val firstTarget = View(activity).apply {
                    setOnClickListener { firstClicks.incrementAndGet() }
                }
                val secondTarget = View(activity).apply {
                    setOnClickListener { secondClicks.incrementAndGet() }
                }

                host.addView(
                    firstTarget,
                    FrameLayout.LayoutParams(targetSize, targetSize).apply {
                        leftMargin = firstLeft
                        topMargin = top
                    }
                )
                host.addView(
                    secondTarget,
                    FrameLayout.LayoutParams(targetSize, targetSize).apply {
                        leftMargin = secondLeft
                        topMargin = top
                    }
                )

                // Installing from a descendant must still produce one delegate on the window host.
                MinimumTouchTargetInstaller.install(firstTarget)
            }

            instrumentation.waitForIdleSync()

            scenario.onActivity { activity ->
                val host = activity.findViewById<FrameLayout>(android.R.id.content)
                val density = host.resources.displayMetrics.density
                dispatchTap(
                    host = host,
                    // 45dp is outside both 20dp visual controls but inside both 48dp targets.
                    x = dp(45, density).toFloat(),
                    y = dp(40, density).toFloat()
                )
            }

            assertEquals(0, firstClicks.get())
            assertEquals(1, secondClicks.get())

            scenario.onActivity { activity ->
                val host = activity.findViewById<FrameLayout>(android.R.id.content)
                host.removeAllViews()
                MinimumTouchTargetInstaller.install(host)
            }

            instrumentation.waitForIdleSync()

            scenario.onActivity { activity ->
                val host = activity.findViewById<FrameLayout>(android.R.id.content)
                val density = host.resources.displayMetrics.density
                dispatchTap(
                    host = host,
                    x = dp(45, density).toFloat(),
                    y = dp(40, density).toFloat()
                )
            }

            assertEquals(0, firstClicks.get())
            assertEquals(1, secondClicks.get())
        } finally {
            scenario.close()
        }
    }

    private fun dispatchTap(host: View, x: Float, y: Float) {
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(
            downTime,
            downTime,
            MotionEvent.ACTION_DOWN,
            x,
            y,
            0
        )
        val up = MotionEvent.obtain(
            downTime,
            downTime + 16L,
            MotionEvent.ACTION_UP,
            x,
            y,
            0
        )

        try {
            host.dispatchTouchEvent(down)
            host.dispatchTouchEvent(up)
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    private fun dp(value: Int, density: Float): Int {
        return (value * density).roundToInt()
    }
}
