package com.aqua.aqualight.data.devices.runtime.modules.light

import org.json.JSONArray
import org.json.JSONObject

internal object DeviceLightRuntimeFixtures {
    fun status(product: DeviceLightProduct = DeviceLightProduct.WRGB_PRO_ELITE): JSONObject {
        val supported = product == DeviceLightProduct.WRGB_PRO_ELITE
        val scene = if (supported) {
            JSONObject("""{"redPercent":20,"greenPercent":30,"bluePercent":40,"whitePercent":50}""")
        } else {
            JSONObject("""{"redPercent":20,"greenPercent":30,"bluePercent":40}""")
        }
        return JSONObject()
            .put("schema", "aqualight.light.v1")
            .put("storageVersion", 1)
            .put("productKey", product.wireValue)
            .put("channelScale", 100)
            .put("channels", channelDescriptors(product))
            .put(
                "features",
                JSONObject()
                    .put("acclimation", supported)
                    .put("fanControl", supported)
                    .put("temperatureSensor", supported)
                    .put("thermal", supported)
                    .put("estimatedPower", supported)
                    .put("estimatedColor", supported)
            )
            .put("mode", "MANUAL")
            .put("outputActive", true)
            .put("outputReason", "ACTIVE")
            .put("requested", JSONObject(scene.toString()))
            .put("effective", JSONObject(scene.toString()))
            .put(
                "scales",
                JSONObject().put("acclimation", 1000).put("thermal", 1000).put("powerLimit", 1000)
            )
            .put("electricalDesign", electricalDesign(supported))
            .put("power", power(supported))
            .put("color", color(supported))
            .put("preview", JSONObject().put("active", false).put("remainingMs", 0))
            .put("manual", JSONObject().put("scene", JSONObject(scene.toString())))
            .put("policy", policy(supported))
            .put(
                "scheduler",
                JSONObject()
                    .put("ready", true)
                    .put("reason", "OK")
                    .put("generation", 3)
                    .put("localDate", "2026-09-12")
                    .put("currentWeekdayMask", 2)
                    .put("currentTimeMs", 43_200_000)
            )
            .put(
                "auto",
                JSONObject()
                    .put("revision", 1)
                    .put("programCount", 0)
                    .put("enabledCount", 0)
                    .put("runtimeState", "NOT_SELECTED")
                    .put("activeProgramId", JSONObject.NULL)
            )
            .put(
                "custom",
                JSONObject()
                    .put("revision", 1)
                    .put("installed", false)
                    .put("weekdaysMask", 0)
                    .put("pointCount", 0)
                    .put("runtimeState", "NOT_SELECTED")
            )
            .put("acclimation", acclimation(supported))
            .put(
                "runtime",
                JSONObject()
                    .put("rtcReady", true)
                    .put("physicalChannelCount", product.channelCount)
                    .put("physicalOutputHealthy", true)
                    .put("event", "light.status.changed")
            )
    }

    private fun channelDescriptors(product: DeviceLightProduct): JSONArray {
        val definitions = listOf(
            listOf("red", "redPercent", "Red", 0xFF0000),
            listOf("green", "greenPercent", "Green", 0x00FF00),
            listOf("blue", "bluePercent", "Blue", 0x0000FF),
            listOf("white", "whitePercent", "White", 0xFFFFFF)
        ).take(product.channelCount)
        return JSONArray().also { array ->
            definitions.forEachIndexed { index, definition ->
                array.put(
                    JSONObject()
                        .put("key", definition[0])
                        .put("percentField", definition[1])
                        .put("displayName", definition[2])
                        .put("displayColorRgb", definition[3])
                        .put("order", index)
                )
            }
        }
    }

    private fun electricalDesign(available: Boolean): JSONObject = JSONObject()
        .put("available", available)
        .put("contractRevision", if (available) 2 else JSONObject.NULL)
        .put("fixtureLengthMm", if (available) 1200 else JSONObject.NULL)
        .put("maximumFixtureInputPowerW", if (available) 150 else JSONObject.NULL)
        .put("minimumAdapterContinuousPowerW", if (available) 180 else JSONObject.NULL)
        .put("maximumLedElectricalPowerW", if (available) 130.0 else JSONObject.NULL)
        .put("nominalLedElectricalPowerW", if (available) 129.36 else JSONObject.NULL)

