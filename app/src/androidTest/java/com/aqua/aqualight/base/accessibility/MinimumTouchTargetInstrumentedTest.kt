package com.aqua.aqualight.base.accessibility

import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.TouchDelegate
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aqua.aqualight.ui.common.feedback.Stage8DialogTestActivity
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MinimumTouchTargetInstrumentedTest {

    @Test
    fun nearestExpandedTargetWinsOverlapGapAndRemovedTargetsAreReleasedDynamically() {
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

            settleDynamicLayout(instrumentation)

            scenario.onActivity { activity ->
                val host = activity.findViewById<FrameLayout>(android.R.id.content)
                val density = host.resources.displayMetrics.density
                dispatchTap(
                    host = host,
                    // 49dp is outside both visuals, inside both expanded targets, and clearly
                    // nearer to the second visual control.
                    x = dp(49, density).toFloat(),
                    y = dp(40, density).toFloat()
                )
            }
            instrumentation.waitForIdleSync()

            assertEquals(0, firstClicks.get())
            assertEquals(1, secondClicks.get())

            scenario.onActivity { activity ->
                val host = activity.findViewById<FrameLayout>(android.R.id.content)
                host.removeAllViews()
            }

            settleDynamicLayout(instrumentation)

            scenario.onActivity { activity ->
                val host = activity.findViewById<FrameLayout>(android.R.id.content)
                val density = host.resources.displayMetrics.density
                dispatchTap(
                    host = host,
                    x = dp(49, density).toFloat(),
                    y = dp(40, density).toFloat()
                )
            }
            instrumentation.waitForIdleSync()

            assertEquals(0, firstClicks.get())
            assertEquals(1, secondClicks.get())
        } finally {
            scenario.close()
        }
    }

    @Test
    fun foreignDelegateKeepsPriorityAndIsRestoredAfterAquaTargetsDisappear() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val foreignClicks = AtomicInteger(0)
        val aquaClicks = AtomicInteger(0)
        val foreignDelegate = AtomicReference<TouchDelegate>()
        val aquaTargetId = View.generateViewId()
        val scenario = ActivityScenario.launch(Stage8DialogTestActivity::class.java)

        try {
            scenario.onActivity { activity ->
                val host = activity.findViewById<FrameLayout>(android.R.id.content)
                host.removeAllViews()

                val density = host.resources.displayMetrics.density
                val foreignTarget = View(activity).apply {
                    setOnClickListener { foreignClicks.incrementAndGet() }
                }
                val aquaTarget = View(activity).apply {
                    id = aquaTargetId
                    setOnClickListener { aquaClicks.incrementAndGet() }
                }

                host.addView(
                    foreignTarget,
                    FrameLayout.LayoutParams(dp(48, density), dp(48, density)).apply {
                        leftMargin = dp(20, density)
                        topMargin = dp(30, density)
                    }
                )
                host.addView(
                    aquaTarget,
                    FrameLayout.LayoutParams(dp(20, density), dp(20, density)).apply {
                        leftMargin = dp(110, density)
                        topMargin = dp(30, density)
                    }
                )

                val delegate = TouchDelegate(
                    Rect(
                        dp(8, density),
                        dp(18, density),
                        dp(80, density),
                        dp(90, density)
                    ),
                    foreignTarget
                )
                foreignDelegate.set(delegate)
                host.touchDelegate = delegate
                MinimumTouchTargetInstaller.install(aquaTarget)
            }

            settleDynamicLayout(instrumentation)

            scenario.onActivity { activity ->
                val host = activity.findViewById<FrameLayout>(android.R.id.content)
                val density = host.resources.displayMetrics.density

                // Outside the foreign target's 48dp visual bounds but inside its own delegate.
                dispatchTap(host, dp(12, density).toFloat(), dp(40, density).toFloat())
                // Outside the Aqua target's visual bounds but inside AquaLight's 48dp expansion.
                dispatchTap(host, dp(98, density).toFloat(), dp(40, density).toFloat())
            }
            instrumentation.waitForIdleSync()

            assertEquals(1, foreignClicks.get())
            assertEquals(1, aquaClicks.get())

            scenario.onActivity { activity ->
                val host = activity.findViewById<FrameLayout>(android.R.id.content)
                host.removeView(host.findViewById<View>(aquaTargetId))
            }

            settleDynamicLayout(instrumentation)

            scenario.onActivity { activity ->
                val host = activity.findViewById<FrameLayout>(android.R.id.content)
                assertSame(foreignDelegate.get(), host.touchDelegate)

                val density = host.resources.displayMetrics.density
                dispatchTap(host, dp(12, density).toFloat(), dp(40, density).toFloat())
            }
            instrumentation.waitForIdleSync()

            assertEquals(2, foreignClicks.get())
            assertEquals(1, aquaClicks.get())
        } finally {
            scenario.close()
        }
    }

    @Test
    fun viewsAddedRemovedAndReplacedAfterInstallAreRebuiltWithoutManualReinstall() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val clicks = AtomicInteger(0)
        val firstTargetId = View.generateViewId()
        val scenario = ActivityScenario.launch(Stage8DialogTestActivity::class.java)

        try {
            scenario.onActivity { activity ->
                val host = activity.findViewById<FrameLayout>(android.R.id.content)
                host.removeAllViews()
                MinimumTouchTargetInstaller.install(host)
            }
            settleDynamicLayout(instrumentation)

            scenario.onActivity { activity ->
                val host = activity.findViewById<FrameLayout>(android.R.id.content)
                val density = host.resources.displayMetrics.density
                host.addView(
                    View(activity).apply {
                        id = firstTargetId
                        setOnClickListener { clicks.incrementAndGet() }
                    },
                    FrameLayout.LayoutParams(dp(20, density), dp(20, density)).apply {
                        leftMargin = dp(60, density)
                        topMargin = dp(30, density)
                    }
                )
            }
            settleDynamicLayout(instrumentation)

            scenario.onActivity { activity ->
                val host = activity.findViewById<FrameLayout>(android.R.id.content)
                val density = host.resources.displayMetrics.density
                dispatchTap(host, dp(48, density).toFloat(), dp(40, density).toFloat())
            }
            instrumentation.waitForIdleSync()
            assertEquals(1, clicks.get())

            scenario.onActivity { activity ->
                val host = activity.findViewById<FrameLayout>(android.R.id.content)
                host.removeView(host.findViewById<View>(firstTargetId))
            }
            settleDynamicLayout(instrumentation)

            scenario.onActivity { activity ->
                val host = activity.findViewById<FrameLayout>(android.R.id.content)
                val density = host.resources.displayMetrics.density
                dispatchTap(host, dp(48, density).toFloat(), dp(40, density).toFloat())
            }
            instrumentation.waitForIdleSync()
            assertEquals(1, clicks.get())

            scenario.onActivity { activity ->
                val host = activity.findViewById<FrameLayout>(android.R.id.content)
                val density = host.resources.displayMetrics.density
                host.addView(
                    View(activity).apply {
                        setOnClickListener { clicks.incrementAndGet() }
                    },
                    FrameLayout.LayoutParams(dp(20, density), dp(20, density)).apply {
                        leftMargin = dp(140, density)
                        topMargin = dp(30, density)
                    }
                )
            }
            settleDynamicLayout(instrumentation)

            scenario.onActivity { activity ->
                val host = activity.findViewById<FrameLayout>(android.R.id.content)
                val density = host.resources.displayMetrics.density
                dispatchTap(host, dp(128, density).toFloat(), dp(40, density).toFloat())
            }
            instrumentation.waitForIdleSync()

            assertEquals(2, clicks.get())
        } finally {
            scenario.close()
        }
    }

    private fun settleDynamicLayout(instrumentation: android.app.Instrumentation) {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(100L)
        instrumentation.waitForIdleSync()
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
