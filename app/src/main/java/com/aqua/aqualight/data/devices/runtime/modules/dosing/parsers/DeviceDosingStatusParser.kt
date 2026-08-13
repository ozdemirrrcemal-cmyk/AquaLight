package com.aqua.aqualight.data.devices.runtime.modules.dosing.parsers

import com.aqua.aqualight.data.devices.runtime.modules.dosing.contract.DeviceDosingRuntimeContract
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingChannelStatus
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingGlobalStatus
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingStatusChange
import org.json.JSONObject

internal object DeviceDosingStatusParser {
    fun parseGlobal(data: JSONObject): DeviceDosingGlobalStatus {
        data.requireDosingKeys(GLOBAL_STATUS_KEYS, "Dosing global status")
        val envelope = DeviceDosingComponentParsers.parseEnvelope(data)
        val channelsArray = data.requireDosingArray("channels")
        val channels = List(channelsArray.length()) { index ->
            DeviceDosingComponentParsers.parseGlobalSummary(
                channelsArray.get(index) as? JSONObject
                    ?: error("Dosing channels must contain objects.")
            )
        }
        require(channels.size == envelope.channelCount)
        require(channels.map { it.channelKey }.toSet().size == channels.size)
        return DeviceDosingGlobalStatus(
            envelope = envelope,
            scheduling = DeviceDosingComponentParsers.parseScheduling(
                data.requireDosingObject("scheduling")
            ),
            channels = channels,
            runtime = DeviceDosingComponentParsers.parseRuntimeCapabilities(
                data.requireDosingObject("runtime")
            ),
            resources = DeviceDosingComponentParsers.parseResources(
                data.requireDosingObject("resources")
            )
        )
    }

    fun parseChannel(data: JSONObject): DeviceDosingChannelStatus {
        data.requireDosingKeys(CHANNEL_STATUS_KEYS, "Dosing channel-scoped status")
        val envelope = DeviceDosingComponentParsers.parseEnvelope(data)
        val channel = DeviceDosingComponentParsers.parseChannelDetail(
            data.requireDosingObject("channel")
        )
        require(channel.index in 0 until envelope.channelCount)
        return DeviceDosingChannelStatus(
            envelope = envelope,
            scheduling = DeviceDosingComponentParsers.parseScheduling(
                data.requireDosingObject("scheduling")
            ),
            channel = channel
        )
    }

    fun parseStatusChange(data: JSONObject): DeviceDosingStatusChange {
        data.requireDosingKeys(STATUS_CHANGE_KEYS, "Dosing status change event")
        return DeviceDosingStatusChange(
            schema = data.requireDosingText("schema").also {
                require(it == DeviceDosingRuntimeContract.SCHEMA)
            },
            schemaVersion = data.requireDosingInt("schemaVersion").also {
                require(it == DeviceDosingRuntimeContract.SCHEMA_VERSION)
            },
            channelKey = com.aqua.aqualight.data.devices.runtime.modules.dosing.contract
                .normalizeDosingChannelKey(data.requireDosingText("channelKey")),
            revision = data.requireDosingLong(
                "revision",
                minimum = 0L,
                maximum = DeviceDosingRuntimeContract.Limit.MAX_UINT32
            ),
            storageHealthy = data.requireDosingBoolean("storageHealthy"),
            change = DeviceDosingComponentParsers.parseRuntimeEvent(
                data.requireDosingObject("change")
            )
        )
    }

    private val COMMON_STATUS_KEYS = setOf(
        "supported", "schema", "schemaVersion", "unit", "channelCount", "uptimeMs",
        "bootReady", "storageHealthy", "storageIssue"
    )
    private val GLOBAL_STATUS_KEYS = COMMON_STATUS_KEYS + setOf(
        "scheduling", "channels", "runtime", "resources"
    )
    private val CHANNEL_STATUS_KEYS = COMMON_STATUS_KEYS + setOf("scheduling", "channel")
    private val STATUS_CHANGE_KEYS = setOf(
        "schema", "schemaVersion", "channelKey", "revision", "storageHealthy", "change"
    )
}
