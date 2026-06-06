package com.aqua.aqualight.data.devices.light.runtime

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LightDeviceTimeRepository(
    context: Context,
    private val addressResolver: LightDeviceAddressResolver =
        LightDeviceAddressResolver(context),
    private val timeReader: Esp32LightDeviceTimeReader =
        Esp32LightDeviceTimeReader(),
    private val timeWriter: Esp32LightDeviceTimeWriter =
        Esp32LightDeviceTimeWriter()
) {

    suspend fun readDeviceTime(
        deviceId: Long,
        fallbackToPhone: Boolean = true
    ): LightDeviceTimeState {
        return withContext(Dispatchers.IO) {
            if (deviceId <= 0L) {
                return@withContext fallbackOrThrow(
                    fallbackToPhone = fallbackToPhone,
                    message = "Device information is missing"
                )
            }

            val address = when (
                val result = addressResolver.resolve(
                    deviceId = deviceId,
                    requireOnline = false
                )
            ) {
                is LightDeviceAddressResolver.Result.Success -> result

                is LightDeviceAddressResolver.Result.Failure -> {
                    return@withContext fallbackOrThrow(
                        fallbackToPhone = fallbackToPhone,
                        message = result.message
                    )
                }
            }

            timeReader.readTime(
                ip = address.ip
            ).getOrElse { error ->
                return@withContext fallbackOrThrow(
                    fallbackToPhone = fallbackToPhone,
                    message = error.message ?: "Device time could not be read"
                )
            }
        }
    }

    suspend fun syncDeviceTimeWithPhone(
        deviceId: Long
    ): LightCommandResult {
        return withContext(Dispatchers.IO) {
            if (deviceId <= 0L) {
                return@withContext LightCommandResult.failure(
                    "Device information is missing"
                )
            }

            val address = when (
                val result = addressResolver.resolve(
                    deviceId = deviceId,
                    requireOnline = false
                )
            ) {
                is LightDeviceAddressResolver.Result.Success -> result

                is LightDeviceAddressResolver.Result.Failure -> {
                    return@withContext LightCommandResult.failure(
                        result.message
                    )
                }
            }

            val phoneTime = Esp32LightDeviceTimeReader.phoneFallback()

            timeWriter.writeTime(
                ip = address.ip,
                timeState = phoneTime
            )
        }
    }

    private fun fallbackOrThrow(
        fallbackToPhone: Boolean,
        message: String
    ): LightDeviceTimeState {
        if (fallbackToPhone) {
            return Esp32LightDeviceTimeReader.phoneFallback()
        }

        throw IllegalStateException(message)
    }
}