package com.aqua.aqualight.data.devices.runtime.modules.time

import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeJson
import org.json.JSONObject

object DeviceTimeStatusParser {
    fun parse(data: JSONObject): DeviceTimeStatus {
        val status = data.optJSONObject(FIELD_STATUS) ?: data
        return DeviceTimeStatus(
            timeSet = status.optBoolean("timeSet", false),
            timeString = status.optString("timeString", ""),
            timezoneId = status.optString("timezoneId", ""),
            posixTimeZone = status.optString("posixTimeZone", ""),
            utcOffsetMinutes = status.optInt(
                "utcOffsetMinutes",
                status.optInt("timeZone", 0) * 60
            ),
            autoSyncNtpEnabled = status.optBoolean("autoSyncNtpEnabled", false),
            autoSyncGadgetEnabled = status.optBoolean("autoSyncGadgetEnabled", false),
            ntpServerPrimary = status.optString(
                "ntpServerPrimary",
                DeviceTimeRuntimeContract.Default.PRIMARY_NTP_SERVER
            ),
            ntpServerSecondary = status.optString(
                "ntpServerSecondary",
                DeviceTimeRuntimeContract.Default.SECONDARY_NTP_SERVER
            ),
            lastSyncSource = status.optString("lastSyncSource", ""),
            lastSyncEpochMillis = status.optLong("lastSyncEpochMillis", 0L),
            lastSyncUptimeMs = status.optLong("lastSyncUptimeMs", 0L)
        )
    }

    fun parseExact(data: JSONObject): DeviceTimeStatus {
        DeviceRuntimeJson.requireExactKeys(data, STATUS_KEYS, STATUS_LABEL)
        validateParts(DeviceRuntimeJson.objectValue(data, "parts"))
        validateRuntime(DeviceRuntimeJson.objectValue(data, "runtime"))
        val uptimeMs = DeviceRuntimeJson.longValue(data, "uptimeMs")
        val millisStartDay = DeviceRuntimeJson.longValue(data, "millisStartDay")
        val lastSyncEpochMillis = DeviceRuntimeJson.longValue(data, "lastSyncEpochMillis")
        val lastSyncUptimeMs = DeviceRuntimeJson.longValue(data, "lastSyncUptimeMs")
        require(uptimeMs >= 0L && millisStartDay >= 0L)
        require(lastSyncEpochMillis >= 0L && lastSyncUptimeMs >= 0L)
        return DeviceTimeStatus(
            timeSet = DeviceRuntimeJson.booleanValue(data, "timeSet"),
            timeString = DeviceRuntimeJson.stringValue(data, "timeString"),
            timezoneId = DeviceRuntimeJson.stringValue(data, "timezoneId"),
            posixTimeZone = DeviceRuntimeJson.stringAllowEmpty(data, "posixTimeZone"),
            utcOffsetMinutes = DeviceRuntimeJson.intValue(data, "utcOffsetMinutes"),
            autoSyncNtpEnabled = DeviceRuntimeJson.booleanValue(data, "autoSyncNtpEnabled"),
            autoSyncGadgetEnabled = DeviceRuntimeJson.booleanValue(
                data,
                "autoSyncGadgetEnabled"
            ),
            ntpServerPrimary = DeviceRuntimeJson.stringValue(data, "ntpServerPrimary"),
            ntpServerSecondary = DeviceRuntimeJson.stringValue(data, "ntpServerSecondary"),
            lastSyncSource = DeviceRuntimeJson.stringValue(data, "lastSyncSource"),
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

    private fun validateParts(data: JSONObject) {
        DeviceRuntimeJson.requireExactKeys(data, PART_KEYS, "$STATUS_LABEL.parts")
        require(DeviceRuntimeJson.intValue(data, "year") in 2000..2199)
        require(DeviceRuntimeJson.intValue(data, "month") in 1..12)
        require(DeviceRuntimeJson.intValue(data, "day") in 1..31)
        require(DeviceRuntimeJson.intValue(data, "weekday") in 1..7)
        require(DeviceRuntimeJson.intValue(data, "hour") in 0..23)
        require(DeviceRuntimeJson.intValue(data, "minute") in 0..59)
        require(DeviceRuntimeJson.intValue(data, "second") in 0..59)
    }

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
