package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.detail

import com.aqua.aqualight.ui.common.devicemenu.AquaDeviceMenuTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DosingDetailMenuCatalogTest {

    @Test
    fun `menu exposes every reference control exactly once`() {
        val items = DOSING_DETAIL_MENU_SECTIONS.flatMap(DosingDetailMenuSection::items)

        assertEquals(DosingDetailMenuItem.entries.toSet(), items.toSet())
        assertEquals(items.size, items.distinct().size)
    }

    @Test
    fun `destructive reset remains visually isolated`() {
        val destructiveItems = DosingDetailMenuItem.entries.filter { item ->
            item.tone == AquaDeviceMenuTone.DANGER
        }

        assertEquals(listOf(DosingDetailMenuItem.RESET_CHANNEL), destructiveItems)
        assertTrue(
            DOSING_DETAIL_MENU_SECTIONS.last().items.last() ==
                DosingDetailMenuItem.RESET_CHANNEL
        )
    }

    @Test
    fun `every menu entry has a unique round trip route key`() {
        val routeKeys = DosingDetailMenuItem.entries.map(DosingDetailMenuItem::routeKey)

        assertEquals(routeKeys.size, routeKeys.distinct().size)
        DosingDetailMenuItem.entries.forEach { item ->
            assertEquals(item, DosingDetailMenuItem.fromRouteKey(item.routeKey))
        }
    }
}
