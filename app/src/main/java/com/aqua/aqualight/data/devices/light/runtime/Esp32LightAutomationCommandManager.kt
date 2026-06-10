package com.aqua.aqualight.data.devices.light.runtime

import android.content.Context
import com.aqua.aqualight.data.devices.light.automation.model.CloudFrequency
import com.aqua.aqualight.data.devices.light.automation.model.LightAutomationSettings
import com.aqua.aqualight.data.devices.light.automation.model.MoonlightChannel
import org.json.JSONObject

class Esp32LightAutomationCommandManager(
    context: Context,
    private val addressResolver: LightDeviceAddressResolver =
        LightDeviceAddressResolver(context),
    private val httpClient: Esp32HttpJsonClient =
        Esp32HttpJsonClient()
) {

    suspend fun applyAutomationSettings(
        deviceId: Long,
        settings: LightAutomationSettings
    ): LightCommandResult {
        if (deviceId <= 0L) {
            return LightCommandResult.failure("Device information is missing")
        }

        val address = resolveAddress(deviceId)
            ?: return LightCommandResult.failure("Device address could not be resolved")

        val json = buildAutomationJson(
            settings = settings.copy(deviceId = deviceId)
        )

        return httpClient.postSet(
            ip = address.ip,
            json = json,
            requestTag = "light_automation_update"
        )
    }

    private suspend fun resolveAddress(
        deviceId: Long
    ): LightDeviceAddressResolver.Result.Success? {
        return when (
            val result = addressResolver.resolve(
                deviceId = deviceId,
                requireOnline = true,
                forceLiveCheck = true
            )
        ) {
            is LightDeviceAddressResolver.Result.Success -> result
            is LightDeviceAddressResolver.Result.Failure -> null
        }
    }

    private fun buildAutomationJson(
        settings: LightAutomationSettings
    ): String {
        val moonlight = settings.moonlight
        val cloud = settings.cloudSimulation

        val moonlightJson = JSONObject()
            .put("Enabled", moonlight.enabled)
            .put("FollowProgramEnd", moonlight.followProgramEnd)
            .put("StartMinutes", moonlight.startTime.totalMinutes)
            .put("Start", moonlight.startTime.label)
            .put("EndMinutes", moonlight.endTime.totalMinutes)
            .put("End", moonlight.endTime.label)
            .put("Channel", moonlight.channel.toFirmwareValue())
            .put("IntensityPercent", moonlight.intensityPercent.coerceIn(0, 100))

        val cloudJson = JSONObject()
            .put("Enabled", cloud.enabled)
            .put("CoveragePercent", cloud.coveragePercent.coerceIn(0, 100))
            .put("Frequency", cloud.frequency.toFirmwareValue())

        val lightAutomationJson = JSONObject()
            .put("DeviceId", settings.deviceId)
            .put("Moonlight", moonlightJson)
            .put("CloudSimulation", cloudJson)
            .put("UpdatedAt", settings.updatedAt)

        val lightMetaJson = JSONObject()
            .put("DeviceId", settings.deviceId)
            .put("MoonlightEnabled", moonlight.enabled)
            .put("MoonlightFollowProgramEnd", moonlight.followProgramEnd)
            .put("MoonlightStartMinutes", moonlight.startTime.totalMinutes)
            .put("MoonlightStart", moonlight.startTime.label)
            .put("MoonlightEndMinutes", moonlight.endTime.totalMinutes)
            .put("MoonlightEnd", moonlight.endTime.label)
            .put("MoonlightChannel", moonlight.channel.toFirmwareValue())
            .put("MoonlightIntensityPercent", moonlight.intensityPercent.coerceIn(0, 100))
            .put("CloudSimulationEnabled", cloud.enabled)
            .put("CloudSimulationCoveragePercent", cloud.coveragePercent.coerceIn(0, 100))
            .put("CloudSimulationFrequency", cloud.frequency.toFirmwareValue())

        return JSONObject()
            .put("LLightAutomation", lightAutomationJson)
            .put("LLightMeta", lightMetaJson)
            .toString()
    }

    private fun MoonlightChannel.toFirmwareValue(): String {
        return when (this) {
            MoonlightChannel.BLUE -> "blue"
            MoonlightChannel.WHITE -> "white"
            MoonlightChannel.BLUE_WHITE -> "blue_white"
        }
    }

    private fun CloudFrequency.toFirmwareValue(): String {
        return when (this) {
            CloudFrequency.RARE -> "rare"
            CloudFrequency.NORMAL -> "normal"
            CloudFrequency.FREQUENT -> "frequent"
        }
    }
}
