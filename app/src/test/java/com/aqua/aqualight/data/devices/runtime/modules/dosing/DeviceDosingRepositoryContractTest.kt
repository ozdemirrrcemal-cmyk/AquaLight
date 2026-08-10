package com.aqua.aqualight.data.devices.runtime.modules.dosing

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToLong
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingRepositoryContractTest {
    @Test
    fun `all eleven actions use correlated results and device isolated state`() = runBlocking {
        val gateway = FixtureGateway()
        val repository = repository(gateway)

        val outcomes = executeBasicActions(repository) +
            executeCalibrationActions(repository) +
            executeConfigActions(repository)

        assertEquals(21, outcomes.size)
        assertTrue(outcomes.all { outcome -> outcome is DeviceRuntimeCommandOutcome.Success })
        assertEquals(ALL_ACTIONS, gateway.actions.toSet())
        assertEquals(DeviceDosingRuntimeContract.Action.STATUS_GET, gateway.actions.first())

        val state = repository.states.value.getValue(DEVICE_UID)
        assertEquals("Macro Pump", state.status?.channels?.first()?.displayName)
        assertEquals("Macro Pump", state.config?.channels?.first()?.displayNameOverride)
        assertEquals(12_100L, state.status?.channels?.first()?.dosing?.lastCalibratedAt)
        assertEquals(500.0, state.status?.channels?.first()?.dosing?.reservoirRemainingMl)
        assertTrue(state.config?.schedules?.isEmpty() == true)
        assertTrue(state.status?.schedules?.isEmpty() == true)
        assertTrue(state.requiresStatusRefresh)

        val finalPayload = gateway.encoded.last()
        assertEquals(setOf("schedules", "save"), finalPayload.keys().asSequence().toSet())
        assertEquals(0, finalPayload.getJSONArray("schedules").length())
    }

    private suspend fun executeBasicActions(
        repository: DeviceDosingRuntimeRepository
    ): List<DeviceRuntimeCommandOutcome<*>> = listOf(
        repository.requestStatus(DEVICE_UID),
        repository.primeStart(DEVICE_UID, "channel1"),
        repository.primeStop(DEVICE_UID, "channel1"),
        repository.doseNow(
            DEVICE_UID,
            DeviceDosingDoseNowPayload("channel1", amountMl = 10.0)
        ),
        repository.doseStop(DEVICE_UID, "channel1")
    )

    @Suppress("LongMethod")
    private suspend fun executeCalibrationActions(
        repository: DeviceDosingRuntimeRepository
    ): List<DeviceRuntimeCommandOutcome<*>> {
        val outcomes = mutableListOf<DeviceRuntimeCommandOutcome<*>>()
        outcomes += repository.calibrationStart(
            DEVICE_UID,
            DeviceDosingCalibrationStartPayload("channel1")
        )
        outcomes += repository.requestStatus(DEVICE_UID)
        outcomes += repository.calibrationFinish(
            DEVICE_UID,
            DeviceDosingCalibrationFinishPayload("channel1", measuredMl = 5.0)
        )
        outcomes += repository.calibrationCancel(DEVICE_UID, "channel1")
        outcomes += repository.calibrationStart(
            DEVICE_UID,
            DeviceDosingCalibrationStartPayload("channel1")
        )
        outcomes += repository.requestStatus(DEVICE_UID)
        outcomes += repository.calibrationFinish(
            DEVICE_UID,
            DeviceDosingCalibrationFinishPayload("channel1", measuredMl = 5.0)
        )
        outcomes += repository.doseNow(
            DEVICE_UID,
            DeviceDosingDoseNowPayload(
                channelKey = "channel1",
                amountMl = DeviceDosingRuntimeContract.Limit.VERIFICATION_DOSE_ML,
                usePendingCalibration = true
            )
        )
        outcomes += repository.requestStatus(DEVICE_UID)
        outcomes += repository.calibrationConfirm(DEVICE_UID, "channel1")
        return outcomes
    }

    private suspend fun executeConfigActions(
        repository: DeviceDosingRuntimeRepository
    ): List<DeviceRuntimeCommandOutcome<*>> = listOf(
        repository.reservoirRefill(DEVICE_UID, "channel1"),
        repository.setChannelDisplayName(DEVICE_UID, "channel1", "Macro Pump"),
        repository.createSchedule(
            DEVICE_UID,
            DeviceDosingRuntimeFixtures.schedulePayload(
                name = "Evening Nutrients",
                startTimeMs = 72_000_000L
            )
        ),
        repository.updateSchedule(
            DEVICE_UID,
            scheduleIndex = 1,
            schedule = DeviceDosingRuntimeFixtures.schedulePayload(
                name = "Late Nutrients",
                startTimeMs = 75_600_000L
            )
        ),
        repository.deleteSchedule(DEVICE_UID, scheduleIndex = 0),
        repository.deleteSchedule(DEVICE_UID, scheduleIndex = 0)
    )

    @Test
    fun `schedule helper loads an exact baseline before full list mutation`() = runBlocking {
        val gateway = FixtureGateway()
        val repository = repository(gateway)

        val outcome = repository.createSchedule(
            DEVICE_UID,
            DeviceDosingRuntimeFixtures.schedulePayload(name = "Second Dose")
        )

        assertTrue(outcome is DeviceRuntimeCommandOutcome.Success)
        assertEquals(listOf("status.get", "config.apply"), gateway.actions)
        assertEquals(2, gateway.encoded.last().getJSONArray("schedules").length())
    }

    private fun repository(
        gateway: DeviceRuntimeCommandGateway
    ) = DeviceDosingRuntimeRepository(
        gateway,
        DeviceDosingRuntimeStateStore()
    ) { SUPPORTED_ACCESS }

    @Suppress("TooManyFunctions")
    private class FixtureGateway : DeviceRuntimeCommandGateway {
        val actions = mutableListOf<String>()
        val encoded = mutableListOf<JSONObject>()
        private var displayNameOverride: String? = "Nutrients"
        private var schedules = JSONArray().put(DeviceDosingRuntimeFixtures.configSchedule())
        private var calibrationDurationMs = 5_000L
        private var doseMsPerMl = 1_000L
        private var lastCalibratedAt = 100L
        private var calibrationState = "idle"
        private var measuredMl = 0.0
        private var pendingDoseMsPerMl = -1L
        private var verificationDoseStarted = false
        private var verificationDoseComplete = false
        private var uptimeMs = 12_000L

        override suspend fun <T> execute(
            deviceUid: DeviceUid,
            command: DeviceRuntimeCommand<T>,
            timeoutMillis: Long
        ): DeviceRuntimeCommandOutcome<T> {
            actions += command.action
            val data = command.encodeData()
            encoded += JSONObject(data.toString())
            val responseData = response(command.action, data)
            val value = command.parseSuccess(
                AqlWsIncomingMessage.Response(
                    id = "res-${command.action}",
                    type = "res",
                    module = command.module,
                    action = command.action,
                    data = responseData,
                    ok = true,
                    statusCode = 200
                )
            )
            return DeviceRuntimeCommandOutcome.Success(
                deviceUid = deviceUid,
                module = command.module,
                action = command.action,
                messageId = "res-${command.action}",
                generation = DeviceRuntimeConnectionGeneration(1L),
                statusCode = 200,
                value = value
            )
        }

        @Suppress("CyclomaticComplexMethod", "LongMethod")
        private fun response(action: String, data: JSONObject): JSONObject = when (action) {
            DeviceDosingRuntimeContract.Action.STATUS_GET -> {
                uptimeMs += 1_000L
                DeviceDosingRuntimeFixtures.status(
                    uptimeMs = uptimeMs,
                    calibrationState = calibrationState,
                    calibrationDurationMs = if (calibrationState == "idle") {
                        0L
                    } else {
                        calibrationDurationMs
                    },
                    measuredMl = measuredMl,
                    pendingDoseMsPerMl = pendingDoseMsPerMl,
                    verificationDoseStarted = verificationDoseStarted,
                    verificationDoseComplete = verificationDoseComplete
                )
            }
            DeviceDosingRuntimeContract.Action.CONFIG_APPLY -> applyConfig(data)
            DeviceDosingRuntimeContract.Action.PRIME_START -> pump(action, active = true)
            DeviceDosingRuntimeContract.Action.PRIME_STOP -> pump(action, active = false)
            DeviceDosingRuntimeContract.Action.DOSE_NOW ->
                DeviceDosingRuntimeFixtures.doseNow(
                    amountMl = data.getDouble("amountMl"),
                    doseMsPerMl = if (data.getBoolean("usePendingCalibration")) {
                        pendingDoseMsPerMl
                    } else {
                        doseMsPerMl
                    },
                    usePendingCalibration = data.getBoolean("usePendingCalibration")
                ).also {
                    if (data.getBoolean("usePendingCalibration")) {
                        verificationDoseStarted = true
                        verificationDoseComplete = true
                    }
                }
            DeviceDosingRuntimeContract.Action.DOSE_STOP -> pump(action, active = false)
            DeviceDosingRuntimeContract.Action.CALIBRATION_START -> {
                calibrationDurationMs = data.getLong("durationMs")
                calibrationState = "running"
                DeviceDosingRuntimeFixtures.calibrationStart(calibrationDurationMs)
            }
            DeviceDosingRuntimeContract.Action.CALIBRATION_FINISH -> {
                measuredMl = data.getDouble("measuredMl")
                pendingDoseMsPerMl =
                    (calibrationDurationMs.toDouble() / measuredMl).roundToLong()
                calibrationState = "pendingVerification"
                DeviceDosingRuntimeFixtures.calibrationFinish(
                    measuredMl = measuredMl,
                    durationMs = calibrationDurationMs
                )
            }
            DeviceDosingRuntimeContract.Action.CALIBRATION_CANCEL -> {
                resetCalibrationSession()
                DeviceDosingRuntimeFixtures.calibrationCancel()
            }
            DeviceDosingRuntimeContract.Action.CALIBRATION_CONFIRM -> confirmCalibration()
            DeviceDosingRuntimeContract.Action.RESERVOIR_REFILL ->
                DeviceDosingRuntimeFixtures.reservoirRefill()
            else -> error("Unexpected Dosing action: $action")
        }

        private fun pump(action: String, active: Boolean): JSONObject =
            DeviceDosingRuntimeFixtures.pump(
                action,
                active,
                displayName = displayNameOverride ?: "Channel 1"
            )

        private fun confirmCalibration(): JSONObject {
            doseMsPerMl = pendingDoseMsPerMl
            lastCalibratedAt = 12_100L
            resetCalibrationSession()
            return DeviceDosingRuntimeFixtures.calibrationConfirm(
                doseMsPerMl,
                lastCalibratedAt
            )
        }

        private fun resetCalibrationSession() {
            calibrationState = "idle"
            measuredMl = 0.0
            pendingDoseMsPerMl = -1L
            verificationDoseStarted = false
            verificationDoseComplete = false
        }

        private fun applyConfig(data: JSONObject): JSONObject {
            data.optJSONArray("channels")?.let { requestedChannels ->
                val channel = requestedChannels.getJSONObject(0)
                if (channel.has("displayName")) {
                    displayNameOverride = channel.getString("displayName").ifBlank { null }
                }
            }
            data.optJSONArray("schedules")?.let { requestedSchedules ->
                schedules = JSONArray(requestedSchedules.toString())
            }
            return DeviceDosingRuntimeFixtures.configApply(
                save = data.getBoolean("save"),
                appliedChannels = data.has("channels"),
                appliedSchedules = data.has("schedules"),
                channelOneDisplayNameOverride = displayNameOverride,
                schedules = JSONArray(schedules.toString()),
                doseMsPerMl = doseMsPerMl,
                lastCalibratedAt = lastCalibratedAt
            )
        }
    }

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-DOSING-REPOSITORY")
        val SUPPORTED_ACCESS = DeviceDosingRuntimeAccess(
            supportsApi = true,
            channelCount = 2,
            supportsSchedules = true,
            supportsPrime = true,
            supportsManualDose = true,
            supportsCalibrationWorkflow = true,
            supportsReservoirRefill = true,
            supportsChannelDisplayName = true
        )
        val ALL_ACTIONS = setOf(
            DeviceDosingRuntimeContract.Action.STATUS_GET,
            DeviceDosingRuntimeContract.Action.CONFIG_APPLY,
            DeviceDosingRuntimeContract.Action.PRIME_START,
            DeviceDosingRuntimeContract.Action.PRIME_STOP,
            DeviceDosingRuntimeContract.Action.CALIBRATION_START,
            DeviceDosingRuntimeContract.Action.CALIBRATION_FINISH,
            DeviceDosingRuntimeContract.Action.CALIBRATION_CONFIRM,
            DeviceDosingRuntimeContract.Action.CALIBRATION_CANCEL,
            DeviceDosingRuntimeContract.Action.DOSE_NOW,
            DeviceDosingRuntimeContract.Action.DOSE_STOP,
            DeviceDosingRuntimeContract.Action.RESERVOIR_REFILL
        )
    }
}
