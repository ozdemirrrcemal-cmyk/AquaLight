package com.aqua.aqualight.data.devices.api.v1

import com.aqua.aqualight.data.devices.api.model.ApiErrorCode
import com.aqua.aqualight.data.devices.api.model.ApiResult
import com.aqua.aqualight.data.devices.api.model.DeviceIdentity

class V1JsonParser {

    fun parseIdentity(
        payload: String
    ): ApiResult<DeviceIdentity> {
        if (payload.isBlank()) {
            return ApiResult.failure(
                code = ApiErrorCode.INVALID_RESPONSE,
                message = "Empty V1 identity payload"
            )
        }

        return ApiResult.failure(
            code = ApiErrorCode.PARSE,
            message = "V1 identity parser is not implemented yet"
        )
    }
}
