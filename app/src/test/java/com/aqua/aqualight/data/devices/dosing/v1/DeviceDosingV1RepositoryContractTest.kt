package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("LongMethod", "MagicNumber")
class DeviceDosingV1RepositoryContractTest {
    @Test
    fun `repository mirrors all fourteen actions without production state ownership`() = runBlocking {
        val gateway = FixtureGateway()
        val repository = DeviceDosingV1Repository(gateway)
        val program = DeviceDosingV1Program(
            enabled = true,
            weekdays = DeviceDosingV1Weekdays(List(7) { true }),
            config = DeviceDosingV1ProgramConfig.Hourly24(
                dailyDose = DeviceDosingV1Amount.fromMilliliters(2.4),
                startTimeMillis = 36_900_000
            ),
            missedDoseRecoveryEnabled = false
        )
        val outcomes: List<DeviceRuntimeCommandOutcome<*>> = listOf(
            repository.requestGlobalStatus(DEVICE_UID),
            repository.requestChannelStatus(DEVICE_UID, CHANNEL),
            repository.requestProgress(DEVICE_UID, CHANNEL),
            repository.applyConfig(
                DEVICE_UID,
                DeviceDosingV1ConfigApplyRequest(
                    channelKey = CHANNEL,
                    expectedRevision = 7,
                    displayName = DeviceDosingV1DisplayNameUpdate.Set("Macro"),
                    reservoir = DeviceDosingV1ReservoirUpdate(
                        trackingEnabled = true,
                        capacity = DeviceDosingV1Amount.fromMilliliters(500.0)
                    )
                )
            ),
            repository.applyProgram(
                DEVICE_UID,
                DeviceDosingV1ProgramApplyRequest(CHANNEL, 7, program)
            ),
            repository.resetChannel(
                DEVICE_UID,
                DeviceDosingV1ChannelResetRequest(CHANNEL, 7)
            ),
            repository.startPrime(DEVICE_UID, CHANNEL),
            repository.stopPrime(DEVICE_UID, CHANNEL),
            repository.startCalibration(
                DEVICE_UID,
                DeviceDosingV1CalibrationStartRequest(CHANNEL, 5_000)
            ),
            repository.finishCalibration(
                DEVICE_UID,
                DeviceDosingV1CalibrationFinishRequest(CHANNEL, 4.0)
            ),
            repository.confirmCalibration(DEVICE_UID, CHANNEL),
            repository.cancelCalibration(DEVICE_UID, CHANNEL),
            repository.doseNow(
                DEVICE_UID,
                DeviceDosingV1DoseNowRequest(
                    channelKey = CHANNEL,
                    amount = DeviceDosingV1Amount.fromMilliliters(4.0),
                    usePendingCalibration = true
                )
            ),
            repository.stopDose(DEVICE_UID, CHANNEL),
            repository.refillReservoir(DEVICE_UID, CHANNEL)
        )

        assertTrue(outcomes.all { outcome -> outcome is DeviceRuntimeCommandOutcome.Success })
        assertEquals(
            listOf(DeviceDosingV1Contract.Action.STATUS_GET) +
                DeviceDosingV1Contract.Action.ALL.toList(),
            gateway.calls.map(Call::action)
        )
        assertTrue(gateway.calls.all { call -> call.module == DeviceDosingV1Contract.MODULE })
        assertEquals(
            listOf(
                emptySet(),
                setOf("channelKey"),
                setOf("channelKey"),
                setOf("channelKey", "expectedRevision", "displayName", "reservoir"),
                setOf("channelKey", "expectedRevision", "program"),
                setOf("channelKey", "expectedRevision"),
                setOf("channelKey"),
                setOf("channelKey"),
                setOf("channelKey", "durationMs"),
                setOf("channelKey", "measuredMl"),
                setOf("channelKey"),
                setOf("channelKey"),
                setOf("channelKey", "amountMl", "usePendingCalibration"),
                setOf("channelKey"),
                setOf("channelKey")
            ),
            gateway.calls.map { call -> call.data.keys().asSequence().toSet() }
        )
    }

    @Test
    fun `firmware error identity passes through without blind retry`() = runBlocking {
        val expected = DeviceRuntimeCommandOutcome.FirmwareError(
            deviceUid = DEVICE_UID,
            module = DeviceDosingV1Contract.MODULE,
            action = DeviceDosingV1Contract.Action.PROGRAM_APPLY,
            messageId = "error-1",
            generation = DeviceRuntimeConnectionGeneration(4L),
            statusCode = 422,
            code = "INVALID_VALUE",
            field = "expectedRevision",
            message = "stale revision"
        )
        val gateway = FirmwareErrorGateway(expected)
        val outcome = DeviceDosingV1Repository(gateway).applyProgram(
            DEVICE_UID,
            DeviceDosingV1ProgramApplyRequest(
                channelKey = CHANNEL,
                expectedRevision = 3,
                program = DeviceDosingV1Program(
                    enabled = false,
                    weekdays = DeviceDosingV1Weekdays(List(7) { true }),
                    config = DeviceDosingV1ProgramConfig.Single(
                        dailyDose = DeviceDosingV1Amount.fromMilliliters(1.0),
                        startTimeMillis = 0
                    )
                )
            )
        )

        assertSame(expected, outcome)
        assertEquals(1, gateway.executionCount)
    }

