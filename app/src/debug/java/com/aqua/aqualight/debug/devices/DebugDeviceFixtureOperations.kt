package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.DeleteOwnerDevicesResult
import com.aqua.aqualight.application.devices.DeviceFamilySettingsOperations
import com.aqua.aqualight.application.devices.DeviceFirmwareCommandResult
import com.aqua.aqualight.application.devices.DeviceFirmwareReleaseContent
import com.aqua.aqualight.application.devices.DeviceFirmwareUpdateOperations
import com.aqua.aqualight.application.devices.DeviceLightProtectionSnapshot
import com.aqua.aqualight.application.devices.DeviceLightProtectionThresholdPolicy
import com.aqua.aqualight.application.devices.DeviceMenuAccessOperations
import com.aqua.aqualight.application.devices.DeviceMenuAccessResult
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.application.devices.DeviceOtaState
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.OwnerDeviceListItem
import com.aqua.aqualight.application.devices.OwnerDevicesOperations
import com.aqua.aqualight.application.devices.PreparedDeviceFirmwareUpdate
import com.aqua.aqualight.data.devices.DefaultDeviceFamilySettingsOperations
import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogValidation
import com.aqua.aqualight.data.devices.catalog.AqlCommercialDeviceCatalog
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.toOwnerDeviceFamily
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Adds fixture cards without changing or persisting the real owner device registry. */
internal class DebugFixtureOwnerDevicesOperations(
    private val delegate: OwnerDevicesOperations,
    private val fixtures: DebugDeviceFixtureCatalog
) : OwnerDevicesOperations {

    override val devices: Flow<List<OwnerDeviceListItem>> = delegate.devices.map { realDevices ->
        fixtures.listItems() + realDevices.filterNot { item -> fixtures.contains(item.deviceUid) }
    }

    override fun start(scope: CoroutineScope): Job = delegate.start(scope)

    override fun refreshVisibleDevices() = delegate.refreshVisibleDevices()

    override suspend fun deleteDevices(deviceUids: Set<String>): DeleteOwnerDevicesResult {
        val fixtureUids = deviceUids.filterTo(linkedSetOf(), fixtures::contains)
        val realUids = deviceUids - fixtureUids
        if (realUids.isEmpty()) {
            return DeleteOwnerDevicesResult(
                succeededDeviceUids = emptySet(),
                failedDeviceUids = fixtureUids
            )
        }

        val realResult = delegate.deleteDevices(realUids)
        return realResult.copy(
            failedDeviceUids = realResult.failedDeviceUids + fixtureUids
        )
    }
}

/** Bypasses physical liveness only for known debug fixture UIDs, while retaining catalog closure. */
internal class DebugFixtureMenuAccessOperations(
    private val delegate: DeviceMenuAccessOperations,
    private val fixtures: DebugDeviceFixtureCatalog
) : DeviceMenuAccessOperations {

    override suspend fun resolve(deviceUid: String): DeviceMenuAccessResult {
        val snapshot = fixtures.snapshot(deviceUid) ?: return delegate.resolve(deviceUid)
        if (!snapshot.hasValidatedRuntimeMetadata) {
            return DeviceMenuAccessResult.Unavailable(
                title = snapshot.title,
                reason = DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN
            )
        }

        return when (val validation = AqlCommercialDeviceCatalog.validateSnapshot(snapshot)) {
            is AqlCommercialCatalogValidation.Valid -> DeviceMenuAccessResult.Available(
                deviceUid = snapshot.deviceUid.value,
                title = snapshot.title,
                family = validation.product.family.toOwnerDeviceFamily()
            )
            is AqlCommercialCatalogValidation.Invalid -> DeviceMenuAccessResult.Unavailable(
                title = snapshot.title,
                reason = DeviceMenuUnavailableReason.COMMERCIAL_PRODUCT_MISMATCH
            )
        }
    }
}

/** Uses real root operations for real UIDs and catalog-derived snapshots for fixture UIDs. */
internal class DebugFixtureDeviceRootOperations(
    private val delegate: DeviceRootOperations,
    private val fixtures: DebugDeviceFixtureCatalog
) : DeviceRootOperations {

    override fun observe(deviceUid: String): Flow<DeviceRootSnapshot?> {
        val fixture = fixtures.rootSnapshot(deviceUid)
        return if (fixture != null) flowOf(fixture) else delegate.observe(deviceUid)
    }

    override fun current(deviceUid: String): DeviceRootSnapshot? =
        fixtures.rootSnapshot(deviceUid) ?: delegate.current(deviceUid)

    override fun connect(deviceUid: String): Result<Unit> =
        if (fixtures.contains(deviceUid)) Result.success(Unit) else delegate.connect(deviceUid)
}

