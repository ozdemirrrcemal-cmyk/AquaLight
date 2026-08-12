package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DosingDetailMenuCatalogTest {

    @Test
    fun `menu exposes every child destination exactly once`() {
        val items = DOSING_DETAIL_MENU_SECTIONS.flatMap(DosingDetailMenuSection::items)

        assertEquals(DosingDetailMenuItem.entries.toSet(), items.toSet())
        assertEquals(items.size, items.distinct().size)
        assertEquals(
            listOf(DosingDetailMenuItem.DOSING_PLAN, DosingDetailMenuItem.RESERVOIR),
            items
        )
    }

    @Test
    fun `reset channel is a direct action instead of a child destination`() {
        assertTrue(DOSING_DETAIL_MENU_SECTIONS.last().hasResetChannelAction)
    }

    @Test
    fun `manual dose is a direct action instead of a child destination`() {
        assertTrue(DOSING_DETAIL_MENU_SECTIONS.last().hasManualDoseAction)
    }

    @Test
    fun `calibration is a direct action instead of a generic child route`() {
        assertTrue(DOSING_DETAIL_MENU_SECTIONS[1].hasCalibrationAction)
    }

    @Test
    fun `missed dose recovery is a direct switch instead of a child destination`() {
        assertTrue(DOSING_DETAIL_MENU_SECTIONS.first().hasMissedDoseRecoverySwitch)
    }
}
