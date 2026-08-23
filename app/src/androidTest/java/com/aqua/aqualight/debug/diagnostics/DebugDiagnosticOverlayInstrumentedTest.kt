package com.aqua.aqualight.debug.diagnostics

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DebugDiagnosticOverlayInstrumentedTest {

    @Test
    fun overlaySurvivesSetContentViewAndShowsCopyControlAboveTheActivity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(DebugDiagnosticOverlayTestActivity::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val decor = activity.window.decorView as ViewGroup
                val overlay = requireNotNull(
                    decor.findViewWithTag<View>(OVERLAY_TAG)
                )
                val header = requireNotNull(
                    overlay.findViewWithTag<TextView>(HEADER_TAG)
                )

                assertSame(decor, overlay.parent)
                assertTrue(overlay.isAttachedToWindow)
                assertTrue(overlay.isShown)
                assertTrue(header.width > 0)
                assertTrue(header.height > 0)
                assertTrue(header.text.toString().contains(COPY_HINT))

                val statusBarBottom = ViewCompat.getRootWindowInsets(decor)
                    ?.getInsets(
                        WindowInsetsCompat.Type.statusBars() or
                            WindowInsetsCompat.Type.displayCutout()
                    )
                    ?.top
                    ?: 0
                assertTrue(statusBarBottom > 0)
                val visibleHeader = Rect()
                assertTrue(header.getGlobalVisibleRect(visibleHeader))
                assertTrue(visibleHeader.top >= statusBarBottom)
            }
        }
    }

    private companion object {
        const val OVERLAY_TAG = "aqualight-debug-diagnostic-overlay"
        const val HEADER_TAG = "aqualight-debug-diagnostic-header"
        const val COPY_HINT = "HOLD=COPY"
    }
}
