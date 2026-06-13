package com.aqua.aqualight.data.devices.api.model

sealed class ApiResult<out T> {

    data class Success<T>(
        val value: T
    ) : ApiResult<T>()

    data class Error(
        val error: ApiError
    ) : ApiResult<Nothing>()

    val isSuccess: Boolean
        get() = this is Success<*>

    fun getOrNull(): T? {
        return when (this) {
            is Success -> value
            is Error -> null
        }
    }

    companion object {
        fun <T> success(
            value: T
        ): ApiResult<T> {
            return Success(value)
        }

        fun failure(
            code: ApiErrorCode,
            message: String,
            cause: Throwable? = null
        ): ApiResult<Nothing> {
            return Error(
                ApiError(
                    code = code,
                    message = message,
                    cause = cause
                )
            )
        }

        fun notConnected(
            message: String = "Device API is not connected yet"
        ): ApiResult<Nothing> {
            return failure(
                code = ApiErrorCode.NOT_CONNECTED,
                message = message
            )
        }
    }
}
