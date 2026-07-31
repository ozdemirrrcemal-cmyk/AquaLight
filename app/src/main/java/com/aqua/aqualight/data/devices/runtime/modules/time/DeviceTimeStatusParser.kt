package com.aqua.aqualight.data.devices.runtime.modules.time

import java.util.GregorianCalendar
import org.json.JSONObject

object DeviceTimeStatusParser {

    fun parse(data: JSONObject): DeviceTimeStatus {
        data.requireExactKeys(STATUS_KEYS, "time status")
        val partsJson = data.requiredObject("parts")
        val runtimeJson = data.requiredObject("runtime")
        partsJson.requireExactKeys(PARTS_KEYS, "time status parts")
        runtimeJson.requireExactKeys(RUNTIME_KEYS, "time status runtime")

        val utcOffsetMinutes = data.requiredInt("utcOffsetMinutes").also {
            require(it in DeviceTimeRuntimeContract.Limit.MIN_UTC_OFFSET_MINUTES..
                DeviceTimeRuntimeContract.Limit.MAX_UTC_OFFSET_MINUTES)
        }
        val timeZoneHours = data.requiredInt("timeZone")
        require(timeZoneHours == utcOffsetMinutes / 60) {
            "timeZone differs from utcOffsetMinutes."
        }

        val parts = parseParts(partsJson)
        return DeviceTimeStatus(
            timeSet = data.requiredBoolean("timeSet"),
            timeString = data.requiredNonBlankString("timeString"),
            uptime = data.requiredNonBlankString("uptime"),
            uptimeMs = data.requiredNonNegativeLong("uptimeMs"),
            millisStartDay = data.requiredNonNegativeLong("millisStartDay"),
            timeZoneHours = timeZoneHours,
            utcOffsetMinutes = utcOffsetMinutes,
            timezoneId = data.requiredNonBlankString("timezoneId"),
            posixTimeZone = data.requiredStringAllowEmpty("posixTimeZone"),
            autoSyncNtpEnabled = data.requiredBoolean("autoSyncNtpEnabled"),
            autoSyncGadgetEnabled = data.requiredBoolean("autoSyncGadgetEnabled"),
            ntpServerPrimary = data.requiredStringAllowEmpty("ntpServerPrimary"),
            ntpServerSecondary = data.requiredStringAllowEmpty("ntpServerSecondary"),
            lastSyncSource = data.requiredNonBlankString("lastSyncSource"),
            lastSyncEpochMillis = data.requiredNonNegativeLong("lastSyncEpochMillis"),
            lastSyncUptimeMs = data.requiredNonNegativeLong("lastSyncUptimeMs"),
            parts = parts,
            runtime = DeviceTimeRuntimeCapabilities(
                module = runtimeJson.requiredNonBlankString("module").also {
                    require(it == DeviceTimeRuntimeContract.MODULE)
                },
                readOnly = runtimeJson.requiredBoolean("readOnly").also { require(!it) },
                supportsConfigApply = runtimeJson.requiredBoolean("supportsConfigApply")
                    .also(::requireTrue),
                supportsPhoneSync = runtimeJson.requiredBoolean("supportsPhoneSync")
                    .also(::requireTrue),
                supportsNtpSync = runtimeJson.requiredBoolean("supportsNtpSync")
                    .also(::requireTrue),
                supportsRtcSet = runtimeJson.requiredBoolean("supportsRtcSet")
                    .also(::requireTrue)
            )
        )
    }

    fun parseConfigApply(data: JSONObject): DeviceTimeConfigApplyResult {
        data.requireExactKeys(CONFIG_APPLY_KEYS, "time.config.apply.data")
        val saveRequested = data.requiredBoolean("saveRequested")
        val saved = data.requiredBoolean("saved")
        require(saved == saveRequested)
        return DeviceTimeConfigApplyResult(
            operation = data.requiredNonBlankString("operation").also {
                require(it == "timeConfigApply")
            },
            changed = data.requiredBoolean("changed"),
            saved = saved,
            saveRequested = saveRequested,
            event = data.requiredNonBlankString("event").also(::requireStatusEvent),
            status = parse(data.requiredObject("status"))
        )
    }

    fun parsePhoneSync(data: JSONObject): DeviceTimeSyncResult =
        parsePersistentSync(data, expectedOperation = "phoneSync", label = "time.phone.sync.data")

    fun parseRtcSet(data: JSONObject): DeviceTimeSyncResult =
        parsePersistentSync(data, expectedOperation = "rtcSet", label = "time.rtc.set.data")

    fun parseNtpSync(data: JSONObject): DeviceTimeSyncResult {
        data.requireExactKeys(NTP_SYNC_KEYS, "time.ntp.sync.data")
        return DeviceTimeSyncResult(
            operation = data.requiredNonBlankString("operation").also {
                require(it == "ntpSync")
            },
            synced = data.requiredBoolean("synced").also(::requireTrue),
            saved = null,
            saveRequested = null,
            event = data.requiredNonBlankString("event").also(::requireStatusEvent),
            status = parse(data.requiredObject("status"))
        )
    }

