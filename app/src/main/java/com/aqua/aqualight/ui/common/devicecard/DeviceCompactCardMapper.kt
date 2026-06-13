package com.aqua.aqualight.ui.common.devicecard

import com.aqua.aqualight.data.devices.card.DeviceCardUiState

class DeviceCompactCardMapper {

    fun map(
        cardState: DeviceCardUiState,
        showTankText: Boolean
    ): DeviceCompactCardUi {
        return DeviceCompactCardUi(
            deviceId = cardState.deviceId,
            displayName = cardState.title,
            serialText = primarySerialText(
                cardState = cardState
            ),
            tankText = cardState.tankName,
            showTankText = showTankText,
            iconRes = DeviceCardIconMapper.iconFor(
                category = cardState.category
            ),
            isOnline = cardState.isOnline
        )
    }

    fun mapAll(
        cardStates: List<DeviceCardUiState>,
        showTankText: Boolean
    ): List<DeviceCompactCardUi> {
        return cardStates.map { cardState ->
            map(
                cardState = cardState,
                showTankText = showTankText
            )
        }
    }

    private fun primarySerialText(
        cardState: DeviceCardUiState
    ): String {
        return listOf(
            cardState.serial,
            cardState.serialNumber,
            cardState.shortId,
            cardState.deviceUid,
            cardState.identityText.substringBefore(
                delimiter = " • "
            )
        ).map { value ->
            value.trim()
        }.firstOrNull { value ->
            value.isNotBlank()
        }.orEmpty()
    }
}
