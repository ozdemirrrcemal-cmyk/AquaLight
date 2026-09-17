package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationFailure
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationMutationResult
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationOperations
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationPolicy
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationReadResult
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationSnapshot
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationState
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightAdaptationSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-process adaptation authority for the installable WRGB Pro Elite debug fixture. */
internal class DebugFixtureLightAdaptationOperations(
    private val delegate: DeviceLightAdaptationOperations,
    fixtures: DebugDeviceFixtureCatalog,
    private val epochSeconds: () -> Long = { System.currentTimeMillis() / MILLIS_PER_SECOND }
) : DeviceLightAdaptationOperations {

    private val lock = Any()
    private val fixtureStates = fixtures.snapshots
        .mapNotNull { snapshot -> fixtures.rootSnapshot(snapshot.deviceUid.value) }
        .filter { root -> LIGHT_ACCLIMATION_FEATURE in root.supportedFeatures }
        .associate { root ->
            root.deviceUid to MutableStateFlow(initialSnapshot(root.deviceUid))
        }

    override fun observe(deviceUid: String): Flow<DeviceLightAdaptationReadResult> =
        fixtureState(deviceUid)?.map { snapshot -> available(snapshot) }
            ?: delegate.observe(deviceUid)

    override fun current(deviceUid: String): DeviceLightAdaptationReadResult =
        fixtureState(deviceUid)?.value?.let(::available)
            ?: delegate.current(deviceUid)

    override suspend fun refresh(deviceUid: String): DeviceLightAdaptationReadResult =
        fixtureState(deviceUid)?.value?.let(::available)
            ?: delegate.refresh(deviceUid)

    override suspend fun start(
        deviceUid: String,
        expectedRevision: Long,
        startPercent: Int,
        durationDays: Int
    ): DeviceLightAdaptationMutationResult {
        val state = fixtureState(deviceUid)
        return if (state == null) {
            delegate.start(deviceUid, expectedRevision, startPercent, durationDays)
        } else {
            synchronized(lock) {
                startFixture(state, expectedRevision, startPercent, durationDays)
            }
        }
    }

    override suspend fun stop(
        deviceUid: String,
        expectedRevision: Long
    ): DeviceLightAdaptationMutationResult {
        val state = fixtureState(deviceUid)
        return if (state == null) {
            delegate.stop(deviceUid, expectedRevision)
        } else {
            synchronized(lock) { stopFixture(state, expectedRevision) }
        }
    }

    fun supportsFixture(deviceUid: String): Boolean = fixtureState(deviceUid) != null

    fun summary(deviceUid: String): DeviceLightAdaptationSummary? =
        fixtureState(deviceUid)?.value?.let { snapshot ->
            DeviceLightAdaptationSummary(
                supported = true,
                state = snapshot.state,
                currentPermille = snapshot.currentPermille,
                remainingSeconds = snapshot.remainingSeconds
            )
        }

    private fun startFixture(
        state: MutableStateFlow<DeviceLightAdaptationSnapshot>,
        expectedRevision: Long,
        startPercent: Int,
        durationDays: Int
    ): DeviceLightAdaptationMutationResult {
        val current = state.value
        val failure = current.startFailure(expectedRevision, startPercent, durationDays)
        return if (failure != null) {
            DeviceLightAdaptationMutationResult.Failed(failure)
        } else {
            val startedAt = epochSeconds()
            val updated = current.copy(
                revision = current.revision + REVISION_INCREMENT,
                state = DeviceLightAdaptationState.ACTIVE,
                startPercent = startPercent,
                currentPermille = startPercent * PERMILLE_PER_PERCENT,
                durationDays = durationDays,
                startedAtEpochSeconds = startedAt,
                endsAtEpochSeconds = startedAt + durationDays * SECONDS_PER_DAY,
                remainingSeconds = durationDays * SECONDS_PER_DAY
            )
            state.value = updated
            DeviceLightAdaptationMutationResult.Success(updated)
        }
    }

    private fun stopFixture(
        state: MutableStateFlow<DeviceLightAdaptationSnapshot>,
        expectedRevision: Long
    ): DeviceLightAdaptationMutationResult {
        val current = state.value
        return if (expectedRevision != current.revision) {
            DeviceLightAdaptationMutationResult.Failed(
                DeviceLightAdaptationFailure.STALE_REVISION
            )
        } else {
            val updated = current.copy(
                revision = current.revision + REVISION_INCREMENT,
                state = DeviceLightAdaptationState.DISABLED,
                currentPermille = current.policy.targetPercent * PERMILLE_PER_PERCENT,
                remainingSeconds = 0L
            )
            state.value = updated
            DeviceLightAdaptationMutationResult.Success(updated)
        }
    }

    private fun fixtureState(
        deviceUid: String
    ): MutableStateFlow<DeviceLightAdaptationSnapshot>? = fixtureStates[deviceUid.trim()]
}

private fun DeviceLightAdaptationSnapshot.startFailure(
    expectedRevision: Long,
    requestedStartPercent: Int,
    requestedDurationDays: Int
): DeviceLightAdaptationFailure? = when {
    expectedRevision != revision -> DeviceLightAdaptationFailure.STALE_REVISION
    !clockReady -> DeviceLightAdaptationFailure.CLOCK_NOT_READY
    !policy.accepts(requestedStartPercent, requestedDurationDays) ->
        DeviceLightAdaptationFailure.INVALID_REQUEST
    else -> null
}

private fun DeviceLightAdaptationPolicy.accepts(
    startPercent: Int,
    durationDays: Int
): Boolean {
    val startAccepted = startPercent in startPercentMin..startPercentMax &&
        (startPercent - startPercentMin) % startPercentStep == 0
    val durationAccepted = durationDays in durationDaysMin..durationDaysMax &&
        (durationDays - durationDaysMin) % durationDaysStep == 0
    return startAccepted && durationAccepted
}

private fun initialSnapshot(deviceUid: String) = DeviceLightAdaptationSnapshot(
    deviceUid = deviceUid,
    revision = INITIAL_REVISION,
    state = DeviceLightAdaptationState.DISABLED,
    clockReady = true,
    startPercent = ADAPTATION_POLICY.defaultStartPercent,
    currentPermille = ADAPTATION_POLICY.targetPercent * PERMILLE_PER_PERCENT,
    targetPercent = ADAPTATION_POLICY.targetPercent,
    durationDays = ADAPTATION_POLICY.defaultDurationDays,
    startedAtEpochSeconds = 0L,
    endsAtEpochSeconds = 0L,
    remainingSeconds = 0L,
    policy = ADAPTATION_POLICY
)

private fun available(snapshot: DeviceLightAdaptationSnapshot) =
    DeviceLightAdaptationReadResult.Available(snapshot)

private val ADAPTATION_POLICY = DeviceLightAdaptationPolicy(
    startPercentMin = 20,
    startPercentMax = 90,
    startPercentStep = 5,
    defaultStartPercent = 50,
    durationDaysMin = 7,
    durationDaysMax = 90,
    durationDaysStep = 1,
    defaultDurationDays = 30,
    targetPercent = 100
)

private const val LIGHT_ACCLIMATION_FEATURE = "LIGHT_ACCLIMATION"
private const val INITIAL_REVISION = 1L
private const val REVISION_INCREMENT = 1L
private const val PERMILLE_PER_PERCENT = 10
private const val SECONDS_PER_DAY = 86_400L
private const val MILLIS_PER_SECOND = 1_000L
