package com.aqua.aqualight.data.devices.light.runtime

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.TimeZone

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
                    requireOnline = true
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
                    requireOnline = true
                )
            ) {
                is LightDeviceAddressResolver.Result.Success -> result

                is LightDeviceAddressResolver.Result.Failure -> {
                    return@withContext LightCommandResult.failure(
                        result.message
                    )
                }
            }

            val phoneTime =
                Esp32LightDeviceTimeReader.phoneFallback()

            val offsetMinutes =
                currentPhoneTimeZoneOffsetMinutes()

            val protocol =
                resolveTimeSyncProtocol()

            timeWriter.syncClock(
                ip = address.ip,
                timeState = phoneTime,
                timeZoneOffsetMinutes = offsetMinutes,
                protocol = protocol
            )
        }
    }

    private fun resolveTimeSyncProtocol(): LightDeviceTimeSyncProtocol {
        return LightDeviceTimeSyncProtocol.LEGACY_HOUR_TIME_ZONE
    }

    private fun currentPhoneTimeZoneOffsetMinutes(): Int {
        val timeZone = TimeZone.getDefault()
        val now = System.currentTimeMillis()

        return (timeZone.getOffset(now) / ONE_MINUTE_MILLIS)
            .coerceIn(
                MIN_TIME_ZONE_OFFSET_MINUTES,
                MAX_TIME_ZONE_OFFSET_MINUTES
            )
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

    companion object {
        private const val ONE_MINUTE_MILLIS = 60_000

        private const val MIN_TIME_ZONE_OFFSET_MINUTES = -12 * 60
        private const val MAX_TIME_ZONE_OFFSET_MINUTES = 14 * 60
    }
}