/** Keeps the shared Settings screen usable for fixtures without sending runtime commands. */
internal class DebugFixtureFamilySettingsOperations(
    repository: DevicesRepository,
    private val fixtures: DebugDeviceFixtureCatalog
) : DeviceFamilySettingsOperations {

    private val real = DefaultDeviceFamilySettingsOperations(repository)
    private val root = DebugFixtureDeviceRootOperations(real, fixtures)

    override fun observe(deviceUid: String): Flow<DeviceRootSnapshot?> = root.observe(deviceUid)

    override fun current(deviceUid: String): DeviceRootSnapshot? = root.current(deviceUid)

    override fun connect(deviceUid: String): Result<Unit> = root.connect(deviceUid)

    override suspend fun updateCustomName(deviceUid: String, customName: String): Result<Unit> =
        if (fixtures.contains(deviceUid)) Result.success(Unit)
        else real.updateCustomName(deviceUid, customName)

    override fun observeLightProtection(deviceUid: String): Flow<DeviceLightProtectionSnapshot> =
        if (fixtures.contains(deviceUid)) flowOf(fixtureLightProtection(deviceUid))
        else real.observeLightProtection(deviceUid)

    override fun currentLightProtection(deviceUid: String): DeviceLightProtectionSnapshot =
        if (fixtures.contains(deviceUid)) fixtureLightProtection(deviceUid)
        else real.currentLightProtection(deviceUid)

    override suspend fun refreshLightProtection(deviceUid: String): Result<Unit> =
        if (fixtures.contains(deviceUid)) Result.success(Unit)
        else real.refreshLightProtection(deviceUid)

    override suspend fun updateLightProtectionThreshold(
        deviceUid: String,
        thresholdCelsius: Int
    ): Result<Unit> = if (fixtures.contains(deviceUid)) {
        Result.success(Unit)
    } else {
        real.updateLightProtectionThreshold(deviceUid, thresholdCelsius)
    }

    private fun fixtureLightProtection(deviceUid: String): DeviceLightProtectionSnapshot {
        val rootSnapshot = fixtures.rootSnapshot(deviceUid)
        if (rootSnapshot?.family != OwnerDeviceFamily.LIGHT) {
            return DeviceLightProtectionSnapshot(loaded = true)
        }
        return DeviceLightProtectionSnapshot(
            available = true,
            currentTemperatureCelsius = 26.4,
            thresholdCelsius = 32.0,
            thresholdPolicy = DeviceLightProtectionThresholdPolicy(
                currentCelsius = 32,
                minimumCelsius = 20,
                maximumCelsius = 50,
                stepCelsius = 1
            ),
            loaded = true
        )
    }
}

/** Stable OTA presentation state for fixture UIDs; real devices retain the production updater. */
internal class DebugFixtureFirmwareUpdateOperations(
    private val delegate: DeviceFirmwareUpdateOperations,
    fixtures: DebugDeviceFixtureCatalog
) : DeviceFirmwareUpdateOperations {

    private val fixtureStates: Map<String, MutableStateFlow<DeviceOtaState>> =
        fixtures.snapshots.associate { snapshot ->
            snapshot.deviceUid.value to MutableStateFlow<DeviceOtaState>(
                DeviceOtaState.UpToDate(
                    deviceUid = snapshot.deviceUid.value,
                    currentVersion = snapshot.firmwareVersion,
                    latestVersion = snapshot.firmwareVersion,
                    releaseContent = DeviceFirmwareReleaseContent.EMPTY
                )
            )
        }

    override fun observe(deviceUid: String): StateFlow<DeviceOtaState> =
        fixtureStates[deviceUid.trim()] ?: delegate.observe(deviceUid)

    override suspend fun refreshAvailabilityIfStale(
        deviceUid: String,
        manifestUrl: String,
        applyNow: Boolean
    ): Result<DeviceOtaState> {
        val fixtureState = fixtureStates[deviceUid.trim()]?.value
        return if (fixtureState != null) {
            Result.success(fixtureState)
        } else {
            delegate.refreshAvailabilityIfStale(deviceUid, manifestUrl, applyNow)
        }
    }

    override suspend fun checkAvailability(
        deviceUid: String,
        manifestUrl: String,
        applyNow: Boolean
    ): Result<DeviceOtaState> {
        val fixtureState = fixtureStates[deviceUid.trim()]?.value
        return if (fixtureState != null) {
            Result.success(fixtureState)
        } else {
            delegate.checkAvailability(deviceUid, manifestUrl, applyNow)
        }
    }

    override suspend fun prepareUpdate(
        deviceUid: String,
        manifestUrl: String,
        applyNow: Boolean
    ): Result<PreparedDeviceFirmwareUpdate> {
        if (fixtureStates.containsKey(deviceUid.trim())) {
            return Result.failure(
                UnsupportedOperationException(
                    "Firmware installation is disabled for debug fixtures."
                )
            )
        }
        return delegate.prepareUpdate(deviceUid, manifestUrl, applyNow)
    }

    override suspend fun startUpdate(plan: PreparedDeviceFirmwareUpdate): DeviceFirmwareCommandResult =
        if (fixtureStates.containsKey(plan.deviceUid.trim())) {
            DeviceFirmwareCommandResult(sent = false)
        } else {
            delegate.startUpdate(plan)
        }

    override suspend fun requestStatus(deviceUid: String): DeviceFirmwareCommandResult =
        if (fixtureStates.containsKey(deviceUid.trim())) {
            DeviceFirmwareCommandResult(sent = true)
        } else {
            delegate.requestStatus(deviceUid)
        }

    override suspend fun clearStatus(deviceUid: String): DeviceFirmwareCommandResult =
        if (fixtureStates.containsKey(deviceUid.trim())) {
            DeviceFirmwareCommandResult(sent = true)
        } else {
            delegate.clearStatus(deviceUid)
        }

    override fun close() = delegate.close()
}
