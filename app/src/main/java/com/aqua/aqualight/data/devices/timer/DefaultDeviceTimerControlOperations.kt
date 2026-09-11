package com.aqua.aqualight.data.devices.timer

import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.timer.DeviceTimerChannelRegime
import com.aqua.aqualight.application.devices.timer.DeviceTimerControlFailure
import com.aqua.aqualight.application.devices.timer.DeviceTimerControlOperations
import com.aqua.aqualight.application.devices.timer.DeviceTimerControlResult
import com.aqua.aqualight.application.devices.timer.DeviceTimerDisplayNameUpdate
import com.aqua.aqualight.application.devices.timer.DeviceTimerScheduleDraft
import com.aqua.aqualight.data.devices.DefaultDeviceRootOperations
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerRegime as RuntimeRegime
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerScheduleConfig
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerStatus
import com.aqua.aqualight.data.devices.runtime.modules.timer.currentAuthoritativeState
import com.aqua.aqualight.data.devices.timer.v1.DeviceTimerV1FailureMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

/** Stateless application adapter over the owner-scoped central Timer runtime repository. */
@Suppress("TooManyFunctions")
internal class DefaultDeviceTimerControlOperations(
    private val devicesRepository: DevicesRepository
) : DeviceTimerControlOperations {

    private val rootOperations = DefaultDeviceRootOperations(devicesRepository)

    override fun observeControl(deviceUid: String): Flow<DeviceTimerControlResult> {
        val uid = deviceUid.toDeviceUidOrNull()
        val runtime = uid?.let { devicesRepository.runtimeModules()?.timer }
        return if (uid == null || runtime == null) {
            flowOf(failed(DeviceTimerControlFailure.Unavailable))
        } else {
            combine(rootOperations.observe(uid.value), runtime.states) { root, states ->
                projectRead(root, states[uid])
            }.distinctUntilChanged()
        }
    }

    override fun currentControl(deviceUid: String): DeviceTimerControlResult =
        when (val resolved = resolveRuntime(deviceUid)) {
            is RuntimeResolution.Failed -> failed(resolved.failure)
            is RuntimeResolution.Ready -> projectRead(
                resolved.root,
                resolved.runtime.currentAuthoritativeState(resolved.deviceUid)
            )
        }

    override suspend fun refreshControl(deviceUid: String): DeviceTimerControlResult =
        refreshStatus(deviceUid = deviceUid, slotId = null)

    override suspend fun refreshChannel(
        deviceUid: String,
        slotId: String
    ): DeviceTimerControlResult = refreshStatus(deviceUid = deviceUid, slotId = slotId)

    override suspend fun setRegime(
        deviceUid: String,
        slotId: String,
        regime: DeviceTimerChannelRegime
    ): DeviceTimerControlResult = executeChannel(deviceUid, slotId) { resolved, channelKey ->
        resolved.runtime.setChannelRegime(
            deviceUid = resolved.deviceUid,
            channelKey = channelKey,
            regime = RuntimeRegime.valueOf(regime.name)
        )
    }

    override suspend fun setTemporaryOverride(
        deviceUid: String,
        slotId: String,
        regime: DeviceTimerChannelRegime,
        durationMillis: Long
    ): DeviceTimerControlResult = executeChannel(deviceUid, slotId) { resolved, channelKey ->
        resolved.runtime.setTemporaryOverride(
            deviceUid = resolved.deviceUid,
            channelKey = channelKey,
            regime = RuntimeRegime.valueOf(regime.name),
            durationMs = durationMillis
        )
    }

    override suspend fun setDisplayName(
        deviceUid: String,
        slotId: String,
        update: DeviceTimerDisplayNameUpdate
    ): DeviceTimerControlResult = executeChannel(deviceUid, slotId) { resolved, channelKey ->
        when (update) {
            DeviceTimerDisplayNameUpdate.ResetToDefault ->
                resolved.runtime.clearChannelDisplayName(resolved.deviceUid, channelKey)
            is DeviceTimerDisplayNameUpdate.Value ->
                resolved.runtime.setChannelDisplayName(
                    resolved.deviceUid,
                    channelKey,
                    update.displayName
                )
        }
    }

    override suspend fun replaceSchedules(
        deviceUid: String,
        slotId: String,
        expectedRevision: Long,
        schedules: List<DeviceTimerScheduleDraft>
    ): DeviceTimerControlResult = executeChannel(deviceUid, slotId) { resolved, channelKey ->
        resolved.runtime.replaceSchedules(
            deviceUid = resolved.deviceUid,
            channelKey = channelKey,
            expectedRevision = expectedRevision,
            schedules = schedules.map(DeviceTimerScheduleDraft::toRuntimeConfig)
        )
    }

    private suspend fun refreshStatus(
        deviceUid: String,
        slotId: String?
    ): DeviceTimerControlResult {
        return when (val resolved = resolveRuntime(deviceUid)) {
            is RuntimeResolution.Failed -> failed(resolved.failure)
            is RuntimeResolution.Ready -> {
                val channelKey = slotId?.let { requestedSlotId ->
                    resolved.root.channelSlots.timerChannels
                        .singleOrNull { slot -> slot.id.value == requestedSlotId.trim() }
                        ?.wireKey
                        ?.value
                        ?: return failed(DeviceTimerControlFailure.Unsupported)
                }
                runCatching {
                    resolved.runtime.requestStatus(resolved.deviceUid, channelKey)
                }.fold(
                    onSuccess = { outcome ->
                        outcome.toRefreshedControlResult(resolved, channelKey)
                    },
                    onFailure = { failed(DeviceTimerControlFailure.InvalidData) }
                )
            }
        }
    }

    private suspend fun executeChannel(
        deviceUid: String,
        slotId: String,
        command: suspend (RuntimeResolution.Ready, String) -> DeviceRuntimeCommandOutcome<*>
    ): DeviceTimerControlResult {
        return when (val resolved = resolveRuntime(deviceUid)) {
            is RuntimeResolution.Failed -> failed(resolved.failure)
            is RuntimeResolution.Ready -> {
                val channelKey = resolved.root.channelSlots.timerChannels
                    .singleOrNull { slot -> slot.id.value == slotId.trim() }
                    ?.wireKey
                    ?.value
                    ?: return failed(DeviceTimerControlFailure.Unsupported)
                runCatching { command(resolved, channelKey) }
                    .fold(
                        onSuccess = { outcome -> outcome.toControlResult(resolved) },
                        onFailure = { failed(DeviceTimerControlFailure.InvalidData) }
                    )
            }
        }
    }

    private fun resolveRuntime(deviceUid: String): RuntimeResolution {
        val uid = deviceUid.toDeviceUidOrNull()
        val root = uid?.let { rootOperations.current(it.value) }
        return when {
            uid == null || root == null ->
                RuntimeResolution.Failed(DeviceTimerControlFailure.Unavailable)
            !root.isSupportedTimerRoot() ->
                RuntimeResolution.Failed(DeviceTimerControlFailure.Unsupported)
            else -> devicesRepository.runtimeModules()?.timer
                ?.let { runtime -> RuntimeResolution.Ready(uid, root, runtime) }
                ?: RuntimeResolution.Failed(DeviceTimerControlFailure.Unavailable)
        }
    }

    private fun DeviceRootSnapshot.isSupportedTimerRoot(): Boolean =
        catalogState == DeviceRootCatalogState.VALID &&
            family == OwnerDeviceFamily.TIMER &&
            matchesTimerCatalog()
}

