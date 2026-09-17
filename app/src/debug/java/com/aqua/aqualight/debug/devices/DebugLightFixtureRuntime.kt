package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightChannelOutputSnapshot
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlMode
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlSnapshot
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightHeroSnapshot
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightOutputCondition
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightPlanReason
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightPlanSnapshot
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomChannel
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomPoint
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomScene
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomSnapshot
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryChannel
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryCustomPoint
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryPayload
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualChannel
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualChannelDescriptor
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualScene
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Single in-process Light authority for installable Debug fixtures. */
internal class DebugLightFixtureRuntime(fixtures: DebugDeviceFixtureCatalog) {

    private val lock = Any()
    private val roots = fixtures.snapshots
        .mapNotNull { snapshot -> fixtures.rootSnapshot(snapshot.deviceUid.value) }
        .filter { root -> root.family == OwnerDeviceFamily.LIGHT }
        .associateBy(DeviceRootSnapshot::deviceUid)
    private val snapshots = roots.values
        .associate { root -> root.deviceUid to root.toFixtureCustomSnapshot() }
        .toMutableMap()
    private val manualSnapshots = roots.values
        .associate { root -> root.deviceUid to root.toFixtureManualSnapshot() }
        .toMutableMap()
    private val previewTimes = mutableMapOf<String, Long>()
    private val _revisions = MutableStateFlow(0L)
    val revisions: StateFlow<Long> = _revisions.asStateFlow()

    fun contains(deviceUid: String): Boolean = deviceUid.trim() in snapshots

    fun currentControl(deviceUid: String): DeviceLightControlSnapshot? = synchronized(lock) {
        val normalizedUid = deviceUid.trim()
        val root = roots[normalizedUid] ?: return@synchronized null
        val manual = manualSnapshots[normalizedUid] ?: return@synchronized null
        val custom = snapshots[normalizedUid] ?: return@synchronized null
        root.toFixtureControlSnapshot(manual, custom)
    }

    fun current(deviceUid: String): DeviceLightCustomSnapshot? = snapshots[deviceUid.trim()]

    fun currentManual(deviceUid: String): DeviceLightManualSnapshot? =
        synchronized(lock) { manualSnapshots[deviceUid.trim()] }

    fun setManual(
        deviceUid: String,
        scene: DeviceLightManualScene
    ): DeviceLightManualSnapshot? = synchronized(lock) {
        val normalizedUid = deviceUid.trim()
        val current = manualSnapshots[normalizedUid] ?: return@synchronized null
        if (scene.channels.keys != current.scene.channels.keys) return@synchronized null
        current.copy(scene = scene).also { snapshot ->
            manualSnapshots[normalizedUid] = snapshot
            _revisions.value += REVISION_INCREMENT
        }
    }

    fun turnManualOff(deviceUid: String): DeviceLightManualSnapshot? = synchronized(lock) {
        val normalizedUid = deviceUid.trim()
        val current = manualSnapshots[normalizedUid] ?: return@synchronized null
        current.copy(
            scene = DeviceLightManualScene(
                current.scene.channels.mapValues { FIXTURE_OFF_PERCENT }
            )
        ).also { snapshot ->
            manualSnapshots[normalizedUid] = snapshot
            _revisions.value += REVISION_INCREMENT
        }
    }

    fun preview(deviceUid: String, virtualTimeMs: Long): Boolean = synchronized(lock) {
        val normalizedUid = deviceUid.trim()
        if (normalizedUid !in snapshots || virtualTimeMs !in 0..LAST_DAY_MILLISECOND) {
            false
        } else {
            previewTimes[normalizedUid] = virtualTimeMs
            true
        }
    }

    fun clearPreview(deviceUid: String): Boolean = synchronized(lock) {
        val normalizedUid = deviceUid.trim()
        val knownFixture = normalizedUid in snapshots
        if (knownFixture) previewTimes.remove(normalizedUid)
        knownFixture
    }

    fun install(deviceUid: String, payload: DeviceLightLibraryPayload.Custom): Boolean =
        synchronized(lock) {
            val normalizedUid = deviceUid.trim()
            val current = snapshots[normalizedUid]
            if (current == null) {
                false
            } else {
                snapshots[normalizedUid] = current.copy(
                    revision = current.revision + REVISION_INCREMENT,
                    installed = true,
                    weekdaysMask = payload.weekdaysMask,
                    points = payload.points.toCustomPoints()
                )
                _revisions.value += REVISION_INCREMENT
                true
            }
        }

