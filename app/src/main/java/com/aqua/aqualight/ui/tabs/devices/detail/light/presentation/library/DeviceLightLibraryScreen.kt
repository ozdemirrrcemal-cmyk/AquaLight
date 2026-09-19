package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryEntry
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryFailure
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryPayload
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.common.devicecard.aquaDeviceCardTypography
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightLibraryAlpha
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightLibraryGeometry
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightManualColors
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.aquaLightManualColors
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.toCommercialLightReadError

@Composable
internal fun DeviceLightLibraryScreen(
    state: DeviceLightLibraryUiState,
    actions: DeviceLightLibraryActions,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false
) {
    val lightColors = aquaLightManualColors()
    val visuals = DeviceLightLibraryVisuals(
        colors = lightColors,
        typography = aquaDeviceCardTypography(lightColors.card)
    )
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colorResource(R.color.background_color)),
        contentPadding = PaddingValues(
            start = AquaLightLibraryGeometry.screenHorizontalPadding,
            top = AquaLightLibraryGeometry.screenTopPadding,
            end = AquaLightLibraryGeometry.screenHorizontalPadding,
            bottom = AquaLightLibraryGeometry.screenBottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(AquaLightLibraryGeometry.sectionGap)
    ) {
        if (!selectionMode) {
            item(key = "library-tabs") {
                LibraryTabs(state.selectedTab, actions.onTabSelected, visuals)
            }
        }
        when {
            state.initialLoading && !state.hasPresentationSnapshot -> Unit
            !state.hasPresentationSnapshot -> item(key = "library-error") {
                val error = state.readError
                    ?: DeviceLightLibraryFailure.INVALID_DATA.toCommercialLightReadError()
                LibraryMessageCard(
                    content = LibraryMessageContent(
                        iconRes = R.drawable.ic_error,
                        title = stringResource(error.titleRes),
                        message = stringResource(error.messageRes),
                        actionText = stringResource(R.string.device_light_library_retry)
                    ),
                    onAction = actions.onRetryClick,
                    visuals = visuals
                )
            }
            else -> {
                state.readError?.let { error ->
                    item(key = "library-refresh-warning") {
                        LibraryMessageCard(
                            content = LibraryMessageContent(
                                iconRes = R.drawable.ic_error,
                                title = stringResource(error.titleRes),
                                message = stringResource(error.messageRes),
                                actionText = stringResource(R.string.device_light_library_retry)
                            ),
                            onAction = actions.onRetryClick,
                            visuals = visuals
                        )
                    }
                }
                libraryContent(state, actions, visuals)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.libraryContent(
    state: DeviceLightLibraryUiState,
    actions: DeviceLightLibraryActions,
    visuals: DeviceLightLibraryVisuals
) {
    val cardPresentation = DeviceLightLibraryCardPresentation(
        descriptors = requireNotNull(state.target).channelDescriptors,
        selectable = selectionMode
    )
    item(key = "library-section-header") {
        LibrarySectionHeader(state, visuals)
    }
    if (state.visibleEntries.isEmpty()) {
        item(key = "library-empty-" + state.selectedTab.name) {
            LibraryEmptyState(state.selectedTab, visuals)
        }
    } else {
        items(state.visibleEntries, key = DeviceLightLibraryEntry::id) { entry ->
            when (val payload = entry.payload) {
                is DeviceLightLibraryPayload.Manual ->
                    ManualLibraryCard(
                        entry,
                        payload,
                        cardPresentation,
                        actions,
                        visuals
                    )
                is DeviceLightLibraryPayload.Custom ->
                    CustomLibraryCard(
                        entry,
                        payload,
                        cardPresentation,
                        actions,
                        visuals
                    )
            }
        }
    }
}

@Composable
private fun LibraryTabs(
    selected: DeviceLightLibraryTab,
    onSelected: (DeviceLightLibraryTab) -> Unit,
    visuals: DeviceLightLibraryVisuals
) {
    val shape = RoundedCornerShape(AquaLightLibraryGeometry.tabCornerRadius)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(AquaLightLibraryGeometry.tabHeight)
            .border(
                AquaLightLibraryGeometry.tabOutlineWidth,
                visuals.colors.card.outline,
                shape
            )
            .padding(AquaLightLibraryGeometry.tabInnerPadding)
    ) {
        LibraryTab(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.device_light_library_tab_manual),
            selected = selected == DeviceLightLibraryTab.MANUAL,
            onClick = { onSelected(DeviceLightLibraryTab.MANUAL) },
            visuals = visuals
        )
        Spacer(Modifier.width(AquaLightLibraryGeometry.tabInnerPadding))
        LibraryTab(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.device_light_library_tab_custom),
            selected = selected == DeviceLightLibraryTab.CUSTOM,
            onClick = { onSelected(DeviceLightLibraryTab.CUSTOM) },
            visuals = visuals
        )
    }
}

@Composable
private fun LibraryTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    visuals: DeviceLightLibraryVisuals,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(AquaLightLibraryGeometry.tabCornerRadius - 3.dp)
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(
                if (selected) {
                    visuals.colors.action.copy(alpha = AquaLightLibraryAlpha.selectedTabSurface)
                } else {
                    androidx.compose.ui.graphics.Color.Transparent
                },
                shape
            )
            .then(
                if (selected) {
                    Modifier.border(
                        AquaLightLibraryGeometry.tabOutlineWidth,
                        visuals.colors.action,
                        shape
                    )
                } else {
                    Modifier
                }
            )
            .clickable(role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = text,
            style = visuals.typography.body.copy(
                color = if (selected) {
                    visuals.colors.action
                } else {
                    visuals.colors.card.secondaryText
                },
                textAlign = TextAlign.Center
            )
        )
    }
}

