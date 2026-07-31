package com.aqua.aqualight.data.devices.runtime.modules.time

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceTimeRuntimeContractTest {

    @Test
    fun `partial config emits only canonical fields and save`() {
        val payload = DeviceTimeConfigApplyPayload(
            ntpEnabled = false,
            utcOffsetMinutes = 180,
            save = false
        )
        val json = payload.toJson()

        assertEquals(
            setOf("ntpEnabled", "utcOffsetMinutes", "save"),
            json.keys().asSequence().toSet()
        )
        assertFalse(json.has("timeZone"))
    }

    @Test
    fun `phone and RTC payloads reject invalid epoch offset and calendar input`() {
        assertFails { DeviceTimeConfigApplyPayload() }
        assertFails { DeviceTimeConfigApplyPayload(utcOffsetMinutes = 841) }
        assertFails {
            DevicePhoneSyncPayload(
                epochMillis = DeviceTimeRuntimeContract.Limit.MIN_EPOCH_MILLIS - 1L
            )
        }
        assertFails {
            DeviceManualRtcPayload(
                year = 2026,
                month = 2,
                day = 30,
                hour = 12,
                minute = 0
            )
        }

        val manual = DeviceManualRtcPayload(
            year = 2026,
            month = 7,
            day = 31,
            weekday = 6,
            hour = 12,
            minute = 30,
            second = 15,
            timezoneId = "Europe/Istanbul",
            posixTimeZone = "TRT-3",
            utcOffsetMinutes = 180,
            save = true
        ).toJson()
        assertTrue(manual.has("parts"))
        assertFalse(manual.has("year"))
        assertFalse(manual.has("epochMillis"))

        val epoch = DeviceEpochRtcPayload(
            epochMillis = 1_754_000_000_000L,
            save = false
        ).toJson()
        assertTrue(epoch.has("epochMillis"))
        assertFalse(epoch.has("parts"))
    }

    @Test
    fun `status parser rejects wrappers aliases unknown fields and type coercion`() {
        val status = statusJson()
        assertEquals(180, DeviceTimeStatusParser.parse(status).utcOffsetMinutes)

        val wrapped = JSONObject().put("status", statusJson())
        val alias = statusJson().apply {
            remove("utcOffsetMinutes")
            put("legacyUtcOffset", 180)
        }
        val unknown = statusJson().put("legacyTime", true)
        val coerced = statusJson().put("timeSet", "true")
        val inconsistentOffset = statusJson().put("timeZone", 2)

        listOf(wrapped, alias, unknown, coerced, inconsistentOffset).forEach { invalid ->
            assertFails { DeviceTimeStatusParser.parse(invalid) }
        }
    }

    @Test
    fun `persistence response must match save request`() {
        val valid = configResultJson(saveRequested = true)
        assertTrue(DeviceTimeStatusParser.parseConfigApply(valid).saved)

        val mismatch = configResultJson(saveRequested = true).put("saved", false)
        assertFails { DeviceTimeStatusParser.parseConfigApply(mismatch) }
    }

    @Test
    fun `typed repository executes and parses all five firmware actions`() = runBlocking {
        val gateway = RespondingGateway()
        val repository = DeviceTimeRuntimeRepository(gateway)

        assertTrue(repository.requestStatus(DEVICE_UID) is DeviceRuntimeCommandOutcome.Success)
        assertTrue(
            repository.applyConfig(
                DEVICE_UID,
                DeviceTimeConfigApplyPayload(ntpEnabled = false, save = false)
            ) is DeviceRuntimeCommandOutcome.Success
        )
        assertTrue(
            repository.syncPhone(
                DEVICE_UID,
                DevicePhoneSyncPayload(
                    epochMillis = 1_754_000_000_000L,
                    save = false
                )
            ) is DeviceRuntimeCommandOutcome.Success
        )
        assertTrue(repository.syncNtp(DEVICE_UID) is DeviceRuntimeCommandOutcome.Success)
        assertTrue(
            repository.setRtc(
                DEVICE_UID,
                DeviceManualRtcPayload(
                    year = 2026,
                    month = 7,
                    day = 31,
                    weekday = 6,
                    hour = 12,
                    minute = 30,
                    save = false
                )
            ) is DeviceRuntimeCommandOutcome.Success
        )

        assertEquals(
            listOf("status.get", "config.apply", "phone.sync", "ntp.sync", "rtc.set"),
            gateway.actions
        )
        assertEquals(0, gateway.requests.first().length())
        assertFalse(gateway.requests[1].has("timeZone"))
    }

    private class RespondingGateway : DeviceRuntimeCommandGateway {
        val actions = mutableListOf<String>()
        val requests = mutableListOf<JSONObject>()

        override suspend fun <T> execute(
            deviceUid: DeviceUid,
            command: DeviceRuntimeCommand<T>,
            timeoutMillis: Long
        ): DeviceRuntimeCommandOutcome<T> {
            actions += command.action
            requests += command.encodeData()
            val data = when (command.action) {
                "status.get" -> statusJson()
                "config.apply" -> configResultJson(
                    saveRequested = requests.last().getBoolean("save")
                )
                "phone.sync" -> persistentSyncJson(
                    operation = "phoneSync",
                    saveRequested = requests.last().getBoolean("save")
                )
                "ntp.sync" -> JSONObject()
                    .put("operation", "ntpSync")
                    .put("synced", true)
                    .put("event", "status.changed")
                    .put("status", statusJson())
                "rtc.set" -> persistentSyncJson(
                    operation = "rtcSet",
                    saveRequested = requests.last().getBoolean("save")
                )
                else -> error("Unexpected Time action: ${command.action}")
            }
            val response = AqlWsIncomingMessage.Response(
                id = "time-${command.action}",
                type = "res",
                module = command.module,
                action = command.action,
                data = data,
                ok = true,
                statusCode = 200
            )
            return DeviceRuntimeCommandOutcome.Success(
                deviceUid = deviceUid,
                module = command.module,
                action = command.action,
                messageId = response.id,
                generation = GENERATION,
                statusCode = 200,
                value = command.parseSuccess(response)
            )
        }
    }

    private fun configResultJson(saveRequested: Boolean): JSONObject = JSONObject()
        .put("operation", "timeConfigApply")
        .put("changed", true)
        .put("saved", saveRequested)
        .put("saveRequested", saveRequested)
        .put("event", "status.changed")
        .put("status", statusJson())

    private fun persistentSyncJson(
        operation: String,
        saveRequested: Boolean
    ): JSONObject = JSONObject()
        .put("operation", operation)
        .put("synced", true)
        .put("saved", saveRequested)
        .put("saveRequested", saveRequested)
        .put("event", "status.changed")
        .put("status", statusJson())

    private fun statusJson(): JSONObject = JSONObject()
        .put("timeSet", true)
        .put("timeString", "12:30:15 31.07.2026 W5")
        .put("uptime", "D0 01:00:00; sec=3600; millis()=3600000; Count=0")
        .put("uptimeMs", 3_600_000)
        .put("millisStartDay", 45_015_000)
        .put("timeZone", 3)
        .put("utcOffsetMinutes", 180)
        .put("timezoneId", "Europe/Istanbul")
        .put("posixTimeZone", "TRT-3")
        .put("autoSyncNtpEnabled", true)
        .put("autoSyncGadgetEnabled", true)
        .put("ntpServerPrimary", "pool.ntp.org")
        .put("ntpServerSecondary", "time.nist.gov")
        .put("lastSyncSource", "phone")
        .put("lastSyncEpochMillis", 1_754_000_000_000L)
        .put("lastSyncUptimeMs", 3_500_000)
        .put(
            "parts",
            JSONObject()
                .put("year", 2026)
                .put("month", 7)
                .put("day", 31)
                .put("weekday", 6)
                .put("hour", 12)
                .put("minute", 30)
                .put("second", 15)
        )
        .put(
            "runtime",
            JSONObject()
                .put("module", "time")
                .put("readOnly", false)
                .put("supportsConfigApply", true)
                .put("supportsPhoneSync", true)
                .put("supportsNtpSync", true)
                .put("supportsRtcSet", true)
        )

    private fun assertFails(block: () -> Unit) {
        assertTrue(runCatching(block).isFailure)
    }

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-TIME-000001")
        val GENERATION = DeviceRuntimeConnectionGeneration(11L)
    }
}