    fun isInstalled(deviceUid: String, payload: DeviceLightLibraryPayload.Custom): Boolean =
        synchronized(lock) {
            val current = snapshots[deviceUid.trim()]
            current != null &&
                current.weekdaysMask == payload.weekdaysMask &&
                current.points == payload.points.toCustomPoints()
        }
}

private fun List<DeviceLightLibraryCustomPoint>.toCustomPoints(): List<DeviceLightCustomPoint> =
    map { point ->
        DeviceLightCustomPoint(
            timeMs = point.timeMs,
            scene = DeviceLightCustomScene(
                point.scene.channels.mapKeys { (channel, _) -> channel.toCustomChannel() }
            )
        )
    }

private fun DeviceLightLibraryChannel.toCustomChannel(): DeviceLightCustomChannel = when (this) {
    DeviceLightLibraryChannel.RED -> DeviceLightCustomChannel.RED
    DeviceLightLibraryChannel.GREEN -> DeviceLightCustomChannel.GREEN
    DeviceLightLibraryChannel.BLUE -> DeviceLightCustomChannel.BLUE
    DeviceLightLibraryChannel.WHITE -> DeviceLightCustomChannel.WHITE
}

private fun DeviceRootSnapshot.toFixtureCustomSnapshot(): DeviceLightCustomSnapshot {
    val channels = channelSlots.lightChannels.map { slot ->
        slot.wireKey.value.toCustomChannel()
    }
    val points = FIXTURE_CURVE.map { fixturePoint ->
        DeviceLightCustomPoint(
            timeMs = fixturePoint.minuteOfDay * MINUTE_MILLIS,
            scene = DeviceLightCustomScene(
                channels.associateWith(fixturePoint::percentFor)
            )
        )
    }
    return DeviceLightCustomSnapshot(
        deviceUid = deviceUid,
        productKey = productKey,
        revision = FIXTURE_INITIAL_REVISION,
        installed = true,
        weekdaysMask = EVERY_DAY_MASK,
        maxPoints = FIXTURE_MAX_POINTS,
        timeStepMs = MINUTE_MILLIS,
        currentTimeMs = FIXTURE_CURRENT_TIME_MINUTES * MINUTE_MILLIS,
        channels = channels,
        points = points
    )
}

private fun DeviceRootSnapshot.toFixtureManualSnapshot(): DeviceLightManualSnapshot {
    val descriptors = channelSlots.lightChannels.mapIndexed { index, slot ->
        DeviceLightManualChannelDescriptor(
            channel = slot.wireKey.value.toManualChannel(),
            key = slot.wireKey.value,
            displayName = slot.defaultDisplayName,
            displayColorRgb = slot.wireKey.value.fixtureDisplayColorRgb(),
            order = index
        )
    }
    val channels = descriptors.map(DeviceLightManualChannelDescriptor::channel)
    val supportsEstimatedPower = DeviceLightManualChannel.WHITE in channels
    return DeviceLightManualSnapshot(
        deviceUid = deviceUid,
        productKey = productKey,
        channelDescriptors = descriptors,
        scene = DeviceLightManualScene(channels.associateWith(::fixtureManualPercent)),
        estimatedPowerWatts = FIXTURE_MANUAL_POWER_WATTS.takeIf { supportsEstimatedPower },
        estimatedPowerRatio = FIXTURE_MANUAL_POWER_RATIO.takeIf { supportsEstimatedPower },
        estimatedPowerDisplayColorRgb = FIXTURE_MANUAL_DISPLAY_COLOR_RGB
            .takeIf { supportsEstimatedPower },
        protection = null,
        firmwareWriteAuthoritative = true
    )
}

