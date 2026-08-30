package com.aqua.aqualight.data.devices.runtime.modules.time

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceTimeStatusParserExactTest {
    @Test
    fun `pre sync epoch status is valid when time is not set`() {
        val parsed = DeviceTimeStatusParser.parseExact(statusJson(timeSet = false))

        assertTrue(!parsed.timeSet)
        assertEquals("", parsed.lastSyncSource)
    }

    @Test
    fun `phone sync mutation parses exact nested status`() {
        val parsed = DeviceTimeStatusParser.parseMutation(
            JSONObject()
                .put("operation", "phoneSync")
                .put("synced", true)
                .put("saved", false)
                .put("saveRequested", false)
                .put("event", "time.status.changed")
                .put("status", statusJson(timeSet = true)),
            DeviceTimeRuntimeContract.Action.PHONE_SYNC
        )

        assertTrue(parsed.synced == true)
        assertTrue(parsed.saved == false)
    }

    @Test
    fun `mutation rejects firmware event identity drift`() {
        val invalid = JSONObject()
            .put("operation", "ntpSync")
            .put("synced", true)
            .put("event", "time.changed")
            .put("status", statusJson(timeSet = true))

        assertTrue(
            runCatching {
                DeviceTimeStatusParser.parseMutation(
                    invalid,
                    DeviceTimeRuntimeContract.Action.NTP_SYNC
                )
            }.isFailure
        )
    }

    @Test
    fun `synced status rejects a year outside mandatory rtc range`() {
        assertTrue(
            runCatching {
                DeviceTimeStatusParser.parseExact(
                    statusJson(timeSet = true, year = 2100)
                )
            }.isFailure
        )
    }

    @Test
    fun `status rejects an invalid calendar date`() {
        assertTrue(
            runCatching {
                DeviceTimeStatusParser.parseExact(
                    statusJson(timeSet = true, year = 2026, month = 2, day = 29)
                )
            }.isFailure
        )
    }

    @Test
    fun `status rejects a protocol v2 selector or extra field`() {
        val invalid = statusJson(timeSet = true)
            .put("statusSchemaVersion", 2)

        assertTrue(runCatching { DeviceTimeStatusParser.parseExact(invalid) }.isFailure)
    }

    private fun statusJson(
        timeSet: Boolean,
        year: Int = if (timeSet) 2026 else 1970,
        month: Int = 8,
        day: Int = 1
    ): JSONObject = JSONObject()
        .put("timeSet", timeSet)
        .put("timeString", if (timeSet) "2026-08-01 12:00:00" else "1970-01-01 00:00:00")
        .put("uptime", "00:00:05")
        .put("uptimeMs", 5_000L)
        .put("millisStartDay", 0L)
        .put("timeZone", 3)
        .put("utcOffsetMinutes", 180)
        .put("timezoneId", "Europe/Istanbul")
        .put("posixTimeZone", "TRT-3")
        .put("autoSyncNtpEnabled", true)
        .put("autoSyncGadgetEnabled", true)
        .put("ntpServerPrimary", "pool.ntp.org")
        .put("ntpServerSecondary", "time.nist.gov")
        .put("lastSyncSource", if (timeSet) "phone" else "")
        .put("lastSyncEpochMillis", if (timeSet) 1_754_041_600_000L else 0L)
        .put("lastSyncUptimeMs", if (timeSet) 4_000L else 0L)
        .put(
            "parts",
            JSONObject()
                .put("year", year)
                .put("month", month)
                .put("day", day)
                .put("weekday", 7)
                .put("hour", 12)
                .put("minute", 0)
                .put("second", 0)
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
}
