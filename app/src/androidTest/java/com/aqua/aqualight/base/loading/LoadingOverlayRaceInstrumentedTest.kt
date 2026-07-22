package com.aqua.aqualight.base.loading

import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.feedback.Stage8DialogTestActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoadingOverlayRaceInstrumentedTest {

    @Test
    fun rapidShowThenHideDoesNotLeaveOverlayVisible() {
        ActivityScenario.launch(Stage8DialogTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragmentManager = activity.supportFragmentManager

                LoadingOverlayDialogFragment.show(fragmentManager)
                LoadingOverlayDialogFragment.hide(fragmentManager)
                fragmentManager.executePendingTransactions()

                assertNull(
                    fragmentManager.findFragmentByTag(LoadingOverlayDialogFragment.TAG)
                )
            }
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun visibleOverlayMirrorsTheHostSystemBarAppearance() {
        ActivityScenario.launch(Stage8DialogTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val hostWindow = activity.window
                hostWindow.statusBarColor = ContextCompat.getColor(
                    activity,
                    R.color.aqua_surface_action
                )
                hostWindow.navigationBarColor = ContextCompat.getColor(
                    activity,
                    R.color.aqua_surface_deep
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    hostWindow.navigationBarDividerColor = ContextCompat.getColor(
                        activity,
                        R.color.aqua_outline_positive
                    )
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    hostWindow.isStatusBarContrastEnforced = false
                    hostWindow.isNavigationBarContrastEnforced = false
                }

                val hostController = WindowCompat.getInsetsController(
                    hostWindow,
                    hostWindow.decorView
                )
                hostController.isAppearanceLightStatusBars = true
                hostController.isAppearanceLightNavigationBars = false

                val fragmentManager = activity.supportFragmentManager
                LoadingOverlayDialogFragment.show(fragmentManager)
                fragmentManager.executePendingTransactions()

                val overlay = fragmentManager.findFragmentByTag(
                    LoadingOverlayDialogFragment.TAG
                ) as? LoadingOverlayDialogFragment
                assertNotNull(overlay)

                val overlayWindow = requireNotNull(overlay?.dialog?.window)
                val overlayController = WindowCompat.getInsetsController(
                    overlayWindow,
                    overlayWindow.decorView
                )

                assertEquals(hostWindow.statusBarColor, overlayWindow.statusBarColor)
                assertEquals(hostWindow.navigationBarColor, overlayWindow.navigationBarColor)
                assertEquals(
                    hostController.isAppearanceLightStatusBars,
                    overlayController.isAppearanceLightStatusBars
                )
                assertEquals(
                    hostController.isAppearanceLightNavigationBars,
                    overlayController.isAppearanceLightNavigationBars
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    assertEquals(
                        hostWindow.navigationBarDividerColor,
                        overlayWindow.navigationBarDividerColor
                    )
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    assertEquals(
                        hostWindow.isStatusBarContrastEnforced,
                        overlayWindow.isStatusBarContrastEnforced
                    )
                    assertEquals(
                        hostWindow.isNavigationBarContrastEnforced,
                        overlayWindow.isNavigationBarContrastEnforced
                    )
                }

                LoadingOverlayDialogFragment.hide(fragmentManager)
                fragmentManager.executePendingTransactions()
            }
        }
    }
}