private fun DeviceRootSnapshot.toFixtureControlSnapshot(
    manual: DeviceLightManualSnapshot,
    custom: DeviceLightCustomSnapshot
): DeviceLightControlSnapshot {
    val effectivePercents = manual.scene.channels.mapKeys { (channel, _) -> channel.toWireKey() }
    val outputActive = effectivePercents.values.any { percent -> percent > FIXTURE_OFF_PERCENT }
    val channels = channelSlots.lightChannels.map { slot ->
        DeviceLightChannelOutputSnapshot(
            key = slot.wireKey.value,
            displayName = slot.defaultDisplayName,
            displayColorRgb = slot.wireKey.value.fixtureDisplayColorRgb(),
            effectivePercent = effectivePercents.getValue(slot.wireKey.value)
        )
    }
    return DeviceLightControlSnapshot(
        deviceUid = deviceUid,
        productKey = productKey,
        physicalChannelCount = channels.size,
        channelKeys = channels.map(DeviceLightChannelOutputSnapshot::key),
        channels = channels,
        plan = DeviceLightPlanSnapshot(
            available = false,
            reason = DeviceLightPlanReason.MODE_HAS_NO_SCHEDULE,
            nowTimeMs = FIXTURE_CURRENT_TIME_MINUTES * MINUTE_MILLIS,
            channelScale = FIRMWARE_CHANNEL_SCALE,
            hasScheduleToday = false,
            points = emptyList()
        ),
        automaticProgramCount = 0,
        customCurvePointCount = custom.points.size,
        hero = DeviceLightHeroSnapshot(
            mode = DeviceLightControlMode.MANUAL,
            outputActive = outputActive,
            outputCondition = if (outputActive) {
                DeviceLightOutputCondition.ACTIVE
            } else {
                DeviceLightOutputCondition.ALL_CHANNELS_ZERO
            },
            outputHealthy = true,
            estimatedPowerWatts = manual.estimatedPowerWatts?.toDouble(),
            estimatedColorTemperatureKelvin = null
        )
    )
}

private fun fixtureManualPercent(channel: DeviceLightManualChannel): Int = when (channel) {
    DeviceLightManualChannel.RED -> FIXTURE_MANUAL_RED_PERCENT
    DeviceLightManualChannel.GREEN -> FIXTURE_MANUAL_GREEN_PERCENT
    DeviceLightManualChannel.BLUE -> FIXTURE_MANUAL_BLUE_PERCENT
    DeviceLightManualChannel.WHITE -> FIXTURE_MANUAL_WHITE_PERCENT
}

private fun String.toCustomChannel(): DeviceLightCustomChannel = checkNotNull(
    DeviceLightCustomChannel.entries.singleOrNull { channel -> channel.wireKey == this }
) { "Unsupported Debug Light fixture channel: $this" }

private fun String.toManualChannel(): DeviceLightManualChannel = when (this) {
    "red" -> DeviceLightManualChannel.RED
    "green" -> DeviceLightManualChannel.GREEN
    "blue" -> DeviceLightManualChannel.BLUE
    "white" -> DeviceLightManualChannel.WHITE
    else -> error("Unsupported Debug Light fixture channel: $this")
}

private fun DeviceLightManualChannel.toWireKey(): String = when (this) {
    DeviceLightManualChannel.RED -> "red"
    DeviceLightManualChannel.GREEN -> "green"
    DeviceLightManualChannel.BLUE -> "blue"
    DeviceLightManualChannel.WHITE -> "white"
}

private fun String.fixtureDisplayColorRgb(): Int = when (this) {
    "red" -> DISPLAY_COLOR_RED
    "green" -> DISPLAY_COLOR_GREEN
    "blue" -> DISPLAY_COLOR_BLUE
    "white" -> DISPLAY_COLOR_WHITE
    else -> error("Unsupported Debug Light fixture channel color: $this")
}

private data class FixtureCurvePoint(
    val minuteOfDay: Long,
    val red: Int,
    val green: Int,
    val blue: Int,
    val white: Int
) {
    fun percentFor(channel: DeviceLightCustomChannel): Int = when (channel) {
        DeviceLightCustomChannel.RED -> red
        DeviceLightCustomChannel.GREEN -> green
        DeviceLightCustomChannel.BLUE -> blue
        DeviceLightCustomChannel.WHITE -> white
    }
}

