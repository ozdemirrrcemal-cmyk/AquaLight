package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DosingDetailMenuCatalogTest {

    @Test
    fun `menu exposes every destination exactly once`() {
        val items = DOSING_DETAIL_MENU_SECTIONS.flatMap(DosingDetailMenuSection::items)

        assertEquals(DosingDetailMenuItem.entries.toSet(), items.toSet())
        assertEquals(items.size, items.distinct().size)
    }

    @Test
    fun `reset channel is a direct action instead of a menu route`() {
        assertTrue(DOSING_DETAIL_MENU_SECTIONS.last().hasResetChannelAction)
        assertNull(DosingDetailMenuItem.fromRouteKey("reset-channel"))
    }

    @Test
    fun `manual dose is a direct action instead of a menu route`() {
        assertTrue(DOSING_DETAIL_MENU_SECTIONS.last().hasManualDoseAction)
        assertNull(DosingDetailMenuItem.fromRouteKey("manual-dose"))
    }

    @Test
    fun `calibration is a direct action instead of a child menu route`() {
        val accuracySection = DOSING_DETAIL_MENU_SECTIONS[1]

        assertTrue(accuracySection.hasCalibrationAction)
        assertNull(DosingDetailMenuItem.fromRouteKey("calibration"))
    }

    @Test
    fun `every menu entry has a unique round trip route key`() {
        val routeKeys = DosingDetailMenuItem.entries.map(DosingDetailMenuItem::routeKey)

        assertEquals(routeKeys.size, routeKeys.distinct().size)
        DosingDetailMenuItem.entries.forEach { item ->
            assertEquals(item, DosingDetailMenuItem.fromRouteKey(item.routeKey))
        }
    }

    @Test
    fun `missed dose recovery is a direct switch instead of a menu route`() {
        assertTrue(DOSING_DETAIL_MENU_SECTIONS.first().hasMissedDoseRecoverySwitch)
        assertNull(DosingDetailMenuItem.fromRouteKey("missed-dose-recovery"))
    }
}
