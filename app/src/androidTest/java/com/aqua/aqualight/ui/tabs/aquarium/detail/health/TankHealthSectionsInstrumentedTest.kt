package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.feedback.Stage8DialogTestActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TankHealthSectionsInstrumentedTest {

    @Test
    fun maintenanceAndSystemRemainAfterAddAnalysisAndBindTankData() {
        ActivityScenario.launch(Stage8DialogTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val parent = FrameLayout(activity)
                val adapter = TankHealthContentAdapter()
                adapter.submitMaintenance(
                    TankHealthMaintenanceUi(
                        waterChangeText = "27 Sep 2026",
                        pruningText = null,
                        filterText = "26 Sep 2026"
                    )
                )
                adapter.submitSystem(
                    TankHealthSystemUi(
                        hasSelectedCo2 = true,
                        lightingName = "Tank light",
                        filterName = "Tank filter",
                        livestockCount = 3
                    )
                )

                // Header, Add Analysis, Maintenance, System: check actual inflated views
                // and their bound values, so losing either lower section fails on device.
                assertEquals(4, adapter.itemCount)
                val add = adapter.onCreateViewHolder(parent, adapter.getItemViewType(1))
                val maintenance = adapter.onCreateViewHolder(parent, adapter.getItemViewType(2))
                val system = adapter.onCreateViewHolder(parent, adapter.getItemViewType(3))
                assertEquals(true, add.itemView.isClickable)
                assertNotNull(maintenance.itemView.findViewById<TextView>(R.id.waterChangeAge))
                assertNotNull(system.itemView.findViewById<TextView>(R.id.co2Value))

                adapter.onBindViewHolder(maintenance, 2)
                adapter.onBindViewHolder(system, 3)
                assertEquals(
                    "27 Sep 2026",
                    maintenance.itemView.findViewById<TextView>(R.id.waterChangeAge).text.toString()
                )
                assertEquals(
                    activity.getString(R.string.tank_health_no_care_record),
                    maintenance.itemView.findViewById<TextView>(R.id.pruningAge).text.toString()
                )
                assertEquals(
                    "Tank light",
                    system.itemView.findViewById<TextView>(R.id.lightingValue).text.toString()
                )
                assertEquals(
                    "Tank filter",
                    system.itemView.findViewById<TextView>(R.id.filterValue).text.toString()
                )
                assertEquals(
                    activity.resources.getQuantityString(R.plurals.tank_health_livestock_count, 3, 3),
                    system.itemView.findViewById<TextView>(R.id.livestockValue).text.toString()
                )
            }
        }
    }
}
