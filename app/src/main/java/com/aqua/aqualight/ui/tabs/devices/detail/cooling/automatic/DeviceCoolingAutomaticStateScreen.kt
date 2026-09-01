package com.aqua.aqualight.ui.tabs.devices.detail.cooling.automatic

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticFailure
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.common.CoolingDataFreshness
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.common.CoolingDataState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.common.CoolingStateMessageCard

/** Routes typed Automatic read states without discarding the last authoritative editor snapshot. */
@Composable
internal fun DeviceCoolingAutomaticStateScreen(
    state: DeviceCoolingAutomaticSettingsUiState,
    actions: DeviceCoolingAutomaticSettingsActions,
    modifier: Modifier = Modifier
) {
    val message = automaticStateMessage(state)
    Column(modifier = modifier.fillMaxSize()) {
        message?.let { content ->
            CoolingStateMessageCard(
                title = stringResource(content.titleRes),
                message = stringResource(content.messageRes),
                retryLabel = if (content.retryAvailable) {
                    stringResource(R.string.device_cooling_state_retry)
                } else {
                    null
                },
                onRetry = actions.onRetry.takeIf { content.retryAvailable },
                modifier = Modifier.padding(
                    start = AquaCoolingAutomaticGeometry.screenHorizontalPadding,
                    top = AquaCoolingAutomaticGeometry.screenTopPadding,
                    end = AquaCoolingAutomaticGeometry.screenHorizontalPadding
                )
            )
        }
        if (state.hasFirmwareSnapshot) {
            DeviceCoolingAutomaticSettingsScreen(
                state = state,
                actions = actions,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private data class AutomaticStateMessage(
    @StringRes val titleRes: Int,
    @StringRes val messageRes: Int,
    val retryAvailable: Boolean
)

private fun automaticStateMessage(
    state: DeviceCoolingAutomaticSettingsUiState
): AutomaticStateMessage? = when (val dataState = state.dataState) {
    CoolingDataState.Initial,
    CoolingDataState.Loading -> automaticLoadingMessage()
    is CoolingDataState.Content -> contentAutomaticMessage(state, dataState.freshness)
    is CoolingDataState.Empty -> AutomaticStateMessage(
        titleRes = R.string.device_cooling_automatic_invalid_title,
        messageRes = R.string.device_cooling_automatic_invalid_message,
        retryAvailable = true
    )
    CoolingDataState.Unsupported -> AutomaticStateMessage(
        titleRes = R.string.device_cooling_automatic_unsupported_title,
        messageRes = R.string.device_cooling_automatic_unsupported_message,
        retryAvailable = false
    )
    CoolingDataState.Unavailable -> automaticUnavailableMessage()
    is CoolingDataState.OperationError -> dataState.failure.toAutomaticErrorMessage()
}

private fun contentAutomaticMessage(
    state: DeviceCoolingAutomaticSettingsUiState,
    freshness: CoolingDataFreshness
): AutomaticStateMessage? = when (freshness) {
    CoolingDataFreshness.REFRESHING -> AutomaticStateMessage(
        titleRes = R.string.device_cooling_automatic_refreshing_title,
        messageRes = R.string.device_cooling_automatic_refreshing_message,
        retryAvailable = false
    )
    CoolingDataFreshness.STALE -> AutomaticStateMessage(
        titleRes = R.string.device_cooling_automatic_stale_title,
        messageRes = R.string.device_cooling_automatic_stale_message,
        retryAvailable = true
    )
    CoolingDataFreshness.CURRENT -> if (state.editable) {
        null
    } else {
        AutomaticStateMessage(
            titleRes = R.string.device_cooling_automatic_read_only_title,
            messageRes = R.string.device_cooling_automatic_read_only_message,
            retryAvailable = false
        )
    }
}

private fun DeviceCoolingAutomaticFailure.toAutomaticErrorMessage(): AutomaticStateMessage =
    when (this) {
        DeviceCoolingAutomaticFailure.Unsupported -> AutomaticStateMessage(
            titleRes = R.string.device_cooling_automatic_unsupported_title,
            messageRes = R.string.device_cooling_automatic_unsupported_message,
            retryAvailable = false
        )
        DeviceCoolingAutomaticFailure.ReadOnly -> AutomaticStateMessage(
            titleRes = R.string.device_cooling_automatic_read_only_title,
            messageRes = R.string.device_cooling_automatic_read_only_message,
            retryAvailable = false
        )
        DeviceCoolingAutomaticFailure.InvalidConfiguration -> AutomaticStateMessage(
            titleRes = R.string.device_cooling_automatic_invalid_title,
            messageRes = R.string.device_cooling_automatic_invalid_message,
            retryAvailable = true
        )
        DeviceCoolingAutomaticFailure.Unavailable,
        DeviceCoolingAutomaticFailure.NotConnected,
        DeviceCoolingAutomaticFailure.TemporaryFailure,
        DeviceCoolingAutomaticFailure.Rejected -> automaticUnavailableMessage()
    }

private fun automaticLoadingMessage() = AutomaticStateMessage(
    titleRes = R.string.device_cooling_automatic_loading_title,
    messageRes = R.string.device_cooling_automatic_loading_message,
    retryAvailable = false
)

private fun automaticUnavailableMessage() = AutomaticStateMessage(
    titleRes = R.string.device_cooling_automatic_unavailable_title,
    messageRes = R.string.device_cooling_automatic_unavailable_message,
    retryAvailable = true
)
