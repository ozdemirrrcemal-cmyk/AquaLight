package com.aqua.aqualight.data.devices.runtime.modules.time

import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeJson
import org.json.JSONObject

object DeviceTimeStatusParser {
    fun parseExact(data: JSONObject): DeviceTimeStatus {
        DeviceRuntimeJson.requireExactKeys(data, STATUS_KEYS, STATUS_LABEL)
        val timeSet = DeviceRuntimeJson.booleanValue(data, "timeSet")
        validateParts(DeviceRuntimeJson.objectValue(data, "parts"), timeSet)
        validateRuntime(DeviceRuntimeJson.objectValue(data, "runtime"))
        DeviceRuntimeJson.stringValue(data, "uptime")
        val timeZone = DeviceRuntimeJson.intValue(data, "timeZone")
        val utcOffsetMinutes = DeviceRuntimeJson.intValue(data, "utcOffsetMinutes")
        val uptimeMs = DeviceRuntimeJson.longValue(data, "uptimeMs")
        val millisStartDay = DeviceRuntimeJson.longValue(data, "millisStartDay")
        val lastSyncEpochMillis = DeviceRuntimeJson.longValue(data, "lastSyncEpochMillis")
        val lastSyncUptimeMs = DeviceRuntimeJson.longValue(data, "lastSyncUptimeMs")
        require(timeZone in MIN_TIME_ZONE_HOURS..MAX_TIME_ZONE_HOURS)
        require(utcOffsetMinutes in MIN_UTC_OFFSET_MINUTES..MAX_UTC_OFFSET_MINUTES)
        require(timeZone == utcOffsetMinutes / MINUTES_PER_HOUR)
        require(uptimeMs >= 0L && millisStartDay in 0L until MILLIS_PER_DAY)
        require(lastSyncEpochMillis >= 0L && lastSyncUptimeMs >= 0L)
        return DeviceTimeStatus(
            timeSet = timeSet,
            timeString = DeviceRuntimeJson.stringValue(data, "timeString"),
            timezoneId = DeviceRuntimeJson.stringValue(data, "timezoneId"),
            posixTimeZone = DeviceRuntimeJson.stringAllowEmpty(data, "posixTimeZone"),
            utcOffsetMinutes = utcOffsetMinutes,
            autoSyncNtpEnabled = DeviceRuntimeJson.booleanValue(data, "autoSyncNtpEnabled"),
            autoSyncGadgetEnabled = DeviceRuntimeJson.booleanValue(
                data,
                "autoSyncGadgetEnabled"
            ),
            ntpServerPrimary = DeviceRuntimeJson.stringValue(data, "ntpServerPrimary"),
            ntpServerSecondary = DeviceRuntimeJson.stringValue(data, "ntpServerSecondary"),
            lastSyncSource = DeviceRuntimeJson.stringAllowEmpty(data, "lastSyncSource"),
            lastSyncEpochMillis = lastSyncEpochMillis,
            lastSyncUptimeMs = lastSyncUptimeMs
        )
    }

    fun parseMutation(data: JSONObject, action: String): DeviceTimeMutationResult {
        val contract = requireNotNull(MUTATIONS[action]) { "Unknown time mutation action: $action" }
        DeviceRuntimeJson.requireExactKeys(data, contract.keys, "time.$action.data")
        require(DeviceRuntimeJson.stringValue(data, FIELD_OPERATION) == contract.operation)
        require(DeviceRuntimeJson.stringValue(data, FIELD_EVENT) == EVENT_TIME_STATUS_CHANGED)
        return DeviceTimeMutationResult(
            operation = contract.operation,
            changed = data.optionalBoolean(FIELD_CHANGED),
            synced = data.optionalBoolean(FIELD_SYNCED),
            saved = data.optionalBoolean(FIELD_SAVED),
            saveRequested = data.optionalBoolean(FIELD_SAVE_REQUESTED),
            status = parseExact(DeviceRuntimeJson.objectValue(data, FIELD_STATUS))
        ).also { result -> validateMutation(result, contract) }
    }

    private fun validateParts(data: JSONObject, timeSet: Boolean) {
        DeviceRuntimeJson.requireExactKeys(data, PART_KEYS, "$STATUS_LABEL.parts")
        val minimumYear = if (timeSet) MIN_SYNCED_YEAR else MIN_UNSET_YEAR
        val year = DeviceRuntimeJson.intValue(data, "year")
        val month = DeviceRuntimeJson.intValue(data, "month")
        val day = DeviceRuntimeJson.intValue(data, "day")
        require(year in minimumYear..MAX_YEAR)
        require(month in MIN_MONTH..MAX_MONTH)
        require(day in MIN_DAY..daysInMonth(year, month))
        require(DeviceRuntimeJson.intValue(data, "weekday") in MIN_WEEKDAY..MAX_WEEKDAY)
        require(DeviceRuntimeJson.intValue(data, "hour") in MIN_HOUR..MAX_HOUR)
        require(DeviceRuntimeJson.intValue(data, "minute") in MIN_MINUTE..MAX_MINUTE)
        require(DeviceRuntimeJson.intValue(data, "second") in MIN_SECOND..MAX_SECOND)
    }

