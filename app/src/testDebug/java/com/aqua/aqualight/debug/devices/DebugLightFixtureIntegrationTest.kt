package com.aqua.aqualight.debug.devices

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
    }
}

private fun DebugDeviceFixtureCatalog.firstLightUid(): String = snapshots
    .first { snapshot -> snapshot.product.family == DeviceFamily.LIGHT }
    .deviceUid
    .value

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
