package com.aqua.aqualight.data.devices.api.v1

import com.aqua.aqualight.data.devices.api.model.ApiErrorCode
import com.aqua.aqualight.data.devices.api.model.ApiResult
import com.aqua.aqualight.data.devices.api.model.DeviceIdentity
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCatalog
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32

class V1JsonParser {

    fun parseIdentity(payload: String): ApiResult<DeviceIdentity> {
        val data = envelopeData(payload) ?: return ApiResult.failure(
            code = ApiErrorCode.INVALID_RESPONSE,
            message = "Invalid V1 identity envelope"
        )

        val productId = data.optString("productId", "").trim()
        val definition = AquaDeviceCatalog.findByProductId(productId) ?: return ApiResult.failure(
            code = ApiErrorCode.UNSUPPORTED_DEVICE,
            message = "Unsupported AquaLight productId: $productId"
        )

        val deviceUid = data.optString("deviceUid", "").trim()
        if (deviceUid.isBlank()) {
            return ApiResult.failure(
                code = ApiErrorCode.INVALID_RESPONSE,
                message = "V1 identity response does not contain deviceUid"
            )
        }

        return ApiResult.success(
            DeviceIdentity(
                deviceId = stablePositiveId(deviceUid),
                deviceUid = deviceUid,
                macAddress = data.optString("macAddress", "").trim(),
                serialNumber = data.optString("serialNumber", "").trim(),
                shortId = data.optString("shortId", "").trim(),
                productId = definition.productId,
                productKey = definition.productKey,
                category = definition.category,
                productFamily = data.optString("productFamily", definition.productFamily).trim(),
                productLine = data.optString("productLine", definition.productLine).trim(),
                productModel = data.optString("productModel", definition.productModel).trim(),
                displayName = data.optString("displayName", definition.displayName).trim(),
                customName = data.optString("customName", "").trim(),
                skuId = data.optString("skuId", definition.variants.firstOrNull()?.skuId.orEmpty()).trim(),
                skuCode = data.optString("skuCode", definition.variants.firstOrNull()?.skuCode.orEmpty()).trim(),
                hardwareRevision = data.optString("hardwareRevision", "").trim(),
                firmwareVersion = data.optString("firmwareVersion", "").trim(),
                firmwareBuild = data.optString("firmwareBuild", "").trim(),
                apiVersion = data.optNullableInt("apiVersion"),
                protocolVersion = data.optNullableInt("protocolVersion"),
                supportedFeatures = definition.features.map { it.name }.toSet(),
                supportedScreens = definition.screens.map { it.name }.toSet()
            )
        )
    }

    companion object {
        fun envelopeData(payload: String): JSONObject? {
            if (payload.isBlank()) return null
            val root = runCatching { JSONObject(payload) }.getOrNull() ?: return null
            if (!root.optBoolean("ok", false)) return null
            return root.optJSONObject("data")
        }

        fun JSONObject.optNullableInt(key: String): Int? {
            if (!has(key) || isNull(key)) return null
            return runCatching { getInt(key) }.getOrNull()
                ?: optString(key, "").trim().toIntOrNull()
        }

        fun stablePositiveId(value: String): Long {
            val crc = CRC32()
            crc.update(value.toByteArray(StandardCharsets.UTF_8))
            return crc.value.toLong().and(0x7FFFFFFF).coerceAtLeast(1L)
        }
    }
}