    private fun parsePersistentSync(
        data: JSONObject,
        expectedOperation: String,
        label: String
    ): DeviceTimeSyncResult {
        data.requireExactKeys(PERSISTENT_SYNC_KEYS, label)
        val saveRequested = data.requiredBoolean("saveRequested")
        val saved = data.requiredBoolean("saved")
        require(saved == saveRequested)
        return DeviceTimeSyncResult(
            operation = data.requiredNonBlankString("operation").also {
                require(it == expectedOperation)
            },
            synced = data.requiredBoolean("synced").also(::requireTrue),
            saved = saved,
            saveRequested = saveRequested,
            event = data.requiredNonBlankString("event").also(::requireStatusEvent),
            status = parse(data.requiredObject("status"))
        )
    }

    private fun parseParts(json: JSONObject): DeviceTimeParts {
        val parts = DeviceTimeParts(
            year = json.requiredInt("year"),
            month = json.requiredInt("month"),
            day = json.requiredInt("day"),
            weekday = json.requiredInt("weekday"),
            hour = json.requiredInt("hour"),
            minute = json.requiredInt("minute"),
            second = json.requiredInt("second")
        )
        require(parts.year in DeviceTimeRuntimeContract.Limit.MIN_MANUAL_YEAR..
            DeviceTimeRuntimeContract.Limit.MAX_MANUAL_YEAR)
        validateCalendarDate(parts.year, parts.month, parts.day)
        require(parts.weekday in 1..7)
        require(parts.hour in 0..23)
        require(parts.minute in 0..59)
        require(parts.second in 0..59)
        return parts
    }

    private fun JSONObject.requireExactKeys(expected: Set<String>, label: String) {
        val actual = keys().asSequence().toSet()
        require(actual == expected) {
            "$label keys differ from firmware; expected=$expected actual=$actual"
        }
    }

    private fun JSONObject.requiredObject(key: String): JSONObject =
        get(key) as? JSONObject ?: error("$key must be a JSON object.")

    private fun JSONObject.requiredBoolean(key: String): Boolean =
        get(key) as? Boolean ?: error("$key must be a boolean.")

    private fun JSONObject.requiredInt(key: String): Int {
        val number = get(key) as? Number ?: error("$key must be an integer.")
        val asLong = number.toLong()
        require(number.toDouble().isFinite() && number.toDouble() == asLong.toDouble())
        require(asLong in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
        return asLong.toInt()
    }

    private fun JSONObject.requiredNonNegativeLong(key: String): Long {
        val number = get(key) as? Number ?: error("$key must be an integer.")
        val asLong = number.toLong()
        require(number.toDouble().isFinite() && number.toDouble() == asLong.toDouble())
        require(asLong >= 0L)
        return asLong
    }

    private fun JSONObject.requiredNonBlankString(key: String): String {
        val value = get(key) as? String ?: error("$key must be a string.")
        require(value.isNotEmpty())
        require(value == value.trim())
        require(value.none(Char::isISOControl))
        return value
    }

    private fun JSONObject.requiredStringAllowEmpty(key: String): String {
        val value = get(key) as? String ?: error("$key must be a string.")
        require(value == value.trim())
        require(value.none(Char::isISOControl))
        return value
    }

    private fun validateCalendarDate(year: Int, month: Int, day: Int) {
        val calendar = GregorianCalendar().apply {
            isLenient = false
            clear()
            set(year, month - 1, day)
        }
        calendar.time
    }

    private fun requireTrue(value: Boolean) {
        require(value)
    }

    private fun requireStatusEvent(value: String) {
        require(value == DeviceTimeRuntimeContract.Event.STATUS_CHANGED)
    }

    private val STATUS_KEYS = setOf(
        "timeSet", "timeString", "uptime", "uptimeMs", "millisStartDay", "timeZone",
        "utcOffsetMinutes", "timezoneId", "posixTimeZone", "autoSyncNtpEnabled",
        "autoSyncGadgetEnabled", "ntpServerPrimary", "ntpServerSecondary",
        "lastSyncSource", "lastSyncEpochMillis", "lastSyncUptimeMs", "parts", "runtime"
    )
    private val PARTS_KEYS = setOf(
        "year", "month", "day", "weekday", "hour", "minute", "second"
    )
    private val RUNTIME_KEYS = setOf(
        "module", "readOnly", "supportsConfigApply", "supportsPhoneSync",
        "supportsNtpSync", "supportsRtcSet"
    )
    private val CONFIG_APPLY_KEYS = setOf(
        "operation", "changed", "saved", "saveRequested", "event", "status"
    )
    private val PERSISTENT_SYNC_KEYS = setOf(
        "operation", "synced", "saved", "saveRequested", "event", "status"
    )
    private val NTP_SYNC_KEYS = setOf("operation", "synced", "event", "status")
}