private val FIXTURE_CURVE = listOf(
    FixtureCurvePoint(MIDNIGHT_MINUTES, OFF_PERCENT, OFF_PERCENT, OFF_PERCENT, OFF_PERCENT),
    FixtureCurvePoint(DAWN_START_MINUTES, OFF_PERCENT, OFF_PERCENT, OFF_PERCENT, OFF_PERCENT),
    FixtureCurvePoint(
        SUNRISE_MINUTES,
        LOW_RED_PERCENT,
        LOW_GREEN_PERCENT,
        LOW_BLUE_PERCENT,
        LOW_WHITE_PERCENT
    ),
    FixtureCurvePoint(
        DAY_START_MINUTES,
        DAY_RED_PERCENT,
        DAY_GREEN_PERCENT,
        DAY_BLUE_PERCENT,
        DAY_WHITE_PERCENT
    ),
    FixtureCurvePoint(
        NOON_MINUTES,
        DAY_RED_PERCENT,
        DAY_GREEN_PERCENT,
        DAY_BLUE_PERCENT,
        DAY_WHITE_PERCENT
    ),
    FixtureCurvePoint(
        SUNSET_START_MINUTES,
        DAY_RED_PERCENT,
        DAY_GREEN_PERCENT,
        DAY_BLUE_PERCENT,
        DAY_WHITE_PERCENT
    ),
    FixtureCurvePoint(
        SUNSET_MINUTES,
        LOW_RED_PERCENT,
        LOW_GREEN_PERCENT,
        LOW_BLUE_PERCENT,
        LOW_WHITE_PERCENT
    ),
    FixtureCurvePoint(NIGHT_MINUTES, OFF_PERCENT, OFF_PERCENT, OFF_PERCENT, OFF_PERCENT),
    FixtureCurvePoint(LAST_DAY_MINUTE, OFF_PERCENT, OFF_PERCENT, OFF_PERCENT, OFF_PERCENT)
)

private const val FIXTURE_INITIAL_REVISION = 1L
private const val REVISION_INCREMENT = 1L
private const val FIXTURE_MAX_POINTS = 96
private const val EVERY_DAY_MASK = 127
private const val MINUTE_MILLIS = 60_000L
private const val MINUTES_PER_HOUR = 60L
private const val LAST_DAY_MILLISECOND = 86_399_999L
private const val MIDNIGHT_MINUTES = 0L
private const val DAWN_START_MINUTES = 4L * MINUTES_PER_HOUR
private const val SUNRISE_MINUTES = 6L * MINUTES_PER_HOUR
private const val DAY_START_MINUTES = 8L * MINUTES_PER_HOUR
private const val NOON_MINUTES = 12L * MINUTES_PER_HOUR
private const val FIXTURE_CURRENT_TIME_MINUTES = 15L * MINUTES_PER_HOUR + 30L
private const val SUNSET_START_MINUTES = 17L * MINUTES_PER_HOUR
private const val SUNSET_MINUTES = 20L * MINUTES_PER_HOUR
private const val NIGHT_MINUTES = 22L * MINUTES_PER_HOUR
private const val LAST_DAY_MINUTE = 24L * MINUTES_PER_HOUR - 1L
private const val OFF_PERCENT = 0
private const val LOW_RED_PERCENT = 10
private const val LOW_GREEN_PERCENT = 20
private const val LOW_BLUE_PERCENT = 30
private const val LOW_WHITE_PERCENT = 40
private const val DAY_RED_PERCENT = 25
private const val DAY_GREEN_PERCENT = 45
private const val DAY_BLUE_PERCENT = 65
private const val DAY_WHITE_PERCENT = 85
private const val FIXTURE_MANUAL_RED_PERCENT = 20
private const val FIXTURE_MANUAL_GREEN_PERCENT = 30
private const val FIXTURE_MANUAL_BLUE_PERCENT = 40
private const val FIXTURE_MANUAL_WHITE_PERCENT = 50
private const val FIXTURE_MANUAL_POWER_WATTS = 46
private const val FIXTURE_MANUAL_POWER_RATIO = 0.46f
private const val FIXTURE_MANUAL_DISPLAY_COLOR_RGB = 0xD8D1FF
private const val FIXTURE_OFF_PERCENT = 0
private const val FIRMWARE_CHANNEL_SCALE = 1_000
private const val DISPLAY_COLOR_RED = 0xFF0000
private const val DISPLAY_COLOR_GREEN = 0x00FF00
private const val DISPLAY_COLOR_BLUE = 0x0000FF
private const val DISPLAY_COLOR_WHITE = 0xFFFFFF
