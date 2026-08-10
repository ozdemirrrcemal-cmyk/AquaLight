package com.aqua.aqualight.data.devices.runtime.modules.dosing

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingMutationContractTest {
    @Test
    fun `all ten Dosing mutation result schemas parse exactly`() {
        val results = listOf(
            DeviceDosingMutationParser.parseConfigApply(
                DeviceDosingRuntimeFixtures.configApply()
            ),
            DeviceDosingMutationParser.parsePrimeStart(
                DeviceDosingRuntimeFixtures.pump(
                    DeviceDosingRuntimeContract.Action.PRIME_START,
                    active = true
                )
            ),
            DeviceDosingMutationParser.parsePrimeStop(
                DeviceDosingRuntimeFixtures.pump(
                    DeviceDosingRuntimeContract.Action.PRIME_STOP,
                    active = false
                )
            ),
            DeviceDosingMutationParser.parseCalibrationStart(
                DeviceDosingRuntimeFixtures.calibrationStart()
            ),
            DeviceDosingMutationParser.parseCalibrationFinish(
                DeviceDosingRuntimeFixtures.calibrationFinish()
            ),
            DeviceDosingMutationParser.parseCalibrationConfirm(
                DeviceDosingRuntimeFixtures.calibrationConfirm()
            ),
            DeviceDosingMutationParser.parseCalibrationCancel(
                DeviceDosingRuntimeFixtures.calibrationCancel()
            ),
            DeviceDosingMutationParser.parseDoseNow(DeviceDosingRuntimeFixtures.doseNow()),
            DeviceDosingMutationParser.parseDoseStop(
                DeviceDosingRuntimeFixtures.pump(
                    DeviceDosingRuntimeContract.Action.DOSE_STOP,
                    active = false
                )
            ),
            DeviceDosingMutationParser.parseReservoirRefill(
                DeviceDosingRuntimeFixtures.reservoirRefill()
            )
        )

        assertEquals(10, results.size)
        assertTrue(results.all { result -> result.command.startsWith("dosing.") })
    }

    @Test
    fun `config result validates exact request echo and full schedule replacement`() {
        val requestedSchedule = DeviceDosingRuntimeFixtures.schedulePayload(
            name = "Evening Nutrients",
            startTimeMs = 72_000_000L,
            amountMl = 7.5
        )
        val payload = DeviceDosingConfigApplyPayload(
            channels = listOf(
                DeviceDosingChannelConfig("channel1", displayName = "Macro Pump")
            ),
            schedules = listOf(requestedSchedule),
            save = true
        )
        val result = DeviceDosingMutationParser.parseConfigApply(
            DeviceDosingRuntimeFixtures.configApply(
                channelOneDisplayNameOverride = "Macro Pump",
                schedules = JSONArray().put(
                    DeviceDosingRuntimeFixtures.configSchedule(
                        name = "Evening Nutrients",
                        startTimeMs = 72_000_000L,
                        amountMl = 7.5
                    )
                )
            )
        )

        DeviceDosingCommandValidation.validateConfigResult(
            payload,
            result,
            DeviceDosingStatusParser.parse(DeviceDosingRuntimeFixtures.status()),
            SUPPORTED_ACCESS
        )

        assertEquals("Macro Pump", result.config.channels.first().displayNameOverride)
        assertEquals(7.5, result.config.schedules.single().amountMl, 0.0)
        assertTrue(result.saved)
    }

    @Test
    fun `manual dose result rejects duration and calibration mismatches`() {
        val badDuration = DeviceDosingRuntimeFixtures.doseNow().put("durationMs", 9_999L)
        val badCalibration = DeviceDosingRuntimeFixtures.doseNow()
        badCalibration.getJSONObject("channel")
            .getJSONObject("dosing")
            .put("doseMsPerMl", 900L)

        assertTrue(
            runCatching { DeviceDosingMutationParser.parseDoseNow(badDuration) }.isFailure
        )
        assertTrue(
            runCatching { DeviceDosingMutationParser.parseDoseNow(badCalibration) }.isFailure
        )
    }

    @Test
    fun `calibration workflow rejects pending persistence and channel inconsistencies`() {
        val badPending = DeviceDosingRuntimeFixtures.calibrationFinish().put("pending", false)
        val badSaved = DeviceDosingRuntimeFixtures.calibrationConfirm().put("saved", false)
        val badChannel = DeviceDosingRuntimeFixtures.calibrationCancel().put(
            "channelKey",
            "channel2"
        )

        assertTrue(
            runCatching {
                DeviceDosingMutationParser.parseCalibrationFinish(badPending)
            }.isFailure
        )
        assertTrue(
            runCatching {
                DeviceDosingMutationParser.parseCalibrationConfirm(badSaved)
            }.isFailure
        )
        assertTrue(
            runCatching {
                DeviceDosingMutationParser.parseCalibrationCancel(badChannel)
            }.isFailure
        )
    }

    @Test
    fun `calibration finish remains pending until verified confirmation`() {
        val result = DeviceDosingMutationParser.parseCalibrationFinish(
            DeviceDosingRuntimeFixtures.calibrationFinish(
                measuredMl = 4.0,
                durationMs = 5_000L
            )
        )

        assertEquals(1_250L, result.pendingDoseMsPerMl)
        assertEquals(1_000L, result.channel.channel.dosing.doseMsPerMl)
        assertEquals(100L, result.channel.channel.dosing.lastCalibratedAt)
        assertEquals(
            DeviceDosingCalibrationState.PENDING_VERIFICATION,
            result.channel.channel.dosing.calibration.state
        )
    }

    @Test
    fun `verification dose uses pending coefficient without changing confirmed coefficient`() {
        val result = DeviceDosingMutationParser.parseDoseNow(
            DeviceDosingRuntimeFixtures.doseNow(
                amountMl = DeviceDosingRuntimeContract.Limit.VERIFICATION_DOSE_ML,
                doseMsPerMl = 1_250L,
                usePendingCalibration = true
            )
        )

        assertEquals(5_000L, result.durationMs)
        assertEquals(1_250L, result.channel.channel.dosing.calibration.pendingDoseMsPerMl)
        assertEquals(1_000L, result.channel.channel.dosing.doseMsPerMl)
    }

    @Test
    fun `reservoir refill result validates firmware before after and capacity echo`() {
        val result = DeviceDosingMutationParser.parseReservoirRefill(
            DeviceDosingRuntimeFixtures.reservoirRefill()
        )
        val badCapacity = DeviceDosingRuntimeFixtures.reservoirRefill()
            .put("reservoirCapacityMl", 600.0)

        assertEquals(400.0, result.reservoirRemainingMlBefore, 0.0)
        assertEquals(500.0, result.reservoirRemainingMl, 0.0)
        assertTrue(
            runCatching {
                DeviceDosingMutationParser.parseReservoirRefill(badCapacity)
            }.isFailure
        )
    }

    @Test
    fun `mutation parser rejects command crossover and extra fields`() {
        val wrongCommand = DeviceDosingRuntimeFixtures.pump(
            DeviceDosingRuntimeContract.Action.PRIME_START,
            active = true
        ).put("command", "dosing.prime.stop")
        val extra = DeviceDosingRuntimeFixtures.calibrationStart().put("channel", "fabricated")

        assertTrue(
            runCatching { DeviceDosingMutationParser.parsePrimeStart(wrongCommand) }.isFailure
        )
        assertTrue(
            runCatching {
                DeviceDosingMutationParser.parseCalibrationStart(extra)
            }.isFailure
        )
    }

    private companion object {
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
    }
}
