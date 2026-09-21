package com.aqua.aqualight.data.devices.runtime.modules.light

import org.json.JSONObject

/** Fail-closed parser for the complete firmware `light.status.get.data` document. */
object DeviceLightStatusParser {
    fun parse(
        data: JSONObject,
        managedAutoPlanSupported: Boolean = true
    ): DeviceLightStatus {
        data.requireLightKeys(
            if (managedAutoPlanSupported) STATUS_KEYS_MANAGED_PLAN else STATUS_KEYS_BASE,
            "light.status.get.data"
        )
        require(data.requireLightText("schema") == DeviceLightRuntimeContract.SCHEMA)
        require(data.requireLightInt("storageVersion") == DeviceLightRuntimeContract.STORAGE_VERSION)

        val product = DeviceLightProduct.fromWireExact(data.requireLightText("productKey"))
        val features = DeviceLightV1JsonParser.parseFeatures(
            data.requireLightObject("features"),
            product
        )
        val scheduler = DeviceLightV1JsonParser.Policy.parseScheduler(
            data.requireLightObject("scheduler")
        )
        val status = parseStatus(
            data = data,
            product = product,
            features = features,
            scheduler = scheduler,
            managedAutoPlanSupported = managedAutoPlanSupported
        )
        require(status.runtime.rtcReady == status.scheduler.ready)
        return status
    }

    private fun parseStatus(
        data: JSONObject,
        product: DeviceLightProduct,
        features: DeviceLightFeatures,
        scheduler: DeviceLightSchedulerStatus,
        managedAutoPlanSupported: Boolean
    ): DeviceLightStatus = DeviceLightStatus(
            schema = DeviceLightRuntimeContract.SCHEMA,
            storageVersion = DeviceLightRuntimeContract.STORAGE_VERSION,
            storageGeneration = if (managedAutoPlanSupported) {
                data.requireLightLong(
                    "storageGeneration",
                    0,
                    DeviceLightRuntimeContract.Limit.UINT32_MAX
                )
            } else {
                0L
            },
            product = product,
            channelScale = data.requireLightInt("channelScale").also {
                require(it == DeviceLightRuntimeContract.Limit.PERCENT_MAX)
            },
            channels = parseChannels(data, product),
            features = features,
            mode = DeviceLightMode.fromWireExact(data.requireLightText("mode")),
            outputActive = data.requireLightBoolean("outputActive"),
            outputReason = DeviceLightOutputReason.fromWireExact(
                data.requireLightText("outputReason")
            ),
            requested = parseScene(data, "requested", product),
            effective = parseScene(data, "effective", product),
            scales = DeviceLightV1JsonParser.parseScales(data.requireLightObject("scales")),
            electricalDesign = DeviceLightV1JsonParser.Metrics.parseElectricalDesign(
                data.requireLightObject("electricalDesign"),
                product
            ),
            power = DeviceLightV1JsonParser.Metrics.parsePower(
                data.requireLightObject("power"),
                features
            ),
            color = DeviceLightV1JsonParser.Metrics.parseColor(
                data.requireLightObject("color"),
                features
            ),
            preview = parsePreview(data.requireLightObject("preview")),
            manual = parseManual(data.requireLightObject("manual"), product),
            policy = DeviceLightV1JsonParser.Policy.parsePolicy(
                data = data.requireLightObject("policy"),
                product = product,
                managedAutoPlanSupported = managedAutoPlanSupported
            ),
            scheduler = scheduler,
            auto = DeviceLightV1JsonParser.Activity.parseAutoSummary(
                data = data.requireLightObject("auto"),
                managedAutoPlanSupported = managedAutoPlanSupported
            ),
            custom = DeviceLightV1JsonParser.Activity.parseCustomSummary(
                data.requireLightObject("custom")
            ),
            acclimation = DeviceLightV1JsonParser.Activity.parseAcclimation(
                data.requireLightObject("acclimation"),
                product
            ),
            runtime = DeviceLightV1JsonParser.parseRuntime(
                data.requireLightObject("runtime"),
                product
            )
        )

    private fun parseChannels(
        data: JSONObject,
        product: DeviceLightProduct
    ): List<DeviceLightChannelDescriptor> = DeviceLightV1JsonParser.parseChannels(
        data.requireLightArray("channels"),
        product
    )

    private fun parseScene(
        data: JSONObject,
        key: String,
        product: DeviceLightProduct
    ): DeviceLightScene = DeviceLightV1JsonParser.parseScene(
        data.requireLightObject(key),
        product,
        "light.status.$key"
    )

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

    private val STATUS_KEYS_BASE = setOf(
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
    private val STATUS_KEYS_MANAGED_PLAN = STATUS_KEYS_BASE + "storageGeneration"
    private val PREVIEW_KEYS = setOf("active", "remainingMs")
    private val MANUAL_KEYS = setOf("scene")
}
