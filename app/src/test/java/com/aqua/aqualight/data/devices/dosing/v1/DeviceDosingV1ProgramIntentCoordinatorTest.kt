package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelCommittedResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgram
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramSchedule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceDosingV1ProgramIntentCoordinatorTest {

    @Test
    fun `rapid saves keep one in flight write and coalesce pending work to latest intent`() =
        runTest {
            val firstEntered = CompletableDeferred<Unit>()
            val releaseFirst = CompletableDeferred<Unit>()
            val attempts = mutableListOf<Long>()
            val coordinator = DeviceDosingV1ProgramIntentCoordinator(
                scope = backgroundScope,
                execute = { _, _, intent ->
                    val amount = (intent.program.schedule as DeviceDosingProgramSchedule.Single)
                        .dailyDoseMicroliters
                    attempts += amount
                    if (attempts.size == 1) {
                        firstEntered.complete(Unit)
                        releaseFirst.await()
                    }
                    DeviceDosingChannelCommittedResult(amount)
                }
            )

            val first = async { coordinator.submit(DEVICE_UID, SLOT_ID, intent(1_000L)) }
            firstEntered.await()
            val superseded = async { coordinator.submit(DEVICE_UID, SLOT_ID, intent(2_000L)) }
            val latest = async { coordinator.submit(DEVICE_UID, SLOT_ID, intent(3_000L)) }
            runCurrent()

            assertEquals(listOf(1_000L), attempts)
            releaseFirst.complete(Unit)

            val finalResult = DeviceDosingChannelCommittedResult(3_000L)
            assertEquals(finalResult, first.await())
            assertEquals(finalResult, superseded.await())
            assertEquals(finalResult, latest.await())
            assertEquals(listOf(1_000L, 3_000L), attempts)
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
        const val DEVICE_UID = "device-program-intent-test"
        const val SLOT_ID = "dosing:channel1"
    }
}
