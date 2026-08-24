package com.aqua.aqualight.application.devices.dosing

/**
 * Read-only application projection for the tank-detail Dosing device card.
 *
 * All scheduling, runtime and reservoir facts originate from the central Dosing state owner. This
 * policy only composes already-authoritative channel snapshots into one device-level read model;
 * it never refreshes firmware, reproduces scheduler logic or owns mutable device state.
 */
object DeviceDosingCardSummaryPolicy {

    fun build(
        deviceUid: String,
        snapshots: List<DeviceDosingChannelSnapshot>
    ): DeviceDosingCardSummary? {
        val channels = snapshots
            .filter { snapshot -> deviceUid.isNotBlank() && snapshot.deviceUid == deviceUid }
            .sortedBy(DeviceDosingChannelSnapshot::channelNumber)
        val pumpCount = channels
            .map(DeviceDosingChannelSnapshot::pumpCount)
            .distinct()
            .singleOrNull()
        val completeChannelSet = pumpCount?.let { count ->
            channels.size == count &&
                channels.map(DeviceDosingChannelSnapshot::channelNumber) == (1..count).toList()
        } == true

        return if (pumpCount == null || !completeChannelSet) {
            null
        } else {
            DeviceDosingCardSummary(
                deviceUid = deviceUid,
                channelCount = pumpCount,
                activeChannelCount = channels.count(DeviceDosingChannelSnapshot::runtimeEnabled),
                channels = channels.map(::toCardChannelSummary)
            )
        }
    }

    private fun toCardChannelSummary(
        snapshot: DeviceDosingChannelSnapshot
    ): DeviceDosingCardChannelSummary {
        val configuredProgram = snapshot.program?.takeIf(DeviceDosingProgram::enabled)
        val nextDose = snapshot.progress
            .nextScheduledOccurrence()
            ?.takeIf { snapshot.runtimeEnabled }
            ?.let { occurrence ->
                DeviceDosingCardNextDose(
                    timeMillis = occurrence.timeMillis,
                    amountMicroliters = occurrence.amountMicroliters
                )
            }

        return DeviceDosingCardChannelSummary(
            channelNumber = snapshot.channelNumber,
            title = snapshot.channelTitle,
            runtimeEnabled = snapshot.runtimeEnabled,
            dailyDoseMicroliters = configuredProgram?.dailyDoseMicroliters(),
            nextDose = nextDose,
            reservoir = snapshot.toCardReservoirSummary()
        )
    }

    private fun DeviceDosingChannelSnapshot.toCardReservoirSummary():
        DeviceDosingCardReservoirSummary? {
        if (!reservoir.trackingEnabled) return null

        val projection = DeviceDosingSupplyProjectionPolicy.evaluate(this)
        val estimatedDays = projection?.estimatedRemainingDays
        val state = when {
            reservoir.lowLevelActive -> DeviceDosingCardReservoirState.LOW
            estimatedDays != null -> DeviceDosingCardReservoirState.ESTIMATED
            !reservoir.accountingCertain -> DeviceDosingCardReservoirState.UNCERTAIN
            else -> DeviceDosingCardReservoirState.ESTIMATE_UNAVAILABLE
        }
        return DeviceDosingCardReservoirSummary(
            remainingMicroliters = reservoir.remainingMicroliters,
            state = state,
            estimatedRemainingDays = estimatedDays.takeIf {
                state == DeviceDosingCardReservoirState.ESTIMATED
            }
        )
    }
}

data class DeviceDosingCardSummary(
    val deviceUid: String,
    val channelCount: Int,
    val activeChannelCount: Int,
    val channels: List<DeviceDosingCardChannelSummary>
) {
    init {
        require(deviceUid.isNotBlank())
        require(channelCount > 0)
        require(activeChannelCount in 0..channelCount)
        require(channels.size == channelCount)
    }
}

data class DeviceDosingCardChannelSummary(
    val channelNumber: Int,
    val title: String,
    val runtimeEnabled: Boolean,
    val dailyDoseMicroliters: Long?,
    val nextDose: DeviceDosingCardNextDose?,
    val reservoir: DeviceDosingCardReservoirSummary?
) {
    init {
        require(channelNumber > 0)
        require(title.isNotBlank())
        require(dailyDoseMicroliters == null || dailyDoseMicroliters > 0L)
    }
}

data class DeviceDosingCardNextDose(
    val timeMillis: Long,
    val amountMicroliters: Long
) {
    init {
        require(timeMillis in 0L until MILLIS_PER_DAY)
        require(amountMicroliters > 0L)
    }
}

data class DeviceDosingCardReservoirSummary(
    val remainingMicroliters: Long,
    val state: DeviceDosingCardReservoirState,
    val estimatedRemainingDays: Int?
) {
    init {
        require(remainingMicroliters >= 0L)
        require(estimatedRemainingDays == null || estimatedRemainingDays >= 0)
        require(
            (state == DeviceDosingCardReservoirState.ESTIMATED) ==
                (estimatedRemainingDays != null)
        )
    }
}

enum class DeviceDosingCardReservoirState {
    ESTIMATED,
    LOW,
    UNCERTAIN,
    ESTIMATE_UNAVAILABLE
}

private const val MILLIS_PER_DAY = 86_400_000L
