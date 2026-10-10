package com.aqua.aqualight.ui.tabs.aquarium.detail

import android.view.LayoutInflater
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.button.MaterialButton
import androidx.viewpager2.widget.ViewPager2
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.feedback.Stage8DialogTestActivity
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.tabs.TabLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TankDetailCollapsingScrollInstrumentedTest {

    @Test
    fun scenesAddMatchesDeviceButtonWithoutActivatingUnimplementedScenes() {
        val scenario = ActivityScenario.launch(Stage8DialogTestActivity::class.java)
        try {
            scenario.onActivity { activity ->
                val header = LayoutInflater.from(activity).inflate(
                    R.layout.item_tank_device_sections_header, null, false
                )
                val sceneButton = header.findViewById<MaterialButton>(R.id.btnAddScene)
                val deviceButton = header.findViewById<MaterialButton>(R.id.btnAddDevice)

                assertEquals(deviceButton.text.toString(), sceneButton.text.toString())
                assertEquals(deviceButton.layoutParams.height, sceneButton.layoutParams.height)
                assertFalse(sceneButton.isClickable)
                assertFalse(sceneButton.isFocusable)
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun aquariumPhotoScrollsAwayAndTankTabsRemainPinned() {
        val scenario = ActivityScenario.launch(Stage8DialogTestActivity::class.java)
        try {
            scenario.onActivity { activity ->
                val tankLayout = LayoutInflater.from(activity).inflate(
                    R.layout.fragment_tank_detail, null, false
                )
                activity.setContentView(tankLayout)
            }

            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity { activity ->
                val appBar = activity.findViewById<AppBarLayout>(R.id.tankAppBar)
                val tabs = activity.findViewById<TabLayout>(R.id.tankTabs)
                val pager = activity.findViewById<ViewPager2>(R.id.tankDetailPager)
                val photo = activity.findViewById<View>(R.id.cardTankImage)

                assertTrue(appBar.totalScrollRange > 0)
                assertTrue(photo.height > 0)
                assertEquals(
                    tabs.height + (tabs.layoutParams as AppBarLayout.LayoutParams).topMargin,
                    appBar.height - appBar.totalScrollRange
                )
                assertTrue(
                    (pager.layoutParams as CoordinatorLayout.LayoutParams).behavior
                        is AppBarLayout.ScrollingViewBehavior
                )
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun everyTankTabExposesANestedVerticalScrollingSurface() {
        val scenario = ActivityScenario.launch(Stage8DialogTestActivity::class.java)
        try {
            scenario.onActivity { activity ->
                val inflater = LayoutInflater.from(activity)
                val nestedScrolls = listOf(
                    R.layout.fragment_tank_detail_activity to R.id.activityScrollView,
                    R.layout.fragment_tank_detail_tank to R.id.tankDetailTankScrollView,
                    R.layout.fragment_tank_detail_plants to R.id.plantsScrollView,
                    R.layout.fragment_tank_detail_life to R.id.tankLifeScrollView
                )
                nestedScrolls.forEach { (layoutId, scrollId) ->
                    val layout = inflater.inflate(layoutId, null, false)
                    val scroll = layout.findViewById<NestedScrollView>(scrollId)
                    assertTrue(scroll.isNestedScrollingEnabled)
                }
                val devices = inflater.inflate(
                    R.layout.fragment_tank_detail_devices, null, false
                ) as RecyclerView
                assertTrue(devices.isNestedScrollingEnabled)
            }
        } finally {
            scenario.close()
        }
    }
}