@Composable
private fun LibrarySectionHeader(
    state: DeviceLightLibraryUiState,
    visuals: DeviceLightLibraryVisuals
) {
    val title = when (state.selectedTab) {
        DeviceLightLibraryTab.MANUAL ->
            stringResource(R.string.device_light_library_manual_records_title)
        DeviceLightLibraryTab.CUSTOM ->
            stringResource(R.string.device_light_library_custom_records_title)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AquaLightLibraryGeometry.sectionHeaderHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = title,
            style = visuals.typography.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(2.dp))
        LibrarySectionInformation(
            information = stringResource(
                when (state.selectedTab) {
                    DeviceLightLibraryTab.MANUAL ->
                        R.string.device_light_library_manual_information
                    DeviceLightLibraryTab.CUSTOM ->
                        R.string.device_light_library_custom_information
                }
            ),
            visuals = visuals
        )
        Spacer(Modifier.weight(1f))
        BasicText(
            text = pluralStringResource(
                R.plurals.device_light_library_record_count,
                state.visibleEntries.size,
                state.visibleEntries.size
            ),
            style = visuals.typography.caption
        )
    }
}

@Composable
private fun LibrarySectionInformation(
    information: String,
    visuals: DeviceLightLibraryVisuals
) {
    var expanded by remember { mutableStateOf(false) }
    val popupOffset = with(LocalDensity.current) {
        IntOffset(0, AquaLightLibraryGeometry.informationTouchSize.roundToPx())
    }
    Box {
        Box(
            modifier = Modifier
                .size(AquaLightLibraryGeometry.informationTouchSize)
                .clip(RoundedCornerShape(percent = 50))
                .clickable(role = Role.Button) { expanded = !expanded }
                .semantics { contentDescription = information },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_info),
                contentDescription = null,
                colorFilter = ColorFilter.tint(visuals.colors.card.secondaryText),
                modifier = Modifier.size(AquaLightLibraryGeometry.informationIconSize)
            )
        }
        if (expanded) {
            Popup(
                alignment = Alignment.TopStart,
                offset = popupOffset,
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true)
            ) {
                BasicText(
                    text = information,
                    style = visuals.typography.caption,
                    modifier = Modifier
                        .widthIn(max = AquaLightLibraryGeometry.informationPopupMaxWidth)
                        .background(
                            visuals.colors.card.surface,
                            RoundedCornerShape(AquaLightLibraryGeometry.badgeCornerRadius)
                        )
                        .border(
                            AquaLightLibraryGeometry.tabOutlineWidth,
                            visuals.colors.card.outline,
                            RoundedCornerShape(AquaLightLibraryGeometry.badgeCornerRadius)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun LibraryEmptyState(
    tab: DeviceLightLibraryTab,
    visuals: DeviceLightLibraryVisuals
) {
    LibraryMessageCard(
        content = LibraryMessageContent(
            iconRes = R.drawable.ic_light_library,
            title = stringResource(
                if (tab == DeviceLightLibraryTab.MANUAL) {
                    R.string.device_light_library_empty_manual_title
                } else {
                    R.string.device_light_library_empty_custom_title
                }
            ),
            message = stringResource(
                if (tab == DeviceLightLibraryTab.MANUAL) {
                    R.string.device_light_library_empty_manual_message
                } else {
                    R.string.device_light_library_empty_custom_message
                }
            ),
            actionText = null
        ),
        onAction = null,
        visuals = visuals
    )
}

@Immutable
private data class LibraryMessageContent(
    val iconRes: Int,
    val title: String,
    val message: String,
    val actionText: String?
)

@Composable
private fun LibraryMessageCard(
    content: LibraryMessageContent,
    onAction: (() -> Unit)?,
    visuals: DeviceLightLibraryVisuals
) {
    AquaDeviceCardSurface(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = AquaLightLibraryGeometry.emptyVerticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AquaLightLibraryGeometry.emptyTextGap)
        ) {
            Image(
                painter = painterResource(content.iconRes),
                contentDescription = null,
                colorFilter = ColorFilter.tint(visuals.colors.action),
                modifier = Modifier.size(AquaLightLibraryGeometry.emptyIconSize)
            )
            BasicText(text = content.title, style = visuals.typography.title)
            BasicText(
                text = content.message,
                style = visuals.typography.caption.copy(textAlign = TextAlign.Center)
            )
            if (content.actionText != null && onAction != null) {
                Spacer(Modifier.height(4.dp))
                LibraryTextAction(content.actionText, onAction, visuals)
            }
        }
    }
}

@Composable
private fun LibraryTextAction(
    text: String,
    onClick: () -> Unit,
    visuals: DeviceLightLibraryVisuals
) {
    val shape = RoundedCornerShape(AquaLightLibraryGeometry.badgeCornerRadius)
    BasicText(
        text = text,
        style = visuals.typography.body.copy(
            color = visuals.colors.action,
            textAlign = TextAlign.Center
        ),
        modifier = Modifier
            .border(1.dp, visuals.colors.action, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    )
}

@Immutable
internal data class DeviceLightLibraryVisuals(
    val colors: AquaLightManualColors,
    val typography: AquaDeviceCardTypography
)
