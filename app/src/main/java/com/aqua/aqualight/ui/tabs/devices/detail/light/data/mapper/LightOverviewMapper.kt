package com.aqua.aqualight.ui.tabs.devices.detail.light.data.mapper

import com.aqua.aqualight.ui.tabs.devices.detail.light.data.remote.LightOverviewRemoteResponse
import com.aqua.aqualight.ui.tabs.devices.detail.light.domain.model.LightDeviceHealth
import com.aqua.aqualight.ui.tabs.devices.detail.light.domain.model.LightOutputSnapshot
import com.aqua.aqualight.ui.tabs.devices.detail.light.domain.model.LightOverview
import com.aqua.aqualight.ui.tabs.devices.detail.light.domain.model.LightProgramSummary

class LightOverviewMapper {

    fun map(
        response: LightOverviewRemoteResponse
    ): LightOverview {
        return LightOverview(
            program = LightProgramSummary(
                id = response.programId,
                name = response.programName.orEmpty(),
                mode = response.mode.orEmpty(),
                isEnabled = response.enabled == true,
                scheduleLabel = response.scheduleLabel.orEmpty(),
                channelsLabel = response.channelsLabel.orEmpty(),
                nowLabel = response.nowLabel.orEmpty(),
                nextLabel = response.nextLabel.orEmpty()
            ),
            health = LightDeviceHealth(
                syncLabel = response.syncLabel.orEmpty(),
                temperatureLabel = response.temperatureLabel.orEmpty(),
                fanLabel = response.fanLabel.orEmpty(),
                deviceTimeLabel = response.deviceTimeLabel.orEmpty(),
                firmwareLabel = response.firmwareLabel.orEmpty()
            ),
            output = LightOutputSnapshot(
                masterPercent = response.masterPercent,
                redPercent = response.redPercent,
                greenPercent = response.greenPercent,
                bluePercent = response.bluePercent,
                whitePercent = response.whitePercent
            )
        )
    }
}