    // Calendar month numbers and their fixed day counts are the domain representation here.
    @Suppress("MagicNumber")
    private fun daysInMonth(year: Int, month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (isLeapYear(year)) 29 else 28
        else -> 0
    }

    @Suppress("MagicNumber")
    private fun isLeapYear(year: Int): Boolean =
        year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

    private fun validateRuntime(data: JSONObject) {
        DeviceRuntimeJson.requireExactKeys(data, RUNTIME_KEYS, "$STATUS_LABEL.runtime")
        require(DeviceRuntimeJson.stringValue(data, "module") == DeviceTimeRuntimeContract.MODULE)
        require(!DeviceRuntimeJson.booleanValue(data, "readOnly"))
        require(DeviceRuntimeJson.booleanValue(data, "supportsConfigApply"))
        require(DeviceRuntimeJson.booleanValue(data, "supportsPhoneSync"))
        require(DeviceRuntimeJson.booleanValue(data, "supportsNtpSync"))
        require(DeviceRuntimeJson.booleanValue(data, "supportsRtcSet"))
    }

    private fun validateMutation(
        result: DeviceTimeMutationResult,
        contract: MutationContract
    ) {
        if (contract.requiresChanged) require(result.changed != null)
        if (contract.requiresSynced) require(result.synced == true)
        if (contract.requiresSaveState) {
            require(result.saved != null && result.saveRequested != null)
            require(result.saved != true || result.saveRequested == true)
        }
    }

    private fun JSONObject.optionalBoolean(key: String): Boolean? =
        if (has(key)) DeviceRuntimeJson.booleanValue(this, key) else null

    private data class MutationContract(
        val operation: String,
        val keys: Set<String>,
        val requiresChanged: Boolean = false,
        val requiresSynced: Boolean = false,
        val requiresSaveState: Boolean = false
    )

    private const val STATUS_LABEL = "time.status.get.data"
    private const val FIELD_OPERATION = "operation"
    private const val FIELD_CHANGED = "changed"
    private const val FIELD_SYNCED = "synced"
    private const val FIELD_SAVED = "saved"
    private const val FIELD_SAVE_REQUESTED = "saveRequested"
    private const val FIELD_EVENT = "event"
    private const val FIELD_STATUS = "status"
    private const val EVENT_TIME_STATUS_CHANGED = "time.status.changed"
    private const val MIN_UNSET_YEAR = 1970
    private const val MIN_SYNCED_YEAR = 2000
    private const val MAX_YEAR = 2099
    private const val MIN_MONTH = 1
    private const val MAX_MONTH = 12
    private const val MIN_DAY = 1
    private const val MIN_WEEKDAY = 1
    private const val MAX_WEEKDAY = 7
    private const val MIN_HOUR = 0
    private const val MAX_HOUR = 23
    private const val MIN_MINUTE = 0
    private const val MAX_MINUTE = 59
    private const val MIN_SECOND = 0
    private const val MAX_SECOND = 59
    private const val MIN_TIME_ZONE_HOURS = -14
    private const val MAX_TIME_ZONE_HOURS = 14
    private const val MIN_UTC_OFFSET_MINUTES = -14 * 60
    private const val MAX_UTC_OFFSET_MINUTES = 14 * 60
    private const val MINUTES_PER_HOUR = 60
    private const val MILLIS_PER_DAY = 86_400_000L

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
        "module", "readOnly", "supportsConfigApply", "supportsPhoneSync",
        "supportsNtpSync", "supportsRtcSet"
    )
    private val CONFIG_KEYS = setOf(
        FIELD_OPERATION, FIELD_CHANGED, FIELD_SAVED, FIELD_SAVE_REQUESTED, FIELD_EVENT, FIELD_STATUS
    )
    private val SAVED_SYNC_KEYS = setOf(
        FIELD_OPERATION, FIELD_SYNCED, FIELD_SAVED, FIELD_SAVE_REQUESTED, FIELD_EVENT, FIELD_STATUS
    )
    private val NTP_KEYS = setOf(FIELD_OPERATION, FIELD_SYNCED, FIELD_EVENT, FIELD_STATUS)
    private val MUTATIONS = mapOf(
        DeviceTimeRuntimeContract.Action.CONFIG_APPLY to MutationContract(
            operation = "timeConfigApply",
            keys = CONFIG_KEYS,
            requiresChanged = true,
            requiresSaveState = true
        ),
        DeviceTimeRuntimeContract.Action.PHONE_SYNC to MutationContract(
            operation = "phoneSync",
            keys = SAVED_SYNC_KEYS,
            requiresSynced = true,
            requiresSaveState = true
        ),
        DeviceTimeRuntimeContract.Action.NTP_SYNC to MutationContract(
            operation = "ntpSync",
            keys = NTP_KEYS,
            requiresSynced = true
        ),
        DeviceTimeRuntimeContract.Action.RTC_SET to MutationContract(
            operation = "rtcSet",
            keys = SAVED_SYNC_KEYS,
            requiresSynced = true,
            requiresSaveState = true
        )
    )
}
