package com.aqua.aqualight.data.devices.runtime.modules.dosing.parsers

import com.aqua.aqualight.data.devices.runtime.modules.dosing.contract.DOSING_DEVICE_UPTIME_MAX_MS
import com.aqua.aqualight.data.devices.runtime.modules.dosing.contract.DOSING_MIN_COUNT
import com.aqua.aqualight.data.devices.runtime.modules.dosing.contract.DOSING_NON_NEGATIVE_LONG
import com.aqua.aqualight.data.devices.runtime.modules.dosing.contract.DeviceDosingRuntimeContract
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingChannelStatus
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingScheduleStatus
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingStatus
import org.json.JSONArray
import org.json.JSONObject

object DeviceDosingStatusParser {
    private val KEYS = setOf(
        "supported", "channelCount", "scheduleCount", "lockLoop", "schema", "rootName",
        "unit", "uptimeMs", "channels", "schedules", "runtime"
    )

    fun parse(data: JSONObject): DeviceDosingStatus {
        data.requireDosingKeys(KEYS, "Dosing status")
        val channels = parseChannels(data.requireDosingArray("channels"))
        val schedules = parseSchedules(data.requireDosingArray("schedules"))
        return DeviceDosingStatus(
            supported = data.requireDosingBoolean("supported"),
            channelCount = data.requireDosingInt(
                "channelCount",
                DOSING_MIN_COUNT,
                DeviceDosingRuntimeContract.Limit.MAX_CHANNELS
            ),
            scheduleCount = data.requireDosingInt(
                "scheduleCount",
                DOSING_MIN_COUNT,
                DeviceDosingRuntimeContract.Limit.MAX_SCHEDULES
            ),
            lockLoop = data.requireDosingBoolean("lockLoop"),
            schema = data.requireDosingText("schema"),
            rootName = data.requireDosingText("rootName"),
            unit = data.requireDosingText("unit"),
            uptimeMs = data.requireDosingLong(
                "uptimeMs",
                DOSING_NON_NEGATIVE_LONG,
                DOSING_DEVICE_UPTIME_MAX_MS
            ),
            channels = channels,
            schedules = schedules,
            runtime = DeviceDosingRuntimeCapabilitiesParser.parse(
                data.requireDosingObject("runtime")
            )
        ).also(::validate)
    }

    private fun parseChannels(data: JSONArray): List<DeviceDosingChannelStatus> =
        List(data.length()) { index ->
            DeviceDosingChannelParser.parseStatus(data.requireDosingObject(index))
        }

    private fun parseSchedules(data: JSONArray): List<DeviceDosingScheduleStatus> =
        List(data.length()) { index ->
            DeviceDosingScheduleParser.parse(data.requireDosingObject(index))
        }

    private fun validate(status: DeviceDosingStatus) {
        require(status.supported)
        require(status.schema == DeviceDosingRuntimeContract.Literal.STATUS_SCHEMA)
        require(status.rootName == DeviceDosingRuntimeContract.Literal.STATUS_ROOT)
        require(status.unit == DeviceDosingRuntimeContract.Literal.UNIT_ML)
        require(status.channelCount == status.channels.size)
        require(status.scheduleCount == status.schedules.size)
        require(status.channelCount > 0)
        require(
            status.channels.map(DeviceDosingChannelStatus::index) == status.channels.indices.toList()
        )
        require(
            status.schedules.map(DeviceDosingScheduleStatus::index) ==
                status.schedules.indices.toList()
        )
        require(
            status.channels.map(DeviceDosingChannelStatus::key).distinct().size ==
                status.channels.size
        )

        val channelsByKey = status.channels.associateBy(DeviceDosingChannelStatus::key)
        status.schedules.forEach { schedule ->
            val channel = requireNotNull(channelsByKey[schedule.channelKey])
            require(schedule.bound)
            val durationReady =
                !(channel.dosing.doseMsPerMl <= 1L && schedule.intervalOnMs < 1L) &&
                    !(channel.dosing.doseMsPerMl >= 1L && schedule.amountMl <= 0.0)
            val expectedRuntimeEnabled = schedule.enabled &&
                channel.dosing.calibrated &&
                schedule.weekdays.any { selected -> selected } &&
                schedule.repeatCount > 0 &&
                durationReady
            require(schedule.runtimeEnabled == expectedRuntimeEnabled)
        }
    }
}
