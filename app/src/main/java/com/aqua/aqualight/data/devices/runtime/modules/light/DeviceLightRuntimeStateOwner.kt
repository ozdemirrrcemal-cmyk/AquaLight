package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DeviceLightThermalRuntimeState(
    val status: DeviceLightThermalStatus? = null,
    val telemetry: DeviceLightThermalTelemetry? = null
)

/**
 * The only mutable, firmware-authoritative Light state owner.
 *
 * Main Light status, custom, temperature protection and thermal documents publish through this
 * aggregate so no Light adapter can retain a parallel authoritative snapshot. Their independent
 * connection-generation lifecycles are delegated to [DeviceLightRuntimeAuthorityCoordinator].
 */
internal class DeviceLightRuntimeStateOwner {
    private val lock = Any()
    private val authorityCoordinator = DeviceLightRuntimeAuthorityCoordinator()
    private val _statuses = MutableStateFlow<Map<DeviceUid, DeviceLightStatus>>(emptyMap())
    private val _stateRevision = MutableStateFlow(0L)
    internal val customProjection = DeviceLightCustomRuntimeProjection(
        lock = lock,
        authorityCoordinator = authorityCoordinator,
        statuses = { _statuses.value },
        publishChange = { _stateRevision.value += 1L }
    )
    private val _temperatureProtection = MutableStateFlow<
        Map<DeviceUid, DeviceLightTemperatureProtectionStatus>
        >(emptyMap())
    private val _thermalStates = MutableStateFlow<
        Map<DeviceUid, DeviceLightThermalRuntimeState>
        >(emptyMap())

    val statuses: StateFlow<Map<DeviceUid, DeviceLightStatus>> = _statuses.asStateFlow()
    val temperatureProtection: StateFlow<Map<DeviceUid, DeviceLightTemperatureProtectionStatus>> =
        _temperatureProtection.asStateFlow()
    val thermalStates: StateFlow<Map<DeviceUid, DeviceLightThermalRuntimeState>> =
        _thermalStates.asStateFlow()
    val stateRevision: StateFlow<Long> = _stateRevision.asStateFlow()

    fun beginGeneration(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): Boolean = synchronized(lock) {
        val accepted = authorityCoordinator.beginGeneration(deviceUid, generation)
        if (accepted) _stateRevision.value += 1L
        accepted
    }

    fun invalidate(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration? = null
    ) = synchronized(lock) {
        val targetsCurrentGeneration = generation == null ||
            authorityCoordinator.isCurrentGeneration(
                DeviceLightRuntimeProjection.STATUS,
                deviceUid,
                generation
            )
        authorityCoordinator.invalidate(deviceUid, generation)
        if (targetsCurrentGeneration) _stateRevision.value += 1L
    }

    fun isAuthoritative(
        projection: DeviceLightRuntimeProjection,
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): Boolean = authorityCoordinator.isAuthoritative(projection, deviceUid, generation)

    fun currentAuthoritativeStatus(deviceUid: DeviceUid): DeviceLightStatus? = synchronized(lock) {
        _statuses.value[deviceUid]?.takeIf {
            authorityCoordinator.isCurrentlyAuthoritative(
                DeviceLightRuntimeProjection.STATUS,
                deviceUid
            )
        }
    }

    fun currentAuthoritativeTemperatureProtection(
        deviceUid: DeviceUid
    ): DeviceLightTemperatureProtectionStatus? = synchronized(lock) {
        _temperatureProtection.value[deviceUid]
            ?.takeIf {
                authorityCoordinator.isCurrentlyAuthoritative(
                    DeviceLightRuntimeProjection.TEMPERATURE_PROTECTION,
                    deviceUid
                )
            }
    }

    fun recordStatus(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        status: DeviceLightStatus
    ): Boolean = synchronized(lock) {
        if (
            !authorityCoordinator.acceptAuthoritativeSnapshot(
                DeviceLightRuntimeProjection.STATUS,
                deviceUid,
                generation
            )
        ) {
            return@synchronized false
        }
        _statuses.value = _statuses.value + (deviceUid to status)
        customProjection.reconcileStatus(deviceUid, generation, status)
        _stateRevision.value += 1L
        true
    }

    fun recordTemperatureProtection(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        status: DeviceLightTemperatureProtectionStatus
    ): Boolean = synchronized(lock) {
        if (
            !authorityCoordinator.acceptAuthoritativeSnapshot(
                DeviceLightRuntimeProjection.TEMPERATURE_PROTECTION,
                deviceUid,
                generation
            )
        ) {
            return@synchronized false
        }
        _temperatureProtection.value = _temperatureProtection.value + (deviceUid to status)
        _stateRevision.value += 1L
        true
    }

    fun recordThermalStatus(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        status: DeviceLightThermalStatus
    ): Boolean = synchronized(lock) {
        val current = _thermalStates.value[deviceUid]
        if (
            authorityCoordinator.isAuthoritative(
                DeviceLightRuntimeProjection.THERMAL,
                deviceUid,
                generation
            ) &&
            current?.status?.uptimeMs?.let { previous -> status.uptimeMs < previous } == true
        ) {
            return@synchronized false
        }
        if (
            !authorityCoordinator.acceptAuthoritativeSnapshot(
                DeviceLightRuntimeProjection.THERMAL,
                deviceUid,
                generation
            )
        ) {
            return@synchronized false
        }
        _thermalStates.value = _thermalStates.value + (
            deviceUid to DeviceLightThermalRuntimeState(
                status = status,
                telemetry = current?.telemetry?.takeIf { telemetry ->
                    telemetry.productKey == status.productKey &&
                        telemetry.uptimeMs >= status.uptimeMs
                }
            )
        )
        _stateRevision.value += 1L
        true
    }

