package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationOperations
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationRequest
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationResult
import com.aqua.aqualight.application.devices.DeviceMenuAccessOperations
import com.aqua.aqualight.application.devices.DeviceMenuAccessResult
import com.aqua.aqualight.application.devices.DeviceMenuOpenResult
import com.aqua.aqualight.application.devices.DeviceMenuOpenUseCase
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.timer.DeviceTimerChannelRegime
import com.aqua.aqualight.application.devices.timer.DeviceTimerControlFailure
import com.aqua.aqualight.application.devices.timer.DeviceTimerControlOperations
import com.aqua.aqualight.application.devices.timer.DeviceTimerControlResult
import com.aqua.aqualight.application.devices.timer.DeviceTimerDisplayNameUpdate
import com.aqua.aqualight.application.devices.timer.DeviceTimerOperatingState
import com.aqua.aqualight.application.devices.timer.DeviceTimerScheduleDraft
import com.aqua.aqualight.data.devices.model.DeviceFamily
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugTimerFixtureIntegrationTest {

    @Test
    fun fixtureMenuOpenPreparesAuthoritativeTimerSurfaceWithoutProductionDelegates() = runTest {
        val dependencies = fixtureDependencies()
        val useCase = DeviceMenuOpenUseCase(
            menuAccessOperations = DebugFixtureMenuAccessOperations(
                delegate = FailingMenuAccessOperations,
                fixtures = dependencies.fixtures
            ),
            controlSurfacePreparationOperations = dependencies.preparation
        )

        val result = useCase.resolve(dependencies.deviceUid)

        assertTrue(result is DeviceMenuOpenResult.Ready)
        val ready = result as DeviceMenuOpenResult.Ready
        assertEquals(OwnerDeviceFamily.TIMER, ready.access.family)
        assertTrue(
            dependencies.preparation.consumeFreshPreparation(
                dependencies.deviceUid,
                OwnerDeviceFamily.TIMER
            )
        )
        assertFalse(
            dependencies.preparation.consumeFreshPreparation(
                dependencies.deviceUid,
                OwnerDeviceFamily.TIMER
            )
        )
        assertTrue(dependencies.controls.currentControl(dependencies.deviceUid).isAvailable())
    }

    @Test
    fun abandonedFixtureMenuOpenDiscardsFreshHandoff() = runTest {
        val dependencies = fixtureDependencies()
        val useCase = DeviceMenuOpenUseCase(
            menuAccessOperations = DebugFixtureMenuAccessOperations(
                delegate = FailingMenuAccessOperations,
                fixtures = dependencies.fixtures
            ),
            controlSurfacePreparationOperations = dependencies.preparation
        )
        val ready = useCase.resolve(dependencies.deviceUid) as DeviceMenuOpenResult.Ready

        useCase.abandon(ready)

        assertFalse(
            dependencies.preparation.consumeFreshPreparation(
                dependencies.deviceUid,
                OwnerDeviceFamily.TIMER
            )
        )
    }

    @Test
    fun fixtureMutationUpdatesTheSharedAuthoritativeSnapshot() = runTest {
        val dependencies = fixtureDependencies()
        val before = dependencies.controls.currentControl(dependencies.deviceUid).availableSnapshot()
        val slot = before.channels.first()

        val mutation = dependencies.controls.setRegime(
            dependencies.deviceUid,
            slot.slotId,
            DeviceTimerChannelRegime.OFF
        ).availableSnapshot()
        val observed = dependencies.controls.observeControl(dependencies.deviceUid)
            .first()
            .availableSnapshot()

        assertTrue(mutation.revision > before.revision)
        assertEquals(mutation, observed)
        assertEquals(DeviceTimerChannelRegime.OFF, observed.channels.first().regime)
        assertEquals(DeviceTimerOperatingState.OFF, observed.channels.first().operatingState)
        assertEquals(before.channels.size, observed.channels.size)
    }

    @Test
    fun temporaryOverrideDoesNotChangePersistentRegimeOrRevision() = runTest {
        val dependencies = fixtureDependencies()
        val before = dependencies.controls.currentControl(dependencies.deviceUid).availableSnapshot()
        val slot = before.channels.first()

        val mutation = dependencies.controls.setTemporaryOverride(
            dependencies.deviceUid,
            slot.slotId,
            DeviceTimerChannelRegime.ON,
            TEMPORARY_OVERRIDE_MILLIS
        ).availableSnapshot()
        val updated = mutation.channels.first()

        assertEquals(before.revision, mutation.revision)
        assertEquals(slot.regime, updated.regime)
        assertEquals(DeviceTimerOperatingState.ON, updated.operatingState)
        assertTrue(updated.temporaryOverrideActive)
        assertEquals(TEMPORARY_OVERRIDE_MILLIS, updated.temporaryOverrideRemainingMillis)
    }

    @Test
    fun realDeviceCallsStillUseProductionDelegates() = runTest {
        val fixtures = DebugDeviceFixtureCatalog()
        val expected = DeviceTimerControlResult.Failed(DeviceTimerControlFailure.NotConnected)
        val timerDelegate = RecordingTimerControlOperations(expected)
        val preparationDelegate = RecordingPreparationOperations()
        val controls = DebugFixtureTimerControlOperations(
            delegate = timerDelegate,
            runtime = DebugTimerFixtureRuntime(fixtures)
        )
        val preparation = DebugFixtureControlSurfacePreparationOperations(
            delegate = preparationDelegate,
            fixtures = fixtures,
            timerControlOperations = controls
        )

        assertSame(expected, controls.refreshControl(REAL_DEVICE_UID))
        val preparationResult = preparation.prepare(
            DeviceControlSurfacePreparationRequest(
                deviceUid = REAL_DEVICE_UID,
                family = OwnerDeviceFamily.TIMER
            )
        )

        assertSame(preparationDelegate.result, preparationResult)
        assertEquals(1, timerDelegate.refreshCount)
        assertEquals(1, preparationDelegate.prepareCount)
    }

    private fun fixtureDependencies(): FixtureDependencies {
        val fixtures = DebugDeviceFixtureCatalog()
        val deviceUid = fixtures.snapshots
            .first { snapshot -> snapshot.product.family == DeviceFamily.TIMER }
            .deviceUid
            .value
        val controls = DebugFixtureTimerControlOperations(
            delegate = FailingTimerControlOperations,
            runtime = DebugTimerFixtureRuntime(fixtures) { FIXED_NOW_MILLIS }
        )
        val preparation = DebugFixtureControlSurfacePreparationOperations(
            delegate = FailingPreparationOperations,
            fixtures = fixtures,
            timerControlOperations = controls
        )
        return FixtureDependencies(fixtures, deviceUid, controls, preparation)
    }

    private data class FixtureDependencies(
        val fixtures: DebugDeviceFixtureCatalog,
        val deviceUid: String,
        val controls: DeviceTimerControlOperations,
        val preparation: DeviceControlSurfacePreparationOperations
    )

    private companion object {
        const val REAL_DEVICE_UID = "REAL-TIMER-001"
        const val FIXED_NOW_MILLIS = 1_800_000_000_000L
        const val TEMPORARY_OVERRIDE_MILLIS = 30L * 60_000L
    }
}