    private class FixtureGateway : DeviceRuntimeCommandGateway {
        val calls = mutableListOf<Call>()

        override suspend fun <T> execute(
            deviceUid: DeviceUid,
            command: DeviceRuntimeCommand<T>,
            timeoutMillis: Long
        ): DeviceRuntimeCommandOutcome<T> {
            val encoded = command.encodeData()
            calls += Call(command.module, command.action, encoded)
            val responseId = "response-" + calls.size
            val value = command.parseSuccess(
                AqlWsIncomingMessage.Response(
                    id = responseId,
                    type = "res",
                    module = command.module,
                    action = command.action,
                    data = response(command.action, encoded),
                    ok = true,
                    statusCode = 200
                )
            )
            return DeviceRuntimeCommandOutcome.Success(
                deviceUid = deviceUid,
                module = command.module,
                action = command.action,
                messageId = responseId,
                generation = DeviceRuntimeConnectionGeneration(1L),
                statusCode = 200,
                value = value
            )
        }

        @Suppress("CyclomaticComplexMethod") // One exhaustive fake response per firmware action.
        private fun response(action: String, encoded: JSONObject): JSONObject = when (action) {
            DeviceDosingV1Contract.Action.STATUS_GET ->
                if (encoded.has("channelKey")) {
                    DeviceDosingV1TestFixtures.channelStatus()
                } else {
                    DeviceDosingV1TestFixtures.globalStatus()
                }
            DeviceDosingV1Contract.Action.PROGRESS_GET ->
                DeviceDosingV1TestFixtures.progressStatus()
            DeviceDosingV1Contract.Action.CONFIG_APPLY ->
                DeviceDosingV1TestFixtures.savedMutation(
                    DeviceDosingV1Contract.Literal.CHANNEL_CONFIG_APPLY
                )
            DeviceDosingV1Contract.Action.PROGRAM_APPLY ->
                DeviceDosingV1TestFixtures.savedMutation(
                    DeviceDosingV1Contract.Literal.PROGRAM_APPLY
                )
            DeviceDosingV1Contract.Action.CHANNEL_RESET ->
                DeviceDosingV1TestFixtures.savedMutation(
                    DeviceDosingV1Contract.Literal.CHANNEL_RESET
                )
            DeviceDosingV1Contract.Action.PRIME_START -> DeviceDosingV1TestFixtures.primeStart()
            DeviceDosingV1Contract.Action.PRIME_STOP ->
                DeviceDosingV1TestFixtures.simpleStop(DeviceDosingV1Contract.Literal.PRIME_STOP)
            DeviceDosingV1Contract.Action.CALIBRATION_START ->
                DeviceDosingV1TestFixtures.calibrationStart()
            DeviceDosingV1Contract.Action.CALIBRATION_FINISH ->
                DeviceDosingV1TestFixtures.calibrationFinish()
            DeviceDosingV1Contract.Action.CALIBRATION_CONFIRM ->
                DeviceDosingV1TestFixtures.calibrationConfirm()
            DeviceDosingV1Contract.Action.CALIBRATION_CANCEL ->
                DeviceDosingV1TestFixtures.calibrationCancel()
            DeviceDosingV1Contract.Action.DOSE_NOW -> DeviceDosingV1TestFixtures.doseNow()
            DeviceDosingV1Contract.Action.DOSE_STOP ->
                DeviceDosingV1TestFixtures.simpleStop(DeviceDosingV1Contract.Literal.DOSE_STOP)
            DeviceDosingV1Contract.Action.RESERVOIR_REFILL ->
                DeviceDosingV1TestFixtures.reservoirRefill()
            else -> error("Unexpected Dosing action: " + action)
        }
    }

    private class FirmwareErrorGateway(
        private val error: DeviceRuntimeCommandOutcome.FirmwareError
    ) : DeviceRuntimeCommandGateway {
        var executionCount = 0

        override suspend fun <T> execute(
            deviceUid: DeviceUid,
            command: DeviceRuntimeCommand<T>,
            timeoutMillis: Long
        ): DeviceRuntimeCommandOutcome<T> {
            executionCount += 1
            return error
        }
    }

    private data class Call(
        val module: String,
        val action: String,
        val data: JSONObject
    )

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-DOSE-PRO-2-TEST")
        val CHANNEL = DeviceDosingV1ChannelKey.from("channel1")
    }
}
