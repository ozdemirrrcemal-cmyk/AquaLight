package com.aqua.aqualight.ui.tabs.aquarium.detail.devices.select

import com.aqua.aqualight.data.devices.card.DeviceCardUiState
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactCardMapper

class TankDeviceSelectItemMapper(
    private val compactCardMapper: DeviceCompactCardMapper = DeviceCompactCardMapper()
) {

    fun map(
        cardState: DeviceCardUiState
    ): TankDeviceSelectItem {
        return TankDeviceSelectItem(
            card = compactCardMapper.map(
                cardState = cardState,
                showTankText = false
            )
        )
    }

    fun mapAll(
        cardStates: List<DeviceCardUiState>
    ): List<TankDeviceSelectItem> {
        return cardStates.map { cardState ->
            map(
                cardState = cardState
            )
        }
    }
}