    fun recordThermalTelemetry(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        telemetry: DeviceLightThermalTelemetry
    ): Boolean = synchronized(lock) {
        if (
            !authorityCoordinator.acceptsPatch(
                DeviceLightRuntimeProjection.THERMAL,
                deviceUid,
                generation
            )
        ) {
            return@synchronized false
        }
        val current = _thermalStates.value[deviceUid] ?: return@synchronized false
        val status = current.status ?: return@synchronized false
        if (
            telemetry.productKey != status.productKey ||
            telemetry.uptimeMs < status.uptimeMs
        ) {
            return@synchronized false
        }
        val previous = current.telemetry
        if (
            previous != null &&
            (
                telemetry.uptimeMs < previous.uptimeMs ||
                    telemetry.temperature.sampledAtMs < previous.temperature.sampledAtMs
                )
        ) {
            return@synchronized false
        }
        _thermalStates.value = _thermalStates.value + (deviceUid to current.copy(telemetry = telemetry))
        _stateRevision.value += 1L
        true
    }

    fun clear(deviceUid: DeviceUid) {
        synchronized(lock) {
            _statuses.value = _statuses.value.without(deviceUid)
            customProjection.clear(deviceUid)
            _temperatureProtection.value = _temperatureProtection.value.without(deviceUid)
            _thermalStates.value = _thermalStates.value.without(deviceUid)
            authorityCoordinator.clear(deviceUid)
            _stateRevision.value += 1L
        }
    }
}

/** Custom is a projection component of [DeviceLightRuntimeStateOwner], never a second owner. */
internal class DeviceLightCustomRuntimeProjection(
    private val lock: Any,
    private val authorityCoordinator: DeviceLightRuntimeAuthorityCoordinator,
    private val statuses: () -> Map<DeviceUid, DeviceLightStatus>,
    private val publishChange: () -> Unit
) {
    private var documents: Map<DeviceUid, DeviceLightCustomDocument> = emptyMap()

    fun currentAuthoritative(deviceUid: DeviceUid): DeviceLightCustomDocument? =
        synchronized(lock) {
            val status = statuses()[deviceUid]
                ?.takeIf {
                    authorityCoordinator.isCurrentlyAuthoritative(
                        DeviceLightRuntimeProjection.STATUS,
                        deviceUid
                    )
                }
                ?: return@synchronized null
            documents[deviceUid]?.takeIf { document ->
                authorityCoordinator.isCurrentlyAuthoritative(
                    DeviceLightRuntimeProjection.CUSTOM,
                    deviceUid
                ) && document.isCoherentWith(status)
            }
        }

    fun record(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        document: DeviceLightCustomDocument
    ): Boolean = synchronized(lock) {
        if (
            !authorityCoordinator.isAuthoritative(
                DeviceLightRuntimeProjection.STATUS,
                deviceUid,
                generation
            )
        ) {
            return@synchronized false
        }
        val status = statuses()[deviceUid] ?: return@synchronized false
        if (!document.isCoherentWith(status)) return@synchronized false
        val current = documents[deviceUid]
        if (
            authorityCoordinator.isAuthoritative(
                DeviceLightRuntimeProjection.CUSTOM,
                deviceUid,
                generation
            ) && current != null && document.revision < current.revision
        ) {
            return@synchronized false
        }
        if (
            !authorityCoordinator.acceptAuthoritativeSnapshot(
                DeviceLightRuntimeProjection.CUSTOM,
                deviceUid,
                generation
            )
        ) {
            return@synchronized false
        }
        documents = documents + (deviceUid to document)
        publishChange()
        true
    }

    fun reconcileStatus(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        status: DeviceLightStatus
    ) = synchronized(lock) {
        val custom = documents[deviceUid]
        if (custom != null && !custom.isCoherentWith(status)) {
            documents = documents.without(deviceUid)
            authorityCoordinator.invalidateProjection(
                DeviceLightRuntimeProjection.CUSTOM,
                deviceUid,
                generation
            )
        }
    }

    fun clear(deviceUid: DeviceUid) = synchronized(lock) {
        documents = documents.without(deviceUid)
    }
}

private fun DeviceLightCustomDocument.isCoherentWith(status: DeviceLightStatus): Boolean =
    summary() == status.customSummary() &&
        pointCount == points.size &&
        points.all { point -> point.scene.product == status.product }

private fun DeviceLightCustomDocument.summary() = DeviceLightCustomDocumentSummary(
    revision = revision,
    installed = installed,
    weekdaysMask = weekdaysMask,
    pointCount = pointCount
)

private fun DeviceLightStatus.customSummary() = DeviceLightCustomDocumentSummary(
    revision = custom.revision,
    installed = custom.installed,
    weekdaysMask = custom.weekdaysMask,
    pointCount = custom.pointCount
)

private data class DeviceLightCustomDocumentSummary(
    val revision: Long,
    val installed: Boolean,
    val weekdaysMask: Int,
    val pointCount: Int
)

private fun <T> Map<DeviceUid, T>.without(deviceUid: DeviceUid): Map<DeviceUid, T> =
    if (deviceUid !in this) this else toMutableMap().apply { remove(deviceUid) }.toMap()
