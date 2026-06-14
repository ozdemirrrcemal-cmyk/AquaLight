package com.aqua.aqualight.data.devices.api

import com.aqua.aqualight.data.devices.api.cooling.CoolingApi
import com.aqua.aqualight.data.devices.api.cooling.LegacyCoolingApi
import com.aqua.aqualight.data.devices.api.cooling.V1CoolingApi
import com.aqua.aqualight.data.devices.api.dosing.DosingApi
import com.aqua.aqualight.data.devices.api.dosing.LegacyDosingApi
import com.aqua.aqualight.data.devices.api.dosing.V1DosingApi
import com.aqua.aqualight.data.devices.api.legacy.LegacyHttpClient
import com.aqua.aqualight.data.devices.api.legacy.LegacyUrlConnectionClient
import com.aqua.aqualight.data.devices.api.light.LegacyLightApi
import com.aqua.aqualight.data.devices.api.light.LightApi
import com.aqua.aqualight.data.devices.api.light.V1LightApi
import com.aqua.aqualight.data.devices.api.model.ApiErrorCode
import com.aqua.aqualight.data.devices.api.model.ApiResult
import com.aqua.aqualight.data.devices.api.model.DeviceIdentity
import com.aqua.aqualight.data.devices.api.timer.LegacyTimerApi
import com.aqua.aqualight.data.devices.api.timer.TimerApi
import com.aqua.aqualight.data.devices.api.timer.V1TimerApi
import com.aqua.aqualight.data.devices.api.v1.V1HttpClient
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCategory

class AquaDeviceApiFactory(
    private val legacyHttpClient: LegacyHttpClient = LegacyUrlConnectionClient(),
    private val v1HttpClient: V1HttpClient = V1HttpClient.NotConnected
) {

    fun create(
        identity: DeviceIdentity,
        connection: AquaDeviceConnection,
        preferredMode: DeviceApiMode? = null
    ): ApiResult<AquaDeviceApi> {
        val mode = preferredMode ?: resolveMode(identity)
        val capabilities = capabilitiesFor(
            identity = identity,
            mode = mode
        )

        return when (identity.category) {
            AquaDeviceCategory.LIGHT -> ApiResult.success(
                AquaLightDeviceApi(
                    identity = identity,
                    connection = connection,
                    mode = mode,
                    capabilities = capabilities,
                    lightApi = createLightApi(mode)
                )
            )

            AquaDeviceCategory.TIMER -> ApiResult.success(
                AquaTimerDeviceApi(
                    identity = identity,
                    connection = connection,
                    mode = mode,
                    capabilities = capabilities,
                    timerApi = createTimerApi(mode)
                )
            )

            AquaDeviceCategory.DOSING -> ApiResult.success(
                AquaDosingDeviceApi(
                    identity = identity,
                    connection = connection,
                    mode = mode,
                    capabilities = capabilities,
                    dosingApi = createDosingApi(mode)
                )
            )

            AquaDeviceCategory.COOLING -> ApiResult.success(
                AquaCoolingDeviceApi(
                    identity = identity,
                    connection = connection,
                    mode = mode,
                    capabilities = capabilities,
                    coolingApi = createCoolingApi(mode)
                )
            )

            else -> ApiResult.failure(
                code = ApiErrorCode.UNSUPPORTED_DEVICE,
                message = "Unsupported device category: ${identity.category}"
            )
        }
    }

    private fun resolveMode(
        identity: DeviceIdentity
    ): DeviceApiMode {
        // Current Light firmware exposes ApiVersion/ProtocolVersion values, but
        // live Light runtime is still served by the legacy /get contract. Keep
        // Light on LEGACY until the dedicated Light V1 endpoints are implemented.
        if (identity.category == AquaDeviceCategory.LIGHT) {
            return DeviceApiMode.LEGACY
        }

        val apiVersion = identity.apiVersion ?: identity.protocolVersion ?: 0
        return if (apiVersion >= 1) {
            DeviceApiMode.V1
        } else {
            DeviceApiMode.LEGACY
        }
    }

    private fun capabilitiesFor(
        identity: DeviceIdentity,
        mode: DeviceApiMode
    ): DeviceApiCapabilities {
        return when (mode) {
            DeviceApiMode.LEGACY -> when (identity.category) {
                AquaDeviceCategory.LIGHT -> DeviceApiCapabilities.LegacyLightDefault
                else -> DeviceApiCapabilities.None
            }
            DeviceApiMode.V1 -> DeviceApiCapabilities.V1Default
        }
    }

    private fun createLightApi(
        mode: DeviceApiMode
    ): LightApi {
        return when (mode) {
            DeviceApiMode.LEGACY -> LegacyLightApi(legacyHttpClient)
            DeviceApiMode.V1 -> V1LightApi(v1HttpClient)
        }
    }

    private fun createTimerApi(
        mode: DeviceApiMode
    ): TimerApi {
        return when (mode) {
            DeviceApiMode.LEGACY -> LegacyTimerApi(legacyHttpClient)
            DeviceApiMode.V1 -> V1TimerApi(v1HttpClient)
        }
    }

    private fun createDosingApi(
        mode: DeviceApiMode
    ): DosingApi {
        return when (mode) {
            DeviceApiMode.LEGACY -> LegacyDosingApi(legacyHttpClient)
            DeviceApiMode.V1 -> V1DosingApi(v1HttpClient)
        }
    }

    private fun createCoolingApi(
        mode: DeviceApiMode
    ): CoolingApi {
        return when (mode) {
            DeviceApiMode.LEGACY -> LegacyCoolingApi(legacyHttpClient)
            DeviceApiMode.V1 -> V1CoolingApi(v1HttpClient)
        }
    }
}
