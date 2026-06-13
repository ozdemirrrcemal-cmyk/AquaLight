package com.aqua.aqualight.data.devices.api.legacy

import com.aqua.aqualight.data.devices.api.model.ApiErrorCode
import com.aqua.aqualight.data.devices.api.model.ApiResult

class LegacyGetParser {

    fun parseKeyValuePayload(
        payload: String
    ): ApiResult<Map<String, String>> {
        if (payload.isBlank()) {
            return ApiResult.failure(
                code = ApiErrorCode.INVALID_RESPONSE,
                message = "Empty legacy payload"
            )
        }

        val values = payload
            .split('&', '\n', ';')
            .mapNotNull { token ->
                val parts = token.split('=', limit = 2)
                val key = parts.getOrNull(0)?.trim().orEmpty()
                val value = parts.getOrNull(1)?.trim().orEmpty()

                if (key.isBlank()) {
                    null
                } else {
                    key to value
                }
            }
            .toMap()

        return ApiResult.success(values)
    }
}
