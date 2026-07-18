package com.aqua.aqualight.base.loading

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.ui.common.feedback.Stage8DialogTestActivity
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
}
