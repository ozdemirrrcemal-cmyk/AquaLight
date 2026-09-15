package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationMutationResult
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationOperations
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationReadResult
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationState
import com.aqua.aqualight.application.devices.light.control.DeviceLightControlOperations
import com.aqua.aqualight.application.devices.light.control.DeviceLightControlResult
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomFailure
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomMutationResult
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomOperations
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomReadResult
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryChannel
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryCustomPoint
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryEntry
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryKind
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryMutationResult
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryOperations
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryPayload
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryResult
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryScene
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibrarySnapshot
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryTarget
import com.aqua.aqualight.data.devices.model.DeviceFamily
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugLightFixtureIntegrationTest {

    @Test
    fun eliteFixtureExposesAndRunsAdaptationWithoutProductionCalls() = runTest {
        val fixtures = DebugDeviceFixtureCatalog()
        val deviceUid = fixtures.firstAdaptationLightUid()
        val adaptation = DebugFixtureLightAdaptationOperations(
            delegate = FailingAdaptationOperations,
            fixtures = fixtures,
            epochSeconds = { FIXED_EPOCH_SECONDS }
        )
        val controls = DebugFixtureLightControlOperations(
            delegate = FailingFixtureLightControlOperations,
            fixtures = fixtures,
            adaptationOperations = adaptation
        )

        val initial = adaptation.current(deviceUid) as DeviceLightAdaptationReadResult.Available
        val initialControl = controls.currentControl(deviceUid) as DeviceLightControlResult.Available
        val started = adaptation.start(
            deviceUid = deviceUid,
            expectedRevision = initial.snapshot.revision,
            startPercent = START_PERCENT,
            durationDays = DURATION_DAYS
        ) as DeviceLightAdaptationMutationResult.Success
        val activeControl = controls.observeControl(deviceUid).first()
            as DeviceLightControlResult.Available
        val stopped = adaptation.stop(deviceUid, started.snapshot.revision)
            as DeviceLightAdaptationMutationResult.Success

        assertTrue(initialControl.snapshot.adaptation.supported)
        assertEquals(DeviceLightAdaptationState.DISABLED, initial.snapshot.state)
        assertEquals(DeviceLightAdaptationState.ACTIVE, started.snapshot.state)
        assertEquals(START_PERCENT * PERMILLE_PER_PERCENT, started.snapshot.currentPermille)
        assertEquals(DeviceLightAdaptationState.ACTIVE, activeControl.snapshot.adaptation.state)
        assertEquals(DeviceLightAdaptationState.DISABLED, stopped.snapshot.state)
    }

    @Test
    fun fixtureCustomDocumentAndPreviewUseInProcessRuntime() = runTest {
        val fixtures = DebugDeviceFixtureCatalog()
        val runtime = DebugLightFixtureRuntime(fixtures)
        val operations = DebugFixtureLightCustomOperations(
            delegate = FailingCustomOperations,
            runtime = runtime
        )
        val deviceUid = fixtures.snapshots
            .first { snapshot -> snapshot.product.family == DeviceFamily.LIGHT }
            .deviceUid
            .value

        val read = operations.read(deviceUid) as DeviceLightCustomReadResult.Available
        val preview = operations.preview(deviceUid, PREVIEW_TIME_MILLIS)
        val cleared = operations.clearPreview(deviceUid)

        assertEquals(deviceUid, read.snapshot.deviceUid)
        assertTrue(read.snapshot.installed)
        assertTrue(read.snapshot.points.isNotEmpty())
        assertEquals(DeviceLightCustomMutationResult.Success, preview)
        assertEquals(DeviceLightCustomMutationResult.Success, cleared)
    }

    @Test
    fun realDeviceCustomCallsRemainOnProductionBoundary() = runTest {
        val expectedRead = DeviceLightCustomReadResult.Failed(
            DeviceLightCustomFailure.NOT_CONNECTED
        )
        val delegate = RecordingCustomOperations(expectedRead)
        val operations = DebugFixtureLightCustomOperations(
            delegate = delegate,
            runtime = DebugLightFixtureRuntime(DebugDeviceFixtureCatalog())
        )

        assertSame(expectedRead, operations.read(REAL_DEVICE_UID))
        operations.preview(REAL_DEVICE_UID, PREVIEW_TIME_MILLIS)
        operations.clearPreview(REAL_DEVICE_UID)

        assertEquals(EXPECTED_DELEGATE_CALL_COUNT, delegate.callCount)
    }

    @Test
    fun fixtureCustomLibraryLoadUpdatesTheSameRuntimeDocument() = runTest {
        val fixtures = DebugDeviceFixtureCatalog()
        val runtime = DebugLightFixtureRuntime(fixtures)
        val deviceUid = fixtures.firstLightUid()
        val current = requireNotNull(runtime.current(deviceUid))
        val entry = customEntry(current.productKey, current.channels.map(::libraryChannel))
        val delegate = FixedLibraryOperations(
            result = DeviceLightLibraryResult.Available(
                DeviceLightLibrarySnapshot(
                    target = DeviceLightLibraryTarget(
                        deviceUid = deviceUid,
                        productKey = current.productKey,
                        channels = entry.channels,
                        estimatedPowerWatts = null
                    ),
                    entries = listOf(entry)
                )
            )
        )
        val operations = DebugFixtureLightLibraryOperations(delegate, runtime)

        val loaded = operations.load(deviceUid, entry.id)
        val observed = operations.observeLibrary(deviceUid).first()
            as DeviceLightLibraryResult.Available

        assertEquals(DeviceLightLibraryMutationResult.Success(entry.id), loaded)
        assertEquals(CUSTOM_WEEKDAYS_MASK, runtime.current(deviceUid)?.weekdaysMask)
        assertTrue(observed.snapshot.entries.single().isLoaded)
    }

    private companion object {
        const val REAL_DEVICE_UID = "REAL-LIGHT-001"
        const val PREVIEW_TIME_MILLIS = 15L * 60L * 60_000L
        const val EXPECTED_DELEGATE_CALL_COUNT = 3
        const val FIXED_EPOCH_SECONDS = 1_800_000_000L
        const val START_PERCENT = 55
        const val DURATION_DAYS = 21
        const val PERMILLE_PER_PERCENT = 10
    }
}

