package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeEventPayload
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("MagicNumber")
class DeviceDosingV1ContractTest {
    @Test
    fun `contract exposes exactly fourteen canonical authenticated actions`() {
        assertEquals(14, DeviceDosingV1Contract.Action.ALL.size)
        assertEquals(
            setOf(
                "status.get",
                "progress.get",
                "config.apply",
                "program.apply",
                "channel.reset",
                "prime.start",
                "prime.stop",
                "calibration.start",
                "calibration.finish",
                "calibration.confirm",
                "calibration.cancel",
                "dose.now",
                "dose.stop",
                "reservoir.refill"
            ),
            DeviceDosingV1Contract.Action.ALL
        )
    }

    @Test
    fun `request serializers preserve omission null and hourly minute contract`() {
        val clearName = DeviceDosingV1ConfigApplyRequest(
            channelKey = CHANNEL,
            expectedRevision = 9,
            displayName = DeviceDosingV1DisplayNameUpdate.Clear
        ).toJson()
        val calibrationDefault = DeviceDosingV1CalibrationStartRequest(CHANNEL).toJson()
        val hourly = DeviceDosingV1ProgramApplyRequest(
            channelKey = CHANNEL,
            expectedRevision = 9,
            program = DeviceDosingV1Program(
                enabled = true,
                weekdays = DeviceDosingV1Weekdays(List(7) { true }),
                config = DeviceDosingV1ProgramConfig.Hourly24(
                    dailyDose = DeviceDosingV1Amount.fromMilliliters(2.4),
                    minuteOfHour = 15
                )
            )
        ).toJson()
        val hourlyConfig = hourly.getJSONObject("program").getJSONObject("config")

        assertEquals(
            setOf("channelKey", "expectedRevision", "displayName"),
            clearName.keys().asSequence().toSet()
        )
        assertTrue(clearName.isNull("displayName"))
        assertEquals(setOf("channelKey"), calibrationDefault.keys().asSequence().toSet())
        assertEquals(setOf("dailyDoseMl", "minuteOfHour"), hourlyConfig.keys().asSequence().toSet())
        assertEquals(15, hourlyConfig.getInt("minuteOfHour"))
        assertFalse(hourlyConfig.has("startTimeMs"))
        assertFalse(hourly.getJSONObject("program").has("missedDoseRecoveryEnabled"))
    }

