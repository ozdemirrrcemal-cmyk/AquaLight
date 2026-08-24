package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid

internal data class DeviceDosingV1MappedSnapshots(
    val channel: DeviceDosingChannelSnapshot,
    val calibration: DeviceDosingCalibrationSnapshot
)

internal data class DeviceDosingV1SnapshotDocuments(
    val deviceUid: DeviceUid,
    val slotId: String,
    val global: DeviceDosingV1GlobalStatus,
    val channelStatus: DeviceDosingV1ChannelStatus,
    val progressStatus: DeviceDosingV1ProgressStatus,
    val lowLevelAlertEnabled: Boolean
)

/** Joins validated wire documents and delegates field mapping to focused domain mappers. */
internal object DeviceDosingV1SnapshotMapper {

    fun map(documents: DeviceDosingV1SnapshotDocuments): DeviceDosingV1MappedSnapshots {
        validateJoinedStatus(documents)
        val detail = documents.channelStatus.channel
        val global = documents.global
        return DeviceDosingV1MappedSnapshots(
            channel = DeviceDosingChannelSnapshot(
                deviceUid = documents.deviceUid.value,
                slotId = documents.slotId,
                pumpCount = global.envelope.channelCount,
                channelNumber = detail.index + 1,
                channelTitle = detail.effectiveName,
                revision = detail.revision,
                runtimeEnabled = detail.runtimeEnabled,
                runtimeReason = DeviceDosingV1ChannelSnapshotMapper.runtimeReason(detail),
                deliveryAccountingCertain = detail.deliveryAccountingCertain,
                calibrated = detail.calibration.confirmed,
                lastCalibratedAtEpochSeconds = detail.calibration.lastCalibratedAt,
                scheduling = DeviceDosingV1ProgramSnapshotMapper.scheduling(
                    documents.channelStatus.scheduling
                ),
                program = detail.program?.let(DeviceDosingV1ProgramSnapshotMapper::program),
                progress = DeviceDosingV1ProgressSnapshotMapper.map(
                    documents.progressStatus,
                    detail
                ),
                reservoir = DeviceDosingV1ChannelSnapshotMapper.reservoir(
                    detail,
                    documents.lowLevelAlertEnabled
                ),
                activeRun = DeviceDosingV1ChannelSnapshotMapper.activeRun(detail),
                controls = DeviceDosingV1ChannelSnapshotMapper.controls(detail, global),
                usageToday = DeviceDosingV1ChannelSnapshotMapper.usage(detail)
            ),
            calibration = DeviceDosingV1CalibrationSnapshotMapper.map(
                detail = detail,
                deviceUid = documents.deviceUid,
                slotId = documents.slotId,
                envelope = documents.channelStatus.envelope
            )
        )
    }

    /**
     * Projects the full channel document returned by a mutation ACK for presentation continuity.
     *
     * The previous progress document is retained until readback, so this projection remains
     * non-authoritative. Calibration runtime is intentionally not projected: its elapsed-time
     * semantics require the envelope uptime that exists only in a coherent status readback. Mixing
     * a new run-start timestamp from the ACK with the previous envelope uptime can make a newly
     * started calibration look already complete.
     */
    fun projectMutation(
        current: DeviceDosingV1MappedSnapshots,
        detail: DeviceDosingV1ChannelDetail,
        lowLevelAlertEnabled: Boolean
    ): DeviceDosingV1MappedSnapshots {
        require(detail.index + 1 == current.channel.channelNumber)
        val projectedChannel = current.channel.copy(
            channelTitle = detail.effectiveName,
            revision = detail.revision,
            runtimeEnabled = detail.runtimeEnabled,
            runtimeReason = DeviceDosingV1ChannelSnapshotMapper.runtimeReason(detail),
            deliveryAccountingCertain = detail.deliveryAccountingCertain,
            calibrated = detail.calibration.confirmed,
            lastCalibratedAtEpochSeconds = detail.calibration.lastCalibratedAt,
            program = detail.program?.let(DeviceDosingV1ProgramSnapshotMapper::program),
            reservoir = DeviceDosingV1ChannelSnapshotMapper.reservoir(
                detail,
                lowLevelAlertEnabled
            ),
            activeRun = DeviceDosingV1ChannelSnapshotMapper.activeRun(detail),
            usageToday = DeviceDosingV1ChannelSnapshotMapper.usage(detail)
        )
        return DeviceDosingV1MappedSnapshots(
            channel = projectedChannel,
            calibration = current.calibration
        )
    }

    private fun validateJoinedStatus(documents: DeviceDosingV1SnapshotDocuments) {
        val global = documents.global
        val channelStatus = documents.channelStatus
        val progressStatus = documents.progressStatus
        val detail = channelStatus.channel
        val expectedKey = DeviceDosingV1SlotKeyMapper.channelKey(documents.slotId)
        require(global.envelope.channelCount == global.channels.size) {
            "Dosing global channel count does not match the channel list."
        }
        require(channelStatus.envelope.channelCount == global.envelope.channelCount)
        require(progressStatus.envelope.channelCount == global.envelope.channelCount)
        require(detail.channelKey == expectedKey)
        require(progressStatus.channelKey == expectedKey)
        require(detail.index in 0 until global.envelope.channelCount)
        require(DeviceDosingV1SlotKeyMapper.channelNumber(expectedKey) == detail.index + 1)
        val globalChannel = requireNotNull(
            global.channels.singleOrNull { candidate -> candidate.channelKey == expectedKey }
        ) { "Dosing global status does not contain the requested channel." }
        require(globalChannel.revision == detail.revision)
        require(progressStatus.revision == detail.revision)
        require(globalChannel.effectiveName == detail.effectiveName)
        require(globalChannel.programMode.raw == (detail.program?.mode?.raw ?: PROGRAM_MODE_NONE))
    }

    private const val PROGRAM_MODE_NONE = "none"
}

/** The catalog's stable `dosing:channelN` identity never crosses the wire boundary. */
internal object DeviceDosingV1SlotKeyMapper {
    fun channelKey(slotId: String): DeviceDosingV1ChannelKey {
        require(SLOT_PATTERN.matches(slotId)) { "Invalid stable Dosing slot id." }
        return DeviceDosingV1ChannelKey.from(slotId.removePrefix(SLOT_PREFIX))
    }

    fun slotId(channelKey: DeviceDosingV1ChannelKey): String {
        channelNumber(channelKey)
        return SLOT_PREFIX + channelKey.value
    }

    fun channelNumber(channelKey: DeviceDosingV1ChannelKey): Int {
        val match = requireNotNull(CHANNEL_PATTERN.matchEntire(channelKey.value)) {
            "Firmware Dosing channel key does not match the commercial catalog shape."
        }
        return match.groupValues[1].toInt().also { number -> require(number > 0) }
    }

    private const val SLOT_PREFIX = "dosing:"
    private val SLOT_PATTERN = Regex("^dosing:channel[1-9][0-9]*$")
    private val CHANNEL_PATTERN = Regex("^channel([1-9][0-9]*)$")
}