private fun DebugDeviceFixtureCatalog.firstLightUid(): String = snapshots
    .first { snapshot -> snapshot.product.family == DeviceFamily.LIGHT }
    .deviceUid
    .value

private fun DebugDeviceFixtureCatalog.firstAdaptationLightUid(): String = snapshots
    .map { snapshot -> requireNotNull(rootSnapshot(snapshot.deviceUid.value)) }
    .first { root -> LIGHT_ACCLIMATION_FEATURE in root.supportedFeatures }
    .deviceUid

private fun customEntry(
    productKey: String,
    channels: List<DeviceLightLibraryChannel>
): DeviceLightLibraryEntry {
    val payload = DeviceLightLibraryPayload.Custom(
        weekdaysMask = CUSTOM_WEEKDAYS_MASK,
        points = listOf(
            DeviceLightLibraryCustomPoint(
                timeMs = CUSTOM_POINT_TIME_MILLIS,
                scene = DeviceLightLibraryScene(
                    channels.associateWith { CUSTOM_POINT_PERCENT }
                )
            )
        )
    )
    return DeviceLightLibraryEntry(
        id = CUSTOM_ENTRY_ID,
        name = CUSTOM_ENTRY_NAME,
        productKey = productKey,
        channels = channels,
        payload = payload,
        createdAtMillis = CUSTOM_CREATED_AT_MILLIS,
        updatedAtMillis = CUSTOM_CREATED_AT_MILLIS,
        isLoaded = false
    )
}

private fun libraryChannel(
    channel: com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomChannel
): DeviceLightLibraryChannel = when (channel) {
    com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomChannel.RED ->
        DeviceLightLibraryChannel.RED
    com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomChannel.GREEN ->
        DeviceLightLibraryChannel.GREEN
    com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomChannel.BLUE ->
        DeviceLightLibraryChannel.BLUE
    com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomChannel.WHITE ->
        DeviceLightLibraryChannel.WHITE
}

private object FailingCustomOperations : DeviceLightCustomOperations {
    override suspend fun read(deviceUid: String): DeviceLightCustomReadResult = fail(deviceUid)

    override suspend fun preview(
        deviceUid: String,
        virtualTimeMs: Long
    ): DeviceLightCustomMutationResult = fail(deviceUid)

