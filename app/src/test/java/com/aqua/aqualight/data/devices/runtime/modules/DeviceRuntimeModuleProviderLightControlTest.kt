package com.aqua.aqualight.data.devices.runtime.modules

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeEventPayload
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeTypedEvent
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeContract
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerRuntimeAccess
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceRuntimeModuleProviderLightControlTest {

    @Test
    fun `control set command event does not duplicate synchronous Light readback`() = runBlocking {
        val gateway = RejectingGateway()
        val provider = DeviceRuntimeModuleProvider(
            commandGateway = gateway,
            revokeLocalCredential = { Result.success(Unit) },
            timerAccessProvider = { DeviceTimerRuntimeAccess.UNAVAILABLE }
        )

        provider.acceptTypedRuntimeEvent(
            DeviceRuntimeTypedEvent(
                deviceUid = DEVICE_UID,
                generation = GENERATION,
                messageId = "evt-control-set",
                type = DeviceRuntimeTypedEvent.Type.LIGHT_STATUS_CHANGED,
                payload = DeviceRuntimeEventPayload.CommandResult(
                    commandId = "cmd-control-set",
                    commandModule = DeviceLightRuntimeContract.MODULE,
                    commandAction = DeviceLightRuntimeContract.Action.CONTROL_SET,
                    sessionId = "session-1",
                    publishedAtMillis = 1L,
                    result = JSONObject()
                )
            )
        )

        assertEquals(0, gateway.calls)
    }

    private class RejectingGateway : DeviceRuntimeCommandGateway {
        var calls = 0

        override suspend fun <T> execute(
            deviceUid: DeviceUid,
            command: DeviceRuntimeCommand<T>,
            timeoutMillis: Long
        ): DeviceRuntimeCommandOutcome<T> {
            calls += 1
            error("control.set invalidation must not start a second Light readback")
        }
    }

    private companion object {
        val DEVICE_UID = DeviceUid("light-control-test")
        val GENERATION = DeviceRuntimeConnectionGeneration(1L)
    }
}
