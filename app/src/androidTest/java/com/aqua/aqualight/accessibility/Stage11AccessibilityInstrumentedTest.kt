package com.aqua.aqualight.accessibility

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.accessibility.AccessibilityChecks
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withTagValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import com.aqua.aqualight.smoke.ReleaseSmokeActivity
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.Matcher
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
@SdkSuppress(minSdkVersion = 29)
class Stage11AccessibilityInstrumentedTest {

    @Test
    fun primaryScreensPassFullHierarchyAccessibilityChecks() {
        SCREEN_NAMES.forEach(::auditScreen)
    }

    private fun auditScreen(screenName: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val marker = "$ACCESSIBILITY_READY_PREFIX:$screenName"
        val intent = Intent(context, ReleaseSmokeActivity::class.java).apply {
            putExtra(EXTRA_SMOKE_THEME, "light")
            putExtra(EXTRA_SMOKE_LOCALE, "en")
            putExtra(EXTRA_SMOKE_FONT_SCALE, "1.0")
            putExtra(EXTRA_SMOKE_SCREEN, screenName)
            putExtra(EXTRA_HOLD_FOR_ACCESSIBILITY, true)
        }

        ActivityScenario.launch<ReleaseSmokeActivity>(intent).use { scenario ->
            onView(withContentDescription(marker)).check(matches(isDisplayed()))

            scenario.onActivity { activity ->
                val markerView = requireNotNull(
                    activity.window.decorView.findViewByContentDescription(marker)
                ) {
                    "Accessibility readiness marker was not found for $screenName."
                }
                markerView.contentDescription = null
                markerView.tag = marker
            }

            onView(withTagValue(equalTo(marker))).perform(AccessibilityAuditAction)
        }
    }

    private fun View.findViewByContentDescription(value: String): View? {
        if (contentDescription?.toString() == value) {
            return this
        }
        if (this !is ViewGroup) {
            return null
        }
        for (index in 0 until childCount) {
            getChildAt(index).findViewByContentDescription(value)?.let { return it }
        }
        return null
    }

    private object AccessibilityAuditAction : ViewAction {
        override fun getConstraints(): Matcher<View> = isDisplayed()

        override fun getDescription(): String =
            "Run Stage 11 accessibility checks from the window root"

        override fun perform(uiController: UiController, view: View) {
            uiController.loopMainThreadUntilIdle()
        }
    }

    companion object {
        private val SCREEN_NAMES = listOf(
            "AquariumFragment",
            "AquariumMaintenanceFragment",
            "DevicesFragment",
            "SettingsFragment"
        )

        private const val ACCESSIBILITY_READY_PREFIX = "ACCESSIBILITY_READY"
        private const val EXTRA_SMOKE_THEME = "aqua_smoke_theme"
        private const val EXTRA_SMOKE_LOCALE = "aqua_smoke_locale"
        private const val EXTRA_SMOKE_FONT_SCALE = "aqua_smoke_font_scale"
        private const val EXTRA_SMOKE_SCREEN = "aqua_smoke_screen"
        private const val EXTRA_HOLD_FOR_ACCESSIBILITY = "aqua_hold_for_accessibility"

        @BeforeClass
        @JvmStatic
        fun enableAccessibilityChecks() {
            AccessibilityChecks.enable().setRunChecksFromRootView(true)
        }

        @AfterClass
        @JvmStatic
        fun disableAccessibilityChecks() {
            AccessibilityChecks.disable()
        }
    }
}