private object FailingMenuAccessOperations : DeviceMenuAccessOperations {
    override suspend fun resolve(deviceUid: String): DeviceMenuAccessResult =
        error("Fixture menu access must not call the production delegate: $deviceUid")
}

private object FailingPreparationOperations : DeviceControlSurfacePreparationOperations {
    override suspend fun prepare(
        request: DeviceControlSurfacePreparationRequest
    ): DeviceControlSurfacePreparationResult =
        error("Fixture preparation must not call the production delegate: ${request.deviceUid}")
}

private object FailingTimerControlOperations : DeviceTimerControlOperations {
    override fun observeControl(deviceUid: String): Flow<DeviceTimerControlResult> = fail(deviceUid)
    override fun currentControl(deviceUid: String): DeviceTimerControlResult = fail(deviceUid)
    override suspend fun refreshControl(deviceUid: String): DeviceTimerControlResult = fail(deviceUid)
    override suspend fun refreshChannel(
        deviceUid: String,
        slotId: String
    ): DeviceTimerControlResult = fail(deviceUid)

    override suspend fun setRegime(
        deviceUid: String,
        slotId: String,
        regime: DeviceTimerChannelRegime
    ): DeviceTimerControlResult = fail(deviceUid)

    override suspend fun setTemporaryOverride(
        deviceUid: String,
        slotId: String,
        regime: DeviceTimerChannelRegime,
        durationMillis: Long
    ): DeviceTimerControlResult = fail(deviceUid)

    override suspend fun setDisplayName(
        deviceUid: String,
        slotId: String,
        update: DeviceTimerDisplayNameUpdate
    ): DeviceTimerControlResult = fail(deviceUid)

    override suspend fun replaceSchedules(
        deviceUid: String,
        slotId: String,
        schedules: List<DeviceTimerScheduleDraft>
    ): DeviceTimerControlResult = fail(deviceUid)

    private fun fail(deviceUid: String): Nothing =
        error("Fixture Timer control must not call the production delegate: $deviceUid")
}

private class RecordingTimerControlOperations(
    private val result: DeviceTimerControlResult
) : DeviceTimerControlOperations {
    var refreshCount = 0
        private set

    override fun observeControl(deviceUid: String): Flow<DeviceTimerControlResult> = flowOf(result)
    override fun currentControl(deviceUid: String): DeviceTimerControlResult = result
    override suspend fun refreshControl(deviceUid: String): DeviceTimerControlResult {
        refreshCount += 1
        return result
    }

    override suspend fun refreshChannel(
        deviceUid: String,
        slotId: String
    ): DeviceTimerControlResult = result

    override suspend fun setRegime(
        deviceUid: String,
        slotId: String,
        regime: DeviceTimerChannelRegime
    ): DeviceTimerControlResult = result

    override suspend fun setTemporaryOverride(
        deviceUid: String,
        slotId: String,
        regime: DeviceTimerChannelRegime,
        durationMillis: Long
    ): DeviceTimerControlResult = result

    override suspend fun setDisplayName(
        deviceUid: String,
        slotId: String,
        update: DeviceTimerDisplayNameUpdate
    ): DeviceTimerControlResult = result

    override suspend fun replaceSchedules(
        deviceUid: String,
        slotId: String,
        schedules: List<DeviceTimerScheduleDraft>
    ): DeviceTimerControlResult = result
}

private class RecordingPreparationOperations : DeviceControlSurfacePreparationOperations {
    val result = DeviceControlSurfacePreparationResult.Unavailable(
        DeviceMenuUnavailableReason.DEVICE_UNRESPONSIVE
    )
    var prepareCount = 0
        private set

    override suspend fun prepare(
        request: DeviceControlSurfacePreparationRequest
    ): DeviceControlSurfacePreparationResult {
        prepareCount += 1
        return result
    }
}

private fun DeviceTimerControlResult.isAvailable(): Boolean =
    this is DeviceTimerControlResult.Available

private fun DeviceTimerControlResult.availableSnapshot() =
    (this as DeviceTimerControlResult.Available).snapshot
