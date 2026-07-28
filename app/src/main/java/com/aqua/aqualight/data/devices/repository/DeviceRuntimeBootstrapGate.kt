package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceCapabilities
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import org.json.JSONObject

/**
 * Fail-closed, two-phase authenticated bootstrap.
 *
 * Identity and capability responses must both succeed before any optional runtime module is
 * queried. The product family is an additional boundary: an internal dosing timer engine can
 * never expose the standalone timer API to Android.
 */
internal class DeviceRuntimeBootstrapGate {

    private var identityReceived = false
    private var family = DeviceFamily.UNKNOWN
    private var capabilities: DeviceCapabilities? = null
    private var dispatched = false

    fun reset() {
        identityReceived = false
        family = DeviceFamily.UNKNOWN
        capabilities = null
        dispatched = false
    }

    fun accept(response: AqlWsIncomingMessage.Response): DeviceRuntimeBootstrapPlan? {
        if (response.ok && !dispatched) {
            when {
                response.isDeviceAction(AqlWsContract.ACTION_DEVICE_IDENTITY_GET) -> {
                    identityReceived = true
                    family = DeviceFamily.fromWire(response.data.optString("family"))
                }

                response.isDeviceAction(AqlWsContract.ACTION_DEVICE_CAPABILITIES_GET) -> {
                    capabilities = response.data
                        .optJSONObject("capabilities")
                        .toBootstrapCapabilities()
                }
            }
        }

        val reportedCapabilities = capabilities
        return if (!dispatched && identityReceived && reportedCapabilities != null) {
            dispatched = true
            DeviceRuntimeBootstrapPlan.from(
                family = family,
                capabilities = reportedCapabilities
            )
        } else {
            null
        }
    }

    private fun AqlWsIncomingMessage.Response.isDeviceAction(actionName: String): Boolean =
        module == AqlWsContract.MODULE_DEVICE && action == actionName

    private fun JSONObject?.toBootstrapCapabilities(): DeviceCapabilities {
        val source = this ?: JSONObject()
        return DeviceCapabilities(
            light = source.optBoolean("light", false),
            manualLight = source.optBoolean("manualLight", false),
            lightProgram = source.optBoolean("lightProgram", false),
            lightPresets = source.optBoolean("lightPresets", false),
            lightSimulation = source.optBoolean("lightSimulation", false),
            fan = source.optBoolean("fan", false),
            cooling = source.optBoolean("cooling", false),
            temperature = source.optBoolean("temperature", false),
            standaloneTimer = source.optBoolean("standaloneTimer", false),
            dosing = source.optBoolean("dosing", false),
            timeSync = source.optBoolean("timeSync", false),
            ota = source.optBoolean("ota", false)
        )
    }
}

internal data class DeviceRuntimeBootstrapPlan(
    val family: DeviceFamily,
    val requestTimeStatus: Boolean,
    val requestFirmwareStatus: Boolean,
    val requestLightStatus: Boolean,
    val requestCoolingStatus: Boolean,
    val requestTimerStatus: Boolean,
    val requestDosingStatus: Boolean
) {
    companion object {
        fun from(
            family: DeviceFamily,
            capabilities: DeviceCapabilities
        ): DeviceRuntimeBootstrapPlan {
            return DeviceRuntimeBootstrapPlan(
                family = family,
                requestTimeStatus = capabilities.timeSync,
                requestFirmwareStatus = capabilities.ota,
                requestLightStatus = family == DeviceFamily.LIGHT && capabilities.light,
                requestCoolingStatus = when (family) {
                    DeviceFamily.LIGHT,
                    DeviceFamily.COOLING -> {
                        capabilities.cooling ||
                            capabilities.fan ||
                            capabilities.temperature
                    }

                    DeviceFamily.TIMER,
                    DeviceFamily.DOSING,
                    DeviceFamily.UNKNOWN -> false
                },
                requestTimerStatus = family == DeviceFamily.TIMER &&
                    capabilities.standaloneTimer,
                requestDosingStatus = family == DeviceFamily.DOSING &&
                    capabilities.dosing
            )
        }
    }
}