    private fun power(available: Boolean): JSONObject = JSONObject()
        .put("available", available)
        .put("source", if (available) "DESIGN_NOMINAL" else "UNAVAILABLE")
        .put("estimatedLedPowerW", if (available) 62.0 else JSONObject.NULL)
        .put("estimatedFixturePowerAvailable", available)
        .put("estimatedFixturePowerW", if (available) 76.0 else JSONObject.NULL)
        .put("hardLedPowerLimitW", if (available) 130.0 else JSONObject.NULL)
        .put("hardFixturePowerLimitAvailable", available)
        .put("hardFixturePowerLimitW", if (available) 150.0 else JSONObject.NULL)
        .put("ratioAvailable", available)
        .put("ratio", if (available) 0.5 else JSONObject.NULL)
        .put("limited", false)
        .put("limitScale", 1000)
        .put("limitBasis", "NONE")
        .put("modelRevision", if (available) 2 else JSONObject.NULL)

    private fun color(available: Boolean): JSONObject = JSONObject()
        .put("available", available)
        .put("source", if (available) "DESIGN_NOMINAL" else "UNAVAILABLE")
        .put("cieX", if (available) 0.31 else JSONObject.NULL)
        .put("cieY", if (available) 0.33 else JSONObject.NULL)
        .put("cctAvailable", available)
        .put("estimatedCctK", if (available) 5000 else JSONObject.NULL)
        .put("duvAvailable", available)
        .put("duv", if (available) 0.001 else JSONObject.NULL)
        .put(
            "displayRgb",
            if (available) JSONObject().put("red", 120).put("green", 140).put("blue", 160)
            else JSONObject.NULL
        )
        .put("modelRevision", if (available) 2 else JSONObject.NULL)

    private fun policy(acclimationSupported: Boolean): JSONObject = JSONObject()
        .put(
            "auto",
            JSONObject()
                .put("capacity", 16)
                .put("timeStepMs", 60_000)
                .put(
                    "rampDurationsMs",
                    JSONArray(listOf(0, 1_800_000, 3_600_000, 5_400_000, 7_200_000, 9_000_000))
                )
        )
        .put("custom", JSONObject().put("maxPoints", 96).put("timeStepMs", 60_000))
        .put(
            "acclimation",
            JSONObject()
                .put("supported", acclimationSupported)
                .put("startPercentMin", if (acclimationSupported) 20 else JSONObject.NULL)
                .put("startPercentMax", if (acclimationSupported) 90 else JSONObject.NULL)
                .put("startPercentStep", if (acclimationSupported) 5 else JSONObject.NULL)
                .put("defaultStartPercent", if (acclimationSupported) 50 else JSONObject.NULL)
                .put("durationDaysMin", if (acclimationSupported) 7 else JSONObject.NULL)
                .put("durationDaysMax", if (acclimationSupported) 90 else JSONObject.NULL)
                .put("durationDaysStep", if (acclimationSupported) 1 else JSONObject.NULL)
                .put("defaultDurationDays", if (acclimationSupported) 30 else JSONObject.NULL)
                .put("targetPercent", if (acclimationSupported) 100 else JSONObject.NULL)
        )

    private fun acclimation(supported: Boolean): JSONObject = JSONObject()
        .put("supported", supported)
        .put("revision", if (supported) 1 else JSONObject.NULL)
        .put("state", if (supported) "DISABLED" else JSONObject.NULL)
        .put("clockReady", if (supported) true else JSONObject.NULL)
        .put("startPercent", if (supported) 50 else JSONObject.NULL)
        .put("currentPermille", if (supported) 1000 else JSONObject.NULL)
        .put("targetPercent", if (supported) 100 else JSONObject.NULL)
        .put("durationDays", if (supported) 30 else JSONObject.NULL)
        .put("startedAtEpochSeconds", if (supported) 0 else JSONObject.NULL)
        .put("endsAtEpochSeconds", if (supported) 0 else JSONObject.NULL)
        .put("remainingSeconds", if (supported) 0 else JSONObject.NULL)
}
