package com.aqua.aqualight.data.devices.runtime.modules.time

import org.json.JSONObject

object DeviceTimeStatusParser {

    fun parse(data: JSONObject): DeviceTimeStatus {
        val status = data.optJSONObject("status") ?: data
        status.requireExactKeys(STATUS_KEYS, "time status")
        val parts = status.requiredObject("parts")
        val runtime = status.requiredObject("runtime")
        parts.requireExactKeys(PART_KEYS, "time status parts")
        runtime.requireExactKeys(RUNTIME_KEYS, "time status runtime")

        return DeviceTimeStatus(
            timeSet = status.requiredBoolean("timeSet"),
            timeString = status.requiredStringAllowEmpty("timeString"),
            uptime = status.requiredString("uptime"),
            uptimeMs = status.requiredNonNegativeLong("uptimeMs"),
            millisStartDay = status.requiredNonNegativeLong("millisStartDay"),
            timeZone = status.requiredInt("timeZone"),
            timezoneId = status.requiredStringAllowEmpty("timezoneId"),
            posixTimeZone = status.requiredStringAllowEmpty("posixTimeZone"),
            utcOffsetMinutes = status.requiredInt("utcOffsetMinutes").also {
                require(it in -18 * 60..18 * 60)
            },
            autoSyncNtpEnabled = status.requiredBoolean("autoSyncNtpEnabled"),
            autoSyncGadgetEnabled = status.requiredBoolean("autoSyncGadgetEnabled"),
            ntpServerPrimary = status.requiredString("ntpServerPrimary"),
            ntpServerSecondary = status.requiredString("ntpServerSecondary"),
            lastSyncSource = status.requiredStringAllowEmpty("lastSyncSource"),
            lastSyncEpochMillis = status.requiredNonNegativeLong("lastSyncEpochMillis"),
            lastSyncUptimeMs = status.requiredNonNegativeLong("lastSyncUptimeMs"),
            parts = DeviceTimeParts(
                year = parts.requiredInt("year").also { require(it in 0..9_999) },
                month = parts.requiredInt("month").also { require(it in 0..12) },
                day = parts.requiredInt("day").also { require(it in 0..31) },
                weekday = parts.requiredInt("weekday").also { require(it in 0..7) },
                hour = parts.requiredInt("hour").also { require(it in 0..23) },
                minute = parts.requiredInt("minute").also { require(it in 0..59) },
                second = parts.requiredInt("second").also { require(it in 0..60) }
            ),
            runtime = DeviceTimeRuntimeCapabilities(
                module = runtime.requiredString("module").also {
                    require(it == DeviceTimeRuntimeContract.MODULE)
                },
                readOnly = runtime.requiredBoolean("readOnly"),
                supportsConfigApply = runtime.requiredBoolean("supportsConfigApply"),
                supportsPhoneSync = runtime.requiredBoolean("supportsPhoneSync"),
                supportsNtpSync = runtime.requiredBoolean("supportsNtpSync"),
                supportsRtcSet = runtime.requiredBoolean("supportsRtcSet")
            )
        )
    }

    private val STATUS_KEYS = setOf(
        "timeSet", "timeString", "uptime", "uptimeMs", "millisStartDay", "timeZone",
        "utcOffsetMinutes", "timezoneId", "posixTimeZone", "autoSyncNtpEnabled",
        "autoSyncGadgetEnabled", "ntpServerPrimary", "ntpServerSecondary", "lastSyncSource",
        "lastSyncEpochMillis", "lastSyncUptimeMs", "parts", "runtime"
    )
    private val PART_KEYS = setOf(
        "year", "month", "day", "weekday", "hour", "minute", "second"
    )
    private val RUNTIME_KEYS = setOf(
        "module", "readOnly", "supportsConfigApply", "supportsPhoneSync", "supportsNtpSync",
        "supportsRtcSet"
    )
}

private fun JSONObject.requireExactKeys(expected: Set<String>, label: String) {
    val actual = buildSet {
        val iterator = keys()
        while (iterator.hasNext()) add(iterator.next())
    }
    require(actual == expected) { "$label keys differ from the firmware contract: $actual" }
}

private fun JSONObject.requiredObject(key: String): JSONObject =
    get(key) as? JSONObject ?: error("$key must be an object.")

private fun JSONObject.requiredString(key: String): String =
    requiredStringAllowEmpty(key).also { require(it.isNotEmpty()) { "$key must not be empty." } }

private fun JSONObject.requiredStringAllowEmpty(key: String): String {
    val value = get(key) as? String ?: error("$key must be a string.")
    require(value.none(Char::isISOControl))
    require(value.isEmpty() || (!value.first().isWhitespace() && !value.last().isWhitespace()))
    return value
}

private fun JSONObject.requiredBoolean(key: String): Boolean =
    get(key) as? Boolean ?: error("$key must be a boolean.")

private fun JSONObject.requiredInt(key: String): Int {
    val number = get(key) as? Number ?: error("$key must be numeric.")
    val doubleValue = number.toDouble()
    val longValue = number.toLong()
    require(doubleValue.isFinite() && doubleValue == longValue.toDouble())
    require(longValue in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
    return longValue.toInt()
}

private fun JSONObject.requiredNonNegativeLong(key: String): Long {
    val number = get(key) as? Number ?: error("$key must be numeric.")
    val doubleValue = number.toDouble()
    val longValue = number.toLong()
    require(doubleValue.isFinite() && doubleValue == longValue.toDouble() && longValue >= 0L)
    return longValue
}