private sealed interface RuntimeResolution {
    data class Ready(
        val deviceUid: DeviceUid,
        val root: DeviceRootSnapshot,
        val runtime: DeviceTimerRuntimeRepository
    ) : RuntimeResolution

    data class Failed(val failure: DeviceTimerControlFailure) : RuntimeResolution
}

private fun projectRead(
    root: DeviceRootSnapshot?,
    state: com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerRuntimeState?
): DeviceTimerControlResult = when {
    root == null || state == null -> failed(DeviceTimerControlFailure.Unavailable)
    root.catalogState != DeviceRootCatalogState.VALID || root.family != OwnerDeviceFamily.TIMER ->
        failed(DeviceTimerControlFailure.Unsupported)
    !state.authoritative || state.requiresStatusRefresh ->
        failed(DeviceTimerControlFailure.Unavailable)
    else -> DeviceTimerControlSnapshotMapper.map(root, state)
        ?.let(DeviceTimerControlResult::Available)
        ?: failed(DeviceTimerControlFailure.InvalidData)
}

private fun DeviceRuntimeCommandOutcome<*>.toControlResult(
    resolved: RuntimeResolution.Ready
): DeviceTimerControlResult = when (this) {
    is DeviceRuntimeCommandOutcome.Success -> projectRead(
        resolved.root,
        resolved.runtime.currentAuthoritativeState(resolved.deviceUid)
    )
    is DeviceRuntimeCommandOutcome.NotConnected,
    is DeviceRuntimeCommandOutcome.NotAuthenticated -> failed(DeviceTimerControlFailure.NotConnected)
    is DeviceRuntimeCommandOutcome.UnsupportedByDevice ->
        failed(DeviceTimerControlFailure.Unsupported)
    is DeviceRuntimeCommandOutcome.FirmwareError -> failed(
        DeviceTimerControlFailure.Rejected(DeviceTimerV1FailureMapper.map(this))
    )
    is DeviceRuntimeCommandOutcome.ProtocolError -> failed(DeviceTimerControlFailure.InvalidData)
    is DeviceRuntimeCommandOutcome.SendFailed,
    is DeviceRuntimeCommandOutcome.Timeout,
    is DeviceRuntimeCommandOutcome.Cancelled -> failed(DeviceTimerControlFailure.Unavailable)
}

private fun DeviceRuntimeCommandOutcome<DeviceTimerStatus>.toRefreshedControlResult(
    resolved: RuntimeResolution.Ready,
    channelKey: String?
): DeviceTimerControlResult = when (this) {
    is DeviceRuntimeCommandOutcome.Success -> {
        val state = resolved.runtime.currentAuthoritativeState(resolved.deviceUid)
        val acceptedStatus = if (channelKey == null) {
            state?.status
        } else {
            state?.channelDetails?.get(channelKey)
        }
        if (state?.connectionGeneration == generation && acceptedStatus == value) {
            projectRead(resolved.root, state)
        } else {
            failed(DeviceTimerControlFailure.Unavailable)
        }
    }
    else -> toControlResult(resolved)
}

private fun DeviceTimerScheduleDraft.toRuntimeConfig() = DeviceTimerScheduleConfig(
    slotId = slotId,
    enabled = enabled,
    name = name,
    weekdays = weekdays,
    startTimeMs = startTimeMillis,
    endTimeMs = endTimeMillis,
    spansMidnight = spansMidnight
)

private fun String.toDeviceUidOrNull(): DeviceUid? = trim()
    .takeIf(String::isNotBlank)
    ?.let(::DeviceUid)

private fun failed(failure: DeviceTimerControlFailure) =
    DeviceTimerControlResult.Failed(failure)
