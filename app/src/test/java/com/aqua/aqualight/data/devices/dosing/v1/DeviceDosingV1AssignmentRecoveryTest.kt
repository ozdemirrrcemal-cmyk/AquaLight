package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelCommittedResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgram
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramSchedule
import com.aqua.aqualight.data.devices.model.DeviceUid
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DeviceDosingV1AssignmentRecoveryTest {

    @Test
    fun `one hundred rapid saves survive ambiguous outcome and converge only latest target`() =
        runTest {
            val gate = DeviceDosingV1AssignmentRecoveryGate()
            val attempts = mutableListOf<Long>()
            val deviceUid = DeviceUid(DEVICE_UID)
            val finalResult = DeviceDosingChannelCommittedResult(99L)
            val coordinator = DeviceDosingV1ProgramIntentCoordinator(
                scope = backgroundScope,
                recoveryGate = gate,
                execute = { _, _, assignment ->
                    val amount = (assignment.program.schedule as DeviceDosingProgramSchedule.Single)
                        .dailyDoseMicroliters
                    attempts += amount
                    if (attempts.size == 1) {
                        gate.markTransportInterrupted(deviceUid)
                        DeviceDosingChannelOperationResult.Failed
                    } else {
                        finalResult
                    }
                }
            )

            val first = async { coordinator.submit(DEVICE_UID, SLOT_ID, intent(1_000L)) }
            runCurrent()
            assertEquals(listOf(1_000L), attempts)
            assertFalse(first.isCompleted)

            val rapid = (1L..100L).map { index ->
                async {
                    coordinator.submit(
                        DEVICE_UID,
                        SLOT_ID,
                        intent((index + 1L) * 1_000L)
                    )
                }
            }
            runCurrent()
            assertEquals(listOf(1_000L), attempts)

            gate.markAuthenticated(deviceUid)
            runCurrent()

            assertEquals(listOf(1_000L, 101_000L), attempts)
            assertEquals(finalResult, first.await())
            rapid.forEach { result -> assertEquals(finalResult, result.await()) }
        }

    @Test
    fun `rapid absolute switch intents converge to latest value after recovery`() = runTest {
        val gate = DeviceDosingV1AssignmentRecoveryGate()
        val attempts = mutableListOf<Boolean>()
        val deviceUid = DeviceUid(DEVICE_UID)
        val finalResult = DeviceDosingChannelCommittedResult(77L)
        val coordinator = DeviceDosingV1MissedDoseRecoveryIntentCoordinator(
            scope = backgroundScope,
            recoveryGate = gate,
            execute = { _, _, enabled ->
                attempts += enabled
                if (attempts.size == 1) {
                    gate.markTransportInterrupted(deviceUid)
                    DeviceDosingChannelOperationResult.Failed
                } else {
                    finalResult
                }
            }
        )

        val first = async { coordinator.submit(DEVICE_UID, SLOT_ID, enabled = true) }
        runCurrent()
        val off = async { coordinator.submit(DEVICE_UID, SLOT_ID, enabled = false) }
        val on = async { coordinator.submit(DEVICE_UID, SLOT_ID, enabled = true) }
        val latest = async { coordinator.submit(DEVICE_UID, SLOT_ID, enabled = false) }
        runCurrent()

        assertEquals(listOf(true), attempts)
        gate.markAuthenticated(deviceUid)
        runCurrent()

        assertEquals(listOf(true, false), attempts)
        listOf(first, off, on, latest).forEach { result ->
            assertEquals(finalResult, result.await())
        }
    }

    private fun intent(amountMicroliters: Long) = DeviceDosingV1ProgramAssignmentIntent(
        program = DeviceDosingProgram(
            enabled = true,
            weekdays = List(7) { true },
            schedule = DeviceDosingProgramSchedule.Single(
                dailyDoseMicroliters = amountMicroliters,
                startTimeMillis = 0L
            ),
            missedDoseRecoveryEnabled = false
        ),
        origin = null
    )

    private companion object {
        const val DEVICE_UID = "device-assignment-recovery-test"
        const val SLOT_ID = "dosing:channel1"
    }
}
