package com.aqua.aqualight.data.devices.runtime.modules.cooling.v1

import org.json.JSONArray
import org.json.JSONObject

/**
 * Projects the already contract-validated status document into the typed fragments consumed by the
 * central Cooling owner. The projection deliberately reuses the strict V1 response parsers instead
 * of introducing a second permissive JSON model.
 */
internal fun DeviceCoolingV1StatusDocument.toConfigSnapshot(): DeviceCoolingV1ConfigSnapshot =
    DeviceCoolingV1ResponseParser.parseConfigApply(
        JSONObject()
            .put("command", "cooling.config.apply")
            .put("operation", "configApply")
            .put("saved", true)
            .put("event", DeviceCoolingV1Contract.Event.STATUS_CHANGED)
            .put("config", JSONObject(data.getJSONObject("config").toString()))
    ).config

/**
 * Status contains the same live control truth as `cooling.telemetry.changed`, but status sensor
 * entries additionally expose `present`. Normalize that one status-only field and run the result
 * through the strict telemetry parser so initial hydration and later events share one typed model.
 */
internal fun DeviceCoolingV1StatusDocument.toTelemetrySnapshot(): DeviceCoolingV1Telemetry {
    val config = data.getJSONObject("config")
    val program = data.getJSONObject("program")
    val control = data.getJSONObject("control")
    val statusTelemetry = data.getJSONObject("telemetry")
    val eventSensors = JSONArray().also { target ->
        val source = statusTelemetry.getJSONArray("sensors")
        for (index in 0 until source.length()) {
            target.put(
                JSONObject(source.getJSONObject(index).toString()).apply {
                    remove("present")
                }
            )
        }
    }

    val normalized = JSONObject()
        .put("schema", schema)
        .put("schemaVersion", schemaVersion)
        .put("catalogSha256", catalogSha256)
        .put("configRevision", configRevision)
        .put("programRevision", programRevision)
        .put("uptimeMs", uptimeMs)
        .put("decisionSequence", control.getLong("decisionSequence"))
        .put("evaluatedAtMs", control.getLong("evaluatedAtMs"))
        .put("inputSampleSequence", control.getLong("inputSampleSequence"))
        .put("timeGeneration", control.getLong("timeGeneration"))
        .put("controlMode", control.getString("controlMode"))
        .put("operatingState", control.getString("operatingState"))
        .put("controlReason", control.getString("controlReason"))
        .put("manualActive", control.getBoolean("manualActive"))
        .put("manualTargetPercent", config.getDouble("manualTargetPercent"))
        .put("clockReady", program.getBoolean("clockReady"))
        .putNullable("currentMinuteOfDay", program.optNullableInt("currentMinuteOfDay"))
        .putNullable("activeProgramSlotIndex", program.optNullableInt("activeSlotIndex"))
        .put("sensors", eventSensors)
        .put("fan", JSONObject(statusTelemetry.getJSONObject("fan").toString()))
        .put("power", JSONObject(statusTelemetry.getJSONObject("power").toString()))
        .put("alarms", JSONArray(data.getJSONArray("alarms").toString()))
        .put("healthSummary", JSONObject(data.getJSONObject("healthSummary").toString()))

    return DeviceCoolingV1ResponseParser.parseTelemetry(normalized)
}

private fun JSONObject.optNullableInt(key: String): Int? =
    if (isNull(key)) null else getInt(key)

private fun JSONObject.putNullable(key: String, value: Int?): JSONObject =
    put(key, value ?: JSONObject.NULL)
