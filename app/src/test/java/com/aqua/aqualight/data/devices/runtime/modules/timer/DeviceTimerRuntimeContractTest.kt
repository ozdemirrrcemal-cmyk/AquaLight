@file:Suppress("MagicNumber")

package com.aqua.aqualight.data.devices.runtime.modules.timer

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceTimerRuntimeContractTest {
    @Test
    fun `firmware fixture command fields and Android serializers are identical`() {
        val fixture = resourceJson("aql_timer_contract_v1.json")
        val commands = fixture.getJSONObject("commands")
        val schedule = DeviceTimerRuntimeFixtures.schedulePayload()
        val config = DeviceTimerConfigApplyPayload(
            channelKey = "channel1",
            expectedRevision = 7L,
            displayName = DeviceTimerDisplayNameUpdate.Value("Filter"),
            schedules = listOf(schedule)
        ).toJson()
        val channelSet = DeviceTimerChannelSetPayload(
            channelKey = "channel1",
            expectedRevision = 7L,
            regime = DeviceTimerRegime.OFF,
            durationMs = 300_000L,
            save = false
        ).toJson()

        assertEquals(
            commands.getJSONObject("timer.config.apply")
                .getJSONArray("requestFields").asStringSet(),
            config.keySetExact()
        )
        assertEquals(
            commands.getJSONObject("timer.config.apply")
                .getJSONArray("scheduleFields").asStringSet(),
            schedule.toJson().keySetExact()
        )
        assertEquals(
            commands.getJSONObject("timer.channel.set")
                .getJSONArray("requestFields").asStringSet(),
            channelSet.keySetExact()
        )
        assertEquals(
            setOf("channelKey"),
            DeviceTimerStatusGetPayload("channel1").toJson().keySetExact()
        )
    }

    @Test
    fun `global and channel-scoped golden statuses parse exactly`() {
        val global = DeviceTimerStatusParser.parse(DeviceTimerRuntimeFixtures.globalStatus())
        val scoped = DeviceTimerStatusParser.parse(DeviceTimerRuntimeFixtures.channelStatus())

        assertFalse(global.channelScoped)
        assertFalse(global.schedulesIncluded)
        assertTrue(global.schedules.isEmpty())
        assertNull(global.selectedChannelKey)
        assertTrue(scoped.channelScoped)
        assertTrue(scoped.schedulesIncluded)
        assertEquals("channel1", scoped.selectedChannelKey)
        assertEquals(1, scoped.schedules.single().slotId)
        assertEquals("12:00", scoped.schedules.single().startTime)
        assertEquals("18:00", scoped.schedules.single().endTime)
        assertEquals(DeviceTimerRuntimeReason.SCHEDULE_ACTIVE, scoped.channels.single().runtimeReason)
    }

    @Test
    fun `status parser fails closed on extra fields aliases and scope inconsistencies`() {
        val extra = DeviceTimerRuntimeFixtures.globalStatus().put("unexpected", true)
        val alias = DeviceTimerRuntimeFixtures.globalStatus().also { status ->
            status.getJSONArray("channels").getJSONObject(0).put("regime", "schedule")
        }
        val missingScopedKey = DeviceTimerRuntimeFixtures.channelStatus()
            .also { status -> status.remove("selectedChannelKey") }

        assertTrue(runCatching { DeviceTimerStatusParser.parse(extra) }.isFailure)
        assertTrue(runCatching { DeviceTimerStatusParser.parse(alias) }.isFailure)
        assertTrue(runCatching { DeviceTimerStatusParser.parse(missingScopedKey) }.isFailure)
    }

    @Test
    fun `status request and channel replacement serializers match firmware field ownership`() {
        val schedule = DeviceTimerRuntimeFixtures.schedulePayload()
        val payload = DeviceTimerConfigApplyPayload(
            channelKey = " CHANNEL1 ",
            expectedRevision = 7L,
            displayName = DeviceTimerDisplayNameUpdate.Value(" Return Pump "),
            schedules = listOf(schedule)
        ).toJson()

        assertEquals(emptySet<String>(), DeviceTimerStatusGetPayload().toJson().keySetExact())
        assertEquals(
            setOf("channelKey"),
            DeviceTimerStatusGetPayload(" CHANNEL1 ").toJson().keySetExact()
        )
        assertEquals(
            setOf("channelKey", "expectedRevision", "displayName", "schedules", "save"),
            payload.keySetExact()
        )
        assertEquals("channel1", payload.getString("channelKey"))
        assertEquals("Return Pump", payload.getString("displayName"))
        assertEquals(
            setOf(
                "slotId", "enabled", "name", "weekdays", "startTimeMs", "endTimeMs",
                "spansMidnight"
            ),
            payload.getJSONArray("schedules").getJSONObject(0).keySetExact()
        )
        assertFalse(payload.getJSONArray("schedules").getJSONObject(0).has("channelKey"))
    }

    @Test
    fun `display name clear is explicit JSON null and schedule delete is channel scoped`() {
        val clear = DeviceTimerConfigApplyPayload(
            channelKey = "channel1",
            expectedRevision = 7L,
            displayName = DeviceTimerDisplayNameUpdate.Clear
        ).toJson()
        val delete = DeviceTimerConfigApplyPayload(
            channelKey = "channel1",
            expectedRevision = 7L,
            schedules = emptyList()
        ).toJson()

        assertTrue(clear.isNull("displayName"))
        assertFalse(clear.has("schedules"))
        assertEquals(0, delete.getJSONArray("schedules").length())
        assertFalse(delete.has("displayName"))
    }

    @Test
    fun `twelve o'clock encodes as whole-minute milliseconds and sub-minute input is rejected`() {
        assertEquals(43_200_000L, timerScheduleBoundaryMillis(12, 0))
        assertEquals("12:00", timerTimeText(timerScheduleBoundaryMillis(12, 0)))
        assertTrue(
            runCatching {
                DeviceTimerScheduleConfig(
                    slotId = 1,
                    enabled = true,
                    name = "Invalid",
                    weekdays = List(7) { true },
                    startTimeMs = 1_000L,
                    endTimeMs = 60_000L
                )
            }.isFailure
        )
    }

    @Test
    fun `duplicate slots and cross-midnight overlaps are rejected before transport`() {
        val overnight = DeviceTimerRuntimeFixtures.schedulePayload(
            slotId = 1,
            startTimeMs = timerScheduleBoundaryMillis(23, 0),
            endTimeMs = timerScheduleBoundaryMillis(1, 0),
            weekdays = listOf(true, false, false, false, false, false, false)
        )
        val nextDayOverlap = DeviceTimerRuntimeFixtures.schedulePayload(
            slotId = 2,
            startTimeMs = timerScheduleBoundaryMillis(0, 30),
            endTimeMs = timerScheduleBoundaryMillis(2, 0),
            weekdays = listOf(false, true, false, false, false, false, false)
        )

        assertTrue(
            runCatching {
                DeviceTimerConfigApplyPayload(
                    channelKey = "channel1",
                    expectedRevision = 7L,
                    schedules = listOf(overnight, overnight)
                )
            }.isFailure
        )
        assertTrue(
            runCatching {
                DeviceTimerConfigApplyPayload(
                    channelKey = "channel1",
                    expectedRevision = 7L,
                    schedules = listOf(overnight, nextDayOverlap)
                )
            }.isFailure
        )
    }

    private fun JSONObject.keySetExact(): Set<String> =
        keys().asSequence().toCollection(linkedSetOf())

    private fun org.json.JSONArray.asStringSet(): Set<String> =
        (0 until length()).mapTo(linkedSetOf()) { index -> getString(index) }

    private fun resourceJson(name: String): JSONObject = JSONObject(
        requireNotNull(javaClass.classLoader?.getResourceAsStream(name)) {
            "Missing Timer contract fixture: $name"
        }.use { stream -> stream.readBytes().toString(Charsets.UTF_8) }
    )
}
