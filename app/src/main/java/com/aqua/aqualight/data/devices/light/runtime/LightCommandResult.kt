package com.aqua.aqualight.data.devices.light.runtime

data class LightCommandResult(
    val isSuccess: Boolean,
    val message: String? = null
) {
    companion object {
        fun success(
            message: String? = null
        ): LightCommandResult {
            return LightCommandResult(
                isSuccess = true,
                message = message
            )
        }

        fun failure(
            message: String
        ): LightCommandResult {
            return LightCommandResult(
                isSuccess = false,
                message = message
            )
        }
    }
}