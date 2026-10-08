package com.aqua.aqualight.data.devices.light.library

import com.aqua.aqualight.data.store.StoreInvariantViolation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DeviceLightLibraryStoreRulesTest {

    @Test
    fun `default store starts at explicit commercial schema one`() {
        assertEquals(1, DeviceLightLibraryStoreRules.defaultStore().schemaVersion)
    }

    @Test
    fun `rgb manual entry cannot persist a white channel`() {
        val invalid = validManual().toBuilder()
            .addChannelKeys("whitePercent")
            .setManual(
                validManual().manual.toBuilder()
                    .addChannels(channel("whitePercent", 30))
            )
            .build()

        assertThrows(StoreInvariantViolation::class.java) {
            DeviceLightLibraryStoreRules.validateEntry(invalid)
        }
    }

    @Test
    fun `same normalized name is unique within owner and type`() {
        val duplicate = validManual().toBuilder()
            .setId("manual-2")
            .setDisplayName("EVENING VIEW")
            .setNormalizedName("evening view")
            .build()
        val store = DeviceLightLibraryStoreRules.defaultStore().toBuilder()
            .addEntries(validManual())
            .addEntries(duplicate)
            .build()

        assertThrows(StoreInvariantViolation::class.java) {
            DeviceLightLibraryStoreRules.validateStore(store)
        }
    }

    @Test
    fun `same name may be used once in manual and once in custom`() {
        val store = DeviceLightLibraryStoreRules.defaultStore().toBuilder()
            .addEntries(validManual())
            .addEntries(validCustom())
            .build()

        assertEquals(2, DeviceLightLibraryStoreRules.validateStore(store).entriesCount)
    }

    @Test
    fun `custom count is the actual strictly ordered time point count`() {
        val entry = validCustom()

        assertEquals(2, entry.custom.pointsCount)
        DeviceLightLibraryStoreRules.validateEntry(entry)
        val unordered = entry.toBuilder()
            .setCustom(
                entry.custom.toBuilder()
                    .setPoints(1, entry.custom.pointsList[1].toBuilder().setTimeMs(1_000L))
            )
            .build()
        assertThrows(StoreInvariantViolation::class.java) {
            DeviceLightLibraryStoreRules.validateEntry(unordered)
        }
    }

    private fun validManual(): StoredDeviceLightLibraryEntry = baseEntry("manual-1")
        .setKind(StoredDeviceLightLibraryKind.STORED_DEVICE_LIGHT_LIBRARY_KIND_MANUAL)
        .setManual(
            StoredDeviceLightManualScene.newBuilder()
                .addChannels(channel("redPercent", 65))
                .addChannels(channel("greenPercent", 45))
                .addChannels(channel("bluePercent", 75))
        )
        .build()

    private fun validCustom(): StoredDeviceLightLibraryEntry = baseEntry("custom-1")
        .setKind(StoredDeviceLightLibraryKind.STORED_DEVICE_LIGHT_LIBRARY_KIND_CUSTOM)
        .setCustom(
            StoredDeviceLightCustomCurve.newBuilder()
                .setWeekdaysMask(31)
                .addPoints(customPoint(28_800_000L, 0, 0, 0))
                .addPoints(customPoint(64_800_000L, 65, 45, 75))
        )
        .build()

    private fun baseEntry(id: String): StoredDeviceLightLibraryEntry.Builder =
        StoredDeviceLightLibraryEntry.newBuilder()
            .setId(id)
            .setOwnerUid("owner-a")
            .setDisplayName("Evening View")
            .setNormalizedName("evening view")
            .setProductKey("LIGHT_RGB_PRO_SLIM")
            .addAllChannelKeys(listOf("redPercent", "greenPercent", "bluePercent"))
            .setCreatedAtMillis(1_767_225_600_000L)
            .setUpdatedAtMillis(1_767_225_600_000L)

    private fun customPoint(
        timeMs: Long,
        red: Int,
        green: Int,
        blue: Int
    ): StoredDeviceLightCustomPoint = StoredDeviceLightCustomPoint.newBuilder()
        .setTimeMs(timeMs)
        .addChannels(channel("redPercent", red))
        .addChannels(channel("greenPercent", green))
        .addChannels(channel("bluePercent", blue))
        .build()

    private fun channel(key: String, percent: Int): StoredDeviceLightChannelValue =
        StoredDeviceLightChannelValue.newBuilder()
            .setChannelKey(key)
            .setPercent(percent)
            .build()
}
