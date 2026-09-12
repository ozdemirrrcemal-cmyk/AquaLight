package com.aqua.aqualight.data.devices.runtime.modules.light

import org.json.JSONObject

/** Fail-closed parser for the complete firmware `light.status.get.data` document. */
object DeviceLightStatusParser {
    fun parse(data: JSONObject): DeviceLightStatus {
        data.requireLightKeys(STATUS_KEYS, "light.status.get.data")
        require(data.requireLightText("schema") == DeviceLightRuntimeContract.SCHEMA)
        require(data.requireLightInt("storageVersion") == DeviceLightRuntimeContract.STORAGE_VERSION)

        val product = DeviceLightProduct.fromWireExact(data.requireLightText("productKey"))
        val channels = DeviceLightV1JsonParser.parseChannels(
            data.requireLightArray("channels"),
            product
        )
        val features = DeviceLightV1JsonParser.parseFeatures(
            data.requireLightObject("features"),
            product
        )
        val scheduler = DeviceLightV1JsonParser.parseScheduler(
            data.requireLightObject("scheduler")
        )
        val runtime = DeviceLightV1JsonParser.parseRuntime(
            data.requireLightObject("runtime"),
            product
        )
        require(runtime.rtcReady == scheduler.ready)

        return DeviceLightStatus(
            schema = DeviceLightRuntimeContract.SCHEMA,
            storageVersion = DeviceLightRuntimeContract.STORAGE_VERSION,
            product = product,
            channelScale = data.requireLightInt("channelScale").also { require(it == 100) },
            channels = channels,
            features = features,
            mode = DeviceLightMode.fromWireExact(data.requireLightText("mode")),
            outputActive = data.requireLightBoolean("outputActive"),
            outputReason = DeviceLightOutputReason.fromWireExact(
                data.requireLightText("outputReason")
            ),
            requested = DeviceLightV1JsonParser.parseScene(
                data.requireLightObject("requested"),
                product,
                "light.status.requested"
            ),
            effective = DeviceLightV1JsonParser.parseScene(
                data.requireLightObject("effective"),
                product,
                "light.status.effective"
            ),
            scales = DeviceLightV1JsonParser.parseScales(data.requireLightObject("scales")),
            electricalDesign = DeviceLightV1JsonParser.parseElectricalDesign(
                data.requireLightObject("electricalDesign"),
                product
            ),
            power = DeviceLightV1JsonParser.parsePower(
                data.requireLightObject("power"),
                features
            ),
            color = DeviceLightV1JsonParser.parseColor(
                data.requireLightObject("color"),
                features
            ),
            preview = parsePreview(data.requireLightObject("preview")),
            manual = parseManual(data.requireLightObject("manual"), product),
            policy = DeviceLightV1JsonParser.parsePolicy(data.requireLightObject("policy"), product),
            scheduler = scheduler,
            auto = DeviceLightV1JsonParser.parseAutoSummary(data.requireLightObject("auto")),
            custom = DeviceLightV1JsonParser.parseCustomSummary(data.requireLightObject("custom")),
            acclimation = DeviceLightV1JsonParser.parseAcclimation(
                data.requireLightObject("acclimation"),
                product
            ),
            runtime = runtime
        )
    }

    private fun parsePreview(data: JSONObject): DeviceLightPreviewStatus {
        data.requireLightKeys(PREVIEW_KEYS, "light.status.preview")
        return DeviceLightPreviewStatus(
            active = data.requireLightBoolean("active"),
            remainingMs = data.requireLightLong("remainingMs", 0)
        )
    }

    private fun parseManual(
        data: JSONObject,
        product: DeviceLightProduct
    ): DeviceLightManualStatus {
        data.requireLightKeys(MANUAL_KEYS, "light.status.manual")
        return DeviceLightManualStatus(
            DeviceLightV1JsonParser.parseScene(
                data.requireLightObject("scene"),
                product,
                "light.status.manual.scene"
            )
        )
    }

    private val STATUS_KEYS = setOf(
        "schema",
        "storageVersion",
        "productKey",
        "channelScale",
        "channels",
        "features",
        "mode",
        "outputActive",
        "outputReason",
        "requested",
        "effective",
        "scales",
        "electricalDesign",
        "power",
        "color",
        "preview",
        "manual",
        "policy",
        "scheduler",
        "auto",
        "custom",
        "acclimation",
        "runtime"
    )
    private val PREVIEW_KEYS = setOf("active", "remainingMs")
    private val MANUAL_KEYS = setOf("scene")
}
