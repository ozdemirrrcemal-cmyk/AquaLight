package com.aqua.aqualight.ui.tabs.aquarium.detail.devices.select

import com.aqua.aqualight.data.devices.card.DeviceCardUiState
import com.aqua.aqualight.ui.tabs.devices.model.DeviceIconMapper

class TankDeviceSelectItemMapper {

    fun map(
        cardState: DeviceCardUiState
    ): TankDeviceSelectItem {
        return TankDeviceSelectItem(
            deviceId = cardState.deviceId,
            displayName = cardState.title,
            productMetaText = cardState.productMetaText,
            identityText = cardState.identityText,
            iconRes = DeviceIconMapper.iconFor(
                category = cardState.category
            ),
            isOnline = cardState.isOnline
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