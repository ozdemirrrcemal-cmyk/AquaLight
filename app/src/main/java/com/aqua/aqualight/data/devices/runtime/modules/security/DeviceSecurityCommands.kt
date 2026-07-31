package com.aqua.aqualight.data.devices.runtime.modules.security

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import org.json.JSONObject

internal class DeviceSecurityStatusGetCommand(
    private val expectedDeviceUid: DeviceUid
) : DeviceRuntimeCommand<DeviceSecurityStatusResponse> {
    override val module: String = AqlWsContract.MODULE_SECURITY
    override val action: String = AqlWsContract.ACTION_SECURITY_STATUS_GET
    override fun encodeData(): JSONObject = JSONObject()

    override fun parseSuccess(
        response: AqlWsIncomingMessage.Response
    ): DeviceSecurityStatusResponse {
        require(response.statusCode == HTTP_OK)
        return DeviceSecurityParsers.parseStatusResponse(expectedDeviceUid, response.data)
    }
}

internal class DeviceSecurityPairCommand : DeviceRuntimeCommand<DeviceSecurityPairResult> {
    override val module: String = AqlWsContract.MODULE_SECURITY
    override val action: String = AqlWsContract.ACTION_SECURITY_PAIR
    override fun encodeData(): JSONObject = JSONObject()

    override fun parseSuccess(
        response: AqlWsIncomingMessage.Response
    ): DeviceSecurityPairResult {
        require(response.statusCode == HTTP_OK)
        return DeviceSecurityParsers.parsePairResult(response.data)
    }
}

internal class DeviceSecurityOwnershipResetCommand(
    private val expectedDeviceUid: DeviceUid,
    override val action: String
) : DeviceRuntimeCommand<DeviceSecurityOwnershipResetResult> {
    init {
        require(
            action == AqlWsContract.ACTION_SECURITY_UNPAIR ||
                action == AqlWsContract.ACTION_SECURITY_RESET
        )
    }

    override val module: String = AqlWsContract.MODULE_SECURITY
    override fun encodeData(): JSONObject = JSONObject()

    override fun parseSuccess(
        response: AqlWsIncomingMessage.Response
    ): DeviceSecurityOwnershipResetResult {
        require(response.statusCode == HTTP_OK)
        return DeviceSecurityParsers.parseOwnershipResetResult(
            expectedDeviceUid = expectedDeviceUid,
            expectedOperation = action,
            data = response.data
        )
    }
}

private const val HTTP_OK = 200
