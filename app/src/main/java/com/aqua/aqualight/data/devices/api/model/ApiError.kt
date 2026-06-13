package com.aqua.aqualight.data.devices.api.model

data class ApiError(
    val code: ApiErrorCode,
    val message: String,
    val cause: Throwable? = null
)

enum class ApiErrorCode {
    NOT_CONNECTED,
    UNSUPPORTED_DEVICE,
    UNSUPPORTED_FIRMWARE,
    TIMEOUT,
    NETWORK,
    PARSE,
    INVALID_RESPONSE,
    INVALID_REQUEST,
    UNKNOWN
}