    override suspend fun clearPreview(deviceUid: String): DeviceLightCustomMutationResult =
        fail(deviceUid)

    private fun fail(deviceUid: String): Nothing =
        error("Fixture Custom operation must not call the production delegate: $deviceUid")
}

private object FailingAdaptationOperations : DeviceLightAdaptationOperations {
    override fun observe(deviceUid: String): Flow<DeviceLightAdaptationReadResult> = fail(deviceUid)

    override fun current(deviceUid: String): DeviceLightAdaptationReadResult = fail(deviceUid)

    override suspend fun refresh(deviceUid: String): DeviceLightAdaptationReadResult = fail(deviceUid)

    override suspend fun start(
        deviceUid: String,
        expectedRevision: Long,
        startPercent: Int,
        durationDays: Int
    ): DeviceLightAdaptationMutationResult = fail(deviceUid)

    override suspend fun stop(
        deviceUid: String,
        expectedRevision: Long
    ): DeviceLightAdaptationMutationResult = fail(deviceUid)

    private fun fail(deviceUid: String): Nothing =
        error("Fixture adaptation must not call the production delegate: $deviceUid")
}

private object FailingFixtureLightControlOperations : DeviceLightControlOperations {
    override fun observeControl(deviceUid: String): Flow<DeviceLightControlResult> = fail(deviceUid)

    override fun currentControl(deviceUid: String): DeviceLightControlResult = fail(deviceUid)

    override suspend fun refreshControl(deviceUid: String): DeviceLightControlResult = fail(deviceUid)

    private fun fail(deviceUid: String): Nothing =
        error("Fixture Light control must not call the production delegate: $deviceUid")
}

private class RecordingCustomOperations(
    private val readResult: DeviceLightCustomReadResult
) : DeviceLightCustomOperations {
    var callCount = 0
        private set

    override suspend fun read(deviceUid: String): DeviceLightCustomReadResult {
        callCount += 1
        return readResult
    }

    override suspend fun preview(
        deviceUid: String,
        virtualTimeMs: Long
    ): DeviceLightCustomMutationResult {
        callCount += 1
        return DeviceLightCustomMutationResult.Failed(DeviceLightCustomFailure.NOT_CONNECTED)
    }

    override suspend fun clearPreview(deviceUid: String): DeviceLightCustomMutationResult {
        callCount += 1
        return DeviceLightCustomMutationResult.Failed(DeviceLightCustomFailure.NOT_CONNECTED)
    }
}

private class FixedLibraryOperations(
    private val result: DeviceLightLibraryResult
) : DeviceLightLibraryOperations {
    override fun observeLibrary(deviceUid: String): Flow<DeviceLightLibraryResult> = flowOf(result)

    override suspend fun usedNames(kind: DeviceLightLibraryKind): List<String> = emptyList()

    override suspend fun refreshInstalledCustom(deviceUid: String) = Unit

    override suspend fun saveManual(
        deviceUid: String,
        name: String,
        scene: DeviceLightLibraryScene
    ): DeviceLightLibraryMutationResult = DeviceLightLibraryMutationResult.Success()

    override suspend fun saveCustom(
        deviceUid: String,
        name: String,
        weekdaysMask: Int,
        points: List<DeviceLightLibraryCustomPoint>
    ): DeviceLightLibraryMutationResult = DeviceLightLibraryMutationResult.Success()

    override suspend fun rename(
        entryId: String,
        name: String
    ): DeviceLightLibraryMutationResult = DeviceLightLibraryMutationResult.Success(entryId)

    override suspend fun delete(entryId: String): DeviceLightLibraryMutationResult =
        DeviceLightLibraryMutationResult.Success(entryId)

    override suspend fun load(
        deviceUid: String,
        entryId: String
    ): DeviceLightLibraryMutationResult = error("Fixture Custom load must not reach the delegate.")
}

private const val CUSTOM_WEEKDAYS_MASK = 31
private const val CUSTOM_POINT_TIME_MILLIS = 10L * 60L * 60_000L
private const val CUSTOM_POINT_PERCENT = 42
private const val CUSTOM_ENTRY_ID = "fixture-custom-entry"
private const val CUSTOM_ENTRY_NAME = "Fixture custom curve"
private const val CUSTOM_CREATED_AT_MILLIS = 1L
private const val LIGHT_ACCLIMATION_FEATURE = "LIGHT_ACCLIMATION"