    @Test
    fun `hourly minute range matches firmware and old start time shape is rejected`() {
        DeviceDosingV1ProgramConfig.Hourly24(
            dailyDose = DeviceDosingV1Amount.fromMilliliters(2.4),
            minuteOfHour = 0
        )
        DeviceDosingV1ProgramConfig.Hourly24(
            dailyDose = DeviceDosingV1Amount.fromMilliliters(2.4),
            minuteOfHour = 59
        )
        assertThrows(IllegalArgumentException::class.java) {
            DeviceDosingV1ProgramConfig.Hourly24(
                dailyDose = DeviceDosingV1Amount.fromMilliliters(2.4),
                minuteOfHour = -1
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeviceDosingV1ProgramConfig.Hourly24(
                dailyDose = DeviceDosingV1Amount.fromMilliliters(2.4),
                minuteOfHour = 60
            )
        }

        val obsolete = DeviceDosingV1TestFixtures.channelDetail()
            .also { detail ->
                detail.getJSONObject("program")
                    .getJSONObject("config")
                    .remove("minuteOfHour")
                detail.getJSONObject("program")
                    .getJSONObject("config")
                    .put("startTimeMs", 36_900_000)
            }
        assertThrows(IllegalArgumentException::class.java) {
            DeviceDosingV1StatusParser.parseChannel(
                DeviceDosingV1TestFixtures.channelStatus(obsolete)
            )
        }
    }

    @Test
    fun `amount serializer never rounds a non representable dose`() {
        assertEquals(2.4, DeviceDosingV1Amount.fromMilliliters(2.4).milliliters, 0.0)
        assertThrows(IllegalArgumentException::class.java) {
            DeviceDosingV1Amount.fromMilliliters(0.0005)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeviceDosingV1Amount.fromMilliliters(Double.NaN)
        }
    }

    @Test
    fun `display name serializer shares the firmware byte policy`() {
        val c1Name = DeviceDosingV1DisplayNameUpdate.Set("Trace\u0085Elements")
        val asciiTrimmedName = DeviceDosingV1DisplayNameUpdate.Set("\tTrace Elements\r")
        val nonAsciiWhitespace = DeviceDosingV1DisplayNameUpdate.Set("\u00a0Trace\u00a0")

        assertEquals("Trace\u0085Elements", c1Name.normalizedValue)
        assertEquals("Trace Elements", asciiTrimmedName.normalizedValue)
        assertEquals("\u00a0Trace\u00a0", nonAsciiWhitespace.normalizedValue)
        assertThrows(IllegalArgumentException::class.java) {
            DeviceDosingV1DisplayNameUpdate.Set("Trace\u007fElements")
        }
    }

    @Test
    fun `global status is strict and has no display name capability alias`() {
        val canonical = DeviceDosingV1TestFixtures.globalStatus()
        val parsed = DeviceDosingV1StatusParser.parseGlobal(canonical)
        val drifted = JSONObject(canonical.toString()).also { status ->
            status.getJSONObject("runtime").put("displayNameEditable", true)
        }

        assertEquals(2, parsed.channels.size)
        assertThrows(IllegalArgumentException::class.java) {
            DeviceDosingV1StatusParser.parseGlobal(drifted)
        }
    }

    @Test
    fun `unknown wire enum is preserved without weakening fixed object keys`() {
        val detail = DeviceDosingV1TestFixtures.channelDetail(
            runtimeReason = "futureFirmwareReason",
            programMode = "futureProgramMode"
        )
        val parsed = DeviceDosingV1StatusParser.parseChannel(
            DeviceDosingV1TestFixtures.channelStatus(detail)
        ).channel

        assertEquals("futureFirmwareReason", parsed.runtimeReason.raw)
        assertEquals("futureProgramMode", parsed.program?.mode?.raw)
        assertTrue(parsed.program?.config is DeviceDosingV1ProgramSnapshotConfig.Unknown)
        assertFalse(parsed.runtimeReason.isKnown(DeviceDosingV1WireValues.RUNTIME_REASONS))
    }

    @Test
    fun `firmware publishes either its default or persisted user name as effective name`() {
        val defaultName = DeviceDosingV1StatusParser.parseChannel(
            DeviceDosingV1TestFixtures.channelStatus(
                DeviceDosingV1TestFixtures.channelDetail()
                    .put("displayName", JSONObject.NULL)
                    .put("effectiveName", "Channel 1")
            )
        ).channel
        val userName = DeviceDosingV1StatusParser.parseChannel(
            DeviceDosingV1TestFixtures.channelStatus()
        ).channel
        val c1Name = DeviceDosingV1StatusParser.parseChannel(
            DeviceDosingV1TestFixtures.channelStatus(
                DeviceDosingV1TestFixtures.channelDetail()
                    .put("displayName", "Trace\u0085Elements")
                    .put("effectiveName", "Trace\u0085Elements")
            )
        ).channel

        assertEquals("Channel 1", defaultName.defaultName)
        assertNull(defaultName.displayName)
        assertEquals("Channel 1", defaultName.effectiveName)
        assertEquals("Macro", userName.displayName)
        assertEquals("Macro", userName.effectiveName)
        assertEquals("Trace\u0085Elements", c1Name.displayName)
        assertEquals("Trace\u0085Elements", c1Name.effectiveName)
    }

    @Test
    fun `progress values remain firmware authoritative`() {
        val progress = DeviceDosingV1StatusParser.parseProgress(
            DeviceDosingV1TestFixtures.progressStatus()
        )

        assertEquals(45.833, progress.progress.completionPercent, 0.0)
        assertEquals(1.3, progress.progress.remainingAmountMilliliters, 0.0)
        assertEquals(902L, progress.occurrences[1].eventId)
        assertEquals("running", progress.occurrences[1].status.raw)
    }

    @Test
    fun `program apply accepts handler shape and rejects obsolete fixture fields`() {
        val canonical = DeviceDosingV1TestFixtures.savedMutation(
            DeviceDosingV1Contract.Literal.PROGRAM_APPLY
        )
        val obsolete = JSONObject(canonical.toString())
            .put("changed", true)
            .put("revision", 8)
            .put("program", JSONObject())
            .also { response -> response.remove("saved") }

        assertTrue(DeviceDosingV1MutationParser.parseProgramApply(canonical).saved)
        assertThrows(IllegalArgumentException::class.java) {
            DeviceDosingV1MutationParser.parseProgramApply(obsolete)
        }
    }

    @Test
    fun `direct and command events only create invalidations`() {
        val direct = DeviceDosingV1EventParser.parseInvalidation(
            DeviceRuntimeEventPayload.Snapshot(DeviceDosingV1TestFixtures.directEvent())
        )
        val wrapper = DeviceDosingV1EventParser.parseInvalidation(
            DeviceRuntimeEventPayload.CommandResult(
                commandId = "command-1",
                commandModule = "dosing",
                commandAction = "program.apply",
                sessionId = "session-1",
                publishedAtMillis = 1_000,
                result = DeviceDosingV1TestFixtures.savedMutation(
                    DeviceDosingV1Contract.Literal.PROGRAM_APPLY
                )
            )
        )

        assertEquals(8L, direct.revisionHint)
        assertEquals(7L, wrapper.revisionHint)
        assertTrue(direct.refreshGlobal)
        assertTrue(direct.refreshChannel)
        assertTrue(direct.refreshProgress)
        assertEquals(CHANNEL, wrapper.channelKey)
    }

    @Test
    fun `nullable program remains distinct from disabled program`() {
        val detail = DeviceDosingV1TestFixtures.channelDetail().put("program", JSONObject.NULL)
        val parsed = DeviceDosingV1StatusParser.parseChannel(
            DeviceDosingV1TestFixtures.channelStatus(detail)
        )

        assertNull(parsed.channel.program)
    }

    private companion object {
        val CHANNEL = DeviceDosingV1ChannelKey.from("channel1")
    }
}
