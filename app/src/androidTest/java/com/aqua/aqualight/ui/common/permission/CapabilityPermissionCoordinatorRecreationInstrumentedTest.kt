package com.aqua.aqualight.ui.common.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aqua.aqualight.ui.common.feedback.Stage8DialogTestActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CapabilityPermissionCoordinatorRecreationInstrumentedTest {
    private var notificationPermissionGrantedByTest = false

    @Before
    fun setUp() {
        CapabilityPermissionRecreationTestFragment.resetRecordedActions()
        ensureNotificationRuntimePermission()
    }

    @After
    fun tearDown() {
        CapabilityPermissionRecreationTestFragment.resetRecordedActions()
        if (
            notificationPermissionGrantedByTest &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.uiAutomation.revokeRuntimePermission(
                instrumentation.targetContext.packageName,
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }

    @Test
    fun appSettingsExplanationRemainsPendingAcrossActivityRecreation() {
        assertExplanationSurvivesRecreation(
            showExplanation = { fragment -> fragment.showAppSettingsExplanation() }
        )
    }

    @Test
    fun channelSettingsExplanationRemainsPendingAcrossActivityRecreation() {
        assertExplanationSurvivesRecreation(
            showExplanation = { fragment -> fragment.showChannelSettingsExplanation() }
        )
    }

    private fun assertExplanationSurvivesRecreation(
        showExplanation: (CapabilityPermissionRecreationTestFragment) -> Unit
    ) {
        ActivityScenario.launch(Stage8DialogTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = CapabilityPermissionRecreationTestFragment()
                activity.supportFragmentManager.beginTransaction()
                    .replace(android.R.id.content, fragment, FRAGMENT_TAG)
                    .commitNow()
                showExplanation(fragment)
                fragment.childFragmentManager.executePendingTransactions()
                assertEquals(1, fragment.permissionExplanationCount())
            }

            scenario.recreate()

            scenario.onActivity { activity ->
                val restored = activity.supportFragmentManager
                    .findFragmentByTag(FRAGMENT_TAG) as?
                    CapabilityPermissionRecreationTestFragment
                assertNotNull(restored)
                assertEquals(1, requireNotNull(restored).permissionExplanationCount())
                assertEquals(
                    emptyList<String>(),
                    CapabilityPermissionRecreationTestFragment.recordedActions()
                )
            }
        }
    }

    private fun ensureNotificationRuntimePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context: Context = instrumentation.targetContext
        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.POST_NOTIFICATIONS
        )
        notificationPermissionGrantedByTest = true
    }

    private fun CapabilityPermissionRecreationTestFragment.permissionExplanationCount(): Int =
        childFragmentManager.fragments.count { fragment ->
            fragment is CapabilityPermissionBottomSheet
        }

    private companion object {
        const val FRAGMENT_TAG = "capability-permission-recreation-test"
    }
}
