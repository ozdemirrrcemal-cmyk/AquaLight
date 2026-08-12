package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.detail

import com.aqua.aqualight.R
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DosingPlanMenuArchitectureTest {

    private val repositoryRoot = locateRepositoryRoot()

    @Test
    fun `schedule catalog exposes four exclusive presentation modes`() {
        assertEquals(
            listOf(
                R.string.device_dosing_detail_schedule_single,
                R.string.device_dosing_detail_schedule_hourly,
                R.string.device_dosing_detail_schedule_custom,
                R.string.device_dosing_detail_schedule_timer
            ),
            DOSING_PLAN_SCHEDULE_OPTIONS.map(DosingPlanScheduleOption::labelRes)
        )
        assertEquals(1, DOSING_PLAN_SCHEDULE_OPTIONS.count(DosingPlanScheduleOption::selected))
        assertEquals(
            listOf(
                DosingPlanScheduleMode.SINGLE,
                DosingPlanScheduleMode.HOURLY,
                DosingPlanScheduleMode.CUSTOM,
                DosingPlanScheduleMode.TIMER
            ),
            DOSING_PLAN_SCHEDULE_OPTIONS.map(DosingPlanScheduleOption::mode)
        )
    }

    @Test
    fun `recurrence catalog keeps every weekday in one stable order`() {
        assertEquals(
            listOf(
                R.string.device_dosing_weekday_mon,
                R.string.device_dosing_weekday_tue,
                R.string.device_dosing_weekday_wed,
                R.string.device_dosing_weekday_thu,
                R.string.device_dosing_weekday_fri,
                R.string.device_dosing_weekday_sat,
                R.string.device_dosing_weekday_sun
            ),
            DOSING_PLAN_WEEKDAY_LABELS
        )
        assertEquals(DOSING_PLAN_WEEKDAY_LABELS.size, DOSING_PLAN_WEEKDAY_LABELS.distinct().size)
        assertEquals(DOSING_PLAN_WEEKDAYS.size, DOSING_PLAN_WEEKDAYS.distinct().size)
    }

    @Test
    fun `schedule state is hoisted and disables every dependent plan control`() {
        val fragment = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/channel/" +
                "detail/DeviceDosingChannelMenuFragment.kt"
        )
        val screen = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/channel/" +
                "detail/DeviceDosingChannelMenuScreen.kt"
        )

        assertTrue(fragment.contains("STATE_SCHEDULE_ENABLED"))
        assertTrue(fragment.contains("STATE_SCHEDULE_WEEKDAYS"))
        assertTrue(screen.contains("checked = scheduleEnabled"))
        assertTrue(screen.contains("enabled = scheduleEnabled"))
        assertTrue(screen.contains("onWeekdaySelectionChange"))
        assertFalse(screen.contains("selected = true,\n                modifier = Modifier.weight(1f)"))
    }

    @Test
    fun `dosing plan consumes central menu selection styling only`() {
        val screen = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/" +
                "channel/detail/DeviceDosingChannelMenuScreen.kt"
        )
        val centralSelectionRow = source(
            "app/src/main/java/com/aqua/aqualight/ui/common/devicemenu/" +
                "AquaDeviceMenuSelectionRow.kt"
        )

        assertTrue(screen.contains("AquaDeviceMenuSelectionRow("))
        assertTrue(screen.contains("compact = true"))
        assertTrue(centralSelectionRow.contains("fun AquaDeviceMenuSelectionRow("))
        assertFalse(screen.contains("DetailChoiceGroup("))
        assertFalse(screen.contains("weekdayRows"))
    }

    @Test
    fun `single dose editor reuses the detail pump shell and central menu components`() {
        val fragment = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/channel/" +
                "schedule/single/DeviceDosingSingleScheduleFragment.kt"
        )
        val screen = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/channel/" +
                "schedule/single/DeviceDosingSingleScheduleScreen.kt"
        )
        val navigation = source("app/src/main/res/navigation/nav_app.xml")

        assertTrue(fragment.contains("R.layout.fragment_device_dosing_channel_detail"))
        assertTrue(fragment.contains("setupSelectedPump("))
        assertTrue(fragment.contains("AquaTimePickerBottomSheet.show("))
        assertTrue(screen.contains("AquaDeviceMenuHeroCard("))
        assertTrue(screen.contains("AquaDeviceMenuEditableValueRow("))
        assertTrue(navigation.contains("deviceDosingSingleScheduleFragment"))
        assertFalse(screen.contains("DosingPumpDevice("))
    }

    @Test
    fun `hourly editor reuses the pump shell and central minute picker mode`() {
        val fragment = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/channel/" +
                "schedule/hourly/DeviceDosingHourlyScheduleFragment.kt"
        )
        val screen = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/channel/" +
                "schedule/hourly/DeviceDosingHourlyScheduleScreen.kt"
        )
        val navigation = source("app/src/main/res/navigation/nav_app.xml")

        assertTrue(fragment.contains("R.layout.fragment_device_dosing_channel_detail"))
        assertTrue(fragment.contains("setupSelectedPump("))
        assertTrue(fragment.contains("SelectionMode.MINUTE_OF_HOUR"))
        assertTrue(fragment.contains("AquaTimePickerBottomSheet.show("))
        assertTrue(screen.contains("AquaDeviceMenuHeroCard("))
        assertTrue(screen.contains("AquaDeviceMenuEditableValueRow("))
        assertTrue(navigation.contains("deviceDosingHourlyScheduleFragment"))
        assertFalse(screen.contains("DosingPumpDevice("))
    }

    @Test
    fun `custom-period editor uses central time count and device-menu components`() {
        val fragment = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/channel/" +
                "schedule/custom/DeviceDosingCustomScheduleFragment.kt"
        )
        val screen = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/channel/" +
                "schedule/custom/DeviceDosingCustomScheduleScreen.kt"
        )
        val editor = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/channel/" +
                "schedule/custom/DeviceDosingCustomScheduleEditor.kt"
        )
        val navigation = source("app/src/main/res/navigation/nav_app.xml")

        assertTrue(fragment.contains("R.layout.fragment_device_dosing_channel_detail"))
        assertTrue(fragment.contains("setupSelectedPump("))
        assertTrue(editor.contains("AquaTimePickerBottomSheet.show("))
        assertTrue(editor.contains("IntegerStepperBottomSheet.show("))
        assertTrue(screen.contains("AquaDeviceMenuHeroCard("))
        assertTrue(screen.contains("AquaDeviceMenuActionRow("))
        assertTrue(navigation.contains("deviceDosingCustomScheduleFragment"))
        assertFalse(screen.contains("DosingPumpDevice("))
    }

    @Test
    fun `timer editor uses central time amount and daily-total boundaries`() {
        val fragment = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/channel/" +
                "schedule/timer/DeviceDosingTimerScheduleFragment.kt"
        )
        val screen = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/channel/" +
                "schedule/timer/DeviceDosingTimerScheduleScreen.kt"
        )
        val editor = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/channel/" +
                "schedule/timer/DeviceDosingTimerScheduleEditor.kt"
        )
        val menu = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/channel/" +
                "detail/DeviceDosingChannelMenuFragment.kt"
        )
        val navigation = source("app/src/main/res/navigation/nav_app.xml")

        assertTrue(fragment.contains("R.layout.fragment_device_dosing_channel_detail"))
        assertTrue(fragment.contains("setupSelectedPump("))
        assertTrue(editor.contains("AquaTimePickerBottomSheet.show("))
        assertTrue(editor.contains("TextInputBottomSheet.show("))
        assertTrue(screen.contains("totalDoseMicroliters"))
        assertTrue(menu.contains("displayedDailyDoseMicroliters"))
        assertTrue(navigation.contains("deviceDosingTimerScheduleFragment"))
        assertFalse(screen.contains("DosingPumpDevice("))
    }

    private fun source(relativePath: String): String = File(repositoryRoot, relativePath).readText()

    private fun locateRepositoryRoot(): File {
        var candidate: File? = File(System.getProperty("user.dir")).absoluteFile
        while (candidate != null) {
            if (File(candidate, "app/src/main").isDirectory) return candidate
            candidate = candidate.parentFile
        }
        error("Cannot locate AquaLight repository root from user.dir.")
    }
}
