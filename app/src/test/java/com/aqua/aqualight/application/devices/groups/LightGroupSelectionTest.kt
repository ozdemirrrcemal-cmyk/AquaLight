package com.aqua.aqualight.application.devices.groups

import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.TankDeviceListItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LightGroupSelectionTest {
    private val elite = LightGroupCompatibility("elite", "elite-id", "wrgb_pro_elite_120", "r1", 4)
    private val slim = LightGroupCompatibility("slim", "slim-id", "rgb_pro_slim_120", "r1", 3)
    private val devices = listOf(light("a"), light("b"), light("slim", slim), light("pending", null))

    @Test fun `first selection filters by exact commercial identity and keeps unverified rows visible`() {
        val selection = LightGroupSelection().add("a", devices)
        assertEquals(listOf("b", "pending"), selection.available(devices).map { it.deviceUid })
        assertEquals(selection, selection.add("slim", devices))
        assertEquals(selection, selection.add("pending", devices))
        assertEquals(selection, selection.add("missing", devices))
        assertEquals(selection, selection.add("a", devices))
    }

    @Test fun `fixture size channel count and hardware mismatches cannot join`() {
        val selection = LightGroupSelection().add("a", devices)
        for (key in listOf(elite.copy(model = "wrgb_pro_elite_90"),
            elite.copy(channelCount = 3), elite.copy(hardwareRevision = "r2"))) {
            assertEquals(selection, selection.add("other", listOf(light("other", key))))
        }
    }

    @Test fun `removing first member preserves filter and removing last restores all lights`() {
        val selection = LightGroupSelection().add("a", devices).add("b", devices).remove("a")
        assertEquals(elite, selection.compatibility)
        assertEquals(listOf("a", "pending"), selection.available(devices).map { it.deviceUid })
        val empty = selection.remove("b")
        assertEquals(LightGroupSelection(), empty)
        assertEquals(devices, empty.available(devices))
    }

    @Test fun `two validated members are required and temporary unknown identity suspends readiness`() {
        val one = LightGroupSelection().add("a", devices)
        assertFalse(one.isReady(devices))
        val two = one.add("b", devices)
        assertTrue(two.isReady(devices))
        val temporarilyUnknown = listOf(light("a"), light("b", null))
        assertEquals(two, two.reconcile(temporarilyUnknown))
        assertFalse(two.isReady(temporarilyUnknown))
        assertTrue(two.reconcile(devices).isReady(devices))
    }

    @Test fun `confirmed removal and identity change remove stale selections and reset an empty group`() {
        val two = LightGroupSelection().add("a", devices).add("b", devices)
        val changed = listOf(light("a", slim), light("b"))
        assertEquals(listOf("b"), two.reconcile(changed).deviceUids)
        assertEquals(LightGroupSelection(), two.reconcile(emptyList()))
    }

    private fun light(uid: String, key: LightGroupCompatibility? = elite) = TankControlGroupDevice(
        TankDeviceListItem(uid, "Custom name", "serial-$uid", OwnerDeviceFamily.LIGHT,
            OwnerDeviceAvailability.UNREACHABLE), "Product label", key
    )
}
